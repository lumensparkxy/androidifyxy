export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type OfferStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'EXPIRED';
export type SalesLeadStatus = 'initiated';
export type SalesLeadOpsStatus = 'new' | 'claimed' | 'called' | 'qualified' | 'callback' | 'lost';
export type SalesLeadCommissionStatus = 'preview' | 'approved' | 'paid';
export type SalesLeadBackendProcessingStatus = 'pending' | 'completed';
export type SalesLeadCommerceChannel = 'supplier_local' | 'amazon_affiliate' | 'admin_review';
export type SalesLeadFallbackPolicy = 'amazon_on_no_match_or_timeout' | 'admin_review_only';
export type SalesLeadConversionStatus =
  | 'intent_captured'
  | 'handoff_ready'
  | 'handoff_sent'
  | 'conversion_pending'
  | 'conversion_confirmed'
  | 'commission_realized'
  | 'lost';
export type SalesLeadWhatsAppState = 'not_ready' | 'ready' | 'shared' | 'failed';
export type LeadAffiliateProvider = 'amazon';
export type LeadAffiliateCandidateStatus = 'stub_pending_provider' | 'provider_ready' | 'shared' | 'rejected';
export type SalesLeadHandoffChannel = 'app' | 'whatsapp';
export type SalesLeadRoutingStatus =
  | 'initiated'
  | 'suggested_for_supplier'
  | 'ready_for_assignment'
  | 'supplier_pending'
  | 'supplier_accepted'
  | 'supplier_rejected'
  | 'supplier_timeout'
  | 'admin_queue'
  | 'admin_claimed'
  | 'admin_closed';
export type SalesLeadReviewStatus =
  | 'pending_recommendation'
  | 'pending_admin_review'
  | 'reviewed'
  | 'assigned_to_supplier'
  | 'retained_for_admin';
export type SalesLeadRecommendationStatus = 'pending' | 'ready' | 'needs_admin_review' | 'no_match';
export type SalesLeadSupplierVisibility = 'hidden' | 'masked' | 'unlocked';

export interface LeadLocationSnapshot {
  district?: string;
  districtKey?: string;
  tehsil?: string;
  tehsilKey?: string;
  village?: string;
  villageKey?: string;
}

export interface LeadSupplierSnapshot {
  supplierId: string;
  businessName?: string;
  districtId?: string;
  districtName?: string;
  matchScore?: number | null;
  matchSummary?: string | null;
  source?: 'system' | 'admin';
  selectedByUid?: string;
  selectedByEmail?: string;
  selectedAt?: string;
  assignedAt?: string;
}

export interface LeadCommissionPreview {
  category?: string;
  amount?: number | null;
  currency?: string;
  ruleId?: string | null;
}

export interface LeadAffiliateCandidate {
  provider: LeadAffiliateProvider;
  providerStatus?: LeadAffiliateCandidateStatus;
  reason?: string | null;
  stubbed?: boolean;
  productName?: string | null;
  normalizedProductName?: string | null;
  leadCategory?: string | null;
  searchQuery?: string | null;
  asin?: string | null;
  specialLink?: string | null;
  matchedTitle?: string | null;
  imageUrl?: string | null;
  priceAmount?: number | null;
  priceCurrency?: string | null;
  priceDisplay?: string | null;
  marketplace?: string | null;
  searchIndex?: string | null;
  languageOfPreference?: string | null;
}

export interface CommissionMonthSummary {
  monthKey: string;
  approvedCount: number;
  approvedTotal: number;
  paidCount: number;
  paidTotal: number;
  outstandingTotal: number;
  currency?: string;
  updatedAt?: string;
}

export interface FarmerProfileSnapshot {
  name?: string;
  village?: string;
  tehsil?: string;
  district?: string;
  totalFarmAcres?: number;
  mobileNumber?: string;
  phoneNumber?: string; // deprecated legacy alias; prefer mobileNumber
  emailId?: string;
  email?: string;
}

