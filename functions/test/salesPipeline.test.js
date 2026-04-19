const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildAmazonAffiliateCandidate,
  buildSalesPipelineDocId,
  buildInitialCommerceFields,
  generateRequestNumber,
  normalizeProductName,
} = require("../salesPipeline");

test("buildInitialCommerceFields starts leads in supplier-first mode", () => {
  assert.deepEqual(buildInitialCommerceFields({ productName: "Neem Spray" }), {
    commerceChannel: "supplier_local",
    channelDecisionReason: "default_supplier_first",
    fallbackPolicy: "amazon_on_no_match_or_timeout",
    affiliateProvider: null,
    affiliateCandidate: null,
    amazonAsin: null,
    amazonSearchQuery: "neem spray",
    amazonSpecialLink: null,
    amazonContentRefreshedAt: null,
    affiliateDisclosureRequired: false,
    conversionStatus: "intent_captured",
    whatsappState: "not_ready",
    fallbackTriggeredAt: null,
  });
});

test("buildAmazonAffiliateCandidate returns stub candidate for provider integration", () => {
  assert.deepEqual(buildAmazonAffiliateCandidate({
    lead: {
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadCategory: "pesticide",
    },
  }), {
    provider: "amazon",
    providerStatus: "stub_pending_provider",
    reason: "no_matching_supplier",
    stubbed: true,
    productName: "Neem Spray",
    normalizedProductName: "neem spray",
    leadCategory: "pesticide",
    searchQuery: "neem spray",
    asin: null,
    specialLink: null,
  });
});

test("normalizeProductName trims, lowercases, and collapses spaces", () => {
  assert.equal(normalizeProductName("  Urea   50 KG  "), "urea 50 kg");
});

test("buildSalesPipelineDocId is deterministic for normalized product names", () => {
  const first = buildSalesPipelineDocId({
    userId: "user-1",
    conversationId: "conv-1",
    productName: "Urea  50 KG",
  });
  const second = buildSalesPipelineDocId({
    userId: "user-1",
    conversationId: "conv-1",
    productName: "  urea 50 kg ",
  });

  assert.equal(first, second);
  assert.equal(first.length, 32);
});

test("generateRequestNumber returns the expected tracking format", () => {
  const requestNumber = generateRequestNumber(new Date("2026-03-10T12:00:00Z"));
  assert.match(requestNumber, /^KR-20260310-[A-F0-9]{6}$/);
});

