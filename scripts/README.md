# Mandi Price Sync Service

A local Python service that fetches agricultural market (mandi) prices from data.gov.in and syncs them to Firebase Firestore every hour.

## Why Local?

The data.gov.in API blocks requests from Google Cloud Platform (GCP) IP ranges, preventing Firebase Cloud Functions from fetching data. This local service runs from your own network, bypassing those restrictions.

## Prerequisites

- Python 3.9+
- Firebase project with Firestore enabled
- Firebase service account key

## Setup

### 1. Install Dependencies

```bash
cd scripts
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Get Firebase Service Account Key

1. Go to [Firebase Console](https://console.firebase.google.com) → Your Project
2. Click ⚙️ Settings → Project settings → Service accounts
3. Click "Generate new private key"
4. Save the downloaded JSON file as `scripts/serviceAccountKey.json`

> ⚠️ **Security**: Never commit `serviceAccountKey.json` to version control!

### 3. (Optional) Set Environment Variables

If you want to customize settings:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
export DATA_GOV_API_KEY="your_api_key_if_different"
```

## Usage

### Run as Foreground Service

```bash
cd scripts
source venv/bin/activate
python mandi_sync_service.py
```

This will:
- Run an immediate sync on startup
- Then sync every hour
- Log to console and `mandi_sync.log`

Press `Ctrl+C` to stop gracefully.

### Run Single Sync (for cron/testing)

```bash
python mandi_sync_service.py --once
```

### Run as Background Service (macOS)

1. Edit the launchd plist to update paths:
   ```bash
   nano com.androidifyxy.mandisync.plist
   ```

2. Update these paths to match your system:
   - `/Users/m1/AndroidStudioProjects/androidifyxy/scripts/` → your actual path
   - Python path in the venv

3. Install the service:
   ```bash
   cp com.androidifyxy.mandisync.plist ~/Library/LaunchAgents/
   launchctl load ~/Library/LaunchAgents/com.androidifyxy.mandisync.plist
   ```

4. Check status:
   ```bash
   launchctl list | grep mandisync
   ```

5. View logs:
   ```bash
   tail -f /tmp/mandisync.log
   tail -f scripts/mandi_sync.log
   ```

6. Stop service:
   ```bash
   launchctl unload ~/Library/LaunchAgents/com.androidifyxy.mandisync.plist
   ```

## Logs

- `mandi_sync.log` - Application log with sync details
- `/tmp/mandisync.log` - stdout/stderr when running via launchd

## Configuration

Edit `mandi_sync_service.py` to change:

| Variable | Default | Description |
|----------|---------|-------------|
| `SYNC_INTERVAL_SECONDS` | 3600 | Sync interval (1 hour) |
| `COLLECTION_NAME` | `mandi_prices` | Firestore collection name |
| `MAX_RETRIES` | 5 | API request retry attempts |
| `BATCH_SIZE` | 500 | Firestore batch write size |

## Troubleshooting

### "serviceAccountKey.json not found"
- Download the service account key from Firebase Console
- Place it in the `scripts/` directory
- Or set `GOOGLE_APPLICATION_CREDENTIALS` environment variable

### Connection errors
- Check your internet connection
- The data.gov.in API may be temporarily down
- Check `mandi_sync.log` for details

### Firebase permission errors
- Ensure the service account has Firestore read/write permissions
- Check that your Firebase project ID matches

## Files

- `mandi_sync_service.py` - Main service script
- `requirements.txt` - Python dependencies
- `serviceAccountKey.json` - Firebase credentials (you create this)
- `com.androidifyxy.mandisync.plist` - macOS launchd config
- `mandi_sync.log` - Application log
