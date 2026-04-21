const test = require("node:test");
const assert = require("node:assert/strict");

const {
  AFFILIATE_CANDIDATE_REVIEW_STATUS_PENDING,
  AFFILIATE_HISTORY_SOURCE_AMAZON_REPORT,
  AFFILIATE_PRODUCT_REGISTRY_CANDIDATES_COLLECTION,
  buildAffiliateProductRegistryCandidateDocId,
  buildAffiliateRegistryCandidateImportPayload,
  buildAffiliateRegistryImportBatches,
} = require("../affiliateRegistryHistory");

test("buildAffiliateProductRegistryCandidateDocId is stable for ASIN-backed candidates", () => {
  const left = buildAffiliateProductRegistryCandidateDocId({
    provider: "amazon",
    asin: "b0testasin",
    marketplace: "WWW.AMAZON.IN",
  });
  const right = buildAffiliateProductRegistryCandidateDocId({
    provider: " amazon ",
    asin: "B0TESTASIN",
    marketplace: "www.amazon.in",
  });

  assert.ok(left);
  assert.equal(left, right);
});

test("buildAffiliateRegistryImportBatches groups historical rows by candidate id", () => {
  const batches = buildAffiliateRegistryImportBatches({
    sourceFile: "amazon-orders-apr.csv",
    rows: [
      {
        asin: "B0TESTASIN",
        sourceProductName: "Neem Spray Bottle",
        orderCount: 2,
      },
      {
        asin: "B0TESTASIN",
        sourceProductName: "Neem Spray Combo",
        orderCount: 1,
      },
      {
        asin: "B0SECONDASIN",
        sourceProductName: "NPK Fertilizer",
        orderCount: 3,
      },
    ],
  });

  assert.equal(AFFILIATE_PRODUCT_REGISTRY_CANDIDATES_COLLECTION, "affiliate_product_registry_candidates");
  assert.equal(batches.length, 2);
  assert.equal(batches[0].rows[0].source, AFFILIATE_HISTORY_SOURCE_AMAZON_REPORT);
});

test("buildAffiliateRegistryCandidateImportPayload aggregates evidence and skips duplicate rows", () => {
  const [{ candidateId, rows }] = buildAffiliateRegistryImportBatches({
    sourceFile: "amazon-orders-apr.csv",
    rows: [
      {
        asin: "B0TESTASIN",
        sourceProductName: "Neem Spray Bottle",
        orderCount: 2,
        lastSeenAt: "2026-04-18",
      },
      {
        asin: "B0TESTASIN",
        sourceProductName: "Neem Spray Bottle",
        orderCount: 2,
        lastSeenAt: "2026-04-18",
      },
      {
        asin: "B0TESTASIN",
        sourceProductName: "Neem Spray Combo",
        orderCount: 1,
        lastSeenAt: "2026-04-19",
      },
    ],
  });

  const payload = buildAffiliateRegistryCandidateImportPayload({
    candidateId,
    rows,
    actor: {
      uid: "admin-1",
      email: "admin@example.com",
    },
    buildSpecialLink: ({ asin, marketplace }) => `https://${marketplace}/dp/${asin}?tag=store-21`,
  });

  assert.equal(payload.addedOrderCount, 3);
  assert.equal(payload.addedRowCount, 2);
  assert.equal(payload.skippedRowCount, 1);
  assert.equal(payload.data.reviewStatus, AFFILIATE_CANDIDATE_REVIEW_STATUS_PENDING);
  assert.equal(payload.data.generatedSpecialLink, "https://www.amazon.in/dp/B0TESTASIN?tag=store-21");
  assert.equal(payload.data.orderCount, 3);
  assert.equal(payload.data.sourceRowCount, 2);
  assert.deepEqual(payload.data.rawTitles, ["Neem Spray Bottle", "Neem Spray Combo"]);
  assert.equal(payload.data.updatedByEmail, "admin@example.com");
});