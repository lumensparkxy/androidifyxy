const {
  AFFILIATE_PROVIDER_AMAZON,
} = require("./salesPipeline");
const {
  buildAffiliateProductRegistryDocId,
  normalizeAffiliateRegistryProductName,
} = require("./affiliateRegistry");

const DEFAULT_AFFILIATE_REGISTRY_BACKFILL_LIMIT = 200;
const MAX_AFFILIATE_REGISTRY_BACKFILL_LIMIT = 500;

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function timestampToMillis(value) {
  if (!value) return 0;
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  if (value instanceof Date) {
    return Number.isFinite(value.getTime()) ? value.getTime() : 0;
  }
  if (typeof value?.toMillis === "function") {
    const millis = value.toMillis();
    return Number.isFinite(millis) ? millis : 0;
  }
  if (typeof value?.toDate === "function") {
    const date = value.toDate();
    return date instanceof Date && Number.isFinite(date.getTime()) ? date.getTime() : 0;
  }
  return 0;
}

function timestampToIsoString(value) {
  const millis = timestampToMillis(value);
  return millis > 0 ? new Date(millis).toISOString() : null;
}

function parseAffiliateRegistryBackfillLimit(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return DEFAULT_AFFILIATE_REGISTRY_BACKFILL_LIMIT;
  }

  return Math.min(
    Math.max(Math.floor(parsed), 1),
    MAX_AFFILIATE_REGISTRY_BACKFILL_LIMIT,
  );
}

function extractAffiliateLink(leadData = {}) {
  return trimString(leadData.amazonSpecialLink)
    || trimString(leadData?.affiliateCandidate?.specialLink);
}

function extractNormalizedProductName(leadData = {}) {
  return normalizeAffiliateRegistryProductName(
    leadData.normalizedProductName
      || leadData.productName
      || leadData.amazonSearchQuery
      || leadData?.affiliateCandidate?.normalizedProductName
      || leadData?.affiliateCandidate?.searchQuery
      || leadData?.affiliateCandidate?.productName,
  );
}

function extractDisplayProductName(leadData = {}, normalizedProductName = "") {
  return trimString(leadData.productName)
    || trimString(leadData?.affiliateCandidate?.productName)
    || trimString(leadData.amazonSearchQuery)
    || normalizedProductName;
}

function buildEligibleAffiliateRegistryBackfillItem({ leadId, leadData = {} } = {}) {
  if (trimString(leadData.commerceChannel) !== "amazon_affiliate") {
    return null;
  }

  if (!leadData.appMessageSentAt) {
    return null;
  }

  const specialLink = extractAffiliateLink(leadData);
  if (!specialLink) {
    return null;
  }

  const normalizedProductName = extractNormalizedProductName(leadData);
  if (!normalizedProductName) {
    return null;
  }

  const appMessageSentAtMillis = timestampToMillis(leadData.appMessageSentAt);
  const latestActivityMillis = Math.max(
    appMessageSentAtMillis,
    timestampToMillis(leadData.updatedAt),
    timestampToMillis(leadData.createdAt),
  );

  return {
    leadId: trimString(leadId),
    productName: extractDisplayProductName(leadData, normalizedProductName),
    normalizedProductName,
    specialLink,
    appMessageSentAt: timestampToIsoString(leadData.appMessageSentAt),
    appMessageSentAtMillis,
    latestActivityMillis,
  };
}

function chooseRepresentativeBackfillItem(items = []) {
  return [...items].sort((left, right) => {
    const timeDifference = right.latestActivityMillis - left.latestActivityMillis;
    if (timeDifference !== 0) return timeDifference;
    return trimString(left.leadId).localeCompare(trimString(right.leadId));
  })[0] || null;
}

function toExistingRegistryEntry(existingEntriesById, entryId) {
  if (!existingEntriesById || !entryId) return null;
  if (existingEntriesById instanceof Map) {
    return existingEntriesById.get(entryId) || null;
  }
  return existingEntriesById[entryId] || null;
}

