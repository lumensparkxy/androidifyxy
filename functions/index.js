const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const fetch = require("node-fetch");
const https = require("https");
const dns = require("dns");
const fs = require("fs/promises");
const path = require("path");
const os = require("os");

admin.initializeApp();

const db = admin.firestore();
const COLLECTION_NAME = "mandi_prices";
const REQUEST_TIMEOUT_MS = 60000; // 60s per API call
const FETCH_MAX_ATTEMPTS = 5;
const FETCH_BASE_DELAY_MS = 1500; // 1.5s base delay
const CACHE_FILENAME = "datagov_cache.json";
const API_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

const API_HTTPS_AGENT = new https.Agent({
  keepAlive: false,
  maxSockets: 1,
  lookup: (hostname, options, callback) => {
    let lookupOptions = options;
    let lookupCallback = callback;

    if (typeof lookupOptions === "function") {
      lookupCallback = lookupOptions;
      lookupOptions = {};
    }

    return dns.lookup(hostname, { ...lookupOptions, family: 4 }, lookupCallback);
  },
});

// API configuration
const API_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070";
const API_KEY = process.env.DATA_GOV_API_KEY || "579b464db66ec23bdd0000015d0d42cb9328410e6bd0a1af77fa3f53";
const BATCH_SIZE = 500; // Firestore batch write limit

/**
 * Scheduled function to sync Mandi prices hourly between 1 PM and 8 PM IST
 * Runs every hour from 13:00 to 20:00 IST
 */
