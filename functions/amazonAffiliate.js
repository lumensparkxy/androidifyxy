const crypto = require("crypto");
const fetch = require("node-fetch");

const {
  AFFILIATE_CANDIDATE_STATUS_STUB,
  AFFILIATE_PROVIDER_AMAZON,
  COMMERCE_CHANNEL_ADMIN_REVIEW,
  COMMERCE_CHANNEL_AMAZON_AFFILIATE,
  normalizeProductName,
} = require("./salesPipeline");

const AMAZON_PAAPI_HOST_DEFAULT = "webservices.amazon.in";
const AMAZON_PAAPI_REGION_DEFAULT = "eu-west-1";
const AMAZON_PAAPI_MARKETPLACE_DEFAULT = "www.amazon.in";
const AMAZON_PAAPI_LANGUAGE_DEFAULT = "en_IN";
const AMAZON_PAAPI_SEARCH_INDEX_DEFAULT = "All";
const AMAZON_PAAPI_ITEM_COUNT_DEFAULT = 1;
const AMAZON_PAAPI_ITEM_COUNT_MAX = 10;
const AMAZON_PAAPI_PATH_SEARCH_ITEMS = "/paapi5/searchitems";
const AMAZON_PAAPI_TARGET_SEARCH_ITEMS = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems";
const AMAZON_PAAPI_SERVICE = "ProductAdvertisingAPI";
const AMAZON_PARTNER_TYPE_ASSOCIATES = "Associates";
const AFFILIATE_CANDIDATE_STATUS_PROVIDER_READY = "provider_ready";

const AMAZON_DEFAULT_RESOURCES = Object.freeze([
  "Images.Primary.Medium",
  "ItemInfo.Title",
  "OffersV2.Listings.Price",
]);

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function toPositiveInt(value, fallbackValue) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallbackValue;
  }
  return Math.min(Math.floor(parsed), AMAZON_PAAPI_ITEM_COUNT_MAX);
}

function buildAmazonAffiliateConfig(env = process.env) {
  const partnerTag = trimString(env.AMAZON_ASSOCIATES_TAG);
  const accessKey = trimString(env.AMAZON_PAAPI_ACCESS_KEY);
  const secretKey = trimString(env.AMAZON_PAAPI_SECRET_KEY);

  return {
    partnerTag,
    accessKey,
    secretKey,
    host: trimString(env.AMAZON_PAAPI_HOST) || AMAZON_PAAPI_HOST_DEFAULT,
    region: trimString(env.AMAZON_PAAPI_REGION) || AMAZON_PAAPI_REGION_DEFAULT,
    marketplace: trimString(env.AMAZON_PAAPI_MARKETPLACE) || AMAZON_PAAPI_MARKETPLACE_DEFAULT,
    languageOfPreference: trimString(env.AMAZON_PAAPI_LANGUAGE) || AMAZON_PAAPI_LANGUAGE_DEFAULT,
    defaultSearchIndex: trimString(env.AMAZON_PAAPI_SEARCH_INDEX) || AMAZON_PAAPI_SEARCH_INDEX_DEFAULT,
    itemCount: toPositiveInt(env.AMAZON_PAAPI_ITEM_COUNT, AMAZON_PAAPI_ITEM_COUNT_DEFAULT),
    isConfigured: Boolean(partnerTag && accessKey && secretKey),
  };
}

function buildAmazonSearchItemsPayload({ lead = {}, affiliateCandidate = {}, config }) {
  const keywords = normalizeProductName(
    affiliateCandidate.searchQuery || lead.normalizedProductName || lead.productName,
  );
  if (!keywords) {
    return null;
  }

  return {
    Keywords: keywords,
    SearchIndex: config.defaultSearchIndex || AMAZON_PAAPI_SEARCH_INDEX_DEFAULT,
    PartnerTag: config.partnerTag,
    PartnerType: AMAZON_PARTNER_TYPE_ASSOCIATES,
    Marketplace: config.marketplace,
    LanguagesOfPreference: [config.languageOfPreference || AMAZON_PAAPI_LANGUAGE_DEFAULT],
    ItemCount: config.itemCount || AMAZON_PAAPI_ITEM_COUNT_DEFAULT,
    Resources: [...AMAZON_DEFAULT_RESOURCES],
  };
}

