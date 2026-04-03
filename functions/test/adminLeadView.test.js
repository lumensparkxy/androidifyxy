const test = require("node:test");
const assert = require("node:assert/strict");

const { buildAdminLeadView } = require("../adminLeadView");

test("buildAdminLeadView merges live farmer profile over stored snapshot", () => {
  const lead = buildAdminLeadView(
    "lead-1",
    {
      userId: "user-1",
      routingStatus: "supplier_pending",
      recommendationStatus: "ready",
      farmerProfileSnapshot: {
        name: "Ramesh Patil",
        district: "Pune",
        village: "Baramati",
        mobileNumber: "9876543210",
      },
    },
    {
      name: "Ramesh Jadhav",
      district: "Nashik",
      tehsil: "Daund",
      emailId: "ramesh@example.com",
    },
  );

  assert.equal(lead.id, "lead-1");
  assert.equal(lead.farmerProfileSnapshot.name, "Ramesh Jadhav");
  assert.equal(lead.farmerProfileSnapshot.district, "Nashik");
  assert.equal(lead.farmerProfileSnapshot.village, "Baramati");
  assert.equal(lead.farmerProfileSnapshot.tehsil, "Daund");
  assert.equal(lead.farmerProfileSnapshot.mobileNumber, "9876543210");
  assert.equal(lead.farmerProfileSnapshot.emailId, "ramesh@example.com");
});

test("buildAdminLeadView derives compatibility workflow fields and supplier fallbacks", () => {
  const createdAt = new Date("2026-04-03T12:34:56.000Z");
  const lead = buildAdminLeadView("lead-2", {
    status: "",
    routingStatus: "supplier_accepted",
    recommendationStatus: "ready",
    assignedSupplier: { supplierId: "supplier-1", businessName: "Best Inputs" },
    createdAt,
  });

  assert.equal(lead.status, "initiated");
  assert.equal(lead.reviewStatus, "assigned_to_supplier");
  assert.equal(lead.supplierVisibility, "unlocked");
  assert.deepEqual(lead.selectedSupplier, { supplierId: "supplier-1", businessName: "Best Inputs" });
  assert.deepEqual(lead.assignedSupplier, { supplierId: "supplier-1", businessName: "Best Inputs" });
  assert.equal(lead.createdAt, createdAt.toISOString());
  assert.equal(lead.backendProcessingStatus, "pending");
  assert.equal(lead.commissionStatus, "preview");
});