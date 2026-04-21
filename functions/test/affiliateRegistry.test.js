const test = require("node:test");
const assert = require("node:assert/strict");

const {
  AFFILIATE_MATCH_SOURCE_REGISTRY_EXACT,
  AFFILIATE_PRODUCT_REGISTRY_COLLECTION,
  buildAffiliateProductRegistryDocId,
  buildAffiliateRegistryCandidate,
  buildAffiliateRegistryEntryPayload,
} = require("../affiliateRegistry");

test("buildAffiliateProductRegistryDocId is stable for normalized product names", () => {
  const left = buildAffiliateProductRegistryDocId({
    provider: "amazon",
    normalizedProductName: "neem spray",
  });
  const right = buildAffiliateProductRegistryDocId({
    provider: "amazon",
    normalizedProductName: "  neem   spray ",
  });

  assert.ok(left);
  assert.equal(left, right);
});

test("buildAffiliateRegistryEntryPayload normalizes the registry entry and extracts ASIN", () => {
  const payload = buildAffiliateRegistryEntryPayload({
    provider: "amazon",
    productName: " Neem Spray ",
    specialLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
    actor: {
      uid: "admin-1",
      email: "admin@example.com",
    },
  });

  assert.equal(payload.provider, "amazon");
  assert.equal(payload.productName, "Neem Spray");
  assert.equal(payload.normalizedProductName, "neem spray");
  assert.equal(payload.specialLink, "https://www.amazon.in/dp/B0TESTASIN?tag=store-21");
  assert.equal(payload.asin, "B0TESTASIN");
  assert.equal(payload.isActive, true);
  assert.equal(payload.updatedByUid, "admin-1");
  assert.equal(payload.updatedByEmail, "admin@example.com");
});

test("buildAffiliateRegistryCandidate creates a provider-ready exact-match candidate", () => {
  const candidate = buildAffiliateRegistryCandidate({
    lead: {
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadCategory: "pesticide",
    },
    registryEntry: {
      id: "registry-1",
      provider: "amazon",
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      specialLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
      asin: "B0TESTASIN",
    },
  });

  assert.equal(AFFILIATE_PRODUCT_REGISTRY_COLLECTION, "affiliate_product_registry");
  assert.equal(candidate.provider, "amazon");
  assert.equal(candidate.providerStatus, "provider_ready");
  assert.equal(candidate.reason, "registry_exact_match");
  assert.equal(candidate.matchSource, AFFILIATE_MATCH_SOURCE_REGISTRY_EXACT);
  assert.equal(candidate.registryEntryId, "registry-1");
  assert.equal(candidate.asin, "B0TESTASIN");
  assert.equal(candidate.specialLink, "https://www.amazon.in/dp/B0TESTASIN?tag=store-21");
});
