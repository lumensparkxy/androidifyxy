const {
  AFFILIATE_PROVIDER_AMAZON,
  normalizeProductName,
} = require("./salesPipeline");

const CONVERSATIONS_COLLECTION = "conversations";
const USER_NOTIFICATION_TOPIC_PREFIX = "user_";

function trimString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeProductSearchQuery(value) {
  return normalizeProductName(value) || null;
}

function extractAmazonAsin(value) {
  const link = trimString(value);
  if (!link) return null;

  try {
    const parsed = new URL(link);
    const segments = parsed.pathname.split("/").filter(Boolean);
    for (let index = 0; index < segments.length; index += 1) {
      const segment = segments[index]?.toLowerCase();
      if ((segment === "dp" || segment === "product") && segments[index + 1]) {
        const asin = trimString(segments[index + 1]).toUpperCase();
        return /^[A-Z0-9]{10}$/.test(asin) ? asin : null;
      }
    }
  } catch (_error) {
    return null;
  }

  return null;
}

function buildUserNotificationTopic(userId) {
  const normalizedUserId = trimString(userId);
  if (!normalizedUserId) return null;
  const sanitizedUserId = normalizedUserId.replace(/[^A-Za-z0-9\-_.~%]/g, "_");
  return `${USER_NOTIFICATION_TOPIC_PREFIX}${sanitizedUserId}`;
}

function buildAffiliateGreeting(leadData = {}) {
  const farmerName = trimString(leadData?.farmerProfileSnapshot?.name);
  if (!farmerName) return "Hi,";

  const firstName = farmerName.split(/\s+/)[0];
  return `Hi ${firstName},`;
}

function buildAffiliateOfferMessageLines({ leadData = {}, affiliateLink }) {
  const productName = trimString(leadData?.productName) || "your requested product";
  const requestNumber = trimString(leadData?.requestNumber);
  const lines = [
    buildAffiliateGreeting(leadData),
    "",
    `Good news — we found a great Amazon offer for ${productName}.`,
  ];

  if (requestNumber) {
    lines.push(`Request ID: ${requestNumber}`);
  }

  lines.push(
    "",
    "We are associated with Amazon, which allows us to share special links and offers with you.",
    "",
    "If you'd like to place the order, we recommend using the link below within the next 24 hours, as pricing and availability can change quickly:",
    trimString(affiliateLink),
  );

  return lines;
}

function buildAffiliateHandoffMessage({ leadData = {}, affiliateLink }) {
  const lines = buildAffiliateOfferMessageLines({ leadData, affiliateLink });

  lines.push(
    "",
    "As an Amazon Associate, Krishi AI may earn from qualifying purchases.",
    "If you need any more help or want us to compare alternatives, just reply here.",
    "Thank you.",
  );

  return lines.join("\n");
}

function buildAffiliateWhatsAppMessage({ leadData = {}, affiliateLink }) {
  const lines = buildAffiliateOfferMessageLines({ leadData, affiliateLink });

  lines.push(
    "",
    "If you need any more help or want us to compare alternatives, just reply here.",
    "Thank you.",
  );

  return lines.join("\n");
}

function buildAffiliateNotificationPayload({ leadData = {} }) {
  const productName = trimString(leadData?.productName) || "your requested product";
  return {
    title: "Krishi AI update",
    body: `We found an option for ${productName}. Tap to view it in chat.`,
  };
}

function buildConversationTitle(leadData = {}) {
  const existingTitle = trimString(leadData?.conversationTitle);
  if (existingTitle) return existingTitle;

  const productName = trimString(leadData?.productName);
  if (productName) return productName;

  const chatMessageText = trimString(leadData?.chatMessageText);
  return chatMessageText ? chatMessageText.slice(0, 50) : "Krishi AI update";
}

function buildAssistantConversationMessage({ text, timestamp, imageUrl = null }) {
  return {
    text: trimString(text),
    isUser: false,
    timestamp,
    imageUrl,
  };
}

