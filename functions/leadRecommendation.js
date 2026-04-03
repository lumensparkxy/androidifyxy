const OFFER_STATUS_ACTIVE = "ACTIVE";
const OFFER_STATUS_APPROVED = "APPROVED";
const SUPPLIER_STATUS_APPROVED = "APPROVED";

const COMMISSION_BY_CATEGORY = Object.freeze({
  fertilizer: 150,
  pesticide: 180,
  seed: 200,
  other: 120,
});

function normalizeText(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, " ");
}

function splitKeywords(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => normalizeText(item))
    .filter(Boolean);
}

function normalizeDistrictCandidates(supplier = {}) {
  const candidates = new Set();
  const districtId = normalizeText(supplier.districtId);
  const districtName = normalizeText(supplier.districtName);

  if (districtId) {
    candidates.add(districtId);
    const districtSuffix = districtId.split(":").pop();
    if (districtSuffix) candidates.add(districtSuffix);
  }
  if (districtName) {
    candidates.add(districtName);
    const districtSuffix = districtName.split(",")[0];
    if (districtSuffix) candidates.add(normalizeText(districtSuffix));
  }

  return candidates;
}

function computeCommissionPreview(category) {
  const normalizedCategory = normalizeText(category) || "other";
  return {
    category: normalizedCategory,
    amount: COMMISSION_BY_CATEGORY[normalizedCategory] || COMMISSION_BY_CATEGORY.other,
    currency: "INR",
    ruleId: `fixed_${normalizedCategory}`,
  };
}

function isOfferPublished(offer = {}) {
  const status = String(offer.status || "").toUpperCase();
  return offer.supplierApproved === true && (status === OFFER_STATUS_ACTIVE || status === OFFER_STATUS_APPROVED);
}

function buildOfferKeywordSet(offer = {}) {
  const keywords = new Set(splitKeywords(offer.keywords));
  const productName = normalizeText(offer.productName);
  const brand = normalizeText(offer.brand);
  const category = normalizeText(offer.category);

  if (productName) keywords.add(productName);
  if (brand) keywords.add(brand);
  if (category) keywords.add(category);

  for (const token of productName.split(/\W+/)) {
    const normalizedToken = normalizeText(token);
    if (normalizedToken) keywords.add(normalizedToken);
  }

  return keywords;
}

function scoreCandidate({ lead, offer, supplier }) {
  const leadDistrict = normalizeText(lead?.leadLocation?.districtKey || lead?.leadLocation?.district);
  const leadTehsil = normalizeText(lead?.leadLocation?.tehsilKey || lead?.leadLocation?.tehsil);
  const leadVillage = normalizeText(lead?.leadLocation?.villageKey || lead?.leadLocation?.village);
  const leadCategory = normalizeText(lead?.leadCategory);
  const leadProduct = normalizeText(lead?.normalizedProductName || lead?.productName);
  const leadQuantity = Number(lead?.quantity);

  const supplierDistricts = normalizeDistrictCandidates(supplier);
  if (leadDistrict && !supplierDistricts.has(leadDistrict)) {
    return null;
  }

  const serviceCategories = new Set(splitKeywords(supplier?.serviceCategories));
  if (leadCategory && serviceCategories.size > 0 && !serviceCategories.has(leadCategory)) {
    return null;
  }

  const offerKeywords = buildOfferKeywordSet(offer);
  if (leadProduct && !offerKeywords.has(leadProduct) && !leadProduct.split(" ").every((token) => offerKeywords.has(token))) {
    const hasLooseMatch = [...offerKeywords].some((keyword) => keyword && leadProduct.includes(keyword));
    if (!hasLooseMatch && normalizeText(offer.category) !== leadCategory) {
      return null;
    }
  }

  const villageCoverage = new Set(splitKeywords(supplier?.villageCoverage));
  const tehsilCoverage = new Set(splitKeywords(supplier?.tehsilCoverage));
  const productKeywords = new Set(splitKeywords(supplier?.productKeywords));
  const maxOpenLeads = Number(supplier?.maxOpenLeads);
  const openLeadCount = Number(supplier?.openLeadCount || 0);

  if (Number.isFinite(maxOpenLeads) && maxOpenLeads > 0 && openLeadCount >= maxOpenLeads) {
    return null;
  }

  let score = 0;
  const reasons = [];

  score += 40;
  reasons.push("district-match");

  if (leadVillage && villageCoverage.has(leadVillage)) {
    score += 25;
    reasons.push("village-coverage");
  }

  if (leadTehsil && tehsilCoverage.has(leadTehsil)) {
    score += 15;
    reasons.push("tehsil-coverage");
  }

  if (leadCategory && normalizeText(offer.category) === leadCategory) {
    score += 20;
    reasons.push("category-match");
  }

  if (leadProduct && offerKeywords.has(leadProduct)) {
    score += 30;
    reasons.push("exact-product-match");
  } else if (leadProduct && [...offerKeywords].some((keyword) => keyword && leadProduct.includes(keyword))) {
    score += 12;
    reasons.push("keyword-product-match");
  }

  if (leadProduct && productKeywords.has(leadProduct)) {
    score += 10;
    reasons.push("supplier-keyword-match");
  }

  const packSize = Number(offer?.packSize);
  if (Number.isFinite(leadQuantity) && leadQuantity > 0 && Number.isFinite(packSize) && packSize > 0) {
    score += 8;
    reasons.push("quantity-compatible");
  }

  score += Math.max(0, 10 - openLeadCount);

  return {
    score,
    reasons,
  };
}

function buildLeadRecommendation({ lead, suppliersById = new Map(), offers = [] }) {
  const commissionPreview = computeCommissionPreview(lead?.leadCategory);
  const eligibleOffers = offers.filter((offer) => isOfferPublished(offer));
  let bestMatch = null;

  for (const offer of eligibleOffers) {
    const supplierId = String(offer.supplierId || "");
    if (!supplierId) continue;

    const supplier = suppliersById.get(supplierId);
    if (!supplier || supplier.verificationStatus !== SUPPLIER_STATUS_APPROVED) {
      continue;
    }
    if (supplier.leadIntakeEnabled === false) {
      continue;
    }

    const scored = scoreCandidate({ lead, offer, supplier });
    if (!scored) continue;

    const candidate = {
      supplierId,
      businessName: supplier.businessName || offer.supplierName || "",
      districtId: supplier.districtId || offer.districtId || null,
      districtName: supplier.districtName || null,
      matchScore: scored.score,
      matchSummary: scored.reasons.join(", "),
      source: "system",
      offerId: offer.id || null,
      offerCategory: offer.category || null,
    };

    if (!bestMatch || candidate.matchScore > bestMatch.matchScore) {
      bestMatch = candidate;
    }
  }

  if (!bestMatch) {
    return {
      routingStatus: "admin_queue",
      reviewStatus: "pending_admin_review",
      recommendationStatus: "no_match",
      suggestedSupplier: null,
      commissionPreview,
      adminFallbackReason: "no_matching_supplier",
    };
  }

  return {
    routingStatus: "suggested_for_supplier",
    reviewStatus: "pending_admin_review",
    recommendationStatus: "ready",
    suggestedSupplier: bestMatch,
    commissionPreview,
    adminFallbackReason: null,
  };
}

module.exports = {
  buildLeadRecommendation,
  computeCommissionPreview,
  normalizeDistrictCandidates,
  normalizeText,
};