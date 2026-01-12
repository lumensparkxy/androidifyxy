# Knowledge Base Feature - Firebase Setup Guide

## Overview
The Knowledge Base feature allows farmers to access PDF documents organized by crop type. Documents are stored in Firebase Storage and metadata is stored in Firestore. **Multi-language support** is built-in - crop names, document titles, and descriptions can be provided in multiple languages.

## Supported Language Codes
- `en` - English (default/fallback)
- `hi` - Hindi
- `mr` - Marathi
- `te` - Telugu
- `ta` - Tamil

## Firestore Collections

### 1. `knowledge_crops` Collection
Each document represents a crop category with multi-language support.

**Document Structure:**
```json
{
  "id": "corn",
  "name": "Corn / Maize",
  "names": {
    "hi": "मक्का",
    "mr": "मका",
    "te": "మొక్కజొన్న",
    "ta": "மக்காச்சோளம்"
  },
  "iconUrl": "https://storage.googleapis.com/your-bucket/icons/corn.png",
  "displayOrder": 1
}
```

**Fields:**
- `id` (string): Unique identifier for the crop (e.g., "corn", "soybean", "wheat")
- `name` (string): Default/fallback name in English
- `names` (map): Language code → localized name (e.g., `{"hi": "मक्का", "mr": "मका"}`)
- `iconUrl` (string): URL to the crop icon image (can be a Firebase Storage URL or external URL)
- `displayOrder` (number): Order in which crops appear in the grid (lower numbers appear first)

### 2. `knowledge_documents` Collection
Each document represents a PDF file with multi-language titles and descriptions.

**Document Structure:**
```json
{
  "id": "corn_planting_guide",
  "title": "Corn Planting Guide 2024",
  "titles": {
    "hi": "मक्का रोपण गाइड 2024",
    "mr": "मका लागवड मार्गदर्शक 2024",
    "te": "మొక్కజొన్న నాటడం గైడ్ 2024",
    "ta": "மக்காச்சோளம் நடவு வழிகாட்டி 2024"
  },
  "description": "Complete guide for corn planting including soil preparation, seed selection, and timing",
  "descriptions": {
    "hi": "मिट्टी की तैयारी, बीज चयन और समय सहित मक्का रोपण के लिए पूर्ण गाइड",
    "mr": "माती तयारी, बियाणे निवड आणि वेळ यासह मका लागवडीसाठी संपूर्ण मार्गदर्शक",
    "te": "నేల తయారీ, విత్తన ఎంపిక మరియు సమయంతో సహా మొక్కజొన్న నాటడానికి పూర్తి గైడ్",
    "ta": "மண் தயாரிப்பு, விதை தேர்வு மற்றும் நேரம் உள்ளிட்ட மக்காச்சோளம் நடவுக்கான முழுமையான வழிகாட்டி"
  },
  "storagePath": "knowledge/corn/corn_planting_guide.pdf",
  "cropId": "corn",
  "displayOrder": 1
}
```

**Fields:**
- `id` (string): Unique identifier for the document
- `title` (string): Default/fallback title in English
- `titles` (map): Language code → localized title
- `description` (string): Default/fallback description in English
- `descriptions` (map): Language code → localized description
- `storagePath` (string): Path to the PDF in Firebase Storage
- `cropId` (string): Reference to the crop this document belongs to
- `displayOrder` (number): Order in which documents appear in the list

## Firebase Storage Structure

```
knowledge/
├── corn/
│   ├── corn_planting_guide.pdf
│   ├── corn_pest_control.pdf
│   └── corn_harvest_tips.pdf
├── soybean/
│   ├── soybean_cultivation.pdf
│   └── soybean_diseases.pdf
├── wheat/
│   └── wheat_varieties.pdf
└── icons/
    ├── corn.png
    ├── soybean.png
    └── wheat.png
```

## Firebase Storage Rules

Add these rules to your `storage.rules` file:

```rules
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Allow authenticated users to read knowledge base files
    match /knowledge/{allPaths=**} {
      allow read: if request.auth != null;
      allow write: if false; // Only admin can write via Console
    }
  }
}
```

## Firestore Security Rules

Add these rules to your `firestore.rules` file:

```rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Knowledge Base - read only for authenticated users
    match /knowledge_crops/{cropId} {
      allow read: if request.auth != null;
      allow write: if false; // Only admin can write via Console
    }
    
    match /knowledge_documents/{docId} {
      allow read: if request.auth != null;
      allow write: if false; // Only admin can write via Console
    }
  }
}
```