exports.syncMandiPrices = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 540,
    memory: "128MB",
  })
  .pubsub.schedule("0 13-20 * * *")
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
    memory: "128MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Manual Mandi prices sync triggered");

    try {
      const result = await syncPricesFromAPI();
      res.json({
        success: true,
        message: "Sync completed",
        recordsProcessed: result.recordsProcessed,
        states: result.states || [],
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
 * Fetches ALL states since data availability varies by day
 */
async function syncPricesFromAPI() {
  let offset = 0;
  const limit = 1000; // Smaller batches to avoid API timeouts
  let totalRecords = 0;
  let hasMore = true;

  // Delete records older than 7 days (keep recent data for fallback)
  await deleteOldRecords(7);

  const statesSeen = new Set();

  while (hasMore) {
    // Fetch all states - data availability varies by day
    if (!API_KEY) {
      throw new Error("DATA_GOV_API_KEY is missing. Set it in environment variables.");
    }

    const url = new URL(API_URL);
    url.searchParams.set("format", "json");
    url.searchParams.set("api-key", API_KEY);
    url.searchParams.set("limit", String(limit));
    url.searchParams.set("offset", String(offset));

    console.log(`Fetching data with offset ${offset}...`);
    const data = await fetchJsonWithRetry(url.toString());

    if (!data.records || data.records.length === 0) {
      hasMore = false;
      break;
    }

    // Track unique states
    data.records.forEach(record => {
      if (record.state) statesSeen.add(record.state);
    });

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
  console.log(`States with data: ${[...statesSeen].sort().join(', ')}`);

  // Update sync metadata
  await updateSyncMetadata(totalRecords);

  return { recordsProcessed: totalRecords, states: [...statesSeen] };
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
async function fetchJsonWithRetry(url, timeoutMs = REQUEST_TIMEOUT_MS) {
  const maxAttempts = FETCH_MAX_ATTEMPTS;
  const cachePath = path.join(os.tmpdir(), CACHE_FILENAME);
  let lastError;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    console.log(`Fetch attempt ${attempt}/${maxAttempts} starting...`);

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

      const response = await fetch(url, {
        signal: controller.signal,
        agent: API_HTTPS_AGENT,
        headers: {
          Accept: "application/json",
          "User-Agent": API_USER_AGENT,
          Connection: "close",
        },
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        const errorText = await response.text().catch(() => "");
        throw new Error(
          `API request failed: ${response.status} ${response.statusText}${errorText ? ` - ${errorText}` : ""}`
        );
      }

      const data = await response.json();
      console.log(`Fetch attempt ${attempt} succeeded, got ${data.records?.length || 0} records`);

      await fs.writeFile(cachePath, JSON.stringify(data), "utf-8");
      return data;
    } catch (error) {
      // Retry with HTTPS client if the remote endpoint resets the connection
      if (error?.code === "ECONNRESET" || `${error?.message || ""}`.includes("ECONNRESET")) {
        try {
          const data = await fetchJsonWithHttps(url, timeoutMs);
          console.log(`Fetch attempt ${attempt} succeeded via https fallback, got ${data.records?.length || 0} records`);

          await fs.writeFile(cachePath, JSON.stringify(data), "utf-8");
          return data;
        } catch (fallbackError) {
          lastError = fallbackError;
        }
      } else {
        lastError = error;
      }

      if (attempt === maxAttempts) {
        break;
      }

      const jitter = Math.random() * 500;
      const backoffMs = FETCH_BASE_DELAY_MS * Math.pow(2, attempt - 1) + jitter;
      console.warn(`Fetch attempt ${attempt} failed (${error.message}). Retrying in ${Math.round(backoffMs)}ms...`);
      await new Promise((resolve) => setTimeout(resolve, backoffMs));
    }
  }

  try {
    const cached = await fs.readFile(cachePath, "utf-8");
    const data = JSON.parse(cached);
    console.warn(`Warning: using cached data from ${cachePath} due to request failure: ${lastError?.message}`);
    return data;
  } catch (cacheError) {
    console.error(`Warning: request failed and no cache found: ${lastError?.message}`);
    throw lastError;
  }
}

/**
 * Fallback fetch using https module (helps with ECONNRESET from some gateways)
 */
function fetchJsonWithHttps(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const req = https.request(
      url,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          "User-Agent": API_USER_AGENT,
          Connection: "close",
        },
        agent: API_HTTPS_AGENT,
        timeout: timeoutMs,
      },
      (res) => {
        let body = "";
        res.on("data", (chunk) => {
          body += chunk;
        });
        res.on("end", () => {
          if (res.statusCode < 200 || res.statusCode >= 300) {
            return reject(
              new Error(`API request failed: ${res.statusCode} ${res.statusMessage}${body ? ` - ${body}` : ""}`)
            );
          }

          try {
            const data = JSON.parse(body);
            resolve(data);
          } catch (parseError) {
            reject(new Error(`Failed to parse JSON: ${parseError.message}`));
          }
        });
      }
    );

    req.on("timeout", () => {
      req.destroy(new Error("Request timed out"));
    });
    req.on("error", reject);
    req.end();
  });
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

/**
 * Placeholder callable to gate offer activation (max 20 active per supplier).
 * TODO: implement transactional logic once offer schema is live.
 */
exports.publishOffer = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 30,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    const { offerId } = data || {};
    if (!offerId) {
      throw new functions.https.HttpsError("invalid-argument", "offerId is required");
    }

    // NOTE: Implementation will:
    // 1) Check supplier verificationStatus == APPROVED
    // 2) Count active offers for this supplier
    // 3) If < 20, set offer status=ACTIVE, set activatedAt, priceNormalized, supplierApproved=true
    // For now, respond with a not-implemented stub to keep deploys passing.
    return { ok: false, message: "publishOffer not implemented yet" };
  });

// ============================================================================
// SUPPLIER CLICK TRACKING - Monetization (Pay-per-lead)
// ============================================================================

const SUPPLIER_CLICKS_COLLECTION = "supplier_clicks";
const SUPPLIER_STATS_COLLECTION = "supplier_stats";
const CLICK_RETENTION_DAYS = 360;

/**
 * Scheduled function to aggregate supplier clicks weekly
 * Runs every Sunday at 00:00 IST
 */
exports.aggregateSupplierClicks = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 540,
    memory: "256MB",
  })
  .pubsub.schedule("0 0 * * 0") // Every Sunday at midnight
  .timeZone("Asia/Kolkata")
  .onRun(async (context) => {
    console.log("Starting weekly supplier click aggregation...");

    try {
      const result = await aggregateClicks();
      console.log(`Aggregation completed. Processed ${result.suppliersUpdated} suppliers.`);
      return null;
    } catch (error) {
      console.error("Error aggregating supplier clicks:", error);
      throw error;
    }
  });

/**
 * HTTP endpoint to manually trigger click aggregation (for testing/admin)
 */
