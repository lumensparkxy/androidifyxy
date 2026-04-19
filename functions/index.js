const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const fetch = require("node-fetch");
const { GoogleAuth } = require("google-auth-library");
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
const AGENT_SERVICE_URL = (process.env.AGENT_SERVICE_URL || "").replace(/\/+$/, "");
const AGENT_SERVICE_SHARED_SECRET = process.env.AGENT_SERVICE_SHARED_SECRET || "";
const AMAZON_AFFILIATE_FALLBACK_ENABLED = /^(1|true|yes)$/i.test(
  process.env.AMAZON_AFFILIATE_FALLBACK_ENABLED || "",
);
const AGENT_PROXY_TIMEOUT_MS = 60000;
const googleAuth = new GoogleAuth();
let agentServiceIdTokenClientPromise = null;

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

const {
  COMMERCE_CHANNEL_ADMIN_REVIEW,
  buildInitialCommerceFields,
  SALES_PIPELINE_COLLECTION,
  SALES_PIPELINE_STATUS_INITIATED,
  buildSalesPipelineDocId,
  generateRequestNumber,
  normalizeProductName,
} = require("./salesPipeline");
const {
  buildLeadFarmerProfileSnapshot,
  deriveLeadReviewStatus,
  deriveLeadSupplierVisibility,
  getLeadProfileValidationError,
  mergeLeadFarmerProfileSnapshots,
} = require("./leadProfile");
const { buildAdminLeadView } = require("./adminLeadView");
const {
  CONVERSATIONS_COLLECTION,
  buildAffiliateHandoffMessage,
  buildAffiliateNotificationPayload,
  buildAssistantConversationMessage,
  buildConversationTitle,
  buildManualAmazonAffiliateLeadPatch,
  buildUserNotificationTopic,
} = require("./adminAffiliateMessaging");
const {
  buildAmazonAffiliateConfig,
  finalizeAmazonAffiliateRecommendation,
  resolveAmazonAffiliateCandidate,
} = require("./amazonAffiliate");
const { buildLeadRecommendation } = require("./leadRecommendation");
const {
  buildCommissionLifecycleDefaults,
  getCommissionMonthKey,
  summarizeCommissionMonthEntries,
} = require("./leadWorkflow");

const USERS_COLLECTION = "users";
const SETTINGS_COLLECTION = "settings";
const FARMER_PROFILE_DOC = "farmer_profile";
const INSTALL_ATTRIBUTIONS_COLLECTION = "install_attributions";
const ATTRIBUTION_STATUS_PROMOTER_ATTRIBUTED = "promoter_attributed";
const ATTRIBUTION_STATUS_ORGANIC_OR_UNKNOWN = "organic_or_unknown";
const SUPPLIERS_COLLECTION = "suppliers";
const OFFERS_COLLECTION = "offers";
const COMMISSION_LEDGER_COLLECTION = "commission_ledger";
const COMMISSION_MONTHLY_COLLECTION = "commission_monthly";
const SUPPLIER_RESPONSE_TIMEOUT_REASON = "supplier_timeout";
const SUPPLIER_TIMEOUT_SWEEP_BATCH_SIZE = 50;
const SUPPLIER_RESPONSE_WINDOW_HOURS = 24;

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeSupplierPhone(value) {
  const compact = trimString(value).replace(/\s+/g, "");
  if (!compact) return "";
  return compact.startsWith("+") ? compact : `+91${compact}`;
}

function optionalString(value) {
  const trimmed = trimString(value);
  return trimmed || null;
}

function coerceEpochMillis(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return null;
  }

  return Math.floor(numericValue);
}

function normalizeInstallAttributionStatus(value, promoterId) {
  if (promoterId) {
    return ATTRIBUTION_STATUS_PROMOTER_ATTRIBUTED;
  }

  return value === ATTRIBUTION_STATUS_PROMOTER_ATTRIBUTED ?
    ATTRIBUTION_STATUS_ORGANIC_OR_UNKNOWN :
    ATTRIBUTION_STATUS_ORGANIC_OR_UNKNOWN;
}

function normalizeRecentMessages(value) {
  if (!Array.isArray(value)) return [];

  return value
    .map((item) => ({
      role: trimString(item?.role) || "user",
      text: trimString(item?.text),
      imageUrl: trimString(item?.imageUrl) || null,
    }))
    .filter((item) => item.text || item.imageUrl);
}

async function readAgentServiceError(response) {
  try {
    const json = await response.json();
    return trimString(json?.detail) || trimString(json?.error) || trimString(json?.message);
  } catch (error) {
    const text = await response.text().catch(() => "");
    return trimString(text) || response.statusText || "Agent service request failed";
  }
}

async function getAgentServiceAuthHeaders(targetUrl) {
  if (!AGENT_SERVICE_URL) {
    return {};
  }

  if (!agentServiceIdTokenClientPromise) {
    agentServiceIdTokenClientPromise = googleAuth.getIdTokenClient(AGENT_SERVICE_URL);
  }

  const client = await agentServiceIdTokenClientPromise;
  const headers = await client.getRequestHeaders(targetUrl);
  if (headers && typeof headers.entries === "function") {
    return Object.fromEntries(headers.entries());
  }
  return headers || {};
}

function normalizeLeadLocationPart(value) {
  return trimString(value)
    .toLowerCase()
    .replace(/\s+/g, " ");
}

function inferLeadCategory(productName, chatMessageText = "") {
  const haystack = `${trimString(productName)} ${trimString(chatMessageText)}`.toLowerCase();

  if (/(fertili|urea|dap|npk|potash|micronutrient|manure)/.test(haystack)) {
    return "fertilizer";
  }
  if (/(pesticide|fungicide|herbicide|insecticide|spray|weedicide)/.test(haystack)) {
    return "pesticide";
  }
  if (/(seed|seeds|hybrid|variety|nursery|sapling)/.test(haystack)) {
    return "seed";
  }

  return "other";
}

function buildInitialLeadRoutingFields({ farmerProfile = {}, productName, chatMessageText }) {
  const district = trimString(farmerProfile?.district);
  const tehsil = trimString(farmerProfile?.tehsil);
  const village = trimString(farmerProfile?.village);
  const leadCategory = inferLeadCategory(productName, chatMessageText);
  return {
    leadCategory,
    leadLocation: {
      district,
      districtKey: normalizeLeadLocationPart(district),
      tehsil,
      tehsilKey: normalizeLeadLocationPart(tehsil),
      village,
      villageKey: normalizeLeadLocationPart(village),
    },
    routingStatus: "initiated",
    reviewStatus: "pending_recommendation",
    recommendationStatus: "pending",
    supplierVisibility: "hidden",
    suggestedSupplier: null,
    selectedSupplier: null,
    assignedSupplier: null,
    commissionPreview: {
      category: leadCategory,
      amount: null,
      currency: "INR",
      ruleId: null,
    },
    ...buildInitialCommerceFields({ productName }),
    ...buildCommissionLifecycleDefaults(),
    suggestionGeneratedAt: null,
    assignmentPublishedAt: null,
    supplierResponseDeadlineAt: null,
    supplierRespondedAt: null,
    supplierRejectedReason: null,
    adminFallbackReason: null,
    lastRoutingUpdatedAt: null,
  };
}

function shouldAttemptRecommendation(leadData = {}) {
  const recommendationStatus = trimString(leadData.recommendationStatus);
  const reviewStatus = trimString(leadData.reviewStatus);

  if (!recommendationStatus && !reviewStatus) {
    return true;
  }

  return reviewStatus === "pending_recommendation" || recommendationStatus === "pending";
}

function buildSupplierOpenLeadCounts(leadsSnapshot) {
  const counts = new Map();
  leadsSnapshot.forEach((doc) => {
    const assignedSupplier = doc.data()?.assignedSupplier;
    const supplierId = trimString(assignedSupplier?.supplierId);
    const routingStatus = trimString(doc.data()?.routingStatus);
    if (!supplierId || !["supplier_pending", "supplier_accepted"].includes(routingStatus)) {
      return;
    }
    counts.set(supplierId, (counts.get(supplierId) || 0) + 1);
  });
  return counts;
}

async function isAdminContext(context) {
  const uid = context.auth?.uid;
  if (!uid) return false;

  if (["maswadkar@gmail.com", "neophilex@gmail.com"].includes(trimString(context.auth?.token?.email).toLowerCase())) {
    return true;
  }

  const adminDoc = await db.collection("admin_users").doc(uid).get();
  return adminDoc.exists;
}

async function assertAdminContext(context) {
  const isAdmin = await isAdminContext(context);
  if (!isAdmin) {
    throw new functions.https.HttpsError("permission-denied", "Admin access required");
  }
}

function isApprovedSupplierData(supplierData = {}) {
  return trimString(supplierData?.verificationStatus).toUpperCase() === "APPROVED";
}

function getSupplierSnapshotTimestamp(snapshot) {
  const data = snapshot?.data?.() || {};
  return timestampToMillis(data.updatedAt || data.createdAt);
}

