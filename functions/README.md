# Firebase Cloud Functions

This directory contains Firebase Cloud Functions for the Androidify app.

## Overview

| Function | Type | Purpose |
|----------|------|---------|
| `syncMandiPrices` | Scheduled | Syncs commodity prices from Government API |
| `syncMandiPricesManual` | HTTP | Manual trigger for mandi price sync |
| `seedKnowledgeCrops` | HTTP | Seeds/updates knowledge base crops |
| `seedKnowledgeDocuments` | HTTP | Seeds/updates knowledge base documents |
| `deleteAllKnowledgeCrops` | HTTP | Deletes all crops (use with caution) |
| `deleteAllKnowledgeDocuments` | HTTP | Deletes all documents (use with caution) |
| `aggregateSupplierClicks` | Scheduled | Weekly supplier click aggregation |
| `aggregateSupplierClicksManual` | HTTP | Manual trigger for supplier click aggregation |
| `cleanupOldClicks` | Scheduled | Cleanup old supplier click records |
| `publishOffer` | Callable | Offer activation (placeholder) |

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
- **Schedule**: Runs hourly between 1:00 PM and 8:00 PM IST
- **Region**: asia-south1
- **Purpose**: Automatically fetches and syncs all mandi prices from the government API to Firestore
- **Filter**: Currently restricted to `Maharashtra` in `functions/index.js`

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

---

## Knowledge Base Seeding

The Knowledge Base feature displays crops and PDF documents to farmers. The data is stored in Firestore and seeded using Cloud Functions.

### Seed Data Files

Located in `/functions/seeds/`:

| File | Collection | Purpose |
|------|------------|---------|
| `knowledge_crops.json` | `knowledge_crops` | Crop categories with icons |
| `knowledge_documents.json` | `knowledge_documents` | PDF documents per crop |

### Collection: `knowledge_crops`

| Field | Type | Description |
|-------|------|-------------|
| name | string | English name (e.g., "Soybean") |
| names | map | Localized names (`hi`, `mr`, `ta`, `te`) |
| iconUrl | string | Firebase Storage URL for crop icon |
| displayOrder | number | Sort order for display |
| updatedAt | timestamp | Last update timestamp |

### Collection: `knowledge_documents`

| Field | Type | Description |
|-------|------|-------------|
| cropId | string | Reference to crop (e.g., "soybean") |
| title | string | English title |
| titles | map | Localized titles (`hi`, `mr`, `ta`, `te`) |
| description | string | English description |
| descriptions | map | Localized descriptions |
| storagePath | string | Path to PDF in Firebase Storage |
| displayOrder | number | Sort order within crop |
| updatedAt | timestamp | Last update timestamp |

### How to Seed/Update Crops

1. **Edit the data file**:
   ```bash
   # Edit /functions/seeds/knowledge_crops.json
   ```

   Example structure:
   ```json
   {
     "crops": [
       {
         "id": "soybean",
         "name": "Soybean",
         "names": {
           "hi": "सोयाबीन",
           "mr": "सोयाबीन",
           "ta": "சோயாபீன்",
           "te": "సోయాబీన్"
         },
         "iconUrl": "https://firebasestorage.googleapis.com/...",
         "displayOrder": 1
       }
     ]
   }
   ```

2. **Deploy the function**:
   ```bash
   firebase deploy --only functions:seedKnowledgeCrops --project lumensparkxy
   ```

3. **Execute to seed Firestore**:
   ```bash
   curl "https://asia-south1-lumensparkxy.cloudfunctions.net/seedKnowledgeCrops"
   ```

### How to Seed/Update Documents

1. **Edit the data file**:
   ```bash
   # Edit /functions/seeds/knowledge_documents.json
   ```

   Example structure:
   ```json
   {
     "documents": [
       {
         "id": "soybean_sowing_guide",
         "cropId": "soybean",
         "title": "Soybean Sowing Guide",
         "titles": {
           "hi": "सोयाबीन बुवाई गाइड",
           "mr": "सोयाबीन पेरणी पत्रक",
           "ta": "சோயாபீன் விதை வழிகாட்டி",
           "te": "సోయాబీన్ విత్తన మార్గదర్శకం"
         },
         "description": "Guide for Soybean Cultivation",
         "descriptions": {
           "hi": "सोयाबीन गाइड विवरण",
           "mr": "सोयाबीन Guide description",
           "ta": "சோயாபீன் வழிகாட்டி விளக்கம்",
           "te": "సోయాబీన్ గైడ్ వివరణ"
         },
         "storagePath": "knowledge/soybean/soybean_sowing.pdf",
         "displayOrder": 1
       }
     ]
   }
   ```

2. **Deploy the function**:
   ```bash
   firebase deploy --only functions:seedKnowledgeDocuments --project lumensparkxy
   ```

3. **Execute to seed Firestore**:
   ```bash
   curl "https://asia-south1-lumensparkxy.cloudfunctions.net/seedKnowledgeDocuments"
   ```

### Notes

- Functions use `{ merge: true }` - existing documents are updated, not replaced
- Adding new records to the JSON and re-running will add them without affecting existing ones
- The `id` field becomes the Firestore document ID
- Supported languages: `hi` (Hindi), `mr` (Marathi), `ta` (Tamil), `te` (Telugu)

---

## API Source

Data is fetched from: [data.gov.in - Daily Mandi Prices](https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070)

The API key is currently defined in `functions/index.js` (`API_KEY`).

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