exports.aggregateSupplierClicksManual = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 540,
    memory: "256MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Manual supplier click aggregation triggered");

    try {
      const result = await aggregateClicks();
      res.json({
        success: true,
        message: "Aggregation completed",
        suppliersUpdated: result.suppliersUpdated,
        totalClicks: result.totalClicks,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error("Error in manual aggregation:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  });

/**
 * Aggregate clicks from the past 7 days and update supplier stats
 */
async function aggregateClicks() {
  const sevenDaysAgo = new Date();
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  const cutoffTimestamp = admin.firestore.Timestamp.fromDate(sevenDaysAgo);

  // Query all clicks from the past 7 days
  const clicksSnapshot = await db
    .collection(SUPPLIER_CLICKS_COLLECTION)
    .where("timestamp", ">=", cutoffTimestamp)
    .get();

  console.log(`Found ${clicksSnapshot.size} clicks in the past 7 days`);

  // Aggregate by supplierId and clickType
  const supplierStats = {};

  clicksSnapshot.docs.forEach((doc) => {
    const data = doc.data();
    const { supplierId, clickType } = data;

    if (!supplierId) return;

    if (!supplierStats[supplierId]) {
      supplierStats[supplierId] = {
        weeklyWhatsAppClicks: 0,
        weeklyCallClicks: 0,
      };
    }

    if (clickType === "WHATSAPP") {
      supplierStats[supplierId].weeklyWhatsAppClicks++;
    } else if (clickType === "CALL") {
      supplierStats[supplierId].weeklyCallClicks++;
    }
  });

  // Update supplier_stats collection
  const supplierIds = Object.keys(supplierStats);
  let suppliersUpdated = 0;

  for (const supplierId of supplierIds) {
    const stats = supplierStats[supplierId];
    const statsRef = db.collection(SUPPLIER_STATS_COLLECTION).doc(supplierId);

    // Get existing stats to update totals
    const existingDoc = await statsRef.get();
    const existingData = existingDoc.exists ? existingDoc.data() : {};

    const updatedStats = {
      weeklyWhatsAppClicks: stats.weeklyWhatsAppClicks,
      weeklyCallClicks: stats.weeklyCallClicks,
      totalWhatsAppClicks: (existingData.totalWhatsAppClicks || 0) + stats.weeklyWhatsAppClicks,
      totalCallClicks: (existingData.totalCallClicks || 0) + stats.weeklyCallClicks,
      lastAggregatedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await statsRef.set(updatedStats, { merge: true });
    suppliersUpdated++;

    console.log(`Updated stats for supplier ${supplierId}: WhatsApp=${stats.weeklyWhatsAppClicks}, Call=${stats.weeklyCallClicks}`);
  }

  return {
    suppliersUpdated,
    totalClicks: clicksSnapshot.size,
  };
}

/**
 * Scheduled function to clean up old click records (older than 360 days)
 * Runs every Sunday at 01:00 IST (after aggregation)
 */
exports.cleanupOldClicks = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 540,
    memory: "256MB",
  })
  .pubsub.schedule("0 1 * * 0") // Every Sunday at 1 AM
  .timeZone("Asia/Kolkata")
  .onRun(async (context) => {
    console.log(`Starting cleanup of clicks older than ${CLICK_RETENTION_DAYS} days...`);

    try {
      const deleted = await deleteOldClicks(CLICK_RETENTION_DAYS);
      console.log(`Cleanup completed. Deleted ${deleted} old click records.`);
      return null;
    } catch (error) {
      console.error("Error cleaning up old clicks:", error);
      throw error;
    }
  });

/**
 * Delete click records older than specified days
 */
async function deleteOldClicks(daysToKeep) {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);
  const cutoffTimestamp = admin.firestore.Timestamp.fromDate(cutoffDate);

  const collectionRef = db.collection(SUPPLIER_CLICKS_COLLECTION);
  let totalDeleted = 0;

  while (true) {
    const oldClicksSnapshot = await collectionRef
      .where("timestamp", "<", cutoffTimestamp)
      .limit(500)
      .get();

    if (oldClicksSnapshot.empty) {
      break;
    }

    const batch = db.batch();
    oldClicksSnapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    totalDeleted += oldClicksSnapshot.size;
    console.log(`Deleted ${totalDeleted} old click records so far...`);
  }

  return totalDeleted;
}

// ===========================
// KNOWLEDGE BASE CROPS SEEDING
// ===========================

const KNOWLEDGE_CROPS_COLLECTION = "knowledge_crops";

/**
 * HTTP endpoint to seed/populate knowledge base crops
 * This will insert or update all crop records
 * Usage: Call this endpoint once to populate the database
 */
exports.seedKnowledgeCrops = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "128MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Starting knowledge crops seeding...");

    try {
      const cropsData = require("./seeds/knowledge_crops.json");
      const crops = cropsData.crops;

      const batch = db.batch();

      for (const crop of crops) {
        const docRef = db.collection(KNOWLEDGE_CROPS_COLLECTION).doc(crop.id);
        batch.set(docRef, {
          name: crop.name,
          names: crop.names,
          iconUrl: crop.iconUrl,
          displayOrder: crop.displayOrder,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
      }

      await batch.commit();

      console.log(`Successfully seeded ${crops.length} crops`);
      res.json({
        success: true,
        message: `Seeded ${crops.length} crops`,
        crops: crops.map((c) => ({ id: c.id, name: c.name })),
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error("Error seeding crops:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  });

/**
 * HTTP endpoint to delete all knowledge crops (use with caution)
 */
exports.deleteAllKnowledgeCrops = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "128MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Deleting all knowledge crops...");

    try {
      const snapshot = await db.collection(KNOWLEDGE_CROPS_COLLECTION).get();

      if (snapshot.empty) {
        return res.json({
          success: true,
          message: "No crops to delete",
          deleted: 0,
        });
      }

      const batch = db.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });

      await batch.commit();

      console.log(`Deleted ${snapshot.size} crops`);
      res.json({
        success: true,
        message: `Deleted ${snapshot.size} crops`,
        deleted: snapshot.size,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error("Error deleting crops:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  });

// ===========================
// KNOWLEDGE BASE DOCUMENTS SEEDING
// ===========================

const KNOWLEDGE_DOCUMENTS_COLLECTION = "knowledge_documents";

/**
 * HTTP endpoint to seed/populate knowledge base documents
 * This will insert or update all document records
 * Usage: Call this endpoint to populate/update the database
 *
 * To execute:
 *   curl "https://asia-south1-lumensparkxy.cloudfunctions.net/seedKnowledgeDocuments"
 */
exports.seedKnowledgeDocuments = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "128MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Starting knowledge documents seeding...");

    try {
      const docsData = require("./seeds/knowledge_documents.json");
      const documents = docsData.documents;

      const batch = db.batch();

      for (const doc of documents) {
        const docRef = db.collection(KNOWLEDGE_DOCUMENTS_COLLECTION).doc(doc.id);
        batch.set(docRef, {
          cropId: doc.cropId,
          title: doc.title,
          titles: doc.titles,
          description: doc.description,
          descriptions: doc.descriptions,
          storagePath: doc.storagePath,
          displayOrder: doc.displayOrder,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        }, { merge: true });
      }

      await batch.commit();

      console.log(`Successfully seeded ${documents.length} documents`);
      res.json({
        success: true,
        message: `Seeded ${documents.length} documents`,
        documents: documents.map((d) => ({ id: d.id, title: d.title, cropId: d.cropId })),
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error("Error seeding documents:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  });

/**
 * HTTP endpoint to delete all knowledge documents (use with caution)
 */
exports.deleteAllKnowledgeDocuments = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "128MB",
  })
  .https.onRequest(async (req, res) => {
    console.log("Deleting all knowledge documents...");

    try {
      const snapshot = await db.collection(KNOWLEDGE_DOCUMENTS_COLLECTION).get();

      if (snapshot.empty) {
        return res.json({
          success: true,
          message: "No documents to delete",
          deleted: 0,
        });
      }

      const batch = db.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });

      await batch.commit();

      console.log(`Deleted ${snapshot.size} documents`);
      res.json({
        success: true,
        message: `Deleted ${snapshot.size} documents`,
        deleted: snapshot.size,
        timestamp: new Date().toISOString(),
      });
    } catch (error) {
      console.error("Error deleting documents:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  });
