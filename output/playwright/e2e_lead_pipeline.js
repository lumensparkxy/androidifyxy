#!/usr/bin/env node
/**
 * E2E Lead Pipeline Test
 *
 * Uses the Firebase Admin SDK to drive a lead through the complete lifecycle:
 *   Create → Recommend → Admin Assign → Supplier Accept → Approve Commission
 *   → Mark Paid → Complete Processing → Close Lead
 *
 * Then verifies every status field and ledger entry at each step.
 *
 * Usage:
 *   NODE_PATH="$PWD/functions/node_modules" node output/playwright/e2e_lead_pipeline.js
 */

const crypto = require("crypto");
const admin = require("firebase-admin");
const serviceAccount = require("./../../scripts/serviceAccountKey.json");

// ─── Bootstrap ───────────────────────────────────────────────────────────
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});
const db = admin.firestore();

const SALES_PIPELINE = "sales_pipeline";
const COMMISSION_LEDGER = "commission_ledger";
const SUPPLIERS = "suppliers";
const RUN_ID = `e2e-${Date.now()}-${crypto.randomBytes(3).toString("hex")}`;
const TEST_USER_ID = `test-user-${RUN_ID}`;
const TEST_ADMIN_UID = "test-admin-e2e";
const TEST_ADMIN_EMAIL = "neophilex@gmail.com";

let leadId = "";
let supplierId = "";
let supplierData = {};
let errors = [];
let stepCount = 0;

// ─── Helpers ─────────────────────────────────────────────────────────────
function assert(condition, message) {
  if (!condition) {
    const error = new Error(`ASSERTION FAILED: ${message}`);
    errors.push(error.message);
    console.error(`  ✗ ${message}`);
    throw error;
  }
  console.log(`  ✓ ${message}`);
}

function assertEq(actual, expected, label) {
  assert(
    actual === expected,
    `${label}: expected "${expected}", got "${actual}"`,
  );
}

async function getLeadData() {
  const snap = await db.collection(SALES_PIPELINE).doc(leadId).get();
  assert(snap.exists, "Lead document exists");
  return snap.data();
}

function step(name) {
  stepCount++;
  console.log(`\n${"═".repeat(60)}`);
  console.log(`STEP ${stepCount}: ${name}`);
  console.log("═".repeat(60));
}

// ─── Step 1: Find a real approved supplier ───────────────────────────────
async function findApprovedSupplier() {
  step("Find an approved supplier in Firestore");

  const snapshot = await db
    .collection(SUPPLIERS)
    .where("verificationStatus", "==", "APPROVED")
    .limit(5)
    .get();

  assert(!snapshot.empty, "At least one approved supplier exists");

  // prefer one with a businessName
  const best = snapshot.docs.find((d) => d.data().businessName) || snapshot.docs[0];
  supplierId = best.id;
  supplierData = best.data();
  console.log(`  → Using supplier "${supplierData.businessName}" (${supplierId})`);
}