function getCanonicalSupplierSnapshotScore(snapshot, preferredUid = "") {
  const data = snapshot?.data?.() || {};
  const canonicalSupplierId = trimString(data.canonicalSupplierId);
  const mergedIntoSupplierId = trimString(data.mergedIntoSupplierId);
  const ownerUid = trimString(data.ownerUid);
  let score = 0;

  if (
    preferredUid &&
    (snapshot.id === preferredUid || ownerUid === preferredUid || canonicalSupplierId === preferredUid)
  ) {
    score += 100;
  }
  if (canonicalSupplierId && snapshot.id === canonicalSupplierId) {
    score += 50;
  }
  if (!mergedIntoSupplierId) {
    score += 10;
  }
  if (isApprovedSupplierData(data)) {
    score += 5;
  }

  return score;
}

function chooseCanonicalSupplierSnapshot(supplierSnapshots, options = {}) {
  const preferredUid = trimString(options.preferredUid);
  if (!Array.isArray(supplierSnapshots) || supplierSnapshots.length === 0) {
    return null;
  }

  const uniqueSnapshots = [];
  const seenIds = new Set();
  supplierSnapshots.forEach((snapshot) => {
    if (!snapshot?.exists || seenIds.has(snapshot.id)) return;
    seenIds.add(snapshot.id);
    uniqueSnapshots.push(snapshot);
  });

  if (uniqueSnapshots.length === 0) {
    return null;
  }

  return uniqueSnapshots.sort((left, right) => {
    const scoreDifference =
      getCanonicalSupplierSnapshotScore(right, preferredUid) -
      getCanonicalSupplierSnapshotScore(left, preferredUid);
    if (scoreDifference !== 0) return scoreDifference;

    const timeDifference = getSupplierSnapshotTimestamp(right) - getSupplierSnapshotTimestamp(left);
    if (timeDifference !== 0) return timeDifference;

    return left.id.localeCompare(right.id);
  })[0];
}

async function lookupSupplierAuthUidByPhone(normalizedPhone) {
  const phone = normalizeSupplierPhone(normalizedPhone);
  if (!phone) return null;

  try {
    const userRecord = await admin.auth().getUserByPhoneNumber(phone);
    return trimString(userRecord?.uid) || null;
  } catch (error) {
    if (error?.code === "auth/user-not-found") {
      return null;
    }
    throw error;
  }
}

async function listSupplierSnapshotsByPhone(normalizedPhone) {
  const phone = normalizeSupplierPhone(normalizedPhone);
  if (!phone) return [];

  const snapshot = await db.collection(SUPPLIERS_COLLECTION)
    .where("phone", "==", phone)
    .get();
  return snapshot.docs;
}

function buildCanonicalSupplierProfilePatch(canonicalSupplierId, isCanonical) {
  return {
    canonicalSupplierId,
    mergedIntoSupplierId: isCanonical ? null : canonicalSupplierId,
  };
}

function buildCanonicalLeadSupplierSnapshot(existingSnapshot, canonicalSupplierId, canonicalSupplierData = {}) {
  if (!existingSnapshot || typeof existingSnapshot !== "object") {
    return existingSnapshot;
  }

  const nextSnapshot = {
    ...existingSnapshot,
    supplierId: canonicalSupplierId,
  };
  const businessName = trimString(canonicalSupplierData.businessName);
  const districtId = trimString(canonicalSupplierData.districtId);
  const districtName = trimString(canonicalSupplierData.districtName);

  if (businessName) nextSnapshot.businessName = businessName;
  if (districtId) nextSnapshot.districtId = districtId;
  if (districtName) nextSnapshot.districtName = districtName;

  return nextSnapshot;
}

async function commitMergeWrites(writeSpecs = []) {
  if (!Array.isArray(writeSpecs) || writeSpecs.length === 0) {
    return;
  }

  for (let index = 0; index < writeSpecs.length; index += 400) {
    const batch = db.batch();
    writeSpecs.slice(index, index + 400).forEach(({ ref, data }) => {
      batch.set(ref, data, { merge: true });
    });
    await batch.commit();
  }
}

async function migrateSupplierAliasOffers(aliasSupplierId, canonicalSnapshot) {
  const canonicalSupplierId = trimString(canonicalSnapshot?.id);
  if (!aliasSupplierId || !canonicalSupplierId || aliasSupplierId === canonicalSupplierId) {
    return 0;
  }

  const snapshot = await db.collection(OFFERS_COLLECTION)
    .where("supplierId", "==", aliasSupplierId)
    .get();
  if (snapshot.empty) {
    return 0;
  }

  const canonicalSupplierData = canonicalSnapshot.data() || {};
  const canonicalSupplierName = trimString(canonicalSupplierData.businessName);

  await commitMergeWrites(snapshot.docs.map((offerDoc) => ({
    ref: offerDoc.ref,
    data: {
      supplierId: canonicalSupplierId,
      supplierName: canonicalSupplierName || trimString(offerDoc.data()?.supplierName) || null,
      supplierApproved: isApprovedSupplierData(canonicalSupplierData),
    },
  })));

  return snapshot.size;
}

async function migrateSupplierAliasCommissionLedger(aliasSupplierId, canonicalSnapshot) {
  const canonicalSupplierId = trimString(canonicalSnapshot?.id);
  if (!aliasSupplierId || !canonicalSupplierId || aliasSupplierId === canonicalSupplierId) {
    return 0;
  }

  const snapshot = await db.collection(COMMISSION_LEDGER_COLLECTION)
    .where("supplierId", "==", aliasSupplierId)
    .get();
  if (snapshot.empty) {
    return 0;
  }

  const canonicalSupplierData = canonicalSnapshot.data() || {};
  const canonicalSupplierName = trimString(canonicalSupplierData.businessName);

  await commitMergeWrites(snapshot.docs.map((ledgerDoc) => ({
    ref: ledgerDoc.ref,
    data: {
      supplierId: canonicalSupplierId,
      supplierName: canonicalSupplierName || trimString(ledgerDoc.data()?.supplierName) || null,
    },
  })));

  return snapshot.size;
}

async function migrateSupplierAliasLeads(aliasSupplierId, canonicalSnapshot) {
  const canonicalSupplierId = trimString(canonicalSnapshot?.id);
  if (!aliasSupplierId || !canonicalSupplierId || aliasSupplierId === canonicalSupplierId) {
    return 0;
  }

  const canonicalSupplierData = canonicalSnapshot.data() || {};
  const leadUpdates = new Map();
  const addLeadUpdate = (leadSnapshot, updater) => {
    const current = leadUpdates.get(leadSnapshot.id) || {};
    updater(current, leadSnapshot.data() || {});
    leadUpdates.set(leadSnapshot.id, current);
  };

  const [
    assignedSnapshot,
    selectedSnapshot,
    suggestedSnapshot,
    suggestedIdSnapshot,
  ] = await Promise.all([
    db.collection(SALES_PIPELINE_COLLECTION).where("assignedSupplier.supplierId", "==", aliasSupplierId).get(),
    db.collection(SALES_PIPELINE_COLLECTION).where("selectedSupplier.supplierId", "==", aliasSupplierId).get(),
    db.collection(SALES_PIPELINE_COLLECTION).where("suggestedSupplier.supplierId", "==", aliasSupplierId).get(),
    db.collection(SALES_PIPELINE_COLLECTION).where("suggestedSupplierId", "==", aliasSupplierId).get(),
  ]);

  assignedSnapshot.docs.forEach((leadSnapshot) => {
    addLeadUpdate(leadSnapshot, (payload, leadData) => {
      payload.assignedSupplier = buildCanonicalLeadSupplierSnapshot(
        leadData.assignedSupplier,
        canonicalSupplierId,
        canonicalSupplierData,
      );
    });
  });

  selectedSnapshot.docs.forEach((leadSnapshot) => {
    addLeadUpdate(leadSnapshot, (payload, leadData) => {
      payload.selectedSupplier = buildCanonicalLeadSupplierSnapshot(
        leadData.selectedSupplier,
        canonicalSupplierId,
        canonicalSupplierData,
      );
    });
  });

  suggestedSnapshot.docs.forEach((leadSnapshot) => {
    addLeadUpdate(leadSnapshot, (payload, leadData) => {
      payload.suggestedSupplier = buildCanonicalLeadSupplierSnapshot(
        leadData.suggestedSupplier,
        canonicalSupplierId,
        canonicalSupplierData,
      );
      payload.suggestedSupplierId = canonicalSupplierId;
    });
  });

  suggestedIdSnapshot.docs.forEach((leadSnapshot) => {
    addLeadUpdate(leadSnapshot, (payload) => {
      payload.suggestedSupplierId = canonicalSupplierId;
    });
  });

  const writeSpecs = [...leadUpdates.entries()].map(([leadId, data]) => ({
    ref: db.collection(SALES_PIPELINE_COLLECTION).doc(leadId),
    data,
  }));

  await commitMergeWrites(writeSpecs);
  return writeSpecs.length;
}

