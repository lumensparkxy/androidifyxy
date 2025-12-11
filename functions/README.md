# Mandi Prices Cloud Function

This Firebase Cloud Function syncs commodity prices from the Government of India's Open Data API to Firestore.

## Setup

1. **Install Firebase CLI** (if not already installed):
   ```bash
   npm install -g firebase-tools
   ```

2. **Login to Firebase**:
   ```bash
   firebase login
   ```

3. **Initialize Firebase in the project** (if not already done):
   ```bash
   cd /path/to/androidifyxy
   firebase init functions
   ```
   - Select your existing Firebase project
   - Choose JavaScript as the language
   - Say "No" to overwriting existing files

4. **Install dependencies**:
   ```bash
   cd functions
   npm install
   ```

## Deployment

Deploy the Cloud Functions:

```bash
firebase deploy --only functions
```

## Functions

### `syncMandiPrices` (Scheduled)
- **Schedule**: Runs daily at 6:00 AM IST (00:30 UTC)
- **Region**: asia-south1
- **Purpose**: Automatically fetches and syncs all mandi prices from the government API to Firestore

### `syncMandiPricesManual` (HTTP)
- **Endpoint**: `https://asia-south1-<project-id>.cloudfunctions.net/syncMandiPricesManual`
- **Purpose**: Manual trigger for testing or one-time sync
- **Usage**: Simply make a GET or POST request to the endpoint

## Firestore Structure

### Collection: `mandi_prices`

Each document contains:
| Field | Type | Description |
|-------|------|-------------|
| state | string | State name |
| district | string | District name |
| market | string | Market/Mandi name |
| commodity | string | Commodity name |
| variety | string | Variety of commodity |
| grade | string | Grade (e.g., Local, Medium) |
| arrival_date | string | Date in DD/MM/YYYY format |
| min_price | number | Minimum price per quintal |
| max_price | number | Maximum price per quintal |
| modal_price | number | Modal (most common) price per quintal |
| updated_at | timestamp | Last update timestamp |

### Collection: `metadata`

Document `mandi_prices_sync` contains:
| Field | Type | Description |
|-------|------|-------------|
| last_sync | timestamp | Last successful sync time |
| record_count | number | Number of records synced |
| status | string | "success" or error message |

## Firestore Indexes

Deploy the indexes for optimal query performance:

```bash
firebase deploy --only firestore:indexes
```

The indexes support filtering by:
- state + district
- state + commodity
- state + district + commodity
- state + district + market
- state + district + market + commodity

## API Source

Data is fetched from: [data.gov.in - Daily Mandi Prices](https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070)

## Monitoring

View function logs:

```bash
firebase functions:log
```

## Costs

- **Cloud Functions**: Free tier includes 2 million invocations/month
- **Firestore**: Free tier includes 50K reads, 20K writes, 20K deletes per day
- **Scheduled functions**: Uses Cloud Scheduler (free tier: 3 jobs)

With ~15,000 records updated daily, this should stay well within free tier limits.

