const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildLeadFarmerProfileSnapshot,
  deriveLeadReviewStatus,
  deriveLeadSupplierVisibility,
  getLeadProfileValidationError,
  mergeLeadFarmerProfileSnapshots,
  normalizeLeadMobileNumber,
} = require("../leadProfile");

test("normalizeLeadMobileNumber extracts the last 10 digits", () => {
  assert.equal(normalizeLeadMobileNumber("+91 98765 43210"), "9876543210");
});

test("buildLeadFarmerProfileSnapshot exposes canonical mobile and email fields", () => {
  const snapshot = buildLeadFarmerProfileSnapshot({
    name: "Ramesh Patil",
    mobileNumber: " 98765 43210 ",
    emailId: "ramesh@example.com",
  });

  assert.equal(snapshot.mobileNumber, "9876543210");
  assert.equal(snapshot.emailId, "ramesh@example.com");
  assert.equal(snapshot.email, "ramesh@example.com");
});

test("buildLeadFarmerProfileSnapshot stores only compact lead fields", () => {
  const snapshot = buildLeadFarmerProfileSnapshot({
    name: "Ramesh Patil",
    state: "Maharashtra",
    district: " Pune ",
    village: "Baramati",
    tehsil: "Daund",
    market: "Baramati APMC",
    totalFarmAcres: "5",
    majorCrops: ["soybean"],
    mobileNumber: "+91 98765 43210",
  });

  assert.equal(snapshot.name, "Ramesh Patil");
  assert.equal(snapshot.district, "Pune");
  assert.equal(snapshot.totalFarmAcres, 5);
  assert.equal(snapshot.mobileNumber, "9876543210");
  assert.equal("state" in snapshot, false);
  assert.equal("market" in snapshot, false);
  assert.equal("majorCrops" in snapshot, false);
});

test("mergeLeadFarmerProfileSnapshots prefers live values and falls back to stored contact", () => {
  const snapshot = mergeLeadFarmerProfileSnapshots(
    {
      name: "Ramesh Jadhav",
      district: "Nashik",
    },
    {
      name: "Ramesh Patil",
      village: "Baramati",
      tehsil: "Daund",
      district: "Pune",
      mobileNumber: "9876543210",
      emailId: "ramesh@example.com",
    },
  );

  assert.equal(snapshot.name, "Ramesh Jadhav");
  assert.equal(snapshot.village, "Baramati");
  assert.equal(snapshot.tehsil, "Daund");
  assert.equal(snapshot.district, "Nashik");
  assert.equal(snapshot.mobileNumber, "9876543210");
  assert.equal(snapshot.emailId, "ramesh@example.com");
});

test("derived lead status helpers infer review and visibility from routing", () => {
  assert.equal(
    deriveLeadReviewStatus({ routingStatus: "supplier_pending" }),
    "assigned_to_supplier",
  );
  assert.equal(
    deriveLeadSupplierVisibility({ routingStatus: "supplier_accepted" }),
    "unlocked",
  );
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