async function migrateSupplierAliasReferences(aliasSupplierId, canonicalSnapshot) {
  const [offerCount, leadCount, ledgerCount] = await Promise.all([
    migrateSupplierAliasOffers(aliasSupplierId, canonicalSnapshot),
    migrateSupplierAliasLeads(aliasSupplierId, canonicalSnapshot),
    migrateSupplierAliasCommissionLedger(aliasSupplierId, canonicalSnapshot),
  ]);

  return {
    offerCount,
    leadCount,
    ledgerCount,
  };
}

async function reconcileSupplierPhoneIdentity(normalizedPhone, options = {}) {
  const phone = normalizeSupplierPhone(normalizedPhone);
  if (!phone) return null;

  const supplierSnapshots = await listSupplierSnapshotsByPhone(phone);
  if (supplierSnapshots.length === 0) {
    return null;
  }

  const preferredUid =
    trimString(options.preferredUid) ||
    (await lookupSupplierAuthUidByPhone(phone)) ||
    "";
  const canonicalSnapshot = chooseCanonicalSupplierSnapshot(supplierSnapshots, { preferredUid });
  if (!canonicalSnapshot) {
    return null;
  }

  const canonicalSupplierId = canonicalSnapshot.id;
  const supplierWriteSpecs = [];
  let migratedOfferCount = 0;
  let migratedLeadCount = 0;
  let migratedLedgerCount = 0;

  supplierSnapshots.forEach((snapshot) => {
    const snapshotData = snapshot.data() || {};
    const isCanonical = snapshot.id === canonicalSupplierId;
    const currentCanonicalSupplierId = trimString(snapshotData.canonicalSupplierId);
    const currentMergedIntoSupplierId = trimString(snapshotData.mergedIntoSupplierId);
    const nextMergedIntoSupplierId = isCanonical ? "" : canonicalSupplierId;

    if (
      currentCanonicalSupplierId !== canonicalSupplierId ||
      currentMergedIntoSupplierId !== nextMergedIntoSupplierId
    ) {
      supplierWriteSpecs.push({
        ref: snapshot.ref,
        data: buildCanonicalSupplierProfilePatch(canonicalSupplierId, isCanonical),
      });
    }
  });

  await commitMergeWrites(supplierWriteSpecs);

  for (const snapshot of supplierSnapshots) {
    if (snapshot.id === canonicalSupplierId) continue;
    const migrationResult = await migrateSupplierAliasReferences(snapshot.id, canonicalSnapshot);
    migratedOfferCount += migrationResult.offerCount;
    migratedLeadCount += migrationResult.leadCount;
    migratedLedgerCount += migrationResult.ledgerCount;
  }

  return {
    phone,
    canonicalSupplierId,
    canonicalOwnerUid: trimString(canonicalSnapshot.data()?.ownerUid) || canonicalSupplierId,
    aliasSupplierIds: supplierSnapshots
      .map((snapshot) => snapshot.id)
      .filter((supplierId) => supplierId !== canonicalSupplierId),
    supplierCount: supplierSnapshots.length,
    migratedOfferCount,
    migratedLeadCount,
    migratedLedgerCount,
  };
}

async function resolveCanonicalSupplierSnapshot(supplierSnapshot, options = {}) {
  if (!supplierSnapshot?.exists) {
    return {
      canonicalSnapshot: supplierSnapshot,
      normalizationResult: null,
    };
  }

  const supplierData = supplierSnapshot.data() || {};
  const normalizedPhone = normalizeSupplierPhone(supplierData.phone);
  if (!normalizedPhone) {
    return {
      canonicalSnapshot: supplierSnapshot,
      normalizationResult: null,
    };
  }

  const normalizationResult = await reconcileSupplierPhoneIdentity(normalizedPhone, options);
  if (normalizationResult?.canonicalSupplierId && normalizationResult.canonicalSupplierId !== supplierSnapshot.id) {
    const canonicalSnapshot = await db.collection(SUPPLIERS_COLLECTION)
      .doc(normalizationResult.canonicalSupplierId)
      .get();
    if (canonicalSnapshot.exists) {
      return {
        canonicalSnapshot,
        normalizationResult,
      };
    }
  }

  return {
    canonicalSnapshot: supplierSnapshot,
    normalizationResult,
  };
}

async function findCurrentSupplierSnapshot(context) {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in required");
  }

  const supplierCandidates = [];
  const seenSupplierIds = new Set();
  const addSupplierCandidate = (snapshot) => {
    if (!snapshot?.exists || seenSupplierIds.has(snapshot.id)) return;
    seenSupplierIds.add(snapshot.id);
    supplierCandidates.push(snapshot);
  };

  addSupplierCandidate(await db.collection(SUPPLIERS_COLLECTION).doc(uid).get());

  const ownerUidSnapshot = await db.collection(SUPPLIERS_COLLECTION)
    .where("ownerUid", "==", uid)
    .limit(5)
    .get();
  ownerUidSnapshot.docs.forEach(addSupplierCandidate);

  const normalizedPhone = normalizeSupplierPhone(context.auth?.token?.phone_number);
  if (normalizedPhone) {
    const byPhoneSnapshot = await db.collection(SUPPLIERS_COLLECTION)
      .where("phone", "==", normalizedPhone)
      .limit(10)
      .get();
    byPhoneSnapshot.docs.forEach(addSupplierCandidate);
  }

  if (supplierCandidates.length === 0) {
    return null;
  }

  const initialSupplierSnapshot = chooseCanonicalSupplierSnapshot(supplierCandidates, { preferredUid: uid });
  const { canonicalSnapshot } = await resolveCanonicalSupplierSnapshot(initialSupplierSnapshot, { preferredUid: uid });

  return canonicalSnapshot?.exists ? canonicalSnapshot : null;
}

async function getApprovedSupplierContext(context) {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in required");
  }

  const canonicalSnapshot = await findCurrentSupplierSnapshot(context);
  if (!canonicalSnapshot) {
    throw new functions.https.HttpsError("failed-precondition", "Supplier profile not found");
  }

  const supplierData = canonicalSnapshot.data() || {};
  if (!isApprovedSupplierData(supplierData)) {
    throw new functions.https.HttpsError("permission-denied", "Supplier approval is required");
  }

  return {
    uid,
    supplierId: canonicalSnapshot.id,
    supplierData,
  };
}

function timestampToIsoString(value) {
  if (!value) return undefined;
  if (typeof value === "string") return value;
  if (value instanceof Date) return value.toISOString();
  if (typeof value?.toDate === "function") {
    return value.toDate().toISOString();
  }
  return undefined;
}

function timestampToMillis(value) {
  if (!value) return 0;
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (value instanceof Date) return value.getTime();
  if (typeof value?.toMillis === "function") return value.toMillis();
  if (typeof value?.toDate === "function") return value.toDate().getTime();
  return 0;
}

function buildAdminActor(context) {
  return {
    uid: trimString(context.auth?.uid) || null,
    email: trimString(context.auth?.token?.email) || null,
  };
}

function buildAdminAssignedSupplierSnapshot(supplierId, supplierData = {}, actor = {}, selectedAt) {
  const businessName = trimString(supplierData.businessName) || "Unnamed supplier";
  const districtId = trimString(supplierData.districtId) || null;
  const districtName = trimString(supplierData.districtName) || null;

  return {
    supplierId,
    businessName,
    districtId,
    districtName,
    source: "admin",
    selectedByUid: actor.uid || null,
    selectedByEmail: actor.email || null,
    selectedAt,
  };
}

function buildSupplierResponseDeadlineTimestamp(selectedAt) {
  const selectedAtMillis = Date.parse(selectedAt);
  const baseMillis = Number.isFinite(selectedAtMillis) ? selectedAtMillis : Date.now();
  return admin.firestore.Timestamp.fromMillis(baseMillis + SUPPLIER_RESPONSE_WINDOW_HOURS * 60 * 60 * 1000);
}

