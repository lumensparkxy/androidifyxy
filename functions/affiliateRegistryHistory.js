const crypto = require("crypto");

const { normalizeAffiliateRegistryProductName } = require("./affiliateRegistry");
const { AFFILIATE_PROVIDER_AMAZON } = require("./salesPipeline");

const AMAZON_MARKETPLACE_DEFAULT = "www.amazon.in";

const AFFILIATE_PRODUCT_REGISTRY_CANDIDATES_COLLECTION = "affiliate_product_registry_candidates";
const AFFILIATE_HISTORY_SOURCE_AMAZON_REPORT = "amazon_report";
const AFFILIATE_CANDIDATE_REVIEW_STATUS_PENDING = "pending_review";
const AFFILIATE_CANDIDATE_REVIEW_STATUS_APPROVED = "approved";
const AFFILIATE_CANDIDATE_REVIEW_STATUS_REJECTED = "rejected";

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function toPositiveInt(value, fallbackValue = 1) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallbackValue;
  }
  return Math.floor(parsed);
}

function normalizeMarketplace(value) {
  return trimString(value).toLowerCase() || AMAZON_MARKETPLACE_DEFAULT;
}

function normalizeHistorySource(value) {
  return trimString(value).toLowerCase() || AFFILIATE_HISTORY_SOURCE_AMAZON_REPORT;
}

function parseIsoDate(value) {
  if (!value) return null;

  if (value instanceof Date) {
    return Number.isFinite(value.getTime()) ? value.toISOString() : null;
  }

  const parsed = typeof value === "number"
    ? new Date(value)
    : new Date(trimString(value));
  return Number.isFinite(parsed.getTime()) ? parsed.toISOString() : null;
}

function sanitizeStringArray(values) {
  if (!Array.isArray(values)) return [];
  return [...new Set(values.map((value) => trimString(value)).filter(Boolean))].sort();
}

function buildAffiliateProductRegistryCandidateDocId({
  provider,
  asin,
  marketplace,
  sourceProductName,
} = {}) {
  const normalizedProvider = trimString(provider).toLowerCase();
  const normalizedAsin = trimString(asin).toUpperCase();
  const normalizedMarketplace = normalizeMarketplace(marketplace);
  const normalizedSourceProductName = normalizeAffiliateRegistryProductName(sourceProductName);
  if (!normalizedProvider || (!normalizedAsin && !normalizedSourceProductName)) {
    return "";
  }

  const candidateKey = normalizedAsin
    ? `${normalizedProvider}|asin|${normalizedMarketplace}|${normalizedAsin}`
    : `${normalizedProvider}|title|${normalizedSourceProductName}`;

  return crypto
    .createHash("sha256")
    .update(candidateKey)
    .digest("hex")
    .slice(0, 40);
}

function buildAffiliateRegistryHistoryRowKey({
  provider,
  source,
  sourceFile,
  asin,
  marketplace,
  sourceProductName,
  lastSeenAt,
  orderCount,
} = {}) {
  const normalizedProvider = trimString(provider).toLowerCase();
  const normalizedSource = normalizeHistorySource(source);
  const normalizedSourceFile = trimString(sourceFile);
  const normalizedAsin = trimString(asin).toUpperCase();
  const normalizedMarketplace = normalizeMarketplace(marketplace);
  const normalizedSourceProductName = normalizeAffiliateRegistryProductName(sourceProductName);
  const normalizedLastSeenAt = parseIsoDate(lastSeenAt) || "";
  const normalizedOrderCount = String(toPositiveInt(orderCount, 1));

  return crypto
    .createHash("sha256")
    .update([
      normalizedProvider,
      normalizedSource,
      normalizedSourceFile,
      normalizedAsin,
      normalizedMarketplace,
      normalizedSourceProductName,
      normalizedLastSeenAt,
      normalizedOrderCount,
    ].join("|"))
    .digest("hex")
    .slice(0, 40);
}

