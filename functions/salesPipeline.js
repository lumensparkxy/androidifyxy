const crypto = require("crypto");

const SALES_PIPELINE_COLLECTION = "sales_pipeline";
const SALES_PIPELINE_STATUS_INITIATED = "initiated";

function normalizeProductName(productName) {
  return String(productName || "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, " ");
}

function buildSalesPipelineDocId({ userId, conversationId, productName }) {
  const normalizedProduct = normalizeProductName(productName);
  return crypto
    .createHash("sha256")
    .update(`${userId}|${conversationId}|${normalizedProduct}`)
    .digest("hex")
    .slice(0, 32);
}

function generateRequestNumber(now = new Date()) {
  const yyyy = now.getUTCFullYear();
  const mm = String(now.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(now.getUTCDate()).padStart(2, "0");
  const suffix = crypto.randomBytes(3).toString("hex").toUpperCase();
  return `KR-${yyyy}${mm}${dd}-${suffix}`;
}

module.exports = {
  SALES_PIPELINE_COLLECTION,
  SALES_PIPELINE_STATUS_INITIATED,
  normalizeProductName,
  buildSalesPipelineDocId,
  generateRequestNumber,
};

