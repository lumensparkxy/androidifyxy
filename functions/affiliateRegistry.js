const crypto = require("crypto");

const { extractAmazonAsin } = require("./adminAffiliateMessaging");
const {
  AFFILIATE_PROVIDER_AMAZON,
  normalizeProductName,
} = require("./salesPipeline");

const AFFILIATE_PRODUCT_REGISTRY_COLLECTION = "affiliate_product_registry";
const AFFILIATE_MATCH_SOURCE_REGISTRY_EXACT = "registry_exact";

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeAffiliateRegistryProductName(value) {
  return normalizeProductName(value) || "";
}

function buildAffiliateProductRegistryDocId({ provider, normalizedProductName } = {}) {
  const normalizedProvider = trimString(provider).toLowerCase();
  const normalizedProduct = normalizeAffiliateRegistryProductName(normalizedProductName);
  if (!normalizedProvider || !normalizedProduct) {
    return "";
  }

  return crypto
    .createHash("sha256")
    .update(`${normalizedProvider}|${normalizedProduct}`)
    .digest("hex")
    .slice(0, 40);
}

function buildAffiliateRegistryEntryPayload({
  provider,
  productName,
  normalizedProductName,
  specialLink,
  isActive = true,
  actor = {},
  existingEntry = {},
} = {}) {
  const normalizedProvider = trimString(provider).toLowerCase();
  if (!normalizedProvider) {
    throw new Error("provider is required");
  }

  const normalizedProduct = normalizeAffiliateRegistryProductName(
    normalizedProductName || productName || existingEntry.normalizedProductName,
  );
  if (!normalizedProduct) {
    throw new Error("productName is required");
  }

  const normalizedSpecialLink = trimString(specialLink || existingEntry.specialLink);
  if (!normalizedSpecialLink) {
    throw new Error("specialLink is required");
  }

  const displayProductName = trimString(productName)
    || trimString(existingEntry.productName)
    || normalizedProduct;
  const asin = normalizedProvider === AFFILIATE_PROVIDER_AMAZON
    ? extractAmazonAsin(normalizedSpecialLink)
    : null;

  return {
    provider: normalizedProvider,
    productName: displayProductName,
    normalizedProductName: normalizedProduct,
    specialLink: normalizedSpecialLink,
    asin,
    isActive: isActive !== false,
    updatedByUid: trimString(actor.uid) || null,
    updatedByEmail: trimString(actor.email) || null,
  };
}

function buildAffiliateRegistryCandidate({ lead = {}, registryEntry = {} } = {}) {
  const provider = trimString(registryEntry.provider).toLowerCase();
  const specialLink = trimString(registryEntry.specialLink);
  if (provider !== AFFILIATE_PROVIDER_AMAZON || !specialLink) {
    return null;
  }

  const normalizedProductName = normalizeAffiliateRegistryProductName(
    lead.normalizedProductName
      || lead.productName
      || registryEntry.normalizedProductName
      || registryEntry.productName,
  );
  if (!normalizedProductName) {
    return null;
  }

  const productName = trimString(lead.productName)
    || trimString(registryEntry.productName)
    || null;

  return {
    provider,
    providerStatus: "provider_ready",
    reason: "registry_exact_match",
    matchSource: AFFILIATE_MATCH_SOURCE_REGISTRY_EXACT,
    registryEntryId: trimString(registryEntry.id) || null,
    stubbed: false,
    productName,
    normalizedProductName,
    leadCategory: trimString(lead.leadCategory) || null,
    searchQuery: trimString(registryEntry.normalizedProductName) || normalizedProductName,
    asin: trimString(registryEntry.asin) || null,
    specialLink,
    matchedTitle: trimString(registryEntry.productName) || productName,
  };
}

module.exports = {
  AFFILIATE_MATCH_SOURCE_REGISTRY_EXACT,
  AFFILIATE_PRODUCT_REGISTRY_COLLECTION,
  buildAffiliateProductRegistryDocId,
  buildAffiliateRegistryCandidate,
  buildAffiliateRegistryEntryPayload,
  normalizeAffiliateRegistryProductName,
};