function buildAffiliateRegistryHistoryRow(row = {}, defaults = {}) {
  const provider = trimString(row.provider || defaults.provider).toLowerCase() || AFFILIATE_PROVIDER_AMAZON;
  if (!provider) {
    throw new Error("provider is required");
  }

  const asin = trimString(row.asin || row.ASIN).toUpperCase() || null;
  const sourceProductName = trimString(
    row.sourceProductName
      || row.productName
      || row.title
      || row.itemTitle
      || row.orderedItemTitle
      || row.orderedItem,
  );
  const normalizedSourceProductName = normalizeAffiliateRegistryProductName(sourceProductName);
  if (!asin && !normalizedSourceProductName) {
    throw new Error("asin or sourceProductName is required");
  }

  const source = normalizeHistorySource(row.source || defaults.source);
  const sourceFile = trimString(row.sourceFile || row.reportName || defaults.sourceFile) || null;
  const marketplace = normalizeMarketplace(row.marketplace || row.marketPlace || row.domain || defaults.marketplace);
  const orderCount = toPositiveInt(row.orderCount || row.orders || row.quantity || 1, 1);
  const lastSeenAt = parseIsoDate(row.lastSeenAt || row.lastOrderedAt || row.orderedAt || row.eventAt);
  const specialLink = trimString(row.specialLink || row.affiliateLink || row.detailPageUrl) || null;

  return {
    provider,
    source,
    sourceFile,
    asin,
    sourceProductName: sourceProductName || null,
    normalizedSourceProductName,
    marketplace,
    orderCount,
    lastSeenAt,
    specialLink,
    rowKey: buildAffiliateRegistryHistoryRowKey({
      provider,
      source,
      sourceFile,
      asin,
      marketplace,
      sourceProductName: normalizedSourceProductName,
      lastSeenAt,
      orderCount,
    }),
  };
}

function buildAffiliateRegistryImportBatches({
  provider = AFFILIATE_PROVIDER_AMAZON,
  source = AFFILIATE_HISTORY_SOURCE_AMAZON_REPORT,
  sourceFile,
  rows = [],
} = {}) {
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error("rows are required");
  }

  const batches = new Map();
  rows.forEach((row) => {
    const normalizedRow = buildAffiliateRegistryHistoryRow(row, {
      provider,
      source,
      sourceFile,
    });
    const candidateId = buildAffiliateProductRegistryCandidateDocId({
      provider: normalizedRow.provider,
      asin: normalizedRow.asin,
      marketplace: normalizedRow.marketplace,
      sourceProductName: normalizedRow.normalizedSourceProductName,
    });
    if (!candidateId) {
      throw new Error("Unable to build candidate id for historical affiliate row");
    }

    const current = batches.get(candidateId) || [];
    current.push(normalizedRow);
    batches.set(candidateId, current);
  });

  return [...batches.entries()].map(([candidateId, candidateRows]) => ({
    candidateId,
    rows: candidateRows,
  }));
}

function maxIsoDate(left, right) {
  const leftIso = parseIsoDate(left);
  const rightIso = parseIsoDate(right);
  if (!leftIso) return rightIso;
  if (!rightIso) return leftIso;
  return Date.parse(leftIso) >= Date.parse(rightIso) ? leftIso : rightIso;
}

