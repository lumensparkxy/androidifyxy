const test = require("node:test");
const assert = require("node:assert/strict");

const {
  AMAZON_PAAPI_HOST_DEFAULT,
  AMAZON_PAAPI_MARKETPLACE_DEFAULT,
  AMAZON_PAAPI_REGION_DEFAULT,
  AMAZON_PAAPI_TARGET_SEARCH_ITEMS,
  buildAmazonAffiliateConfig,
  buildAmazonAffiliateDetailPageUrl,
  buildAmazonSearchItemsPayload,
  buildSignedAmazonSearchRequest,
  finalizeAmazonAffiliateRecommendation,
  resolveAmazonAffiliateCandidate,
} = require("../amazonAffiliate");

test("buildAmazonAffiliateConfig uses India defaults and detects missing credentials", () => {
  const config = buildAmazonAffiliateConfig({});

  assert.equal(config.host, AMAZON_PAAPI_HOST_DEFAULT);
  assert.equal(config.region, AMAZON_PAAPI_REGION_DEFAULT);
  assert.equal(config.marketplace, AMAZON_PAAPI_MARKETPLACE_DEFAULT);
  assert.equal(config.isConfigured, false);
});

test("buildAmazonSearchItemsPayload builds a SearchItems request", () => {
  const config = buildAmazonAffiliateConfig({
    AMAZON_ASSOCIATES_TAG: "store-21",
    AMAZON_PAAPI_ACCESS_KEY: "access",
    AMAZON_PAAPI_SECRET_KEY: "secret",
  });

  assert.deepEqual(
    buildAmazonSearchItemsPayload({
      lead: {
        productName: "Neem Spray",
        normalizedProductName: "neem spray",
      },
      affiliateCandidate: {
        provider: "amazon",
        searchQuery: "neem spray",
      },
      config,
    }),
    {
      Keywords: "neem spray",
      SearchIndex: "All",
      PartnerTag: "store-21",
      PartnerType: "Associates",
      Marketplace: "www.amazon.in",
      LanguagesOfPreference: ["en_IN"],
      ItemCount: 1,
      Resources: [
        "Images.Primary.Medium",
        "ItemInfo.Title",
        "OffersV2.Listings.Price",
      ],
    },
  );
});

test("buildAmazonAffiliateDetailPageUrl builds a tagged India marketplace URL from ASIN", () => {
  assert.equal(
    buildAmazonAffiliateDetailPageUrl({
      asin: "b0testasin",
      partnerTag: "store-21",
    }),
    "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
  );
  assert.equal(
    buildAmazonAffiliateDetailPageUrl({
      asin: "B0TESTASIN",
      partnerTag: "",
    }),
    null,
  );
});

test("buildSignedAmazonSearchRequest signs a PA-API SearchItems request", () => {
  const request = buildSignedAmazonSearchRequest({
    payload: {
      Keywords: "neem spray",
      PartnerTag: "store-21",
      PartnerType: "Associates",
    },
    config: buildAmazonAffiliateConfig({
      AMAZON_ASSOCIATES_TAG: "store-21",
      AMAZON_PAAPI_ACCESS_KEY: "access",
      AMAZON_PAAPI_SECRET_KEY: "secret",
    }),
    now: new Date("2026-04-19T10:20:30Z"),
  });

  assert.equal(request.url, "https://webservices.amazon.in/paapi5/searchitems");
  assert.equal(request.headers.host, "webservices.amazon.in");
  assert.equal(request.headers["x-amz-target"], AMAZON_PAAPI_TARGET_SEARCH_ITEMS);
  assert.match(request.headers.Authorization, /^AWS4-HMAC-SHA256 /);
});

test("resolveAmazonAffiliateCandidate returns provider-ready candidate from API response", async () => {
  const requests = [];
  const resolution = await resolveAmazonAffiliateCandidate({
    lead: {
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadCategory: "pesticide",
    },
    affiliateCandidate: {
      provider: "amazon",
      providerStatus: "stub_pending_provider",
      productName: "Neem Spray",
      normalizedProductName: "neem spray",
      leadCategory: "pesticide",
      searchQuery: "neem spray",
    },
    config: buildAmazonAffiliateConfig({
      AMAZON_ASSOCIATES_TAG: "store-21",
      AMAZON_PAAPI_ACCESS_KEY: "access",
      AMAZON_PAAPI_SECRET_KEY: "secret",
    }),
    fetchImpl: async (url, options) => {
      requests.push({ url, options });
      return {
        ok: true,
        text: async () => JSON.stringify({
          SearchResult: {
            TotalResultCount: 1,
            Items: [
              {
                ASIN: "B0TESTASIN",
                DetailPageURL: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
                ItemInfo: {
                  Title: {
                    DisplayValue: "Neem Spray Bottle",
                  },
                },
                Images: {
                  Primary: {
                    Medium: {
                      URL: "https://images.example/neem-spray.jpg",
                    },
                  },
                },
                OffersV2: {
                  Listings: [
                    {
                      Price: {
                        Amount: 499,
                        Currency: "INR",
                        DisplayAmount: "₹499.00",
                      },
                    },
                  ],
                },
              },
            ],
          },
        }),
      };
    },
    now: new Date("2026-04-19T10:20:30Z"),
  });

  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, "https://webservices.amazon.in/paapi5/searchitems");
  assert.equal(requests[0].options.method, "POST");
  assert.equal(resolution.outcome, "resolved");
  assert.equal(resolution.candidate.providerStatus, "provider_ready");
  assert.equal(resolution.candidate.asin, "B0TESTASIN");
  assert.equal(resolution.candidate.specialLink, "https://www.amazon.in/dp/B0TESTASIN?tag=store-21");
  assert.equal(resolution.candidate.matchedTitle, "Neem Spray Bottle");
  assert.equal(resolution.candidate.priceDisplay, "₹499.00");
});