function buildAmazonAffiliateDetailPageUrl({
  asin,
  partnerTag,
  marketplace = AMAZON_PAAPI_MARKETPLACE_DEFAULT,
} = {}) {
  const normalizedAsin = trimString(asin).toUpperCase();
  const normalizedPartnerTag = trimString(partnerTag);
  const normalizedMarketplace = trimString(marketplace).toLowerCase() || AMAZON_PAAPI_MARKETPLACE_DEFAULT;
  if (!normalizedAsin || !normalizedPartnerTag) {
    return null;
  }

  return `https://${normalizedMarketplace}/dp/${encodeURIComponent(normalizedAsin)}?tag=${encodeURIComponent(normalizedPartnerTag)}`;
}

function formatAmzDate(now = new Date()) {
  const date = now instanceof Date ? now : new Date(now);
  const yyyy = date.getUTCFullYear();
  const mm = String(date.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(date.getUTCDate()).padStart(2, "0");
  const hh = String(date.getUTCHours()).padStart(2, "0");
  const min = String(date.getUTCMinutes()).padStart(2, "0");
  const ss = String(date.getUTCSeconds()).padStart(2, "0");
  return `${yyyy}${mm}${dd}T${hh}${min}${ss}Z`;
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

function hmacSha256(key, value, encoding) {
  return crypto.createHmac("sha256", key).update(value, "utf8").digest(encoding);
}

function buildSignatureKey(secretKey, dateStamp, region, service) {
  const kDate = hmacSha256(`AWS4${secretKey}`, dateStamp);
  const kRegion = hmacSha256(kDate, region);
  const kService = hmacSha256(kRegion, service);
  return hmacSha256(kService, "aws4_request");
}

function buildSignedAmazonSearchRequest({ payload, config, now = new Date() }) {
  const requestBody = JSON.stringify(payload);
  const amzDate = formatAmzDate(now);
  const dateStamp = amzDate.slice(0, 8);
  const headers = {
    "content-encoding": "amz-1.0",
    "content-type": "application/json; charset=utf-8",
    host: config.host,
    "x-amz-date": amzDate,
    "x-amz-target": AMAZON_PAAPI_TARGET_SEARCH_ITEMS,
  };

  const sortedHeaderKeys = Object.keys(headers).sort();
  const canonicalHeaders = sortedHeaderKeys
    .map((key) => `${key}:${headers[key]}\n`)
    .join("");
  const signedHeaders = sortedHeaderKeys.join(";");
  const canonicalRequest = [
    "POST",
    AMAZON_PAAPI_PATH_SEARCH_ITEMS,
    "",
    canonicalHeaders,
    signedHeaders,
    sha256Hex(requestBody),
  ].join("\n");

  const credentialScope = `${dateStamp}/${config.region}/${AMAZON_PAAPI_SERVICE}/aws4_request`;
  const stringToSign = [
    "AWS4-HMAC-SHA256",
    amzDate,
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join("\n");
  const signingKey = buildSignatureKey(config.secretKey, dateStamp, config.region, AMAZON_PAAPI_SERVICE);
  const signature = hmacSha256(signingKey, stringToSign, "hex");

  return {
    url: `https://${config.host}${AMAZON_PAAPI_PATH_SEARCH_ITEMS}`,
    body: requestBody,
    headers: {
      ...headers,
      Authorization: [
        "AWS4-HMAC-SHA256",
        `Credential=${config.accessKey}/${credentialScope},`,
        `SignedHeaders=${signedHeaders},`,
        `Signature=${signature}`,
      ].join(" "),
    },
  };
}

function extractAmazonErrorMessage(payload = {}) {
  const errors = Array.isArray(payload?.Errors) ? payload.Errors : [];
  const firstError = errors[0] || null;
  return trimString(firstError?.Message) || trimString(firstError?.Code) || "Amazon affiliate search failed";
}

function parseAmazonSearchItem(payload = {}) {
  const items = Array.isArray(payload?.SearchResult?.Items)
    ? payload.SearchResult.Items
    : [];
  const item = items[0];
  if (!item) {
    return null;
  }

  const listing = item?.OffersV2?.Listings?.[0] || item?.Offers?.Listings?.[0] || null;
  const price = listing?.Price || null;

  return {
    asin: trimString(item?.ASIN) || null,
    matchedTitle: trimString(item?.ItemInfo?.Title?.DisplayValue) || null,
    specialLink: trimString(item?.DetailPageURL)
      || trimString(payload?.SearchResult?.SearchURL)
      || null,
    imageUrl: trimString(item?.Images?.Primary?.Medium?.URL)
      || trimString(item?.Images?.Primary?.Large?.URL)
      || null,
    priceAmount: Number.isFinite(Number(price?.Amount)) ? Number(price.Amount) : null,
    priceCurrency: trimString(price?.Currency) || null,
    priceDisplay: trimString(price?.DisplayAmount) || null,
  };
}

async function readAmazonPayload(response) {
  const bodyText = await response.text().catch(() => "");
  if (!bodyText) {
    return {};
  }
  try {
    return JSON.parse(bodyText);
  } catch (error) {
    return {
      rawBody: bodyText,
    };
  }
}

async function resolveAmazonAffiliateCandidate({
  lead = {},
  affiliateCandidate = null,
  config = buildAmazonAffiliateConfig(),
  fetchImpl = fetch,
  now = new Date(),
} = {}) {
  if (!affiliateCandidate || affiliateCandidate.provider !== AFFILIATE_PROVIDER_AMAZON) {
    return {
      outcome: "skipped",
      candidate: affiliateCandidate,
    };
  }

  if (!config.isConfigured) {
    return {
      outcome: "missing_configuration",
      candidate: affiliateCandidate,
    };
  }

  const payload = buildAmazonSearchItemsPayload({ lead, affiliateCandidate, config });
  if (!payload) {
    return {
      outcome: "no_result",
      searchQuery: null,
    };
  }

  const signedRequest = buildSignedAmazonSearchRequest({ payload, config, now });

  try {
    const response = await fetchImpl(signedRequest.url, {
      method: "POST",
      headers: signedRequest.headers,
      body: signedRequest.body,
    });
    const responsePayload = await readAmazonPayload(response);

    if (!response.ok) {
      return {
        outcome: "provider_error",
        searchQuery: payload.Keywords,
        errorMessage: extractAmazonErrorMessage(responsePayload),
      };
    }

    const matchedItem = parseAmazonSearchItem(responsePayload);
    if (!matchedItem || !matchedItem.specialLink) {
      return {
        outcome: "no_result",
        searchQuery: payload.Keywords,
      };
    }

    return {
      outcome: "resolved",
      candidate: {
        ...affiliateCandidate,
        providerStatus: AFFILIATE_CANDIDATE_STATUS_PROVIDER_READY,
        reason: "amazon_search_match",
        stubbed: false,
        searchQuery: payload.Keywords,
        asin: matchedItem.asin,
        specialLink: matchedItem.specialLink,
        matchedTitle: matchedItem.matchedTitle,
        imageUrl: matchedItem.imageUrl,
        priceAmount: matchedItem.priceAmount,
        priceCurrency: matchedItem.priceCurrency,
        priceDisplay: matchedItem.priceDisplay,
        marketplace: config.marketplace,
        searchIndex: payload.SearchIndex,
        languageOfPreference: config.languageOfPreference,
      },
    };
  } catch (error) {
    return {
      outcome: "provider_error",
      searchQuery: payload.Keywords,
      errorMessage: trimString(error?.message) || "Amazon affiliate search failed",
    };
  }
}

function finalizeAmazonAffiliateRecommendation({ recommendation = {}, resolution = null } = {}) {
  if (!resolution || !recommendation?.affiliateCandidate || recommendation.affiliateCandidate.provider !== AFFILIATE_PROVIDER_AMAZON) {
    return recommendation;
  }

  if (resolution.outcome === "resolved" && resolution.candidate) {
    const resolvedReason = trimString(resolution.reason || resolution.candidate.reason) || "amazon_search_match";
    return {
      ...recommendation,
      recommendationStatus: "ready",
      commerceChannel: COMMERCE_CHANNEL_AMAZON_AFFILIATE,
      channelDecisionReason: resolvedReason,
      affiliateProvider: AFFILIATE_PROVIDER_AMAZON,
      affiliateCandidate: resolution.candidate,
      amazonAsin: resolution.candidate.asin || null,
      amazonSearchQuery: resolution.candidate.searchQuery || recommendation.amazonSearchQuery || null,
      amazonSpecialLink: resolution.candidate.specialLink || null,
      affiliateDisclosureRequired: true,
      conversionStatus: "handoff_ready",
      whatsappState: "ready",
      fallbackTriggered: true,
      adminFallbackReason: recommendation.adminFallbackReason || "no_matching_supplier",
    };
  }

  if (resolution.outcome === "no_result") {
    return {
      ...recommendation,
      recommendationStatus: "no_match",
      commerceChannel: COMMERCE_CHANNEL_ADMIN_REVIEW,
      channelDecisionReason: "amazon_search_no_match",
      affiliateProvider: null,
      affiliateCandidate: null,
      amazonAsin: null,
      amazonSearchQuery: resolution.searchQuery || recommendation.amazonSearchQuery || null,
      amazonSpecialLink: null,
      affiliateDisclosureRequired: false,
      conversionStatus: "intent_captured",
      whatsappState: "not_ready",
      fallbackTriggered: true,
      adminFallbackReason: "amazon_search_no_match",
    };
  }

  if (resolution.outcome === "provider_error") {
    return {
      ...recommendation,
      recommendationStatus: "needs_admin_review",
      commerceChannel: COMMERCE_CHANNEL_ADMIN_REVIEW,
      channelDecisionReason: "amazon_search_error",
      affiliateProvider: AFFILIATE_PROVIDER_AMAZON,
      affiliateCandidate: recommendation.affiliateCandidate
        ? {
          ...recommendation.affiliateCandidate,
          providerStatus: AFFILIATE_CANDIDATE_STATUS_STUB,
          reason: "amazon_search_error",
          stubbed: true,
        }
        : null,
      amazonAsin: null,
      amazonSearchQuery: resolution.searchQuery || recommendation.amazonSearchQuery || null,
      amazonSpecialLink: null,
      affiliateDisclosureRequired: false,
      conversionStatus: "intent_captured",
      whatsappState: "not_ready",
      fallbackTriggered: true,
      adminFallbackReason: "amazon_search_error",
    };
  }

  return recommendation;
}

module.exports = {
  AFFILIATE_CANDIDATE_STATUS_PROVIDER_READY,
  AMAZON_PAAPI_HOST_DEFAULT,
  AMAZON_PAAPI_LANGUAGE_DEFAULT,
  AMAZON_PAAPI_MARKETPLACE_DEFAULT,
  AMAZON_PAAPI_REGION_DEFAULT,
  AMAZON_PAAPI_TARGET_SEARCH_ITEMS,
  buildAmazonAffiliateConfig,
  buildAmazonAffiliateDetailPageUrl,
  buildAmazonSearchItemsPayload,
  buildSignedAmazonSearchRequest,
  finalizeAmazonAffiliateRecommendation,
  resolveAmazonAffiliateCandidate,
};