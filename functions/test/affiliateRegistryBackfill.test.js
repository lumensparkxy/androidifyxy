const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildAffiliateRegistryBackfillPlan,
  buildEligibleAffiliateRegistryBackfillItem,
  parseAffiliateRegistryBackfillLimit,
  selectAffiliateRegistryBackfillCreateRows,
} = require("../affiliateRegistryBackfill");
const {
  buildAffiliateProductRegistryDocId,
} = require("../affiliateRegistry");

function buildLead(overrides = {}) {
  return {
    commerceChannel: "amazon_affiliate",
    productName: "Neem Oil",
    normalizedProductName: " neem   oil ",
    amazonSpecialLink: "https://amzn.to/neem-oil",
    appMessageSentAt: "2026-04-23T07:59:27.556Z",
    updatedAt: "2026-04-23T08:10:00.000Z",
    ...overrides,
  };
}

test("buildEligibleAffiliateRegistryBackfillItem extracts only app-sent affiliate leads", () => {
  const item = buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-1",
    leadData: buildLead(),
  });

  assert.equal(item.leadId, "lead-1");
  assert.equal(item.productName, "Neem Oil");
  assert.equal(item.normalizedProductName, "neem oil");
  assert.equal(item.specialLink, "https://amzn.to/neem-oil");
  assert.equal(item.appMessageSentAt, "2026-04-23T07:59:27.556Z");

  assert.equal(buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-2",
    leadData: buildLead({ appMessageSentAt: null }),
  }), null);
  assert.equal(buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-3",
    leadData: buildLead({ commerceChannel: "supplier_local" }),
  }), null);
  assert.equal(buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-4",
    leadData: buildLead({ amazonSpecialLink: "", affiliateCandidate: { specialLink: "" } }),
  }), null);
});

test("buildAffiliateRegistryBackfillPlan creates one safe row for a single historical link", () => {
  const items = [
    buildEligibleAffiliateRegistryBackfillItem({
      leadId: "lead-1",
      leadData: buildLead({ appMessageSentAt: "2026-04-20T08:00:00.000Z" }),
    }),
    buildEligibleAffiliateRegistryBackfillItem({
      leadId: "lead-2",
      leadData: buildLead({ appMessageSentAt: "2026-04-21T08:00:00.000Z" }),
    }),
  ];

  const plan = buildAffiliateRegistryBackfillPlan({ items });

  assert.equal(plan.eligibleLeadCount, 2);
  assert.equal(plan.sourceProductCount, 1);
  assert.equal(plan.safeCreateCount, 1);
  assert.equal(plan.skippedExistingCount, 0);
  assert.equal(plan.conflictCount, 0);
  assert.equal(plan.safeCreateRows[0].action, "create");
  assert.equal(plan.safeCreateRows[0].reason, "single_historical_link");
  assert.equal(plan.safeCreateRows[0].specialLink, "https://amzn.to/neem-oil");
});

test("buildAffiliateRegistryBackfillPlan skips an existing active matching registry entry", () => {
  const item = buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-1",
    leadData: buildLead(),
  });
  const entryId = buildAffiliateProductRegistryDocId({
    provider: "amazon",
    normalizedProductName: "neem oil",
  });
  const existingEntriesById = new Map([
    [entryId, {
      isActive: true,
      specialLink: "https://amzn.to/neem-oil",
    }],
  ]);

  const plan = buildAffiliateRegistryBackfillPlan({
    items: [item],
    existingEntriesById,
  });

  assert.equal(plan.safeCreateCount, 0);
  assert.equal(plan.skippedExistingCount, 1);
  assert.equal(plan.conflictCount, 0);
  assert.equal(plan.skippedExistingRows[0].reason, "existing_registry_match");
});

test("buildAffiliateRegistryBackfillPlan reports multiple historical links as a conflict", () => {
  const items = [
    buildEligibleAffiliateRegistryBackfillItem({
      leadId: "lead-1",
      leadData: buildLead({ amazonSpecialLink: "https://amzn.to/neem-oil-a" }),
    }),
    buildEligibleAffiliateRegistryBackfillItem({
      leadId: "lead-2",
      leadData: buildLead({ amazonSpecialLink: "https://amzn.to/neem-oil-b" }),
    }),
  ];

  const plan = buildAffiliateRegistryBackfillPlan({ items });

  assert.equal(plan.safeCreateCount, 0);
  assert.equal(plan.conflictCount, 1);
  assert.equal(plan.conflictRows[0].reason, "multiple_historical_links");
  assert.equal(plan.conflictRows[0].distinctLinkCount, 2);
});

test("buildAffiliateRegistryBackfillPlan does not overwrite an existing differing entry", () => {
  const item = buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-1",
    leadData: buildLead(),
  });
  const entryId = buildAffiliateProductRegistryDocId({
    provider: "amazon",
    normalizedProductName: "neem oil",
  });
  const existingEntriesById = new Map([
    [entryId, {
      isActive: true,
      specialLink: "https://amzn.to/other-neem-oil",
    }],
  ]);

  const plan = buildAffiliateRegistryBackfillPlan({
    items: [item],
    existingEntriesById,
  });

  assert.equal(plan.safeCreateCount, 0);
  assert.equal(plan.conflictCount, 1);
  assert.equal(plan.conflictRows[0].reason, "existing_registry_differs");
});

test("selectAffiliateRegistryBackfillCreateRows returns no writes for dry-run", () => {
  const item = buildEligibleAffiliateRegistryBackfillItem({
    leadId: "lead-1",
    leadData: buildLead(),
  });
  const plan = buildAffiliateRegistryBackfillPlan({ items: [item] });

  assert.deepEqual(selectAffiliateRegistryBackfillCreateRows(plan, { dryRun: true }), []);
  assert.equal(selectAffiliateRegistryBackfillCreateRows(plan, { dryRun: false }).length, 1);
});

test("parseAffiliateRegistryBackfillLimit clamps unsafe limits", () => {
  assert.equal(parseAffiliateRegistryBackfillLimit(undefined), 200);
  assert.equal(parseAffiliateRegistryBackfillLimit(0), 1);
  assert.equal(parseAffiliateRegistryBackfillLimit(9999), 500);
});
