const { SALES_PIPELINE_STATUS_INITIATED } = require("./salesPipeline");
const {
  deriveLeadReviewStatus,
  deriveLeadSupplierVisibility,
  mergeLeadFarmerProfileSnapshots,
} = require("./leadProfile");

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function timestampToIsoString(value) {
  if (!value) return undefined;
  if (typeof value === "string") return value;
  if (value instanceof Date) return value.toISOString();
  if (typeof value?.toDate === "function") {
    return value.toDate().toISOString();
  }
  return undefined;
}

function buildAdminLeadView(leadId, leadData = {}, liveFarmerProfile = null) {
  const routingStatus = trimString(leadData.routingStatus) || SALES_PIPELINE_STATUS_INITIATED;
  const farmerProfileSnapshot = mergeLeadFarmerProfileSnapshots(
    liveFarmerProfile || {},
    leadData.farmerProfileSnapshot || {},
  );

  return {
    id: leadId,
    userId: trimString(leadData.userId) || null,
    conversationId: trimString(leadData.conversationId) || null,
    requestNumber: trimString(leadData.requestNumber) || null,
    status: trimString(leadData.status) || SALES_PIPELINE_STATUS_INITIATED,
    source: trimString(leadData.source) || null,
    dedupeKey: trimString(leadData.dedupeKey) || leadId,
    productName: trimString(leadData.productName) || null,
    normalizedProductName: trimString(leadData.normalizedProductName) || null,
    quantity: trimString(leadData.quantity) || null,
    unit: trimString(leadData.unit) || null,
    chatMessageText: trimString(leadData.chatMessageText),
    farmerProfileSnapshot,
    leadCategory: trimString(leadData.leadCategory) || null,
    leadLocation: leadData.leadLocation || null,
    routingStatus,
    reviewStatus: deriveLeadReviewStatus(leadData),
    recommendationStatus: trimString(leadData.recommendationStatus) || null,
    supplierVisibility: deriveLeadSupplierVisibility(leadData),
    commerceChannel: trimString(leadData.commerceChannel) || "supplier_local",
    channelDecisionReason: trimString(leadData.channelDecisionReason) || "default_supplier_first",
    fallbackPolicy: trimString(leadData.fallbackPolicy) || "amazon_on_no_match_or_timeout",
    affiliateProvider: trimString(leadData.affiliateProvider)
      || trimString(leadData?.affiliateCandidate?.provider)
      || null,
    affiliateCandidate: leadData.affiliateCandidate || null,
    amazonAsin: trimString(leadData.amazonAsin)
      || trimString(leadData?.affiliateCandidate?.asin)
      || null,
    amazonSearchQuery: trimString(leadData.amazonSearchQuery)
      || trimString(leadData?.affiliateCandidate?.searchQuery)
      || trimString(leadData.normalizedProductName)
      || null,
    amazonSpecialLink: trimString(leadData.amazonSpecialLink)
      || trimString(leadData?.affiliateCandidate?.specialLink)
      || null,
    amazonContentRefreshedAt: timestampToIsoString(leadData.amazonContentRefreshedAt),
    affiliateDisclosureRequired: leadData.affiliateDisclosureRequired === true,
    conversionStatus: trimString(leadData.conversionStatus) || "intent_captured",
    whatsappState: trimString(leadData.whatsappState) || "not_ready",
    fallbackTriggeredAt: timestampToIsoString(leadData.fallbackTriggeredAt),
    appMessageSentAt: timestampToIsoString(leadData.appMessageSentAt),
    appMessageSentByUid: trimString(leadData.appMessageSentByUid) || null,
    appMessageSentByEmail: trimString(leadData.appMessageSentByEmail) || null,
    whatsappPreparedAt: timestampToIsoString(leadData.whatsappPreparedAt),
    whatsappPreparedByUid: trimString(leadData.whatsappPreparedByUid) || null,
    whatsappPreparedByEmail: trimString(leadData.whatsappPreparedByEmail) || null,
    lastHandoffChannel: trimString(leadData.lastHandoffChannel) || null,
    lastHandoffMessagePreview: trimString(leadData.lastHandoffMessagePreview) || null,
    suggestedSupplier: leadData.suggestedSupplier || null,
    selectedSupplier: leadData.selectedSupplier || leadData.assignedSupplier || null,
    assignedSupplier: leadData.assignedSupplier || leadData.selectedSupplier || null,
    commissionPreview: leadData.commissionPreview || null,
    commissionStatus: trimString(leadData.commissionStatus) || "preview",
    commissionLedgerEntryId: trimString(leadData.commissionLedgerEntryId) || null,
    commissionMonthKey: trimString(leadData.commissionMonthKey) || null,
    commissionApprovedAt: timestampToIsoString(leadData.commissionApprovedAt),
    commissionApprovedByUid: trimString(leadData.commissionApprovedByUid) || null,
    commissionApprovedByEmail: trimString(leadData.commissionApprovedByEmail) || null,
    commissionPaidAt: timestampToIsoString(leadData.commissionPaidAt),
    commissionPaidByUid: trimString(leadData.commissionPaidByUid) || null,
    commissionPaidByEmail: trimString(leadData.commissionPaidByEmail) || null,
    backendProcessingStatus: trimString(leadData.backendProcessingStatus) || "pending",
    backendProcessedAt: timestampToIsoString(leadData.backendProcessedAt),
    backendProcessedByUid: trimString(leadData.backendProcessedByUid) || null,
    backendProcessedByEmail: trimString(leadData.backendProcessedByEmail) || null,
    closedAt: timestampToIsoString(leadData.closedAt),
    closedByUid: trimString(leadData.closedByUid) || null,
    closedByEmail: trimString(leadData.closedByEmail) || null,
    closedReason: trimString(leadData.closedReason) || null,
    suggestionGeneratedAt: timestampToIsoString(leadData.suggestionGeneratedAt),
    assignmentPublishedAt: timestampToIsoString(leadData.assignmentPublishedAt),
    supplierResponseDeadlineAt: timestampToIsoString(leadData.supplierResponseDeadlineAt),
    supplierRespondedAt: timestampToIsoString(leadData.supplierRespondedAt),
    supplierRejectedReason: trimString(leadData.supplierRejectedReason) || null,
    adminFallbackReason: trimString(leadData.adminFallbackReason) || null,
    lastRoutingUpdatedAt: timestampToIsoString(leadData.lastRoutingUpdatedAt),
    opsStatus: trimString(leadData.opsStatus) || "new",
    opsOwnerUid: trimString(leadData.opsOwnerUid) || null,
    opsOwnerEmail: trimString(leadData.opsOwnerEmail) || null,
    opsNotes: trimString(leadData.opsNotes) || "",
    firstOpsContactAt: timestampToIsoString(leadData.firstOpsContactAt),
    lastOpsActionAt: timestampToIsoString(leadData.lastOpsActionAt),
    createdAt: timestampToIsoString(leadData.createdAt),
    updatedAt: timestampToIsoString(leadData.updatedAt),
  };
}

module.exports = {
  buildAdminLeadView,
};