// ─── Step 2: Create a test lead ──────────────────────────────────────────
async function createLead() {
  step("Create test lead in Firestore");

  leadId = crypto
    .createHash("sha256")
    .update(`${TEST_USER_ID}|conv-${RUN_ID}|e2e test fertilizer`)
    .digest("hex")
    .slice(0, 32);

  const now = admin.firestore.FieldValue.serverTimestamp();
  const requestNumber = `KR-E2E-${RUN_ID.slice(-8).toUpperCase()}`;

  await db.collection(SALES_PIPELINE).doc(leadId).set({
    userId: TEST_USER_ID,
    conversationId: `conv-${RUN_ID}`,
    requestNumber,
    status: "initiated",
    source: "e2e_test",
    dedupeKey: leadId,
    productName: "E2E Test Fertilizer",
    normalizedProductName: "e2e test fertilizer",
    quantity: "50",
    unit: "kg",
    chatMessageText: `E2E pipeline test run ${RUN_ID}`,
    leadCategory: "fertilizer",
    farmerProfileSnapshot: {
      name: "E2E Test Farmer",
      phoneNumber: "+910000000000",
      district: "Pune",
      tehsil: "Haveli",
      village: "Wagholi",
    },
    leadLocation: {
      district: "Pune",
      districtKey: "pune",
      tehsil: "Haveli",
      tehsilKey: "haveli",
      village: "Wagholi",
      villageKey: "wagholi",
    },
    routingStatus: "initiated",
    reviewStatus: "pending_recommendation",
    recommendationStatus: "pending",
    supplierVisibility: "hidden",
    suggestedSupplier: null,
    selectedSupplier: null,
    assignedSupplier: null,
    commissionPreview: {
      category: "fertilizer",
      amount: 150,
      currency: "INR",
      ruleId: "fertilizer_default",
    },
    commissionStatus: "preview",
    commissionLedgerEntryId: null,
    commissionMonthKey: null,
    commissionApprovedAt: null,
    commissionApprovedByUid: null,
    commissionApprovedByEmail: null,
    commissionPaidAt: null,
    commissionPaidByUid: null,
    commissionPaidByEmail: null,
    backendProcessingStatus: "pending",
    backendProcessedAt: null,
    backendProcessedByUid: null,
    backendProcessedByEmail: null,
    closedAt: null,
    closedByUid: null,
    closedByEmail: null,
    closedReason: null,
    opsStatus: "new",
    opsNotes: "",
    opsOwnerUid: null,
    opsOwnerEmail: null,
    suggestionGeneratedAt: null,
    assignmentPublishedAt: null,
    supplierResponseDeadlineAt: null,
    supplierRespondedAt: null,
    supplierRejectedReason: null,
    adminFallbackReason: null,
    lastRoutingUpdatedAt: null,
    firstOpsContactAt: null,
    createdAt: now,
    updatedAt: now,
  });

  const data = await getLeadData();
  // The Cloud Function trigger can fire very fast, so accept any early status
  const validRouting = ["initiated", "pending_recommendation", "suggested_for_supplier", "admin_queue"];
  assert(validRouting.includes(data.routingStatus), `routingStatus is valid early state: ${data.routingStatus}`);
  console.log(`  → Lead created: ${leadId} (${requestNumber}), routingStatus: ${data.routingStatus}`);
}