export interface SalesPipelineLead {
  id: string;
  userId: string;
  conversationId: string;
  requestNumber: string;
  status: SalesLeadStatus;
  source?: string;
  dedupeKey: string;
  productName: string;
  normalizedProductName: string;
  quantity?: string | null;
  unit?: string | null;
  chatMessageText: string;
  farmerProfileSnapshot?: FarmerProfileSnapshot;
  leadCategory?: string;
  leadLocation?: LeadLocationSnapshot;
  routingStatus?: SalesLeadRoutingStatus;
  reviewStatus?: SalesLeadReviewStatus;
  recommendationStatus?: SalesLeadRecommendationStatus;
  supplierVisibility?: SalesLeadSupplierVisibility;
  commerceChannel?: SalesLeadCommerceChannel;
  channelDecisionReason?: string | null;
  fallbackPolicy?: SalesLeadFallbackPolicy | string | null;
  affiliateProvider?: LeadAffiliateProvider | null;
  affiliateCandidate?: LeadAffiliateCandidate | null;
  amazonAsin?: string | null;
  amazonSearchQuery?: string | null;
  amazonSpecialLink?: string | null;
  amazonContentRefreshedAt?: string;
  affiliateDisclosureRequired?: boolean;
  conversionStatus?: SalesLeadConversionStatus;
  whatsappState?: SalesLeadWhatsAppState;
  fallbackTriggeredAt?: string;
  appMessageSentAt?: string;
  appMessageSentByUid?: string | null;
  appMessageSentByEmail?: string | null;
  whatsappPreparedAt?: string;
  whatsappPreparedByUid?: string | null;
  whatsappPreparedByEmail?: string | null;
  lastHandoffChannel?: SalesLeadHandoffChannel | null;
  lastHandoffMessagePreview?: string | null;
  suggestedSupplier?: LeadSupplierSnapshot | null;
  selectedSupplier?: LeadSupplierSnapshot | null;
  assignedSupplier?: LeadSupplierSnapshot | null;
  commissionPreview?: LeadCommissionPreview;
  commissionStatus?: SalesLeadCommissionStatus;
  commissionLedgerEntryId?: string | null;
  commissionMonthKey?: string | null;
  commissionApprovedAt?: string;
  commissionApprovedByUid?: string | null;
  commissionApprovedByEmail?: string | null;
  commissionPaidAt?: string;
  commissionPaidByUid?: string | null;
  commissionPaidByEmail?: string | null;
  backendProcessingStatus?: SalesLeadBackendProcessingStatus;
  backendProcessedAt?: string;
  backendProcessedByUid?: string | null;
  backendProcessedByEmail?: string | null;
  closedAt?: string;
  closedByUid?: string | null;
  closedByEmail?: string | null;
  closedReason?: string | null;
  suggestionGeneratedAt?: string;
  assignmentPublishedAt?: string;
  supplierResponseDeadlineAt?: string;
  supplierRespondedAt?: string;
  supplierRejectedReason?: string | null;
  adminFallbackReason?: string | null;
  lastRoutingUpdatedAt?: string;
  opsStatus?: SalesLeadOpsStatus;
  opsOwnerUid?: string;
  opsOwnerEmail?: string;
  opsNotes?: string;
  firstOpsContactAt?: string;
  lastOpsActionAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SupplierProfile {
  ownerUid: string;
  canonicalSupplierId?: string;
  mergedIntoSupplierId?: string | null;
  businessName: string;
  phone?: string;
  districtId: string;
  districtName?: string;
  verificationStatus: VerificationStatus;
  tehsilCoverage?: string[];
  villageCoverage?: string[];
  serviceCategories?: string[];
  productKeywords?: string[];
  leadIntakeEnabled?: boolean;
  maxOpenLeads?: number;
  capacityNotes?: string;
  approvedAt?: string; // ISO string or Firestore timestamp when fetched
  approvalNote?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Ingredient {
  name: string;
  concentration?: number;
  unit?: string; // %, g/l, kg, etc.
}

export interface Offer {
  id?: string;
  supplierId: string;
  supplierName?: string;
  supplierApproved?: boolean;
  districtId: string;
  category: string; // fertilizer, pesticide, seed, other
  productName: string;
  brand?: string;
  npkRaw?: string; // e.g., "26:26:0"
  npkKey?: string; // normalized e.g., "26-26-0"
  keywords?: string[];
  ingredients?: Ingredient[];
  ingredientKeywords?: string[];
  priceRetail?: number;
  priceGroup?: number;
  priceWholesale?: number;
  priceNormalized?: number; // per-kg or per-litre for comparisons
  packSize?: number;
  packUnit?: string; // kg, l, ml, g
  seasonStart?: string;
  seasonEnd?: string;
  status: OfferStatus;
  imagePath?: string;
  createdAt?: string;
  updatedAt?: string;
  activatedAt?: string;
}
