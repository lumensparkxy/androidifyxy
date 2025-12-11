const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const fetch = require("node-fetch");

admin.initializeApp();

const db = admin.firestore();
const COLLECTION_NAME = "mandi_prices";
const REQUEST_TIMEOUT_MS = 60000; // 60s per API call
const FETCH_RETRIES = 2;

// API configuration
const API_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070";
const API_KEY = "579b464db66ec23bdd0000015d0d42cb9328410e6bd0a1af77fa3f53";
const BATCH_SIZE = 500; // Firestore batch write limit

/**
 * Scheduled function to sync Mandi prices daily at 6 AM IST
 * Runs every day at 00:30 UTC (6:00 AM IST)
 */
exports.syncMandiPrices = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 540,
    memory: "512MB",
  })
  .pubsub.schedule("30 0 * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async (context) => {
    console.log("Starting Mandi prices sync...");

    try {
      await syncPricesFromAPI();
      console.log("Mandi prices sync completed successfully");
      return null;
    } catch (error) {
      console.error("Error syncing Mandi prices:", error);
      throw error;
    }
  });

/**
 * HTTP endpoint to manually trigger sync (for testing)
 */
exports.syncMandiPricesManual = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 540,
    memory: "512MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Manual Mandi prices sync triggered");

    try {
      const result = await syncPricesFromAPI();
      res.json({
        success: true,
        message: "Sync completed",
        recordsProcessed: result.recordsProcessed,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error("Error in manual sync:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  });

/**
 * Fetch data from API and store in Firestore
 */
async function syncPricesFromAPI() {
  let offset = 0;
  const limit = 1000;
  let totalRecords = 0;
  const stateFilter = "Maharashtra";  
  let hasMore = true;

  // Delete records older than 7 days (keep recent data for fallback)
  await deleteOldRecords(7);

  while (hasMore) {
    const url = `${API_URL}?format=json&api-key=${API_KEY}&limit=${limit}&offset=${offset}&filters[state.keyword]=${stateFilter}`;

    console.log(`Fetching data with offset ${offset}...`);
    const data = await fetchJsonWithRetry(url);

    if (!data.records || data.records.length === 0) {
      hasMore = false;
      break;
    }

    // Process records in batches
    await processBatch(data.records);
    totalRecords += data.records.length;

    console.log(`Processed ${totalRecords} records so far...`);

    // Check if there are more records
    if (data.records.length < limit) {
      hasMore = false;
    } else {
      offset += limit;
    }
  }

  console.log(`Total records synced: ${totalRecords}`);

  // Update sync metadata
  await updateSyncMetadata(totalRecords);

  return { recordsProcessed: totalRecords };
}

/**
 * Process a batch of records and write to Firestore
 */
async function processBatch(records) {
  const batches = [];
  let currentBatch = db.batch();
  let operationCount = 0;

  for (const record of records) {
    // Create a unique document ID based on market, commodity, and date
    const docId = generateDocId(record);
    const docRef = db.collection(COLLECTION_NAME).doc(docId);

    // Parse arrival_date (format: DD/MM/YYYY) to Firestore Timestamp
    const arrivalDateParsed = parseArrivalDate(record.arrival_date);

    const docData = {
      state: record.state || "",
      district: record.district || "",
      market: record.market || "",
      commodity: record.commodity || "",
      variety: record.variety || "",
      grade: record.grade || "",
      arrival_date: record.arrival_date || "",
      arrival_date_parsed: arrivalDateParsed,
      min_price: parseFloat(record.min_price) || 0,
      max_price: parseFloat(record.max_price) || 0,
      modal_price: parseFloat(record.modal_price) || 0,
      updated_at: admin.firestore.FieldValue.serverTimestamp(),
    };

    currentBatch.set(docRef, docData, { merge: true });
    operationCount++;

    if (operationCount >= BATCH_SIZE) {
      batches.push(currentBatch.commit());
      currentBatch = db.batch();
      operationCount = 0;
    }
  }

  // Commit remaining operations
  if (operationCount > 0) {
    batches.push(currentBatch.commit());
  }

  await Promise.all(batches);
}

/**
 * Generate a unique document ID for a record
 */
function generateDocId(record) {
  const components = [
    sanitizeForId(record.state),
    sanitizeForId(record.district),
    sanitizeForId(record.market),
    sanitizeForId(record.commodity),
    sanitizeForId(record.variety),
    sanitizeForId(record.arrival_date),
  ];
  return components.join("_");
}

/**
 * Parse arrival date from DD/MM/YYYY format to Firestore Timestamp
 */
function parseArrivalDate(dateStr) {
  if (!dateStr) return null;

  try {
    // Format: DD/MM/YYYY
    const parts = dateStr.split("/");
    if (parts.length !== 3) return null;

    const day = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1; // JS months are 0-indexed
    const year = parseInt(parts[2], 10);

    const date = new Date(year, month, day);
    return admin.firestore.Timestamp.fromDate(date);
  } catch (error) {
    console.error(`Error parsing date ${dateStr}:`, error);
    return null;
  }
}

/**
 * Sanitize a string for use in document ID
 */
function sanitizeForId(str) {
  if (!str) return "unknown";
  return str
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "_")
    .substring(0, 30);
}

/**
 * Fetch JSON with timeout and retries (handles gateway timeouts better)
 */
async function fetchJsonWithRetry(url, retries = FETCH_RETRIES, timeoutMs = REQUEST_TIMEOUT_MS) {
  let attempt = 0;
  let lastError;

  while (attempt <= retries) {
    console.log(`Fetch attempt ${attempt + 1}/${retries + 1} starting...`);

    try {
      // Use timeout option directly (node-fetch v2 supports it)
      const response = await fetch(url, { timeout: timeoutMs });

      if (!response.ok) {
        throw new Error(`API request failed: ${response.status} ${response.statusText}`);
      }

      const data = await response.json();
      console.log(`Fetch attempt ${attempt + 1} succeeded, got ${data.records?.length || 0} records`);
      return data;
    } catch (error) {
      lastError = error;
      attempt++;

      if (attempt > retries) {
        console.error(`All ${retries + 1} fetch attempts failed. Last error: ${error.message}`);
        throw lastError;
      }

      const backoffMs = 2000 * attempt; // 2s, 4s
      console.warn(`Fetch attempt ${attempt} failed (${error.message}). Retrying in ${backoffMs}ms...`);
      await new Promise((resolve) => setTimeout(resolve, backoffMs));
    }
  }

  throw lastError;
}

/**
 * Delete records older than specified days (keeps recent data for fallback)
 */
async function deleteOldRecords(daysToKeep = 7) {
  console.log(`Deleting records older than ${daysToKeep} days...`);

  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);
  cutoffDate.setHours(0, 0, 0, 0); // Start of day

  const cutoffTimestamp = admin.firestore.Timestamp.fromDate(cutoffDate);

  const collectionRef = db.collection(COLLECTION_NAME);
  let deleted = 0;

  while (true) {
    // Query for old records
    const oldRecordsSnapshot = await collectionRef
      .where("arrival_date_parsed", "<", cutoffTimestamp)
      .limit(500)
      .get();

    if (oldRecordsSnapshot.empty) {
      break;
    }

    const batch = db.batch();
    oldRecordsSnapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    deleted += oldRecordsSnapshot.size;
    console.log(`Deleted ${deleted} old documents...`);
  }

  console.log(`Cleanup complete: Deleted ${deleted} records older than ${daysToKeep} days`);
}

/**
 * Update sync metadata for monitoring
 */
async function updateSyncMetadata(recordCount) {
  await db.collection("metadata").doc("mandi_prices_sync").set({
    last_sync: admin.firestore.FieldValue.serverTimestamp(),
    record_count: recordCount,
    status: "success",
  });
}

