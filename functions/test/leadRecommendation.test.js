const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildLeadRecommendation,
  computeCommissionPreview,
} = require("../leadRecommendation");

test("computeCommissionPreview returns fixed category amount", () => {
  assert.deepEqual(computeCommissionPreview("seed"), {
    category: "seed",
    amount: 200,
    currency: "INR",
    ruleId: "fixed_seed",
  });
});

test("buildLeadRecommendation returns suggested supplier for best district and product match", () => {
  const lead = {
    leadCategory: "fertilizer",
    productName: "Urea",
    normalizedProductName: "urea",
    leadLocation: {
      district: "Pune",
      districtKey: "pune",
      tehsil: "Haveli",
      tehsilKey: "haveli",
      village: "Wagholi",
      villageKey: "wagholi",
    },
    quantity: "50",
  };

  const suppliersById = new Map([
    [
      "supplier-1",
      {
        businessName: "Green Agro",
        districtId: "MH:pune",
        districtName: "Pune",
        verificationStatus: "APPROVED",
        villageCoverage: ["Wagholi"],
        tehsilCoverage: ["Haveli"],
      },
    ],
    [
      "supplier-2",
      {
        businessName: "Farm Inputs",
        districtId: "MH:nashik",
        districtName: "Nashik",
        verificationStatus: "APPROVED",
      },
    ],
  ]);

  const recommendation = buildLeadRecommendation({
    lead,
    suppliersById,
    offers: [
      {
        id: "offer-1",
        supplierId: "supplier-1",
        supplierName: "Green Agro",
        supplierApproved: true,
        status: "ACTIVE",
        districtId: "MH:pune",
        category: "fertilizer",
        productName: "Urea",
        keywords: ["urea", "nitrogen"],
        packSize: 50,
      },
      {
        id: "offer-2",
        supplierId: "supplier-2",
        supplierName: "Farm Inputs",
        supplierApproved: true,
        status: "ACTIVE",
        districtId: "MH:nashik",
        category: "fertilizer",
        productName: "Urea",
        keywords: ["urea"],
      },
    ],
  });

  assert.equal(recommendation.routingStatus, "suggested_for_supplier");
  assert.equal(recommendation.recommendationStatus, "ready");
  assert.equal(recommendation.reviewStatus, "pending_admin_review");
  assert.equal(recommendation.adminFallbackReason, null);
  assert.equal(recommendation.commerceChannel, "supplier_local");
  assert.equal(recommendation.channelDecisionReason, "supplier_match_found");
  assert.equal(recommendation.affiliateProvider, null);
  assert.equal(recommendation.affiliateCandidate, null);
  assert.equal(recommendation.amazonSearchQuery, null);
  assert.equal(recommendation.affiliateDisclosureRequired, false);
  assert.equal(recommendation.fallbackTriggered, false);
  assert.equal(recommendation.suggestedSupplier?.supplierId, "supplier-1");
  assert.equal(recommendation.suggestedSupplier?.businessName, "Green Agro");
  assert.match(recommendation.suggestedSupplier?.matchSummary || "", /district-match/);
  assert.deepEqual(recommendation.commissionPreview, {
    category: "fertilizer",
    amount: 150,
    currency: "INR",
    ruleId: "fixed_fertilizer",
  });
});

test("buildLeadRecommendation falls back to admin queue when no supplier matches", () => {
  const recommendation = buildLeadRecommendation({
    lead: {
      leadCategory: "pesticide",
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadLocation: {
        district: "Satara",
        districtKey: "satara",
      },
    },
    suppliersById: new Map([
      [
        "supplier-1",
        {
          businessName: "Green Agro",
          districtId: "MH:pune",
          districtName: "Pune",
          verificationStatus: "APPROVED",
        },
      ],
    ]),
    offers: [
      {
        id: "offer-1",
        supplierId: "supplier-1",
        supplierApproved: true,
        status: "ACTIVE",
        districtId: "MH:pune",
        category: "pesticide",
        productName: "Neem Spray",
        keywords: ["neem spray"],
      },
    ],
  });

  assert.equal(recommendation.routingStatus, "admin_queue");
  assert.equal(recommendation.recommendationStatus, "no_match");
  assert.equal(recommendation.suggestedSupplier, null);
  assert.equal(recommendation.adminFallbackReason, "no_matching_supplier");
  assert.equal(recommendation.commerceChannel, "admin_review");
  assert.equal(recommendation.affiliateProvider, null);
  assert.equal(recommendation.affiliateCandidate, null);
  assert.equal(recommendation.amazonSearchQuery, null);
  assert.equal(recommendation.affiliateDisclosureRequired, false);
  assert.equal(recommendation.fallbackTriggered, false);
});

test("buildLeadRecommendation creates Amazon affiliate stub when fallback is enabled", () => {
  const recommendation = buildLeadRecommendation({
    lead: {
      leadCategory: "pesticide",
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadLocation: {
        district: "Satara",
        districtKey: "satara",
      },
    },
    suppliersById: new Map(),
    offers: [],
    affiliateFallback: {
      enabled: true,
    },
  });

  assert.equal(recommendation.routingStatus, "admin_queue");
  assert.equal(recommendation.recommendationStatus, "no_match");
  assert.equal(recommendation.commerceChannel, "amazon_affiliate");
  assert.equal(recommendation.channelDecisionReason, "no_matching_supplier");
  assert.equal(recommendation.affiliateProvider, "amazon");
  assert.deepEqual(recommendation.affiliateCandidate, {
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
  assert.equal(recommendation.amazonSearchQuery, "neem spray");
  assert.equal(recommendation.affiliateDisclosureRequired, true);
  assert.equal(recommendation.fallbackTriggered, true);
});