function buildManualAmazonAffiliateLeadPatch({
  leadData = {},
  affiliateLink,
  actor = {},
  timestampValue,
  handoffChannel = null,
  handoffMessagePreview = null,
  channelDecisionReason = "admin_manual_affiliate",
  candidateReason = "admin_manual_affiliate",
  adminFallbackReason = "admin_manual_affiliate",
  affiliateMatchSource = null,
  affiliateRegistryEntryId = null,
  affiliateRegistryProductName = null,
  affiliateRegistryMatchedAt = null,
  affiliateAutoAppMessageAt = null,
  affiliateAutoAppMessageSource = null,
} = {}) {
  const normalizedAffiliateLink = trimString(affiliateLink);
  const normalizedProductName = normalizeProductSearchQuery(
    leadData?.normalizedProductName || leadData?.productName,
  );
  const asin = extractAmazonAsin(normalizedAffiliateLink);
  const searchQuery = trimString(leadData?.amazonSearchQuery)
    || normalizedProductName
    || null;
  const productName = trimString(leadData?.productName) || null;
  const currentWhatsAppState = trimString(leadData?.whatsappState) || "ready";
  const patch = {
    commerceChannel: "amazon_affiliate",
    channelDecisionReason,
    affiliateProvider: AFFILIATE_PROVIDER_AMAZON,
    affiliateCandidate: {
      provider: AFFILIATE_PROVIDER_AMAZON,
      providerStatus: "provider_ready",
      reason: candidateReason,
      matchSource: trimString(affiliateMatchSource) || null,
      registryEntryId: trimString(affiliateRegistryEntryId) || null,
      stubbed: false,
      productName,
      normalizedProductName,
      leadCategory: trimString(leadData?.leadCategory) || null,
      searchQuery,
      asin,
      specialLink: normalizedAffiliateLink,
      matchedTitle: productName,
    },
    amazonAsin: asin,
    amazonSearchQuery: searchQuery,
    amazonSpecialLink: normalizedAffiliateLink,
    amazonContentRefreshedAt: timestampValue,
    affiliateDisclosureRequired: true,
    routingStatus: "admin_queue",
    reviewStatus: "reviewed",
    recommendationStatus: "ready",
    supplierVisibility: "hidden",
    selectedSupplier: null,
    assignedSupplier: null,
    assignmentPublishedAt: null,
    supplierResponseDeadlineAt: null,
    supplierRespondedAt: null,
    supplierRejectedReason: null,
    adminFallbackReason,
    affiliateMatchSource: trimString(affiliateMatchSource) || null,
    affiliateRegistryEntryId: trimString(affiliateRegistryEntryId) || null,
    affiliateRegistryProductName: trimString(affiliateRegistryProductName) || null,
    affiliateRegistryMatchedAt: affiliateRegistryMatchedAt || null,
    affiliateAutoAppMessageAt: affiliateAutoAppMessageAt || null,
    affiliateAutoAppMessageSource: trimString(affiliateAutoAppMessageSource) || null,
    fallbackTriggeredAt: timestampValue,
    lastRoutingUpdatedAt: timestampValue,
    updatedAt: timestampValue,
  };

  if (handoffChannel === "app") {
    patch.conversionStatus = "handoff_sent";
    patch.whatsappState = currentWhatsAppState;
    patch.appMessageSentAt = timestampValue;
    patch.appMessageSentByUid = actor.uid || null;
    patch.appMessageSentByEmail = actor.email || null;
    patch.lastHandoffChannel = "app";
    patch.lastHandoffMessagePreview = trimString(handoffMessagePreview) || null;
  } else if (handoffChannel === "whatsapp") {
    patch.conversionStatus = "handoff_sent";
    patch.whatsappState = "shared";
    patch.whatsappPreparedAt = timestampValue;
    patch.whatsappPreparedByUid = actor.uid || null;
    patch.whatsappPreparedByEmail = actor.email || null;
    patch.lastHandoffChannel = "whatsapp";
    patch.lastHandoffMessagePreview = trimString(handoffMessagePreview) || null;
  } else {
    patch.conversionStatus = "handoff_ready";
    patch.whatsappState = currentWhatsAppState || "ready";
  }

  return patch;
}

module.exports = {
  CONVERSATIONS_COLLECTION,
  buildAffiliateHandoffMessage,
  buildAffiliateNotificationPayload,
  buildAffiliateWhatsAppMessage,
  buildAssistantConversationMessage,
  buildConversationTitle,
  buildManualAmazonAffiliateLeadPatch,
  buildUserNotificationTopic,
  extractAmazonAsin,
};