function buildAffiliateRegistryBackfillRow({
  action,
  reason,
  entryId,
  productName,
  normalizedProductName,
  specialLink = null,
  sourceLeadCount,
  distinctLinkCount,
  latestAppMessageSentAt = null,
  existingIsActive = null,
  existingSpecialLink = null,
} = {}) {
  return {
    action,
    reason,
    entryId,
    provider: AFFILIATE_PROVIDER_AMAZON,
    productName,
    normalizedProductName,
    specialLink,
    sourceLeadCount,
    distinctLinkCount,
    latestAppMessageSentAt,
    existingIsActive,
    existingSpecialLink,
  };
}

function buildAffiliateRegistryBackfillPlan({
  items = [],
  existingEntriesById = new Map(),
} = {}) {
  const groupsByProduct = new Map();
  items.forEach((item) => {
    if (!item?.normalizedProductName || !item?.specialLink) return;
    const current = groupsByProduct.get(item.normalizedProductName) || [];
    current.push(item);
    groupsByProduct.set(item.normalizedProductName, current);
  });

  const safeCreateRows = [];
  const skippedExistingRows = [];
  const conflictRows = [];

  [...groupsByProduct.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .forEach(([normalizedProductName, groupItems]) => {
      const entryId = buildAffiliateProductRegistryDocId({
        provider: AFFILIATE_PROVIDER_AMAZON,
        normalizedProductName,
      });
      if (!entryId) {
        return;
      }

      const representative = chooseRepresentativeBackfillItem(groupItems);
      const links = [...new Set(groupItems.map((item) => trimString(item.specialLink)).filter(Boolean))]
        .sort();
      const existingEntry = toExistingRegistryEntry(existingEntriesById, entryId);
      const existingSpecialLink = trimString(existingEntry?.specialLink) || null;
      const existingIsActive = existingEntry ? existingEntry.isActive !== false : null;
      const baseRow = {
        entryId,
        productName: representative?.productName || normalizedProductName,
        normalizedProductName,
        specialLink: links.length === 1 ? links[0] : null,
        sourceLeadCount: groupItems.length,
        distinctLinkCount: links.length,
        latestAppMessageSentAt: representative?.appMessageSentAt || null,
        existingIsActive,
        existingSpecialLink,
      };

      if (existingEntry) {
        if (existingIsActive && links.length === 1 && existingSpecialLink === links[0]) {
          skippedExistingRows.push(buildAffiliateRegistryBackfillRow({
            ...baseRow,
            action: "skip_existing",
            reason: "existing_registry_match",
          }));
          return;
        }

        conflictRows.push(buildAffiliateRegistryBackfillRow({
          ...baseRow,
          action: "conflict",
          reason: existingIsActive ? "existing_registry_differs" : "existing_registry_inactive",
        }));
        return;
      }

      if (links.length === 1) {
        safeCreateRows.push(buildAffiliateRegistryBackfillRow({
          ...baseRow,
          action: "create",
          reason: "single_historical_link",
          specialLink: links[0],
        }));
        return;
      }

      conflictRows.push(buildAffiliateRegistryBackfillRow({
        ...baseRow,
        action: "conflict",
        reason: "multiple_historical_links",
      }));
    });

  return {
    eligibleLeadCount: items.length,
    sourceProductCount: groupsByProduct.size,
    safeCreateRows,
    skippedExistingRows,
    conflictRows,
    safeCreateCount: safeCreateRows.length,
    skippedExistingCount: skippedExistingRows.length,
    conflictCount: conflictRows.length,
    previewRows: [
      ...safeCreateRows,
      ...skippedExistingRows,
      ...conflictRows,
    ],
  };
}

function selectAffiliateRegistryBackfillCreateRows(plan = {}, { dryRun = true } = {}) {
  if (dryRun) {
    return [];
  }

  return Array.isArray(plan.safeCreateRows) ? plan.safeCreateRows : [];
}

module.exports = {
  DEFAULT_AFFILIATE_REGISTRY_BACKFILL_LIMIT,
  MAX_AFFILIATE_REGISTRY_BACKFILL_LIMIT,
  buildAffiliateRegistryBackfillPlan,
  buildEligibleAffiliateRegistryBackfillItem,
  parseAffiliateRegistryBackfillLimit,
  selectAffiliateRegistryBackfillCreateRows,
};