function buildAdminLeadAssignmentUpdate({ supplierId, supplierData, actor, selectedAt }) {
  const selectedSupplier = buildAdminAssignedSupplierSnapshot(supplierId, supplierData, actor, selectedAt);

  return {
    commerceChannel: "supplier_local",
    channelDecisionReason: "admin_manual_supplier",
    affiliateProvider: null,
    affiliateCandidate: null,
    amazonAsin: null,
    amazonSpecialLink: null,
    amazonContentRefreshedAt: null,
    affiliateDisclosureRequired: false,
    conversionStatus: "intent_captured",
    whatsappState: "not_ready",
    fallbackTriggeredAt: null,
    assignedSupplier: {
      ...selectedSupplier,
      assignedAt: selectedAt,
    },
    selectedSupplier,
    routingStatus: "supplier_pending",
    reviewStatus: "assigned_to_supplier",
    recommendationStatus: "ready",
    supplierVisibility: "masked",
    ...buildCommissionLifecycleDefaults(),
    assignmentPublishedAt: admin.firestore.FieldValue.serverTimestamp(),
    supplierResponseDeadlineAt: buildSupplierResponseDeadlineTimestamp(selectedAt),
    supplierRespondedAt: null,
    supplierRejectedReason: null,
    adminFallbackReason: null,
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
}

function getLeadCommissionAmount(leadData = {}) {
  const amount = Number(leadData?.commissionPreview?.amount);
  return Number.isFinite(amount) && amount > 0 ? amount : null;
}

function buildCommissionMonthSummaryView(summaryData = {}, fallbackMonthKey = "") {
  return {
    monthKey: trimString(summaryData.monthKey) || fallbackMonthKey,
    approvedCount: Number(summaryData.approvedCount) || 0,
    approvedTotal: Number(summaryData.approvedTotal) || 0,
    paidCount: Number(summaryData.paidCount) || 0,
    paidTotal: Number(summaryData.paidTotal) || 0,
    outstandingTotal: Number(summaryData.outstandingTotal) || 0,
    currency: trimString(summaryData.currency) || "INR",
    updatedAt: timestampToIsoString(summaryData.updatedAt),
  };
}

async function rebuildCommissionMonthSummary(monthKey) {
  const normalizedMonthKey = trimString(monthKey);
  if (!normalizedMonthKey) {
    return null;
  }

  const ledgerSnapshot = await db.collection(COMMISSION_LEDGER_COLLECTION)
    .where("monthKey", "==", normalizedMonthKey)
    .get();
  const summaryRef = db.collection(COMMISSION_MONTHLY_COLLECTION).doc(normalizedMonthKey);

  if (ledgerSnapshot.empty) {
    await summaryRef.delete().catch(() => null);
    return null;
  }

  const summary = summarizeCommissionMonthEntries(
    ledgerSnapshot.docs.map((doc) => doc.data() || {})
  );

  await summaryRef.set({
    monthKey: normalizedMonthKey,
    ...summary,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });

  const summarySnapshot = await summaryRef.get();
  return buildCommissionMonthSummaryView(summarySnapshot.data() || {}, normalizedMonthKey);
}

async function getApprovedSupplierForAssignment(supplierId) {
  const normalizedSupplierId = trimString(supplierId);
  if (!normalizedSupplierId) {
    throw new functions.https.HttpsError("invalid-argument", "supplierId is required");
  }

  const supplierRef = db.collection(SUPPLIERS_COLLECTION).doc(normalizedSupplierId);
  const supplierSnapshot = await supplierRef.get();
  if (!supplierSnapshot.exists) {
    throw new functions.https.HttpsError("not-found", "Supplier not found");
  }

  const { canonicalSnapshot } = await resolveCanonicalSupplierSnapshot(supplierSnapshot);
  const supplierData = canonicalSnapshot.data() || {};
  if (!isApprovedSupplierData(supplierData)) {
    throw new functions.https.HttpsError("failed-precondition", "Supplier must be approved before assignment");
  }

  return {
    supplierId: canonicalSnapshot.id,
    supplierData,
  };
}

function maskPhoneNumber(value) {
  const digits = trimString(value).replace(/\D/g, "");
  if (!digits) return null;
  const last4 = digits.slice(-4);
  return `••••••${last4}`;
}

async function loadLeadFarmerProfiles(leadRows = []) {
  const userIds = [...new Set(
    leadRows
      .map(({ data }) => trimString(data?.userId))
      .filter(Boolean)
  )];

  if (userIds.length === 0) {
    return new Map();
  }

  const profileEntries = await Promise.all(userIds.map(async (userId) => {
    try {
      const snapshot = await db.collection(USERS_COLLECTION)
        .doc(userId)
        .collection(SETTINGS_COLLECTION)
        .doc(FARMER_PROFILE_DOC)
        .get();
      return [userId, snapshot.exists ? (snapshot.data() || {}) : null];
    } catch (error) {
      console.warn(`Unable to load live farmer profile for lead user ${userId}:`, error);
      return [userId, null];
    }
  }));

  return new Map(profileEntries);
}

function buildSupplierLeadView(leadId, leadData = {}, liveFarmerProfile = null) {
  const routingStatus = trimString(leadData.routingStatus);
  const supplierVisibility = deriveLeadSupplierVisibility(leadData);
  const isUnlocked = supplierVisibility === "unlocked";
  const farmerProfile = mergeLeadFarmerProfileSnapshots(
    liveFarmerProfile || {},
    leadData.farmerProfileSnapshot || {},
  );
  const farmerPhone = trimString(farmerProfile.mobileNumber || farmerProfile.phoneNumber);
  const farmerEmail = trimString(farmerProfile.email || farmerProfile.emailId);

  return {
    id: leadId,
    requestNumber: trimString(leadData.requestNumber),
    productName: trimString(leadData.productName),
    quantity: trimString(leadData.quantity) || null,
    unit: trimString(leadData.unit) || null,
    chatMessageText: trimString(leadData.chatMessageText),
    leadCategory: trimString(leadData.leadCategory) || null,
    routingStatus: routingStatus || null,
    reviewStatus: deriveLeadReviewStatus(leadData),
    recommendationStatus: trimString(leadData.recommendationStatus) || null,
    supplierVisibility,
    contactUnlocked: isUnlocked,
    farmerProfileSnapshot: {
      name: trimString(farmerProfile.name) || null,
      village: trimString(farmerProfile.village) || null,
      tehsil: trimString(farmerProfile.tehsil) || null,
      district: trimString(farmerProfile.district) || null,
      mobileNumber: isUnlocked ? (farmerPhone || null) : maskPhoneNumber(farmerPhone),
      email: isUnlocked ? (farmerEmail || null) : null,
      emailId: isUnlocked ? (farmerEmail || null) : null,
    },
    leadLocation: leadData.leadLocation || null,
    commissionPreview: leadData.commissionPreview || null,
    suggestedSupplier: leadData.suggestedSupplier || null,
    selectedSupplier: leadData.selectedSupplier || leadData.assignedSupplier || null,
    assignedSupplier: leadData.assignedSupplier || null,
    suggestionGeneratedAt: timestampToIsoString(leadData.suggestionGeneratedAt),
    assignmentPublishedAt: timestampToIsoString(leadData.assignmentPublishedAt),
    supplierResponseDeadlineAt: timestampToIsoString(leadData.supplierResponseDeadlineAt),
    supplierRespondedAt: timestampToIsoString(leadData.supplierRespondedAt),
    supplierRejectedReason: trimString(leadData.supplierRejectedReason) || null,
    adminFallbackReason: trimString(leadData.adminFallbackReason) || null,
    lastRoutingUpdatedAt: timestampToIsoString(leadData.lastRoutingUpdatedAt),
    createdAt: timestampToIsoString(leadData.createdAt),
    updatedAt: timestampToIsoString(leadData.updatedAt),
  };
}

function buildSupplierTimeoutUpdate() {
  return {
    routingStatus: "supplier_timeout",
    recommendationStatus: "needs_admin_review",
    supplierResponseDeadlineAt: null,
    adminFallbackReason: SUPPLIER_RESPONSE_TIMEOUT_REASON,
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
}

function isSupplierResponseDeadlineExpired(leadData = {}, nowMillis = Date.now()) {
  const deadlineMillis = timestampToMillis(leadData.supplierResponseDeadlineAt);
  return deadlineMillis > 0 && deadlineMillis <= nowMillis;
}

async function expireOverdueSupplierLeadsBatch() {
  const now = admin.firestore.Timestamp.now();
  let lastDoc = null;
  let expiredCount = 0;

  while (true) {
    let queryRef = db.collection(SALES_PIPELINE_COLLECTION)
      .where("supplierResponseDeadlineAt", "<=", now)
      .orderBy("supplierResponseDeadlineAt")
      .limit(SUPPLIER_TIMEOUT_SWEEP_BATCH_SIZE);

    if (lastDoc) {
      queryRef = queryRef.startAfter(lastDoc);
    }

    const snapshot = await queryRef.get();
    if (snapshot.empty) {
      break;
    }

    lastDoc = snapshot.docs[snapshot.docs.length - 1];
    const batch = db.batch();
    let updatesInBatch = 0;

    snapshot.docs.forEach((leadDoc) => {
      const leadData = leadDoc.data() || {};
      if (trimString(leadData.routingStatus) !== "supplier_pending") {
        return;
      }

      batch.set(leadDoc.ref, buildSupplierTimeoutUpdate(), { merge: true });
      updatesInBatch += 1;
    });

    if (updatesInBatch > 0) {
      await batch.commit();
      expiredCount += updatesInBatch;
    }
  }

  return { expiredCount };
}

exports.getCurrentSupplierProfile = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    const supplierSnapshot = await findCurrentSupplierSnapshot(context);
    if (!supplierSnapshot) {
      return {
        ok: true,
        supplierId: context.auth.uid,
        profile: null,
      };
    }

    return {
      ok: true,
      supplierId: supplierSnapshot.id,
      profile: supplierSnapshot.data() || null,
    };
  });