## Firestore Indexes

Add this index to `firestore.indexes.json`:

```json
{
  "indexes": [
    {
      "collectionGroup": "knowledge_documents",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "cropId", "order": "ASCENDING" },
        { "fieldPath": "displayOrder", "order": "ASCENDING" }
      ]
    }
  ]
}
```

## How to Add Content (Manual via Firebase Console)

### Adding a New Crop:
1. Go to Firebase Console → Firestore Database
2. Navigate to `knowledge_crops` collection
3. Click "Add document"
4. Enter document ID (e.g., "cotton")
5. Add fields:
   - `name`: "Cotton"
   - `iconUrl`: (upload icon to Storage first, then paste URL)
   - `displayOrder`: 4

### Adding a New Document:
1. Upload PDF to Firebase Storage under `knowledge/{cropId}/filename.pdf`
2. Go to Firestore → `knowledge_documents` collection
3. Click "Add document"
4. Add fields:
   - `title`: "Cotton Growing Guide"
   - `description`: "Complete guide for cotton cultivation"
   - `storagePath`: "knowledge/cotton/cotton_growing_guide.pdf"
   - `cropId`: "cotton"
   - `displayOrder`: 1

## Sample Data for Testing

### Crops (with multi-language support):
```json
// Document ID: corn
{
  "name": "Corn / Maize",
  "names": {
    "hi": "मक्का",
    "mr": "मका",
    "te": "మొక్కజొన్న",
    "ta": "மக்காச்சோளம்"
  },
  "iconUrl": "",
  "displayOrder": 1
}

// Document ID: soybean
{
  "name": "Soybean",
  "names": {
    "hi": "सोयाबीन",
    "mr": "सोयाबीन",
    "te": "సోయాబీన్",
    "ta": "சோயாபீன்"
  },
  "iconUrl": "",
  "displayOrder": 2
}

// Document ID: wheat
{
  "name": "Wheat",
  "names": {
    "hi": "गेहूं",
    "mr": "गहू",
    "te": "గోధుమ",
    "ta": "கோதுமை"
  },
  "iconUrl": "",
  "displayOrder": 3
}

// Document ID: cotton
{
  "name": "Cotton",
  "names": {
    "hi": "कपास",
    "mr": "कापूस",
    "te": "పత్తి",
    "ta": "பருத்தி"
  },
  "iconUrl": "",
  "displayOrder": 4
}

// Document ID: rice
{
  "name": "Rice / Paddy",
  "names": {
    "hi": "धान / चावल",
    "mr": "भात / तांदूळ",
    "te": "వరి / బియ్యం",
    "ta": "நெல் / அரிசி"
  },
  "iconUrl": "",
  "displayOrder": 5
}
```

### Documents (with multi-language support):
```json
// Document ID: corn_guide_1
{
  "title": "Corn Cultivation Guide",
  "titles": {
    "hi": "मक्का खेती गाइड",
    "mr": "मका लागवड मार्गदर्शक",
    "te": "మొక్కజొన్న సాగు గైడ్",
    "ta": "மக்காச்சோளம் சாகுபடி வழிகாட்டி"
  },
  "description": "Complete guide for growing corn including soil preparation and irrigation",
  "descriptions": {
    "hi": "मिट्टी की तैयारी और सिंचाई सहित मक्का उगाने के लिए पूर्ण गाइड",
    "mr": "माती तयारी आणि सिंचनासह मका पिकवण्यासाठी संपूर्ण मार्गदर्शक",
    "te": "నేల తయారీ మరియు నీటిపారుదలతో సహా మొక్కజొన్న పెంచడానికి పూర్తి గైడ్",
    "ta": "மண் தயாரிப்பு மற்றும் நீர்ப்பாசனம் உள்ளிட்ட மக்காச்சோளம் வளர்ப்பதற்கான முழுமையான வழிகாட்டி"
  },
  "storagePath": "knowledge/corn/corn_cultivation_guide.pdf",
  "cropId": "corn",
  "displayOrder": 1
}
```

## How the Language Selection Works

The app automatically detects the device's language setting and displays content in that language if available. If a translation is not available for a particular field, it falls back to the English (default) value.

**Example:**
- Device language: Hindi (`hi`)
- Crop name displayed: `मक्का` (from `names.hi`)
- If `names.hi` doesn't exist, falls back to: `Corn / Maize` (from `name`)