test("finalizeAmazonAffiliateRecommendation promotes resolved Amazon item to handoff-ready", () => {
  const finalized = finalizeAmazonAffiliateRecommendation({
    recommendation: {
      routingStatus: "admin_queue",
      reviewStatus: "pending_admin_review",
      recommendationStatus: "no_match",
      adminFallbackReason: "no_matching_supplier",
      commerceChannel: "amazon_affiliate",
      channelDecisionReason: "no_matching_supplier",
      affiliateProvider: "amazon",
      affiliateCandidate: {
        provider: "amazon",
        providerStatus: "stub_pending_provider",
      },
      amazonSearchQuery: "neem spray",
      conversionStatus: "intent_captured",
      whatsappState: "not_ready",
      fallbackTriggered: true,
    },
    resolution: {
      outcome: "resolved",
      candidate: {
        provider: "amazon",
        providerStatus: "provider_ready",
        searchQuery: "neem spray",
        asin: "B0TESTASIN",
        specialLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
      },
    },
  });

  assert.equal(finalized.recommendationStatus, "ready");
  assert.equal(finalized.commerceChannel, "amazon_affiliate");
  assert.equal(finalized.channelDecisionReason, "amazon_search_match");
  assert.equal(finalized.amazonAsin, "B0TESTASIN");
  assert.equal(finalized.amazonSpecialLink, "https://www.amazon.in/dp/B0TESTASIN?tag=store-21");
  assert.equal(finalized.conversionStatus, "handoff_ready");
  assert.equal(finalized.whatsappState, "ready");
});

test("finalizeAmazonAffiliateRecommendation preserves registry-backed exact match reasons", () => {
  const finalized = finalizeAmazonAffiliateRecommendation({
    recommendation: {
      routingStatus: "admin_queue",
      reviewStatus: "pending_admin_review",
      recommendationStatus: "no_match",
      adminFallbackReason: "no_matching_supplier",
      commerceChannel: "amazon_affiliate",
      channelDecisionReason: "no_matching_supplier",
      affiliateProvider: "amazon",
      affiliateCandidate: {
        provider: "amazon",
        providerStatus: "stub_pending_provider",
      },
      amazonSearchQuery: "neem spray",
      conversionStatus: "intent_captured",
      whatsappState: "not_ready",
      fallbackTriggered: true,
    },
    resolution: {
      outcome: "resolved",
      candidate: {
        provider: "amazon",
        providerStatus: "provider_ready",
        reason: "registry_exact_match",
        matchSource: "registry_exact",
        registryEntryId: "registry-1",
        searchQuery: "neem spray",
        asin: "B0TESTASIN",
        specialLink: "https://www.amazon.in/dp/B0TESTASIN?tag=store-21",
      },
    },
  });

  assert.equal(finalized.channelDecisionReason, "registry_exact_match");
  assert.equal(finalized.affiliateCandidate.matchSource, "registry_exact");
  assert.equal(finalized.affiliateCandidate.registryEntryId, "registry-1");
  assert.equal(finalized.amazonSpecialLink, "https://www.amazon.in/dp/B0TESTASIN?tag=store-21");
});

test("finalizeAmazonAffiliateRecommendation sends unresolved search back to admin review", () => {
  const finalized = finalizeAmazonAffiliateRecommendation({
    recommendation: {
      routingStatus: "admin_queue",
      reviewStatus: "pending_admin_review",
      recommendationStatus: "no_match",
      commerceChannel: "amazon_affiliate",
      channelDecisionReason: "no_matching_supplier",
      affiliateProvider: "amazon",
      affiliateCandidate: {
        provider: "amazon",
        providerStatus: "stub_pending_provider",
      },
      amazonSearchQuery: "neem spray",
      conversionStatus: "intent_captured",
      whatsappState: "not_ready",
      fallbackTriggered: true,
    },
    resolution: {
      outcome: "no_result",
      searchQuery: "neem spray",
    },
  });

  assert.equal(finalized.recommendationStatus, "no_match");
  assert.equal(finalized.commerceChannel, "admin_review");
  assert.equal(finalized.channelDecisionReason, "amazon_search_no_match");
  assert.equal(finalized.affiliateCandidate, null);
  assert.equal(finalized.amazonSearchQuery, "neem spray");
});