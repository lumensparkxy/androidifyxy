const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildLeadFarmerProfileSnapshot,
  getLeadProfileValidationError,
  normalizeLeadMobileNumber,
} = require("../leadProfile");

test("normalizeLeadMobileNumber extracts the last 10 digits", () => {
  assert.equal(normalizeLeadMobileNumber("+91 98765 43210"), "9876543210");
});

test("buildLeadFarmerProfileSnapshot exposes canonical phone and email aliases", () => {
  const snapshot = buildLeadFarmerProfileSnapshot({
    name: "Ramesh Patil",
    mobileNumber: " 98765 43210 ",
    emailId: "ramesh@example.com",
  });

  assert.equal(snapshot.mobileNumber, "9876543210");
  assert.equal(snapshot.phoneNumber, "9876543210");
  assert.equal(snapshot.emailId, "ramesh@example.com");
  assert.equal(snapshot.email, "ramesh@example.com");
});

test("getLeadProfileValidationError requires a mobile number for lead creation", () => {
  const error = getLeadProfileValidationError({
    name: "Ramesh Patil",
    village: "Baramati",
    tehsil: "Daund",
    district: "Pune",
    totalFarmAcres: 5,
  });

  assert.equal(error, "mobileNumber is required");
});

test("getLeadProfileValidationError accepts auth phone fallback", () => {
  const error = getLeadProfileValidationError(
    {
      name: "Ramesh Patil",
      village: "Baramati",
      tehsil: "Daund",
      district: "Pune",
      totalFarmAcres: 5,
    },
    "+91 98765 43210",
  );

  assert.equal(error, null);
});
