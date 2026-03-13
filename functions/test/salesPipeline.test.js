const test = require("node:test");
const assert = require("node:assert/strict");
const {
  buildSalesPipelineDocId,
  generateRequestNumber,
  normalizeProductName,
} = require("../salesPipeline");

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