exports.listSupplierAssignedLeads = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    const { supplierId } = await getApprovedSupplierContext(context);
    const limitCount = Math.min(Math.max(Math.floor(Number(data?.limit) || 20), 1), 50);
    const visibleStatuses = new Set(["supplier_pending", "supplier_accepted", "supplier_rejected", "supplier_timeout"]);

    const snapshot = await db.collection(SALES_PIPELINE_COLLECTION)
      .where("assignedSupplier.supplierId", "==", supplierId)
      .limit(limitCount)
      .get();

    const leadRows = snapshot.docs
      .map((doc) => ({ id: doc.id, data: doc.data() || {} }))
      .filter(({ data: leadData }) => visibleStatuses.has(trimString(leadData.routingStatus)));
    const liveFarmerProfiles = await loadLeadFarmerProfiles(leadRows);

    const leads = leadRows
      .sort((left, right) => {
        const rightTime = timestampToMillis(right.data.updatedAt || right.data.assignmentPublishedAt || right.data.createdAt);
        const leftTime = timestampToMillis(left.data.updatedAt || left.data.assignmentPublishedAt || left.data.createdAt);
        return rightTime - leftTime;
      })
      .map(({ id, data: leadData }) => buildSupplierLeadView(
        id,
        leadData,
        liveFarmerProfiles.get(trimString(leadData.userId)) || null,
      ));

    return {
      ok: true,
      leads,
    };
  });

exports.listAdminSalesLeads = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const limitCount = Math.min(Math.max(Math.floor(Number(data?.limit) || 50), 1), 100);
    const snapshot = await db.collection(SALES_PIPELINE_COLLECTION)
      .orderBy("createdAt", "desc")
      .limit(limitCount)
      .get();

    const leadRows = snapshot.docs.map((doc) => ({ id: doc.id, data: doc.data() || {} }));
    const liveFarmerProfiles = await loadLeadFarmerProfiles(leadRows);
    const leads = leadRows.map(({ id, data: leadData }) => buildAdminLeadView(
      id,
      leadData,
      liveFarmerProfiles.get(trimString(leadData.userId)) || null,
    ));

    return {
      ok: true,
      leads,
    };
  });

exports.respondToSupplierLead = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    const { supplierId } = await getApprovedSupplierContext(context);

    const leadId = trimString(data?.leadId);
    const action = trimString(data?.action).toLowerCase();
    const rejectionReason = trimString(data?.rejectionReason);

    if (!leadId) {
      throw new functions.https.HttpsError("invalid-argument", "leadId is required");
    }
    if (!["accept", "reject"].includes(action)) {
      throw new functions.https.HttpsError("invalid-argument", "action must be accept or reject");
    }
    if (action === "reject" && !rejectionReason) {
      throw new functions.https.HttpsError("invalid-argument", "rejectionReason is required when rejecting a lead");
    }

    const leadRef = db.collection(SALES_PIPELINE_COLLECTION).doc(leadId);
    const leadSnap = await leadRef.get();
    if (!leadSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Lead not found");
    }

    const leadData = leadSnap.data() || {};
    const assignedSupplierId = trimString(leadData?.assignedSupplier?.supplierId);
    if (assignedSupplierId !== supplierId) {
      throw new functions.https.HttpsError("permission-denied", "This lead is not assigned to the current supplier");
    }

    const currentRoutingStatus = trimString(leadData.routingStatus);
    if (currentRoutingStatus === "supplier_pending" && isSupplierResponseDeadlineExpired(leadData)) {
      await leadRef.set(buildSupplierTimeoutUpdate(), { merge: true });
      throw new functions.https.HttpsError("failed-precondition", "Lead response window has expired");
    }

    if (!["supplier_pending", "supplier_accepted"].includes(currentRoutingStatus)) {
      throw new functions.https.HttpsError("failed-precondition", `Lead cannot be ${action}ed from status ${currentRoutingStatus || "unknown"}`);
    }

    const updatePayload = action === "accept"
      ? {
        routingStatus: "supplier_accepted",
        recommendationStatus: trimString(leadData.recommendationStatus) || "ready",
        supplierResponseDeadlineAt: null,
        supplierRespondedAt: admin.firestore.FieldValue.serverTimestamp(),
        supplierRejectedReason: null,
        adminFallbackReason: null,
        lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }
      : {
        routingStatus: "supplier_rejected",
        recommendationStatus: "needs_admin_review",
        supplierResponseDeadlineAt: null,
        supplierRespondedAt: admin.firestore.FieldValue.serverTimestamp(),
        supplierRejectedReason: rejectionReason,
        adminFallbackReason: "supplier_rejected",
        lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      };

    await leadRef.set(updatePayload, { merge: true });

    return {
      ok: true,
      lead: buildSupplierLeadView(leadId, {
        ...leadData,
        ...updatePayload,
        supplierRespondedAt: new Date().toISOString(),
        lastRoutingUpdatedAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }),
    };
  });

async function buildSupplierRecommendationContext(leadData) {
  const leadCategory = trimString(leadData?.leadCategory) || inferLeadCategory(leadData?.productName, leadData?.chatMessageText);
  const districtKey = normalizeLeadLocationPart(leadData?.leadLocation?.districtKey || leadData?.leadLocation?.district);
  const districtVariants = [districtKey];
  const districtIdVariants = districtVariants.map((value) => `MH:${value}`);

  const offerQueries = [
    db.collection(OFFERS_COLLECTION)
      .where("status", "==", "ACTIVE")
      .where("supplierApproved", "==", true)
      .where("category", "==", leadCategory),
    db.collection(OFFERS_COLLECTION)
      .where("status", "==", "APPROVED")
      .where("supplierApproved", "==", true)
      .where("category", "==", leadCategory),
  ];

  if (districtKey) {
    offerQueries.push(
      db.collection(OFFERS_COLLECTION)
        .where("districtId", "in", districtIdVariants)
        .where("status", "==", "ACTIVE")
        .where("supplierApproved", "==", true),
      db.collection(OFFERS_COLLECTION)
        .where("districtId", "in", districtIdVariants)
        .where("status", "==", "APPROVED")
        .where("supplierApproved", "==", true)
    );
  }

  const querySnapshots = await Promise.all(
    offerQueries.map((queryRef) => queryRef.get().catch(() => null))
  );
  const offersById = new Map();
  querySnapshots.filter(Boolean).forEach((snapshot) => {
    snapshot.forEach((doc) => {
      offersById.set(doc.id, { id: doc.id, ...doc.data() });
    });
  });

  const offers = [...offersById.values()];
  const supplierIds = [...new Set(offers.map((offer) => trimString(offer.supplierId)).filter(Boolean))];

  const supplierSnapshots = await Promise.all(
    supplierIds.map((supplierId) => db.collection(SUPPLIERS_COLLECTION).doc(supplierId).get())
  );
  const activeLeadSnapshots = supplierIds.length > 0
    ? await db.collection(SALES_PIPELINE_COLLECTION)
      .where("routingStatus", "in", ["supplier_pending", "supplier_accepted"])
      .get()
    : null;
  const openLeadCounts = activeLeadSnapshots ? buildSupplierOpenLeadCounts(activeLeadSnapshots) : new Map();

  const suppliersById = new Map();
  supplierSnapshots.forEach((snapshot) => {
    if (!snapshot.exists) return;
    suppliersById.set(snapshot.id, {
      ...snapshot.data(),
      openLeadCount: openLeadCounts.get(snapshot.id) || 0,
    });
  });

  return {
    lead: {
      ...leadData,
      leadCategory,
    },
    offers,
    suppliersById,
  };
}

