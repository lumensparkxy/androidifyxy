export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type OfferStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'EXPIRED';
export type SalesLeadStatus = 'initiated';
export type SalesLeadOpsStatus = 'new' | 'claimed' | 'called' | 'qualified' | 'callback' | 'lost';
export type SalesLeadCommissionStatus = 'preview' | 'approved' | 'paid';
export type SalesLeadBackendProcessingStatus = 'pending' | 'completed';
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
  phoneNumber?: string;
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
