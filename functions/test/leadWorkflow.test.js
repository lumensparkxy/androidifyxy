const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildCommissionLifecycleDefaults,
  getCommissionMonthKey,
  summarizeCommissionMonthEntries,
} = require("../leadWorkflow");

test("buildCommissionLifecycleDefaults returns preview and pending statuses", () => {
  assert.deepEqual(buildCommissionLifecycleDefaults(), {
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
  });
});

test("getCommissionMonthKey uses Asia/Kolkata for month boundaries", () => {
  assert.equal(getCommissionMonthKey("2026-03-31T20:00:00Z"), "2026-04");
  assert.equal(getCommissionMonthKey("2026-03-31T17:00:00Z"), "2026-03");
});

test("summarizeCommissionMonthEntries rolls up approved, paid, and outstanding totals", () => {
  assert.deepEqual(
    summarizeCommissionMonthEntries([
      { amount: 150, status: "approved" },
      { amount: 200, status: "paid" },
      { amount: 0, status: "paid" },
      { amount: 75, status: "ignored" },
    ]),
    {
      approvedCount: 2,
      approvedTotal: 350,
      paidCount: 1,
      paidTotal: 200,
      outstandingTotal: 150,
      currency: "INR",
    },
  );
});