async function applyLeadRecommendation({ leadRef, leadData, leadId, fallbackReason = "recommendation_failed" }) {
  try {
    const recommendationContext = await buildSupplierRecommendationContext(leadData);
    const recommendation = buildLeadRecommendation({
      ...recommendationContext,
      affiliateFallback: {
        enabled: AMAZON_AFFILIATE_FALLBACK_ENABLED,
      },
    });
    const amazonAffiliateResolution = recommendation.affiliateCandidate?.provider === "amazon"
      ? await resolveAmazonAffiliateCandidate({
        lead: recommendationContext.lead,
        affiliateCandidate: recommendation.affiliateCandidate,
        config: buildAmazonAffiliateConfig(process.env),
      })
      : null;
    const finalizedRecommendation = finalizeAmazonAffiliateRecommendation({
      recommendation,
      resolution: amazonAffiliateResolution,
    });
    await leadRef.set({
      leadCategory: recommendationContext.lead.leadCategory,
      routingStatus: finalizedRecommendation.routingStatus,
      reviewStatus: finalizedRecommendation.reviewStatus,
      recommendationStatus: finalizedRecommendation.recommendationStatus,
      suggestedSupplier: finalizedRecommendation.suggestedSupplier,
      commissionPreview: finalizedRecommendation.commissionPreview,
      commerceChannel: finalizedRecommendation.commerceChannel,
      channelDecisionReason: finalizedRecommendation.channelDecisionReason,
      affiliateProvider: finalizedRecommendation.affiliateProvider,
      affiliateCandidate: finalizedRecommendation.affiliateCandidate,
      amazonAsin: finalizedRecommendation.amazonAsin,
      amazonSearchQuery: finalizedRecommendation.amazonSearchQuery,
      amazonSpecialLink: finalizedRecommendation.amazonSpecialLink,
      amazonContentRefreshedAt: finalizedRecommendation.amazonSpecialLink
        ? admin.firestore.FieldValue.serverTimestamp()
        : null,
      affiliateDisclosureRequired: finalizedRecommendation.affiliateDisclosureRequired,
      conversionStatus: finalizedRecommendation.conversionStatus,
      whatsappState: finalizedRecommendation.whatsappState,
      fallbackTriggeredAt: finalizedRecommendation.fallbackTriggered
        ? admin.firestore.FieldValue.serverTimestamp()
        : null,
      adminFallbackReason: finalizedRecommendation.adminFallbackReason,
      suggestionGeneratedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      ok: true,
      leadId,
      recommendationStatus: finalizedRecommendation.recommendationStatus,
      routingStatus: finalizedRecommendation.routingStatus,
      commerceChannel: finalizedRecommendation.commerceChannel,
      suggestedSupplierId: finalizedRecommendation.suggestedSupplier?.supplierId || null,
      adminFallbackReason: finalizedRecommendation.adminFallbackReason,
      amazonAsin: finalizedRecommendation.amazonAsin || null,
      amazonSpecialLink: finalizedRecommendation.amazonSpecialLink || null,
    };
  } catch (error) {
    console.error(`Failed to recommend supplier for lead ${leadId}:`, error);
    await leadRef.set({
      routingStatus: "admin_queue",
      reviewStatus: "pending_admin_review",
      recommendationStatus: "no_match",
      commerceChannel: COMMERCE_CHANNEL_ADMIN_REVIEW,
      channelDecisionReason: fallbackReason,
      affiliateProvider: null,
      affiliateCandidate: null,
      amazonAsin: null,
      amazonSearchQuery: normalizeProductName(leadData?.productName) || null,
      amazonSpecialLink: null,
      amazonContentRefreshedAt: null,
      affiliateDisclosureRequired: false,
      conversionStatus: "intent_captured",
      whatsappState: "not_ready",
      fallbackTriggeredAt: null,
      adminFallbackReason: fallbackReason,
      suggestionGeneratedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    return {
      ok: false,
      leadId,
      recommendationStatus: "no_match",
      routingStatus: "admin_queue",
      commerceChannel: COMMERCE_CHANNEL_ADMIN_REVIEW,
      suggestedSupplierId: null,
      adminFallbackReason: fallbackReason,
      error: trimString(error?.message) || "Recommendation failed",
    };
  }
}

exports.recommendSupplierForLead = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .firestore.document(`${SALES_PIPELINE_COLLECTION}/{leadId}`)
  .onCreate(async (snapshot, context) => {
    const leadData = snapshot.data() || {};
    if (!shouldAttemptRecommendation(leadData)) {
      return null;
    }

    await applyLeadRecommendation({
      leadRef: snapshot.ref,
      leadData,
      leadId: context.params.leadId,
      fallbackReason: "recommendation_failed",
    });

    return null;
  });

exports.retryLeadRecommendation = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const leadId = trimString(data?.leadId);
    if (!leadId) {
      throw new functions.https.HttpsError("invalid-argument", "leadId is required");
    }

    const leadRef = db.collection(SALES_PIPELINE_COLLECTION).doc(leadId);
    const leadSnap = await leadRef.get();
    if (!leadSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Lead not found");
    }

    const leadData = leadSnap.data() || {};
    const result = await applyLeadRecommendation({
      leadRef,
      leadData,
      leadId,
      fallbackReason: "manual_retry_failed",
    });

    return result;
  });

exports.backfillPendingLeadRecommendations = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 120,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const requestedLimit = Number(data?.limit);
    const limitCount = Number.isFinite(requestedLimit)
      ? Math.min(Math.max(Math.floor(requestedLimit), 1), 50)
      : 20;
    const requestedLeadIds = Array.isArray(data?.leadIds)
      ? [...new Set(data.leadIds.map((leadId) => trimString(leadId)).filter(Boolean))].slice(0, 50)
      : [];

    const candidateLeadDocs = requestedLeadIds.length > 0
      ? (await Promise.all(
        requestedLeadIds.map((leadId) => db.collection(SALES_PIPELINE_COLLECTION).doc(leadId).get())
      )).filter((snapshot) => snapshot.exists)
      : (await db.collection(SALES_PIPELINE_COLLECTION)
        .where("status", "==", SALES_PIPELINE_STATUS_INITIATED)
        .limit(limitCount)
        .get()).docs;

    const results = [];
    for (const leadDoc of candidateLeadDocs) {
      const leadData = leadDoc.data() || {};
      if (!shouldAttemptRecommendation(leadData)) {
        continue;
      }

      const result = await applyLeadRecommendation({
        leadRef: leadDoc.ref,
        leadData,
        leadId: leadDoc.id,
        fallbackReason: "backfill_recommendation_failed",
      });
      results.push(result);
    }

    return {
      ok: true,
      processedCount: results.length,
      results,
    };
  });

exports.adminNormalizeSupplierIdentities = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 120,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const requestedPhone = normalizeSupplierPhone(data?.phone);
    const normalizationResults = [];

    if (requestedPhone) {
      const normalizationResult = await reconcileSupplierPhoneIdentity(requestedPhone);
      if (normalizationResult) {
        normalizationResults.push(normalizationResult);
      }
    } else {
      const supplierSnapshot = await db.collection(SUPPLIERS_COLLECTION).get();
      const phoneCounts = new Map();

      supplierSnapshot.docs.forEach((doc) => {
        const normalizedPhone = normalizeSupplierPhone(doc.data()?.phone);
        if (!normalizedPhone) return;
        phoneCounts.set(normalizedPhone, (phoneCounts.get(normalizedPhone) || 0) + 1);
      });

      for (const [phone, count] of phoneCounts.entries()) {
        if (count < 2) continue;
        const normalizationResult = await reconcileSupplierPhoneIdentity(phone);
        if (normalizationResult) {
          normalizationResults.push(normalizationResult);
        }
      }
    }

    return {
      ok: true,
      normalizedCount: normalizationResults.length,
      results: normalizationResults,
    };
  });

exports.adminAssignLeadsToSupplier = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const actor = buildAdminActor(context);
    const { supplierId, supplierData } = await getApprovedSupplierForAssignment(data?.supplierId);
    const leadIds = Array.isArray(data?.leadIds)
      ? [...new Set(data.leadIds.map((leadId) => trimString(leadId)).filter(Boolean))].slice(0, 50)
      : [];

    if (leadIds.length === 0) {
      throw new functions.https.HttpsError("invalid-argument", "leadIds is required");
    }

    const leadSnapshots = await Promise.all(
      leadIds.map((leadId) => db.collection(SALES_PIPELINE_COLLECTION).doc(leadId).get())
    );
    const missingLeadIds = leadSnapshots.filter((snapshot) => !snapshot.exists).map((snapshot, index) => (
      snapshot.exists ? null : leadIds[index]
    )).filter(Boolean);

    if (missingLeadIds.length > 0) {
      throw new functions.https.HttpsError("not-found", `Lead not found: ${missingLeadIds.join(", ")}`);
    }

    const batch = db.batch();
    const selectedAt = new Date().toISOString();
    const updatePayload = buildAdminLeadAssignmentUpdate({
      supplierId,
      supplierData,
      actor,
      selectedAt,
    });

    leadSnapshots.forEach((leadSnapshot) => {
      batch.set(leadSnapshot.ref, updatePayload, { merge: true });
    });

    await batch.commit();

    return {
      ok: true,
      leadIds,
      supplierId,
      assignedCount: leadIds.length,
    };
  });

