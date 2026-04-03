const BUSINESS_TIME_ZONE = "Asia/Kolkata";

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function coerceDate(value) {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return value;
  }
  if (typeof value === "string") {
    const parsed = new Date(value);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed;
    }
  }
  if (value && typeof value.toDate === "function") {
    const parsed = value.toDate();
    if (parsed instanceof Date && !Number.isNaN(parsed.getTime())) {
      return parsed;
    }
  }
  return new Date();
}

function getCommissionMonthKey(value = new Date()) {
  const date = coerceDate(value);
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
  });
  const parts = formatter.formatToParts(date);
  const year = parts.find((part) => part.type === "year")?.value || "0000";
  const month = parts.find((part) => part.type === "month")?.value || "01";
  return `${year}-${month}`;
}

function toPositiveNumber(value) {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount : null;
}

function summarizeCommissionMonthEntries(entries = []) {
  return entries.reduce((summary, entry) => {
    const amount = toPositiveNumber(entry?.amount);
    if (amount == null) {
      return summary;
    }

    const status = trimString(entry?.status).toLowerCase();
    if (status === "approved" || status === "paid") {
      summary.approvedCount += 1;
      summary.approvedTotal += amount;
    }
    if (status === "paid") {
      summary.paidCount += 1;
      summary.paidTotal += amount;
    }
    summary.outstandingTotal = summary.approvedTotal - summary.paidTotal;
    return summary;
  }, {
    approvedCount: 0,
    approvedTotal: 0,
    paidCount: 0,
    paidTotal: 0,
    outstandingTotal: 0,
    currency: "INR",
  });
}

function buildCommissionLifecycleDefaults() {
  return {
    commissionStatus: "preview",
    commissionLedgerEntryId: null,
    commissionMonthKey: null,
    commissionApprovedAt: null,
    commissionApprovedByUid: null,
    commissionApprovedByEmail: null,
    commissionPaidAt: null,
    commissionPaidByUid: null,
    commissionPaidByEmail: null,
    backendProcessingStatus: "pending",
    backendProcessedAt: null,
    backendProcessedByUid: null,
    backendProcessedByEmail: null,
    closedAt: null,
    closedByUid: null,
    closedByEmail: null,
    closedReason: null,
  };
}

module.exports = {
  buildCommissionLifecycleDefaults,
  getCommissionMonthKey,
  summarizeCommissionMonthEntries,
};
