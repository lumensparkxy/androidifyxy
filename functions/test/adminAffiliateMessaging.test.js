const test = require("node:test");
const assert = require("node:assert/strict");

const {
  CONVERSATIONS_COLLECTION,
  buildAffiliateHandoffMessage,
  buildAffiliateNotificationPayload,
  buildAffiliateWhatsAppMessage,
  buildAssistantConversationMessage,
  buildConversationTitle,
  buildManualAmazonAffiliateLeadPatch,
  buildUserNotificationTopic,
  extractAmazonAsin,
} = require("../adminAffiliateMessaging");

test("buildUserNotificationTopic sanitizes a user id into a topic", () => {
  assert.equal(buildUserNotificationTopic("user:abc/123"), "user_user_abc_123");
  assert.equal(buildUserNotificationTopic(""), null);
});

test("buildAffiliateHandoffMessage includes the richer offer copy and disclosure", () => {
  const text = buildAffiliateHandoffMessage({
    leadData: {
      farmerProfileSnapshot: { name: "Ramesh Patil" },
      productName: "Neem Spray",
      requestNumber: "KR-20260419-ABC123",
    },
    affiliateLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
  });

  assert.match(text, /^Hi Ramesh,/);
  assert.match(text, /Neem Spray/);
  assert.match(text, /KR-20260419-ABC123/);
  assert.match(text, /great Amazon offer/i);
  assert.match(text, /special links and offers/i);
  assert.match(text, /amazon\.in/);
  assert.match(text, /next 24 hours/i);
  assert.match(text, /Amazon Associate/);
  assert.match(text, /Thank you\./);
});

test("buildAffiliateWhatsAppMessage keeps the richer offer copy without the associate disclosure", () => {
  const text = buildAffiliateWhatsAppMessage({
    leadData: {
      productName: "Neem Spray",
      requestNumber: "KR-20260419-ABC123",
    },
    affiliateLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
  });

  assert.match(text, /Neem Spray/);
  assert.match(text, /KR-20260419-ABC123/);
  assert.match(text, /great Amazon offer/i);
  assert.match(text, /special links and offers/i);
  assert.match(text, /next 24 hours/i);
  assert.doesNotMatch(text, /Amazon Associate/);
  assert.match(text, /compare alternatives/i);
  assert.match(text, /Thank you\./);
});

test("buildAffiliateNotificationPayload returns a concise push notification payload", () => {
  assert.deepEqual(buildAffiliateNotificationPayload({
    leadData: { productName: "Neem Spray" },
  }), {
    title: "Krishi AI update",
    body: "We found an option for Neem Spray. Tap to view it in chat.",
  });
});

test("buildConversationTitle falls back from title to product to inquiry text", () => {
  assert.equal(buildConversationTitle({ conversationTitle: "Stored Title" }), "Stored Title");
  assert.equal(buildConversationTitle({ productName: "Neem Spray" }), "Neem Spray");
  assert.equal(buildConversationTitle({ chatMessageText: "Need help with leaf spot issue and product suggestions" }), "Need help with leaf spot issue and product suggest");
});

test("buildAssistantConversationMessage creates an assistant-style chat message", () => {
  const timestamp = { seconds: 123 };
  assert.deepEqual(buildAssistantConversationMessage({ text: "Hello farmer", timestamp }), {
    text: "Hello farmer",
    isUser: false,
    timestamp,
    imageUrl: null,
  });
});

test("extractAmazonAsin extracts ASIN from a detail page URL", () => {
  assert.equal(extractAmazonAsin("https://www.amazon.in/dp/B0TESTASIN?tag=store-21"), "B0TESTASIN");
  assert.equal(extractAmazonAsin("not-a-url"), null);
});

test("buildManualAmazonAffiliateLeadPatch creates ready handoff state by default", () => {
  const timestampValue = { seconds: 456 };
  const patch = buildManualAmazonAffiliateLeadPatch({
    leadData: {
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadCategory: "pesticide",
    },
    affiliateLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
    timestampValue,
  });

  assert.equal(patch.commerceChannel, "amazon_affiliate");
  assert.equal(patch.affiliateProvider, "amazon");
  assert.equal(patch.amazonAsin, "B0TESTASIN");
  assert.equal(patch.conversionStatus, "handoff_ready");
  assert.equal(patch.whatsappState, "ready");
  assert.equal(patch.lastHandoffChannel, undefined);
});

test("buildManualAmazonAffiliateLeadPatch tracks app handoff metadata", () => {
  const timestampValue = { seconds: 789 };
  const patch = buildManualAmazonAffiliateLeadPatch({
    leadData: {
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadCategory: "pesticide",
      whatsappState: "ready",
    },
    affiliateLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
    actor: { uid: "admin-1", email: "admin@example.com" },
    timestampValue,
    handoffChannel: "app",
    handoffMessagePreview: "Preview",
  });

  assert.equal(patch.conversionStatus, "handoff_sent");
  assert.equal(patch.appMessageSentByUid, "admin-1");
  assert.equal(patch.appMessageSentByEmail, "admin@example.com");
  assert.equal(patch.lastHandoffChannel, "app");
  assert.equal(patch.lastHandoffMessagePreview, "Preview");
});

test("exports the conversations collection name used for app handoff writes", () => {
  assert.equal(CONVERSATIONS_COLLECTION, "conversations");
});