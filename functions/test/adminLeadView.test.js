const test = require("node:test");
const assert = require("node:assert/strict");

const { buildAdminLeadView } = require("../adminLeadView");

test("buildAdminLeadView merges live farmer profile over stored snapshot", () => {
  const lead = buildAdminLeadView(
    "lead-1",
    {
      userId: "user-1",
      routingStatus: "supplier_pending",
      recommendationStatus: "ready",
      farmerProfileSnapshot: {
        name: "Ramesh Patil",
        district: "Pune",
        village: "Baramati",
        mobileNumber: "9876543210",
      },
    },
    {
      name: "Ramesh Jadhav",
      district: "Nashik",
      tehsil: "Daund",
      emailId: "ramesh@example.com",
    },
  );

  assert.equal(lead.id, "lead-1");
  assert.equal(lead.farmerProfileSnapshot.name, "Ramesh Jadhav");
  assert.equal(lead.farmerProfileSnapshot.district, "Nashik");
  assert.equal(lead.farmerProfileSnapshot.village, "Baramati");
  assert.equal(lead.farmerProfileSnapshot.tehsil, "Daund");
  assert.equal(lead.farmerProfileSnapshot.mobileNumber, "9876543210");
  assert.equal(lead.farmerProfileSnapshot.emailId, "ramesh@example.com");
});

test("buildAdminLeadView derives compatibility workflow fields and supplier fallbacks", () => {
  const createdAt = new Date("2026-04-03T12:34:56.000Z");
  const lead = buildAdminLeadView("lead-2", {
    status: "",
    routingStatus: "supplier_accepted",
    recommendationStatus: "ready",
    assignedSupplier: { supplierId: "supplier-1", businessName: "Best Inputs" },
    createdAt,
  });

  assert.equal(lead.status, "initiated");
  assert.equal(lead.reviewStatus, "assigned_to_supplier");
  assert.equal(lead.supplierVisibility, "unlocked");
  assert.deepEqual(lead.selectedSupplier, { supplierId: "supplier-1", businessName: "Best Inputs" });
  assert.deepEqual(lead.assignedSupplier, { supplierId: "supplier-1", businessName: "Best Inputs" });
  assert.equal(lead.commerceChannel, "supplier_local");
  assert.equal(lead.channelDecisionReason, "default_supplier_first");
  assert.equal(lead.fallbackPolicy, "amazon_on_no_match_or_timeout");
  assert.equal(lead.conversionStatus, "intent_captured");
  assert.equal(lead.whatsappState, "not_ready");
  assert.equal(lead.createdAt, createdAt.toISOString());
  assert.equal(lead.backendProcessingStatus, "pending");
  assert.equal(lead.commissionStatus, "preview");
});

test("buildAdminLeadView carries affiliate fallback stub fields", () => {
  const fallbackTriggeredAt = new Date("2026-04-10T08:30:00.000Z");
  const lead = buildAdminLeadView("lead-3", {
    productName: "Neem Spray",
    normalizedProductName: "neem spray",
    commerceChannel: "amazon_affiliate",
    channelDecisionReason: "no_matching_supplier",
    fallbackPolicy: "amazon_on_no_match_or_timeout",
    affiliateProvider: "amazon",
    affiliateCandidate: {
      provider: "amazon",
      providerStatus: "stub_pending_provider",
      productName: "Neem Spray",
      searchQuery: "neem spray",
    },
    affiliateMatchSource: "registry_exact",
    affiliateRegistryEntryId: "registry-1",
    affiliateRegistryProductName: "Neem Spray",
    affiliateRegistryMatchedAt: fallbackTriggeredAt,
    affiliateAutoAppMessageAt: fallbackTriggeredAt,
    affiliateAutoAppMessageSource: "registry_exact",
    amazonSearchQuery: "neem spray",
    affiliateDisclosureRequired: true,
    fallbackTriggeredAt,
  });

  assert.equal(lead.commerceChannel, "amazon_affiliate");
  assert.equal(lead.channelDecisionReason, "no_matching_supplier");
  assert.equal(lead.affiliateProvider, "amazon");
  assert.equal(lead.affiliateCandidate?.providerStatus, "stub_pending_provider");
  assert.equal(lead.affiliateMatchSource, "registry_exact");
  assert.equal(lead.affiliateRegistryEntryId, "registry-1");
  assert.equal(lead.affiliateRegistryProductName, "Neem Spray");
  assert.equal(lead.affiliateRegistryMatchedAt, fallbackTriggeredAt.toISOString());
  assert.equal(lead.affiliateAutoAppMessageAt, fallbackTriggeredAt.toISOString());
  assert.equal(lead.affiliateAutoAppMessageSource, "registry_exact");
  assert.equal(lead.amazonSearchQuery, "neem spray");
  assert.equal(lead.affiliateDisclosureRequired, true);
  assert.equal(lead.fallbackTriggeredAt, fallbackTriggeredAt.toISOString());
});