exports.adminSendLeadAffiliateAppMessage = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const leadId = trimString(data?.leadId);
    const affiliateLinkOverride = optionalString(data?.affiliateLink);
    if (!leadId) {
      throw new functions.https.HttpsError("invalid-argument", "leadId is required");
    }

    const actor = buildAdminActor(context);
    const leadRef = db.collection(SALES_PIPELINE_COLLECTION).doc(leadId);
    const leadSnapshot = await leadRef.get();
    if (!leadSnapshot.exists) {
      throw new functions.https.HttpsError("not-found", "Lead not found");
    }

    const leadData = leadSnapshot.data() || {};
    const userId = trimString(leadData.userId);
    const conversationId = trimString(leadData.conversationId);
    const affiliateLink = affiliateLinkOverride
      || trimString(leadData.amazonSpecialLink)
      || trimString(leadData?.affiliateCandidate?.specialLink);

    if (!affiliateLink) {
      throw new functions.https.HttpsError("failed-precondition", "Affiliate link is required before sending an app message");
    }
    if (!userId || !conversationId) {
      throw new functions.https.HttpsError("failed-precondition", "Lead is missing conversation context");
    }

    const handoffMessage = buildAffiliateHandoffMessage({
      leadData,
      affiliateLink,
    });
    const notificationPayload = buildAffiliateNotificationPayload({ leadData });
    const now = admin.firestore.Timestamp.now();
    const conversationRef = db.collection(CONVERSATIONS_COLLECTION).doc(conversationId);

    await db.runTransaction(async (transaction) => {
      const conversationSnapshot = await transaction.get(conversationRef);
      const conversationData = conversationSnapshot.exists ? (conversationSnapshot.data() || {}) : {};
      const existingMessages = Array.isArray(conversationData.messages)
        ? conversationData.messages
        : [];
      const nextMessage = buildAssistantConversationMessage({
        text: handoffMessage,
        timestamp: now,
      });

      transaction.set(conversationRef, {
        userId,
        title: trimString(conversationData.title) || buildConversationTitle(leadData),
        messages: [...existingMessages, nextMessage],
        createdAt: conversationData.createdAt || now,
        updatedAt: now,
      }, { merge: true });

      transaction.set(leadRef, {
        ...buildManualAmazonAffiliateLeadPatch({
          leadData,
          affiliateLink,
          actor,
          timestampValue: now,
          handoffChannel: "app",
          handoffMessagePreview: handoffMessage,
        }),
        lastOpsActionAt: now,
      }, { merge: true });
    });

    let notificationSent = false;
    let notificationMessageId = null;
    const userTopic = buildUserNotificationTopic(userId);
    if (userTopic) {
      try {
        notificationMessageId = await admin.messaging().send({
          topic: userTopic,
          android: {
            priority: "high",
            notification: {
              channelId: "krishi_notifications",
              sound: "default",
              tag: `lead-${leadId}`,
            },
          },
          notification: {
            title: notificationPayload.title,
            body: notificationPayload.body,
          },
          data: {
            conversation_id: conversationId,
            lead_id: leadId,
            handoff_channel: "app",
          },
        });
        notificationSent = true;
      } catch (error) {
        console.error(`Failed to send affiliate handoff notification for lead ${leadId}:`, error);
      }
    }

    return {
      ok: true,
      leadId,
      conversationId,
      notificationSent,
      notificationMessageId,
    };
  });

