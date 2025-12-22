export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type OfferStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'EXPIRED';

export interface SupplierProfile {
  ownerUid: string;
  businessName: string;
  phone?: string;
  districtId: string;
  districtName?: string;
  verificationStatus: VerificationStatus;
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
