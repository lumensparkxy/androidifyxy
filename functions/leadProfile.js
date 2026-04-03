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

function buildLeadFarmerProfileSnapshot(farmerProfile = {}, authPhoneNumber = "", authEmail = "") {
  const mobileNumber = normalizeLeadMobileNumber(
    farmerProfile?.mobileNumber || farmerProfile?.phoneNumber || authPhoneNumber
  );
  const email = normalizeLeadEmail(
    farmerProfile?.emailId || farmerProfile?.email || authEmail
  );

  return {
    ...farmerProfile,
    mobileNumber,
    phoneNumber: mobileNumber,
    emailId: email,
    email,
  };
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
  getLeadProfileValidationError,
  normalizeLeadMobileNumber,
};
