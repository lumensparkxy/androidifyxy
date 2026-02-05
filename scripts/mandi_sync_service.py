#!/usr/bin/env python3
"""
Mandi Price Sync Service

A local Python service that fetches mandi (agricultural market) prices from
data.gov.in API and syncs them to Firebase Firestore every hour.

Usage:
    python mandi_sync_service.py

Environment Variables:
    FIREBASE_APPLICATION_CREDENTIALS: Path to Firebase service account JSON
    DATA_GOV_API_KEY: API key for data.gov.in (optional, has default)
"""

import os
import sys
import time
import signal
import logging
import json
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
import firebase_admin
from firebase_admin import credentials, firestore

# ============================================================================
# Configuration
# ============================================================================

API_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070"
API_KEY = os.getenv("DATA_GOV_API_KEY", "579b464db66ec23bdd0000015d0d42cb9328410e6bd0a1af77fa3f53")
COLLECTION_NAME = "mandi_prices"
BATCH_SIZE = 500  # Firestore batch write limit
SYNC_INTERVAL_SECONDS = 3600  # 1 hour
REQUEST_TIMEOUT_SECONDS = 60
MAX_RETRIES = 5
INITIAL_BACKOFF_SECONDS = 2

# User agent to avoid being blocked
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

# List of all Indian states for fetching data (API has 10k record limit, so we fetch per state)
INDIAN_STATES = [
    "Andaman and Nicobar",
    "Andhra Pradesh",
    "Arunachal Pradesh",
    "Assam",
    "Bihar",
    "Chandigarh",
    "Chattisgarh",
    "Daman and Diu",
    "Goa",
    "Gujarat",
    "Haryana",
    "Himachal Pradesh",
    "Jammu and Kashmir",
    "Jharkhand",
    "Karnataka",
    "Kerala",
    "Madhya Pradesh",
    "Maharashtra",
    "Manipur",
    "Meghalaya",
    "Mizoram",
    "NCT of Delhi",
    "Nagaland",
    "Odisha",
    "Puducherry",
    "Punjab",
    "Rajasthan",
    "Sikkim",
    "Tamil Nadu",
    "Telangana",
    "Tripura",
    "Uttar Pradesh",
    "Uttarakhand",
    "West Bengal",
]

# ============================================================================
# Logging Setup
# ============================================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler("mandi_sync.log", encoding="utf-8"),
    ],
)
logger = logging.getLogger(__name__)

# ============================================================================
# Graceful Shutdown Handling
# ============================================================================

shutdown_requested = False


def signal_handler(signum, frame):
    """Handle shutdown signals gracefully."""
    global shutdown_requested
    logger.info(f"Received signal {signum}. Requesting graceful shutdown...")
    shutdown_requested = True


signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

# ============================================================================
# HTTP Session with Retry Logic
# ============================================================================


def create_session() -> requests.Session:
    """Create a requests session with retry logic and proper headers."""
    session = requests.Session()

    # Configure retry strategy
    retry_strategy = Retry(
        total=MAX_RETRIES,
        backoff_factor=INITIAL_BACKOFF_SECONDS,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET"],
        raise_on_status=False,
    )

    adapter = HTTPAdapter(max_retries=retry_strategy)
    session.mount("https://", adapter)
    session.mount("http://", adapter)

    # Set headers to mimic browser
    session.headers.update({
        "User-Agent": USER_AGENT,
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive",
        "Cache-Control": "no-cache",
    })

    return session


# ============================================================================
# Firebase Initialization
# ============================================================================