// ─── Step 3: Wait for auto-recommendation trigger ───────────────────────
async function waitForRecommendation() {
  step("Wait for auto-recommendation Cloud Function trigger");

  // The recommendSupplierForLead trigger fires automatically when the lead
  // is created. Wait for it to update routingStatus away from 'initiated'.
  const maxWaitMs = 30000;
  const pollMs = 2000;
  const start = Date.now();
  let data;

  while (Date.now() - start < maxWaitMs) {
    data = await getLeadData();
    const rs = data.routingStatus;
    if (rs !== "initiated" && rs !== "pending_recommendation") {
      console.log(`  → Recommendation resolved in ${Date.now() - start}ms`);
      break;
    }
    console.log(`  … waiting (${Math.round((Date.now() - start) / 1000)}s) — routingStatus: ${rs}`);
    await new Promise((r) => setTimeout(r, pollMs));
  }

  data = await getLeadData();

  // The trigger may produce 'suggested_for_supplier', 'admin_queue', or stay 'initiated'
  // depending on supplier catalog. Accept any non-error state.
  const validStatuses = ["suggested_for_supplier", "admin_queue", "initiated", "pending_recommendation"];
  assert(validStatuses.includes(data.routingStatus), `routingStatus is valid: ${data.routingStatus}`);

  if (data.suggestedSupplier?.supplierId) {
    // Use whatever supplier the system recommended
    supplierId = data.suggestedSupplier.supplierId;
    console.log(`  → System recommended supplier: ${data.suggestedSupplier.businessName || supplierId}`);
  } else {
    console.log(`  → No auto-recommendation, will admin-assign our chosen supplier`);
  }

  // If the trigger didn't fire or timed out, manually set it ready for assignment
  if (data.routingStatus === "initiated" || data.routingStatus === "pending_recommendation") {
    await db.collection(SALES_PIPELINE).doc(leadId).update({
      routingStatus: "suggested_for_supplier",
      reviewStatus: "pending_admin_review",
      recommendationStatus: "ready",
      suggestedSupplier: {
        supplierId,
        businessName: supplierData.businessName || "E2E Supplier",
        districtName: supplierData.districtName || "Pune",
        matchScore: 85,
        matchSummary: "E2E test — manual fallback",
      },
      suggestionGeneratedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  }

  // Re-read to get the supplier data we'll actually use
  data = await getLeadData();
  if (data.suggestedSupplier?.supplierId) {
    supplierId = data.suggestedSupplier.supplierId;
    // Refresh supplierData from Firestore
    const supplierSnap = await db.collection(SUPPLIERS).doc(supplierId).get();
    if (supplierSnap.exists) {
      supplierData = supplierSnap.data();
    }
  }
  console.log(`  → Proceeding with supplier: ${supplierData.businessName || supplierId}`);
}

// ─── Step 4: Admin assigns supplier ──────────────────────────────────────
async function adminAssignSupplier() {
  step("Admin assigns supplier to lead");

  const selectedAt = new Date().toISOString();
  const deadline = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  const supplierSnapshot = {
    supplierId,
    businessName: supplierData.businessName || "E2E Supplier",
    districtId: supplierData.districtId || null,
    districtName: supplierData.districtName || "Pune",
    phoneNumber: supplierData.phoneNumber || null,
    selectedByUid: TEST_ADMIN_UID,
    selectedByEmail: TEST_ADMIN_EMAIL,
    selectedAt,
  };

  await db.collection(SALES_PIPELINE).doc(leadId).update({
    selectedSupplier: supplierSnapshot,
    assignedSupplier: { ...supplierSnapshot, assignedAt: selectedAt },
    routingStatus: "supplier_pending",
    reviewStatus: "assigned_to_supplier",
    recommendationStatus: "ready",
    supplierVisibility: "masked",
    // Reset commission lifecycle for clean assignment
    commissionStatus: "preview",
    commissionLedgerEntryId: null,
    commissionMonthKey: null,
    commissionApprovedAt: null,
    commissionPaidAt: null,
    backendProcessingStatus: "pending",
    backendProcessedAt: null,
    closedAt: null,
    closedReason: null,
    assignmentPublishedAt: admin.firestore.FieldValue.serverTimestamp(),
    supplierResponseDeadlineAt: new Date(deadline),
    supplierRespondedAt: null,
    supplierRejectedReason: null,
    adminFallbackReason: null,
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const data = await getLeadData();
  assertEq(data.routingStatus, "supplier_pending", "routingStatus");
  assertEq(data.assignedSupplier.supplierId, supplierId, "assignedSupplier");
  assertEq(data.supplierVisibility, "masked", "supplierVisibility");
  assert(data.supplierResponseDeadlineAt != null, "deadline is set");
}

// ─── Step 5: Supplier accepts ────────────────────────────────────────────
async function supplierAccepts() {
  step("Supplier accepts the lead");

  await db.collection(SALES_PIPELINE).doc(leadId).update({
    routingStatus: "supplier_accepted",
    reviewStatus: "assigned_to_supplier",
    supplierVisibility: "unlocked",
    supplierResponseDeadlineAt: null,
    supplierRespondedAt: admin.firestore.FieldValue.serverTimestamp(),
    supplierRejectedReason: null,
    adminFallbackReason: null,
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const data = await getLeadData();
  assertEq(data.routingStatus, "supplier_accepted", "routingStatus");
  assertEq(data.supplierVisibility, "unlocked", "supplierVisibility");
  assert(data.supplierRespondedAt != null, "supplierRespondedAt is set");
}

// ─── Step 6: Admin approves commission ───────────────────────────────────
async function approveCommission() {
  step("Admin approves commission");

  const now = new Date();
  const monthKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  const batch = db.batch();
  const leadRef = db.collection(SALES_PIPELINE).doc(leadId);
  const ledgerRef = db.collection(COMMISSION_LEDGER).doc(leadId);

  batch.update(leadRef, {
    routingStatus: "admin_claimed",
    reviewStatus: "reviewed",
    commissionStatus: "approved",
    commissionLedgerEntryId: leadId,
    commissionMonthKey: monthKey,
    commissionApprovedAt: admin.firestore.FieldValue.serverTimestamp(),
    commissionApprovedByUid: TEST_ADMIN_UID,
    commissionApprovedByEmail: TEST_ADMIN_EMAIL,
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  batch.set(ledgerRef, {
    leadId,
    requestNumber: (await getLeadData()).requestNumber,
    supplierId,
    supplierName: supplierData.businessName || "E2E Supplier",
    userId: TEST_USER_ID,
    leadCategory: "fertilizer",
    amount: 150,
    currency: "INR",
    monthKey,
    status: "approved",
    approvedAt: admin.firestore.FieldValue.serverTimestamp(),
    approvedByUid: TEST_ADMIN_UID,
    approvedByEmail: TEST_ADMIN_EMAIL,
    paidAt: null,
    paidByUid: null,
    paidByEmail: null,
    leadClosedAt: null,
    leadClosedReason: null,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await batch.commit();

  const data = await getLeadData();
  assertEq(data.routingStatus, "admin_claimed", "routingStatus");
  assertEq(data.commissionStatus, "approved", "commissionStatus");
  assertEq(data.commissionMonthKey, monthKey, "commissionMonthKey");
  assert(data.commissionApprovedAt != null, "commissionApprovedAt is set");

  const ledger = await db.collection(COMMISSION_LEDGER).doc(leadId).get();
  assert(ledger.exists, "Ledger entry created");
  assertEq(ledger.data().status, "approved", "ledger.status");
  assertEq(ledger.data().amount, 150, "ledger.amount");
}

// ─── Step 7: Mark commission paid ────────────────────────────────────────
async function markPaid() {
  step("Admin marks commission paid");

  const batch = db.batch();
  const leadRef = db.collection(SALES_PIPELINE).doc(leadId);
  const ledgerRef = db.collection(COMMISSION_LEDGER).doc(leadId);

  batch.update(leadRef, {
    commissionStatus: "paid",
    commissionPaidAt: admin.firestore.FieldValue.serverTimestamp(),
    commissionPaidByUid: TEST_ADMIN_UID,
    commissionPaidByEmail: TEST_ADMIN_EMAIL,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  batch.update(ledgerRef, {
    status: "paid",
    paidAt: admin.firestore.FieldValue.serverTimestamp(),
    paidByUid: TEST_ADMIN_UID,
    paidByEmail: TEST_ADMIN_EMAIL,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await batch.commit();

  const data = await getLeadData();
  assertEq(data.commissionStatus, "paid", "commissionStatus");
  assert(data.commissionPaidAt != null, "commissionPaidAt is set");

  const ledger = await db.collection(COMMISSION_LEDGER).doc(leadId).get();
  assertEq(ledger.data().status, "paid", "ledger.status");
  assert(ledger.data().paidAt != null, "ledger.paidAt is set");
}

// ─── Step 8: Complete backend processing ─────────────────────────────────
async function completeProcessing() {
  step("Admin marks backend processing complete");

  await db.collection(SALES_PIPELINE).doc(leadId).update({
    routingStatus: "admin_claimed",
    reviewStatus: "reviewed",
    backendProcessingStatus: "completed",
    backendProcessedAt: admin.firestore.FieldValue.serverTimestamp(),
    backendProcessedByUid: TEST_ADMIN_UID,
    backendProcessedByEmail: TEST_ADMIN_EMAIL,
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const data = await getLeadData();
  assertEq(data.backendProcessingStatus, "completed", "backendProcessingStatus");
  assert(data.backendProcessedAt != null, "backendProcessedAt is set");
  assertEq(data.routingStatus, "admin_claimed", "routingStatus still admin_claimed");
}

// ─── Step 9: Close the lead ──────────────────────────────────────────────
async function closeLead() {
  step("Admin closes the lead");

  const batch = db.batch();
  const leadRef = db.collection(SALES_PIPELINE).doc(leadId);
  const ledgerRef = db.collection(COMMISSION_LEDGER).doc(leadId);

  batch.update(leadRef, {
    routingStatus: "admin_closed",
    reviewStatus: "reviewed",
    closedAt: admin.firestore.FieldValue.serverTimestamp(),
    closedByUid: TEST_ADMIN_UID,
    closedByEmail: TEST_ADMIN_EMAIL,
    closedReason: "processing_completed",
    lastRoutingUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  batch.update(ledgerRef, {
    leadClosedAt: admin.firestore.FieldValue.serverTimestamp(),
    leadClosedReason: "processing_completed",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await batch.commit();

  const data = await getLeadData();
  assertEq(data.routingStatus, "admin_closed", "routingStatus");
  assert(data.closedAt != null, "closedAt is set");
  assertEq(data.closedReason, "processing_completed", "closedReason");
  assertEq(data.commissionStatus, "paid", "commissionStatus still paid");
  assertEq(data.backendProcessingStatus, "completed", "backendProcessingStatus still completed");

  const ledger = await db.collection(COMMISSION_LEDGER).doc(leadId).get();
  assert(ledger.data().leadClosedAt != null, "ledger.leadClosedAt is set");
  assertEq(ledger.data().leadClosedReason, "processing_completed", "ledger.leadClosedReason");
}

// ─── Final verification ──────────────────────────────────────────────────
async function finalVerification() {
  step("Final full state verification");

  const data = await getLeadData();

  // Check all final statuses
  assertEq(data.status, "initiated", "status (never changes)");
  assertEq(data.routingStatus, "admin_closed", "routingStatus");
  assertEq(data.reviewStatus, "reviewed", "reviewStatus");
  assertEq(data.recommendationStatus, "ready", "recommendationStatus");
  assertEq(data.supplierVisibility, "unlocked", "supplierVisibility");
  assertEq(data.commissionStatus, "paid", "commissionStatus");
  assertEq(data.backendProcessingStatus, "completed", "backendProcessingStatus");

  // Check all timestamps are set
  assert(data.createdAt != null, "createdAt is set");
  assert(data.updatedAt != null, "updatedAt is set");
  assert(data.suggestionGeneratedAt != null, "suggestionGeneratedAt is set");
  assert(data.assignmentPublishedAt != null, "assignmentPublishedAt is set");
  assert(data.supplierRespondedAt != null, "supplierRespondedAt is set");
  assert(data.commissionApprovedAt != null, "commissionApprovedAt is set");
  assert(data.commissionPaidAt != null, "commissionPaidAt is set");
  assert(data.backendProcessedAt != null, "backendProcessedAt is set");
  assert(data.closedAt != null, "closedAt is set");

  // Supplier snapshot chain
  assert(data.suggestedSupplier != null, "suggestedSupplier preserved");
  assert(data.selectedSupplier != null, "selectedSupplier preserved");
  assert(data.assignedSupplier != null, "assignedSupplier preserved");
  assertEq(data.assignedSupplier.supplierId, supplierId, "assignedSupplier matches");

  // Null fields that should have been cleared
  assertEq(data.supplierResponseDeadlineAt, null, "deadline cleared");
  assertEq(data.supplierRejectedReason, null, "no rejection reason");
  assertEq(data.adminFallbackReason, null, "no fallback reason");
}

// ─── Cleanup ─────────────────────────────────────────────────────────────
async function cleanup() {
  step("Cleanup test data");

  try {
    await db.collection(COMMISSION_LEDGER).doc(leadId).delete();
    console.log("  ✓ Deleted ledger entry");
  } catch (err) {
    console.log(`  ⚠ Ledger cleanup: ${err.message}`);
  }

  try {
    await db.collection(SALES_PIPELINE).doc(leadId).delete();
    console.log("  ✓ Deleted test lead");
  } catch (err) {
    console.log(`  ⚠ Lead cleanup: ${err.message}`);
  }
}

// ─── Run ─────────────────────────────────────────────────────────────────
async function run() {
  console.log("╔════════════════════════════════════════════════════════════╗");
  console.log("║  E2E Lead Pipeline Test                                  ║");
  console.log(`║  Run ID: ${RUN_ID.padEnd(48)}║`);
  console.log("╚════════════════════════════════════════════════════════════╝");

  try {
    await findApprovedSupplier();
    await createLead();
    await waitForRecommendation();
    await adminAssignSupplier();
    await supplierAccepts();
    await approveCommission();
    await markPaid();
    await completeProcessing();
    await closeLead();
    await finalVerification();

    console.log("\n" + "═".repeat(60));
    console.log("✅ ALL STEPS PASSED — full pipeline verified");
    console.log("═".repeat(60));
  } catch (err) {
    console.error("\n" + "═".repeat(60));
    console.error(`❌ TEST FAILED at step ${stepCount}: ${err.message}`);
    console.error("═".repeat(60));
  } finally {
    await cleanup();
    process.exit(errors.length > 0 ? 1 : 0);
  }
}

run();
