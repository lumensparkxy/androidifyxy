function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeLeadMobileNumber(value) {
  const digits = trimString(value).replace(/\D/g, "");
  if (!digits) return null;

  const normalized = digits.length > 10 ? digits.slice(-10) : digits;
  return normalized.length === 10 ? normalized : null;
}

function normalizeLeadEmail(value) {
  const email = trimString(value);
  return email || null;
}

function pickLeadProfileString(...values) {
  for (const value of values) {
    const trimmed = trimString(value);
    if (trimmed) return trimmed;
  }
  return null;
}

function pickLeadProfilePositiveNumber(...values) {
  for (const value of values) {
    const number = Number(value);
    if (Number.isFinite(number) && number > 0) {
      return number;
    }
  }
  return null;
}

function buildLeadFarmerProfileSnapshot(farmerProfile = {}, authPhoneNumber = "", authEmail = "") {
  const mobileNumber = normalizeLeadMobileNumber(
    farmerProfile?.mobileNumber || farmerProfile?.phoneNumber || authPhoneNumber
  );
  const email = normalizeLeadEmail(
    farmerProfile?.emailId || farmerProfile?.email || authEmail
  );

  const snapshot = {};
  const name = pickLeadProfileString(farmerProfile?.name);
  const village = pickLeadProfileString(farmerProfile?.village);
  const tehsil = pickLeadProfileString(farmerProfile?.tehsil);
  const district = pickLeadProfileString(farmerProfile?.district);
  const totalFarmAcres = pickLeadProfilePositiveNumber(farmerProfile?.totalFarmAcres);

  if (name) snapshot.name = name;
  if (village) snapshot.village = village;
  if (tehsil) snapshot.tehsil = tehsil;
  if (district) snapshot.district = district;
  if (totalFarmAcres != null) snapshot.totalFarmAcres = totalFarmAcres;

  snapshot.mobileNumber = mobileNumber;
  snapshot.emailId = email;
  snapshot.email = email;

  return snapshot;
}

function mergeLeadFarmerProfileSnapshots(primaryProfile = {}, fallbackProfile = {}, authPhoneNumber = "", authEmail = "") {
  const primarySnapshot = buildLeadFarmerProfileSnapshot(primaryProfile, authPhoneNumber, authEmail);
  const fallbackSnapshot = buildLeadFarmerProfileSnapshot(fallbackProfile);

  const mergedSnapshot = {};
  const name = pickLeadProfileString(primarySnapshot?.name, fallbackSnapshot?.name);
  const village = pickLeadProfileString(primarySnapshot?.village, fallbackSnapshot?.village);
  const tehsil = pickLeadProfileString(primarySnapshot?.tehsil, fallbackSnapshot?.tehsil);
  const district = pickLeadProfileString(primarySnapshot?.district, fallbackSnapshot?.district);
  const totalFarmAcres = pickLeadProfilePositiveNumber(
    primarySnapshot?.totalFarmAcres,
    fallbackSnapshot?.totalFarmAcres,
  );
  const mobileNumber = primarySnapshot?.mobileNumber || fallbackSnapshot?.mobileNumber || null;
  const email = primarySnapshot?.emailId || primarySnapshot?.email || fallbackSnapshot?.emailId || fallbackSnapshot?.email || null;

  if (name) mergedSnapshot.name = name;
  if (village) mergedSnapshot.village = village;
  if (tehsil) mergedSnapshot.tehsil = tehsil;
  if (district) mergedSnapshot.district = district;
  if (totalFarmAcres != null) mergedSnapshot.totalFarmAcres = totalFarmAcres;

  mergedSnapshot.mobileNumber = mobileNumber;
  mergedSnapshot.emailId = email;
  mergedSnapshot.email = email;

  return mergedSnapshot;
}

function deriveLeadReviewStatus(leadData = {}) {
  const explicitReviewStatus = trimString(leadData?.reviewStatus);
  if (explicitReviewStatus) return explicitReviewStatus;

  const routingStatus = trimString(leadData?.routingStatus);
  const recommendationStatus = trimString(leadData?.recommendationStatus);

  if (["supplier_pending", "supplier_accepted"].includes(routingStatus)) {
    return "assigned_to_supplier";
  }

  if (["admin_claimed", "admin_closed"].includes(routingStatus)) {
    return "reviewed";
  }

  if (["suggested_for_supplier", "admin_queue", "supplier_rejected", "supplier_timeout"].includes(routingStatus)) {
    return "pending_admin_review";
  }

  if (["ready", "needs_admin_review", "no_match"].includes(recommendationStatus)) {
    return "pending_admin_review";
  }

  if (recommendationStatus === "pending" || routingStatus === "initiated") {
    return "pending_recommendation";
  }

  return null;
}

function deriveLeadSupplierVisibility(leadData = {}) {
  const explicitSupplierVisibility = trimString(leadData?.supplierVisibility);
  if (explicitSupplierVisibility) return explicitSupplierVisibility;

  const routingStatus = trimString(leadData?.routingStatus);
  if (["supplier_accepted", "admin_claimed", "admin_closed"].includes(routingStatus)) {
    return "unlocked";
  }
  if (["supplier_pending", "supplier_rejected", "supplier_timeout"].includes(routingStatus)) {
    return "masked";
  }

  return "hidden";
}

function getLeadProfileValidationError(profile = {}, authPhoneNumber = "", authEmail = "") {
  const normalizedProfile = buildLeadFarmerProfileSnapshot(profile, authPhoneNumber, authEmail);

  if (!trimString(normalizedProfile.name)) return "name is required";
  if (!trimString(normalizedProfile.village)) return "village is required";
  if (!trimString(normalizedProfile.tehsil)) return "tehsil is required";
  if (!trimString(normalizedProfile.district)) return "district is required";

  const totalFarmAcres = Number(normalizedProfile.totalFarmAcres);
  if (!Number.isFinite(totalFarmAcres) || totalFarmAcres <= 0) return "totalFarmAcres is required";
  if (!normalizedProfile.mobileNumber) return "mobileNumber is required";

  return null;
}

module.exports = {
  buildLeadFarmerProfileSnapshot,
  deriveLeadReviewStatus,
  deriveLeadSupplierVisibility,
  getLeadProfileValidationError,
  mergeLeadFarmerProfileSnapshots,
  normalizeLeadMobileNumber,
};