exports.adminAdvanceLeadWorkflow = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    await assertAdminContext(context);

    const action = trimString(data?.action).toLowerCase();
    const leadId = trimString(data?.leadId);
    const closedReason = optionalString(data?.closedReason) || "processing_completed";
    const supportedActions = new Set([
      "approve_commission",
      "mark_paid",
      "complete_processing",
      "close_lead",
    ]);

    if (!supportedActions.has(action)) {
      throw new functions.https.HttpsError("invalid-argument", "Unsupported workflow action");
    }
    if (!leadId) {
      throw new functions.https.HttpsError("invalid-argument", "leadId is required");
    }

    const actor = buildAdminActor(context);
    const leadRef = db.collection(SALES_PIPELINE_COLLECTION).doc(leadId);
    const leadSnapshot = await leadRef.get();
    if (!leadSnapshot.exists) {
      throw new functions.https.HttpsError("not-found", "Lead not found");
    }

    const leadData = leadSnapshot.data() || {};
    const routingStatus = trimString(leadData.routingStatus) || "initiated";
    const commissionStatus = trimString(leadData.commissionStatus) || "preview";
    const backendProcessingStatus = trimString(leadData.backendProcessingStatus) || "pending";
    const ledgerRef = db.collection(COMMISSION_LEDGER_COLLECTION).doc(leadId);
    let affectedMonthKey = trimString(leadData.commissionMonthKey) || null;

    if (action === "approve_commission") {
      const commissionAmount = getLeadCommissionAmount(leadData);
      if (commissionAmount == null) {
        throw new functions.https.HttpsError("failed-precondition", "Lead is missing a commission amount");
      }
      if (!["supplier_accepted", "admin_claimed"].includes(routingStatus)) {
        throw new functions.https.HttpsError("failed-precondition", `Lead cannot be approved from status ${routingStatus || "unknown"}`);
      }
      if (["approved", "paid"].includes(commissionStatus)) {
        throw new functions.https.HttpsError("failed-precondition", "Commission has already been approved for this lead");
      }

      affectedMonthKey = getCommissionMonthKey(new Date());
      const batch = db.batch();
      batch.set(leadRef, {
        routingStatus: "admin_claimed",
        commissionStatus: "approved",
        commissionLedgerEntryId: leadId,
        commissionMonthKey: affectedMonthKey,
        commissionApprovedAt: admin.firestore.FieldValue.serverTimestamp(),
        commissionApprovedByUid: actor.uid,
        commissionApprovedByEmail: actor.email,
        backendProcessingStatus: backendProcessingStatus || "pending",
        lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      batch.set(ledgerRef, {
        leadId,
        requestNumber: trimString(leadData.requestNumber) || null,
        supplierId: trimString(leadData?.assignedSupplier?.supplierId) || null,
        supplierName: trimString(leadData?.assignedSupplier?.businessName) || null,
        userId: trimString(leadData.userId) || null,
        leadCategory: trimString(leadData.leadCategory) || null,
        amount: commissionAmount,
        currency: trimString(leadData?.commissionPreview?.currency) || "INR",
        monthKey: affectedMonthKey,
        status: "approved",
        approvedAt: admin.firestore.FieldValue.serverTimestamp(),
        approvedByUid: actor.uid,
        approvedByEmail: actor.email,
        paidAt: null,
        paidByUid: null,
        paidByEmail: null,
        leadClosedAt: null,
        leadClosedReason: null,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      await batch.commit();

      return {
        ok: true,
        action,
        leadId,
        monthSummary: await rebuildCommissionMonthSummary(affectedMonthKey),
      };
    }

    if (action === "mark_paid") {
      if (commissionStatus !== "approved") {
        throw new functions.https.HttpsError("failed-precondition", "Only approved commission can be marked paid");
      }
      const ledgerSnapshot = await ledgerRef.get();
      if (!ledgerSnapshot.exists) {
        throw new functions.https.HttpsError("failed-precondition", "Commission ledger entry is missing for this lead");
      }

      const batch = db.batch();
      batch.set(leadRef, {
        commissionStatus: "paid",
        commissionPaidAt: admin.firestore.FieldValue.serverTimestamp(),
        commissionPaidByUid: actor.uid,
        commissionPaidByEmail: actor.email,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      batch.set(ledgerRef, {
        status: "paid",
        paidAt: admin.firestore.FieldValue.serverTimestamp(),
        paidByUid: actor.uid,
        paidByEmail: actor.email,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
      await batch.commit();

      return {
        ok: true,
        action,
        leadId,
        monthSummary: await rebuildCommissionMonthSummary(affectedMonthKey),
      };
    }

    if (action === "complete_processing") {
      if (!["approved", "paid"].includes(commissionStatus)) {
        throw new functions.https.HttpsError("failed-precondition", "Approve commission before completing backend processing");
      }
      if (backendProcessingStatus === "completed") {
        throw new functions.https.HttpsError("failed-precondition", "Backend processing is already completed");
      }
      if (!["supplier_accepted", "admin_claimed"].includes(routingStatus)) {
        throw new functions.https.HttpsError("failed-precondition", `Lead cannot complete processing from status ${routingStatus || "unknown"}`);
      }

      await leadRef.set({
        routingStatus: "admin_claimed",
        backendProcessingStatus: "completed",
        backendProcessedAt: admin.firestore.FieldValue.serverTimestamp(),
        backendProcessedByUid: actor.uid,
        backendProcessedByEmail: actor.email,
        lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });

      return {
        ok: true,
        action,
        leadId,
      };
    }

    if (routingStatus === "admin_closed") {
      throw new functions.https.HttpsError("failed-precondition", "Lead is already closed");
    }
    if (backendProcessingStatus !== "completed") {
      throw new functions.https.HttpsError("failed-precondition", "Complete backend processing before closing the lead");
    }
    if (!["approved", "paid"].includes(commissionStatus)) {
      throw new functions.https.HttpsError("failed-precondition", "Approve commission before closing the lead");
    }
    const ledgerSnapshot = await ledgerRef.get();
    if (!ledgerSnapshot.exists) {
      throw new functions.https.HttpsError("failed-precondition", "Commission ledger entry is missing for this lead");
    }

    const batch = db.batch();
    batch.set(leadRef, {
      routingStatus: "admin_closed",
      closedAt: admin.firestore.FieldValue.serverTimestamp(),
      closedByUid: actor.uid,
      closedByEmail: actor.email,
      closedReason,
      lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    batch.set(ledgerRef, {
      leadClosedAt: admin.firestore.FieldValue.serverTimestamp(),
      leadClosedReason: closedReason,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
    await batch.commit();

    return {
      ok: true,
      action,
      leadId,
    };
  });

exports.syncCommissionMonthSummary = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 120,
    memory: "256MB",
  })
  .firestore.document(`${COMMISSION_LEDGER_COLLECTION}/{entryId}`)
  .onWrite(async (change) => {
    const monthKeys = [
      trimString(change.before.data()?.monthKey),
      trimString(change.after.data()?.monthKey),
    ].filter(Boolean);

    await Promise.all([...new Set(monthKeys)].map((monthKey) => rebuildCommissionMonthSummary(monthKey)));
    return null;
  });

exports.expireOverdueSupplierLeads = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 120,
    memory: "256MB",
  })
  .pubsub.schedule("*/30 * * * *")
  .timeZone("Asia/Kolkata")
  .onRun(async () => {
    const result = await expireOverdueSupplierLeadsBatch();
    console.log(`Expired ${result.expiredCount} overdue supplier leads`);
    return null;
  });

exports.createSalesPipelineLead = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 30,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    const userId = context.auth?.uid;
    if (!userId) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    const conversationId = trimString(data?.conversationId);
    const productName = trimString(data?.productName);
    const chatMessageText = trimString(data?.chatMessageText);
    const quantity = trimString(data?.quantity);
    const unit = trimString(data?.unit);
    const source = trimString(data?.source) || "chat_recommendation";

    if (!conversationId) {
      throw new functions.https.HttpsError("invalid-argument", "conversationId is required");
    }
    if (!productName) {
      throw new functions.https.HttpsError("invalid-argument", "productName is required");
    }
    if (!chatMessageText) {
      throw new functions.https.HttpsError("invalid-argument", "chatMessageText is required");
    }

    const farmerProfileRef = db
      .collection(USERS_COLLECTION)
      .doc(userId)
      .collection(SETTINGS_COLLECTION)
      .doc(FARMER_PROFILE_DOC);

    const farmerProfileSnap = await farmerProfileRef.get();
    const farmerProfile = farmerProfileSnap.exists ? farmerProfileSnap.data() : null;
    const leadFarmerProfileSnapshot = buildLeadFarmerProfileSnapshot(
      farmerProfile || {},
      context.auth?.token?.phone_number,
      context.auth?.token?.email,
    );
    const profileValidationError = getLeadProfileValidationError(leadFarmerProfileSnapshot);
    if (!farmerProfile || profileValidationError) {
      throw new functions.https.HttpsError("failed-precondition", profileValidationError || "farmer profile is required");
    }

    const docId = buildSalesPipelineDocId({
      userId,
      conversationId,
      productName,
    });
    const leadRef = db.collection(SALES_PIPELINE_COLLECTION).doc(docId);

    const result = await db.runTransaction(async (transaction) => {
      const existingSnap = await transaction.get(leadRef);
      if (existingSnap.exists) {
        const existingData = existingSnap.data() || {};
        return {
          requestNumber: existingData.requestNumber,
          status: existingData.status || SALES_PIPELINE_STATUS_INITIATED,
        };
      }

      const requestNumber = generateRequestNumber();
      const now = admin.firestore.FieldValue.serverTimestamp();
      const normalizedProductName = normalizeProductName(productName);

      transaction.set(leadRef, {
        userId,
        conversationId,
        requestNumber,
        status: SALES_PIPELINE_STATUS_INITIATED,
        source,
        dedupeKey: docId,
        productName,
        normalizedProductName,
        quantity: quantity || null,
        unit: unit || null,
        chatMessageText,
        farmerProfileSnapshot: leadFarmerProfileSnapshot,
        ...buildInitialLeadRoutingFields({
          farmerProfile: leadFarmerProfileSnapshot,
          productName,
          chatMessageText,
        }),
        createdAt: now,
        updatedAt: now,
      });

      return {
        requestNumber,
        status: SALES_PIPELINE_STATUS_INITIATED,
      };
    });

    return result;
  });

exports.upsertInstallAttribution = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 30,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    const installId = trimString(data?.installId);
    if (!installId) {
      throw new functions.https.HttpsError("invalid-argument", "installId is required");
    }

    const promoterId = optionalString(data?.promoterId);
    const rawReferrer = optionalString(data?.rawReferrer);
    const firstOpenAtEpochMillis = coerceEpochMillis(data?.firstOpenAtEpochMillis);
    const requestedStatus = trimString(data?.attributionStatus);
    const userId = context.auth?.uid || null;
    const installRef = db.collection(INSTALL_ATTRIBUTIONS_COLLECTION).doc(installId);

    await db.runTransaction(async (transaction) => {
      const existingSnap = await transaction.get(installRef);
      const existingData = existingSnap.exists ? existingSnap.data() || {} : {};

      const finalPromoterId = existingData.promoterId || promoterId || null;
      const finalRawReferrer = existingData.rawReferrer || rawReferrer || null;
      const finalUserId = existingData.userId || userId || null;
      const finalStatus = finalPromoterId ?
        ATTRIBUTION_STATUS_PROMOTER_ATTRIBUTED :
        normalizeInstallAttributionStatus(existingData.attributionStatus || requestedStatus, null);
      const firstOpenAt = existingData.firstOpenAt ||
        (firstOpenAtEpochMillis ? admin.firestore.Timestamp.fromMillis(firstOpenAtEpochMillis) : admin.firestore.Timestamp.now());
      const createdAt = existingData.createdAt || admin.firestore.FieldValue.serverTimestamp();

      transaction.set(installRef, {
        installId,
        userId: finalUserId,
        promoterId: finalPromoterId,
        rawReferrer: finalRawReferrer,
        attributionStatus: finalStatus,
        firstOpenAt,
        createdAt,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return {
      ok: true,
      installId,
    };
  });

exports.getInstallAttributionReport = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onRequest(async (req, res) => {
    if (req.method !== "GET") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      const snapshot = await db.collection(INSTALL_ATTRIBUTIONS_COLLECTION).get();
      const groupedResults = new Map();

      snapshot.forEach((doc) => {
        const data = doc.data() || {};
        const promoterId = optionalString(data.promoterId);
        const groupKey = promoterId || "__organic__";
        const current = groupedResults.get(groupKey) || {
          promoterId,
          installsCount: 0,
          signupsCount: 0,
        };

        current.installsCount += 1;
        if (optionalString(data.userId)) {
          current.signupsCount += 1;
        }

        groupedResults.set(groupKey, current);
      });

      const rows = Array.from(groupedResults.values())
        .sort((left, right) => {
          if (left.promoterId === null) return 1;
          if (right.promoterId === null) return -1;
          return left.promoterId.localeCompare(right.promoterId);
        });

      res.json({
        rows,
        totalInstallations: snapshot.size,
      });
    } catch (error) {
      console.error("Error generating install attribution report:", error);
      res.status(500).json({
        error: error.message || "Unable to generate install attribution report",
      });
    }
  });

exports.agentChatProxy = functions
  .region("asia-south1")
  .runWith({
    timeoutSeconds: 60,
    memory: "256MB",
  })
  .https.onCall(async (data, context) => {
    const userId = context.auth?.uid;
    if (!userId) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required");
    }

    const conversationId = trimString(data?.conversationId);
    const message = trimString(data?.message);
    const locale = trimString(data?.locale) || "en";
    const imageUrl = trimString(data?.imageUrl) || null;
    const recentMessages = normalizeRecentMessages(data?.recentMessages);

    if (!conversationId) {
      throw new functions.https.HttpsError("invalid-argument", "conversationId is required");
    }

    if (!message && !imageUrl) {
      throw new functions.https.HttpsError("invalid-argument", "message or imageUrl is required");
    }

    if (!AGENT_SERVICE_URL) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "AGENT_SERVICE_URL is not configured"
      );
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), AGENT_PROXY_TIMEOUT_MS);

    try {
      const targetUrl = `${AGENT_SERVICE_URL}/chat`;
      const authHeaders = await getAgentServiceAuthHeaders(targetUrl);
      const response = await fetch(targetUrl, {
        method: "POST",
        signal: controller.signal,
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
          ...(AGENT_SERVICE_SHARED_SECRET
            ? { "X-Agent-Service-Token": AGENT_SERVICE_SHARED_SECRET }
            : {}),
        },
        body: JSON.stringify({
          userId,
          conversationId,
          message,
          locale,
          imageUrl,
          recentMessages,
        }),
      });

      if (!response.ok) {
        const errorMessage = await readAgentServiceError(response);
        if (response.status === 400) {
          throw new functions.https.HttpsError("invalid-argument", errorMessage);
        }
        if (response.status === 401 || response.status === 403) {
          throw new functions.https.HttpsError("permission-denied", errorMessage);
        }
        throw new functions.https.HttpsError("unavailable", errorMessage);
      }

      return await response.json();
    } catch (error) {
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }

      if (error.name === "AbortError") {
        throw new functions.https.HttpsError(
          "deadline-exceeded",
          "Timed out while waiting for the agent service"
        );
      }

      console.error("Agent chat proxy failed:", error);
      throw new functions.https.HttpsError(
        "unavailable",
        trimString(error?.message) || "Unable to contact the agent service"
      );
    } finally {
      clearTimeout(timeoutId);
    }
  });
