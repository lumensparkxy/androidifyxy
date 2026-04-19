const crypto = require("crypto");

const SALES_PIPELINE_COLLECTION = "sales_pipeline";
const SALES_PIPELINE_STATUS_INITIATED = "initiated";
const COMMERCE_CHANNEL_SUPPLIER_LOCAL = "supplier_local";
const COMMERCE_CHANNEL_AMAZON_AFFILIATE = "amazon_affiliate";
const COMMERCE_CHANNEL_ADMIN_REVIEW = "admin_review";
const AFFILIATE_PROVIDER_AMAZON = "amazon";
const AFFILIATE_CANDIDATE_STATUS_STUB = "stub_pending_provider";
const DEFAULT_CHANNEL_DECISION_REASON = "default_supplier_first";
const DEFAULT_FALLBACK_POLICY = "amazon_on_no_match_or_timeout";
const DEFAULT_CONVERSION_STATUS = "intent_captured";
const DEFAULT_WHATSAPP_STATE = "not_ready";

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

function buildInitialCommerceFields({ productName } = {}) {
  const amazonSearchQuery = normalizeProductName(productName);
  return {
    commerceChannel: COMMERCE_CHANNEL_SUPPLIER_LOCAL,
    channelDecisionReason: DEFAULT_CHANNEL_DECISION_REASON,
    fallbackPolicy: DEFAULT_FALLBACK_POLICY,
    affiliateProvider: null,
    affiliateCandidate: null,
    amazonAsin: null,
    amazonSearchQuery: amazonSearchQuery || null,
    amazonSpecialLink: null,
    amazonContentRefreshedAt: null,
    affiliateDisclosureRequired: false,
    conversionStatus: DEFAULT_CONVERSION_STATUS,
    whatsappState: DEFAULT_WHATSAPP_STATE,
    fallbackTriggeredAt: null,
  };
}

function buildAmazonAffiliateCandidate({ lead = {}, reason = "no_matching_supplier" } = {}) {
  const normalizedProductName = normalizeProductName(
    lead.normalizedProductName || lead.productName,
  );
  if (!normalizedProductName) {
    return null;
  }

  const productName = String(lead.productName || "").trim() || null;
  const leadCategory = normalizeProductName(lead.leadCategory) || "other";

  return {
    provider: AFFILIATE_PROVIDER_AMAZON,
    providerStatus: AFFILIATE_CANDIDATE_STATUS_STUB,
    reason,
    stubbed: true,
    productName,
    normalizedProductName,
    leadCategory,
    searchQuery: normalizedProductName,
    asin: null,
    specialLink: null,
  };
}

module.exports = {
  AFFILIATE_CANDIDATE_STATUS_STUB,
  AFFILIATE_PROVIDER_AMAZON,
  COMMERCE_CHANNEL_ADMIN_REVIEW,
  COMMERCE_CHANNEL_AMAZON_AFFILIATE,
  COMMERCE_CHANNEL_SUPPLIER_LOCAL,
  DEFAULT_CHANNEL_DECISION_REASON,
  DEFAULT_CONVERSION_STATUS,
  DEFAULT_FALLBACK_POLICY,
  DEFAULT_WHATSAPP_STATE,
  SALES_PIPELINE_COLLECTION,
  SALES_PIPELINE_STATUS_INITIATED,
  buildAmazonAffiliateCandidate,
  buildInitialCommerceFields,
  normalizeProductName,
  buildSalesPipelineDocId,
  generateRequestNumber,
};