def initialize_firebase() -> firestore.Client:
    """Initialize Firebase Admin SDK and return Firestore client."""
    cred_path = os.getenv("FIREBASE_APPLICATION_CREDENTIALS")

    if not cred_path:
        # Try default location in scripts directory
        default_path = os.path.join(os.path.dirname(__file__), "serviceAccountKey.json")
        if os.path.exists(default_path):
            cred_path = default_path
            logger.info(f"Using service account key from: {default_path}")
        else:
            raise ValueError(
                "FIREBASE_APPLICATION_CREDENTIALS not set and serviceAccountKey.json not found.\n"
                "Please either:\n"
                "  1. Set FIREBASE_APPLICATION_CREDENTIALS environment variable, or\n"
                "  2. Place serviceAccountKey.json in the scripts/ directory"
            )

    if not firebase_admin._apps:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
        logger.info("Firebase initialized successfully")

    return firestore.client()


# ============================================================================
# API Fetching
# ============================================================================


def fetch_mandi_data(
    session: requests.Session,
    limit: int = 1000,
    offset: int = 0,
    state: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """
    Fetch mandi price data from data.gov.in API.
    
    Args:
        session: Requests session with retry logic
        limit: Number of records per request
        offset: Pagination offset
        state: Optional state filter to bypass 10k record limit
        
    Returns:
        API response as dict, or None if failed
    """
    params = {
        "api-key": API_KEY,
        "format": "json",
        "limit": limit,
        "offset": offset,
    }
    
    # Add state filter if specified
    if state:
        params["filters[state]"] = state

    for attempt in range(MAX_RETRIES):
        try:
            logger.debug(f"Fetching data: offset={offset}, state={state}, attempt={attempt + 1}")
            
            response = session.get(
                API_URL,
                params=params,
                timeout=REQUEST_TIMEOUT_SECONDS,
            )

            if response.status_code == 200:
                data = response.json()
                record_count = len(data.get("records", []))
                logger.debug(f"Fetched {record_count} records at offset {offset}")
                return data
            else:
                logger.warning(f"API returned status {response.status_code}: {response.text[:200]}")
                
        except requests.exceptions.Timeout:
            logger.warning(f"Request timeout (attempt {attempt + 1}/{MAX_RETRIES})")
        except requests.exceptions.ConnectionError as e:
            logger.warning(f"Connection error (attempt {attempt + 1}/{MAX_RETRIES}): {e}")
        except json.JSONDecodeError as e:
            logger.warning(f"Invalid JSON response (attempt {attempt + 1}/{MAX_RETRIES}): {e}")
        except Exception as e:
            logger.error(f"Unexpected error (attempt {attempt + 1}/{MAX_RETRIES}): {e}")

        if attempt < MAX_RETRIES - 1:
            backoff = INITIAL_BACKOFF_SECONDS * (2 ** attempt)
            logger.info(f"Retrying in {backoff} seconds...")
            time.sleep(backoff)

    logger.error(f"Failed to fetch data after {MAX_RETRIES} attempts")
    return None


# ============================================================================
# Firestore Operations
# ============================================================================


def sanitize_for_id(value: str) -> str:
    """
    Sanitize a string for use in Firestore document ID.
    Matches the Node.js sanitizeForId function in index.js.
    """
    if not value:
        return "unknown"
    # Replace any non-alphanumeric character with underscore, limit to 30 chars
    import re
    sanitized = re.sub(r"[^a-z0-9]", "_", value.lower())
    return sanitized[:30] if sanitized else "unknown"


def create_document_id(record: Dict[str, Any]) -> str:
    """
    Create a unique document ID for a mandi price record.
    Matches the Node.js generateDocId function in index.js.
    Components: state, district, market, commodity, variety, arrival_date
    """
    components = [
        sanitize_for_id(record.get("state", "")),
        sanitize_for_id(record.get("district", "")),
        sanitize_for_id(record.get("market", "")),
        sanitize_for_id(record.get("commodity", "")),
        sanitize_for_id(record.get("variety", "")),
        sanitize_for_id(record.get("arrival_date", "")),
    ]
    return "_".join(components)


def parse_arrival_date(date_str: str) -> Optional[datetime]:
    """
    Parse arrival date from DD/MM/YYYY format to datetime.
    Matches the Node.js parseArrivalDate function in index.js.
    """
    if not date_str:
        return None
    try:
        parts = date_str.split("/")
        if len(parts) != 3:
            return None
        day = int(parts[0])
        month = int(parts[1])
        year = int(parts[2])
        return datetime(year, month, day, tzinfo=timezone.utc)
    except (ValueError, IndexError) as e:
        logger.debug(f"Error parsing date {date_str}: {e}")
        return None


def transform_record(record: Dict[str, Any]) -> Dict[str, Any]:
    """
    Transform API record to Firestore document format.
    Matches the Node.js processBatch docData structure in index.js.
    """
    def safe_float(value: Any) -> float:
        """Parse float, defaulting to 0 (matches parseFloat() || 0 in JS)."""
        if value is None or value == "":
            return 0
        try:
            return float(value)
        except (ValueError, TypeError):
            return 0

    # Parse arrival_date to Firestore-compatible datetime
    arrival_date_parsed = parse_arrival_date(record.get("arrival_date", ""))

    doc_data = {
        "state": record.get("state", ""),
        "district": record.get("district", ""),
        "market": record.get("market", ""),
        "commodity": record.get("commodity", ""),
        "variety": record.get("variety", ""),
        "grade": record.get("grade", ""),
        "arrival_date": record.get("arrival_date", ""),
        "arrival_date_parsed": arrival_date_parsed,
        "min_price": safe_float(record.get("min_price")),
        "max_price": safe_float(record.get("max_price")),
        "modal_price": safe_float(record.get("modal_price")),
        "updated_at": firestore.SERVER_TIMESTAMP,
    }

    return doc_data


def batch_write_to_firestore(db: firestore.Client, records: List[Dict[str, Any]]) -> int:
    """
    Write records to Firestore in batches.
    
    Args:
        db: Firestore client
        records: List of records to write
        
    Returns:
        Number of records written
    """
    if not records:
        return 0

    collection_ref = db.collection(COLLECTION_NAME)
    written = 0

    for i in range(0, len(records), BATCH_SIZE):
        batch = db.batch()
        batch_records = records[i:i + BATCH_SIZE]

        for record in batch_records:
            doc_id = create_document_id(record)
            doc_data = transform_record(record)
            doc_ref = collection_ref.document(doc_id)
            batch.set(doc_ref, doc_data, merge=True)

        try:
            batch.commit()
            written += len(batch_records)
            logger.debug(f"Committed batch of {len(batch_records)} records (total: {written})")
        except Exception as e:
            logger.error(f"Error committing batch: {e}")
            raise

    return written


# ============================================================================
# Main Sync Logic
# ============================================================================


def fetch_all_records_for_state(session: requests.Session, state: str) -> List[Dict[str, Any]]:
    """
    Fetch all records for a specific state with pagination.
    
    Args:
        session: Requests session with retry logic
        state: State name to filter by
        
    Returns:
        List of all records for the state
    """
    records = []
    offset = 0
    limit = 1000
    
    while True:
        data = fetch_mandi_data(session, limit=limit, offset=offset, state=state)
        
        if data is None:
            logger.warning(f"Failed to fetch data for state {state} at offset {offset}")
            break
            
        batch = data.get("records", [])
        if not batch:
            break
            
        records.extend(batch)
        
        # Check if there are more records
        if len(batch) < limit:
            break
        
        offset += limit
        time.sleep(0.3)  # Small delay between pages
    
    return records


def sync_mandi_prices(db: firestore.Client, session: requests.Session) -> Dict[str, Any]:
    """
    Fetch all mandi prices from API (by state to bypass 10k limit) and sync to Firestore.
    
    Returns:
        Summary of sync operation
    """
    start_time = time.time()
    all_records = []
    states_with_data = []
    limit = 1000

    logger.info("Starting mandi price sync (fetching by state to bypass 10k limit)...")
    
    # Fetch records for each state
    for state in INDIAN_STATES:
        # First check if state has any data
        data = fetch_mandi_data(session, limit=1, offset=0, state=state)
        if data is None:
            continue
            
        total = data.get("total", 0)
        if total == 0:
            continue
        
        logger.info(f"Fetching {state}: {total} records...")
        
        # Fetch all records for this state
        state_records = fetch_all_records_for_state(session, state)
        
        if state_records:
            all_records.extend(state_records)
            states_with_data.append(state)
            logger.info(f"  → Got {len(state_records)} records for {state}")
        
        # Delay between states to avoid rate limiting (30 seconds)
        time.sleep(30)

    if not all_records:
        return {
            "success": False,
            "message": "No records fetched",
            "records_processed": 0,
            "duration_seconds": time.time() - start_time,
        }

    # Write to Firestore
    logger.info(f"Writing {len(all_records)} records to Firestore...")
    written = batch_write_to_firestore(db, all_records)

    duration = time.time() - start_time
    
    result = {
        "success": True,
        "message": "Sync completed",
        "records_processed": written,
        "states": sorted(states_with_data),
        "duration_seconds": round(duration, 2),
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

    logger.info(
        f"Sync completed: {written} records from {len(states_with_data)} states in {duration:.2f}s"
    )

    return result


# ============================================================================
# Service Loop
# ============================================================================


def run_service():
    """Run the sync service in a loop."""
    logger.info("=" * 60)
    logger.info("Mandi Price Sync Service")
    logger.info("=" * 60)
    logger.info(f"Sync interval: {SYNC_INTERVAL_SECONDS} seconds ({SYNC_INTERVAL_SECONDS / 3600:.1f} hours)")
    logger.info(f"API URL: {API_URL}")
    logger.info(f"Firestore collection: {COLLECTION_NAME}")
    logger.info("=" * 60)

    # Initialize Firebase
    try:
        db = initialize_firebase()
    except Exception as e:
        logger.error(f"Failed to initialize Firebase: {e}")
        sys.exit(1)

    # Create HTTP session
    session = create_session()

    # Run initial sync
    logger.info("Running initial sync...")
    try:
        result = sync_mandi_prices(db, session)
        logger.info(f"Initial sync result: {json.dumps(result, indent=2)}")
    except Exception as e:
        logger.error(f"Initial sync failed: {e}")

    # Service loop
    while not shutdown_requested:
        next_run = datetime.now(timezone.utc).timestamp() + SYNC_INTERVAL_SECONDS
        next_run_dt = datetime.fromtimestamp(next_run, timezone.utc)
        
        logger.info(f"Next sync scheduled at: {next_run_dt.isoformat()}")
        logger.info(f"Sleeping for {SYNC_INTERVAL_SECONDS} seconds...")

        # Sleep in small intervals to allow graceful shutdown
        sleep_interval = 30  # Check every 30 seconds
        remaining = SYNC_INTERVAL_SECONDS
        
        while remaining > 0 and not shutdown_requested:
            sleep_time = min(sleep_interval, remaining)
            time.sleep(sleep_time)
            remaining -= sleep_time

        if shutdown_requested:
            break

        # Run sync
        logger.info("=" * 40)
        logger.info("Starting scheduled sync...")
        try:
            result = sync_mandi_prices(db, session)
            logger.info(f"Sync result: {json.dumps(result, indent=2)}")
        except Exception as e:
            logger.error(f"Sync failed: {e}")

    logger.info("Service stopped gracefully")


def run_once():
    """Run a single sync (for testing or cron-based execution)."""
    logger.info("Running single sync...")

    db = initialize_firebase()
    session = create_session()
    result = sync_mandi_prices(db, session)

    print(json.dumps(result, indent=2))
    return result


# ============================================================================
# Entry Point
# ============================================================================

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--once":
        # Single run mode (for cron or manual testing)
        run_once()
    else:
        # Service mode (continuous loop)
        run_service()