function buildAffiliateRegistryCandidateImportPayload({
  candidateId,
  rows = [],
  actor = {},
  existingCandidate = {},
  buildSpecialLink,
} = {}) {
  if (!trimString(candidateId)) {
    throw new Error("candidateId is required");
  }
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error("rows are required");
  }

  const existingRowKeys = sanitizeStringArray(existingCandidate.sourceRowKeys);
  const nextRowKeys = new Set(existingRowKeys);
  const rawTitles = new Set(sanitizeStringArray(existingCandidate.rawTitles));
  const sourceFiles = new Set(sanitizeStringArray(existingCandidate.sourceFiles));
  const marketplaces = new Set(sanitizeStringArray(existingCandidate.marketplaces));
  let provider = trimString(existingCandidate.provider).toLowerCase();
  let source = normalizeHistorySource(existingCandidate.source);
  let asin = trimString(existingCandidate.asin).toUpperCase() || null;
  let sourceProductName = trimString(existingCandidate.sourceProductName) || null;
  let normalizedSourceProductName = normalizeAffiliateRegistryProductName(
    existingCandidate.normalizedSourceProductName || existingCandidate.sourceProductName,
  );
  let generatedSpecialLink = trimString(existingCandidate.generatedSpecialLink) || null;
  let lastSeenAt = parseIsoDate(existingCandidate.lastSeenAt);
  let addedOrderCount = 0;
  let addedRowCount = 0;

  rows.forEach((row) => {
    const expectedCandidateId = buildAffiliateProductRegistryCandidateDocId({
      provider: row.provider,
      asin: row.asin,
      marketplace: row.marketplace,
      sourceProductName: row.normalizedSourceProductName,
    });
    if (expectedCandidateId !== candidateId) {
      throw new Error("rows must all target the same affiliate registry candidate");
    }

    provider = provider || trimString(row.provider).toLowerCase();
    source = normalizeHistorySource(source || row.source);
    asin = asin || trimString(row.asin).toUpperCase() || null;
    sourceProductName = sourceProductName || trimString(row.sourceProductName) || null;
    normalizedSourceProductName = normalizedSourceProductName
      || normalizeAffiliateRegistryProductName(row.normalizedSourceProductName || row.sourceProductName);
    if (row.sourceProductName) rawTitles.add(trimString(row.sourceProductName));
    if (row.sourceFile) sourceFiles.add(trimString(row.sourceFile));
    if (row.marketplace) marketplaces.add(normalizeMarketplace(row.marketplace));
    lastSeenAt = maxIsoDate(lastSeenAt, row.lastSeenAt);

    if (!generatedSpecialLink && row.specialLink) {
      generatedSpecialLink = trimString(row.specialLink) || null;
    }
    if (!generatedSpecialLink && row.asin && typeof buildSpecialLink === "function") {
      generatedSpecialLink = trimString(buildSpecialLink({
        asin: row.asin,
        marketplace: row.marketplace,
      })) || null;
    }

    if (!nextRowKeys.has(row.rowKey)) {
      nextRowKeys.add(row.rowKey);
      addedOrderCount += row.orderCount;
      addedRowCount += 1;
    }
  });

  const currentOrderCount = Number(existingCandidate.orderCount);
  const baseOrderCount = Number.isFinite(currentOrderCount) && currentOrderCount > 0
    ? Math.floor(currentOrderCount)
    : 0;
  const reviewStatus = trimString(existingCandidate.reviewStatus).toLowerCase()
    || AFFILIATE_CANDIDATE_REVIEW_STATUS_PENDING;
  const sortedRowKeys = [...nextRowKeys].sort();
  const sortedRawTitles = [...rawTitles].sort();
  const sortedSourceFiles = [...sourceFiles].sort();
  const sortedMarketplaces = [...marketplaces].sort();

  return {
    candidateId,
    addedOrderCount,
    addedRowCount,
    skippedRowCount: rows.length - addedRowCount,
    data: {
      provider,
      source,
      sourceFile: sortedSourceFiles[0] || null,
      sourceFiles: sortedSourceFiles,
      reviewStatus,
      sourceProductName: sourceProductName || null,
      normalizedSourceProductName: normalizedSourceProductName || "",
      rawTitles: sortedRawTitles,
      asin,
      marketplace: sortedMarketplaces[0] || null,
      marketplaces: sortedMarketplaces,
      generatedSpecialLink,
      orderCount: baseOrderCount + addedOrderCount,
      sourceRowCount: sortedRowKeys.length,
      sourceRowKeys: sortedRowKeys,
      lastSeenAt,
      importedByUid: trimString(existingCandidate.importedByUid) || trimString(actor.uid) || null,
      importedByEmail: trimString(existingCandidate.importedByEmail) || trimString(actor.email) || null,
      updatedByUid: trimString(actor.uid) || null,
      updatedByEmail: trimString(actor.email) || null,
      promotedEntryId: trimString(existingCandidate.promotedEntryId) || null,
      approvedProductName: trimString(existingCandidate.approvedProductName) || null,
      approvedNormalizedProductName: trimString(existingCandidate.approvedNormalizedProductName) || null,
    },
  };
}

module.exports = {
  AFFILIATE_CANDIDATE_REVIEW_STATUS_APPROVED,
  AFFILIATE_CANDIDATE_REVIEW_STATUS_PENDING,
  AFFILIATE_CANDIDATE_REVIEW_STATUS_REJECTED,
  AFFILIATE_HISTORY_SOURCE_AMAZON_REPORT,
  AFFILIATE_PRODUCT_REGISTRY_CANDIDATES_COLLECTION,
  buildAffiliateProductRegistryCandidateDocId,
  buildAffiliateRegistryCandidateImportPayload,
  buildAffiliateRegistryHistoryRow,
  buildAffiliateRegistryHistoryRowKey,
  buildAffiliateRegistryImportBatches,
};