"use client";

import { useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, signInWithPopup, signOut, User } from 'firebase/auth';
import { httpsCallable } from 'firebase/functions';
import {
  collection,
  doc,
  getDocs,
  getDoc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  Timestamp,
  updateDoc,
  where,
} from 'firebase/firestore';
import {
  AlertCircle,
  RefreshCcw,
  CheckSquare,
  Pencil,
  Loader2,
  LogOut,
  Mail,
  Sparkles,
  X,
  Phone,
  ShieldCheck,
  Sprout,
  Wand2,
} from 'lucide-react';
import { auth, db, functions, googleProvider } from '@/lib/firebase';
import { Button, Card, Container } from '@/components';
import type {
  CommissionMonthSummary,
  SalesLeadBackendProcessingStatus,
  SalesLeadCommissionStatus,
  LeadCommissionPreview,
  LeadLocationSnapshot,
  LeadSupplierSnapshot,
  SalesLeadOpsStatus,
  SalesLeadRecommendationStatus,
  SalesLeadReviewStatus,
  SalesLeadRoutingStatus,
  SalesLeadSupplierVisibility,
  SalesPipelineLead,
  SupplierProfile,
} from '@/lib/types';

type AdminGateState = 'checking' | 'allowed' | 'forbidden' | 'signed-out';

type AdminSupplierOption = SupplierProfile & { id: string };

const PRIVILEGED_ADMIN_EMAILS = new Set(['maswadkar@gmail.com', 'neophilex@gmail.com']);

function formatUser(user: User | null): string {
  if (!user) return '';
  return user.displayName || user.email || user.phoneNumber || 'Admin';
}

function formatDate(value?: string): string {
  if (!value) return 'Pending timestamp';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function convertTimestamp(value: unknown): string | undefined {
  if (!value) return undefined;
  if (typeof value === 'string') return value;
  if (value instanceof Timestamp) return value.toDate().toISOString();

  if (
    typeof value === 'object'
    && value !== null
    && 'toDate' in value
    && typeof (value as { toDate: () => Date }).toDate === 'function'
  ) {
    return (value as { toDate: () => Date }).toDate().toISOString();
  }

  return undefined;
}

function toLocationSnapshot(value: unknown): LeadLocationSnapshot | undefined {
  return typeof value === 'object' && value !== null
    ? value as LeadLocationSnapshot
    : undefined;
}

function toSupplierSnapshot(value: unknown): LeadSupplierSnapshot | null | undefined {
  if (value === null) return null;
  return typeof value === 'object' && value !== null
    ? value as LeadSupplierSnapshot
    : undefined;
}

function toCommissionPreview(value: unknown): LeadCommissionPreview | undefined {
  return typeof value === 'object' && value !== null
    ? value as LeadCommissionPreview
    : undefined;
}

function toFarmerProfileSnapshot(value: unknown): SalesPipelineLead['farmerProfileSnapshot'] | undefined {
  if (typeof value !== 'object' || value === null) {
    return undefined;
  }

  const snapshot = value as Record<string, unknown>;
  const phoneNumber = typeof snapshot.phoneNumber === 'string'
    ? snapshot.phoneNumber
    : typeof snapshot.mobileNumber === 'string'
      ? snapshot.mobileNumber
      : undefined;
  const email = typeof snapshot.email === 'string'
    ? snapshot.email
    : typeof snapshot.emailId === 'string'
      ? snapshot.emailId
      : undefined;

  return {
    ...(snapshot as SalesPipelineLead['farmerProfileSnapshot']),
    phoneNumber,
    mobileNumber: typeof snapshot.mobileNumber === 'string' ? snapshot.mobileNumber : phoneNumber,
    email,
    emailId: typeof snapshot.emailId === 'string' ? snapshot.emailId : email,
  };
}

function toCommissionMonthSummary(id: string, data: Record<string, unknown>): CommissionMonthSummary {
  return {
    monthKey: typeof data.monthKey === 'string' ? data.monthKey : id,
    approvedCount: typeof data.approvedCount === 'number' ? data.approvedCount : 0,
    approvedTotal: typeof data.approvedTotal === 'number' ? data.approvedTotal : 0,
    paidCount: typeof data.paidCount === 'number' ? data.paidCount : 0,
    paidTotal: typeof data.paidTotal === 'number' ? data.paidTotal : 0,
    outstandingTotal: typeof data.outstandingTotal === 'number' ? data.outstandingTotal : 0,
    currency: typeof data.currency === 'string' ? data.currency : 'INR',
    updatedAt: convertTimestamp(data.updatedAt),
  };
}

function toSupplierProfile(id: string, data: Record<string, unknown>): AdminSupplierOption {
  return {
    id,
    ownerUid: typeof data.ownerUid === 'string' ? data.ownerUid : id,
    canonicalSupplierId: typeof data.canonicalSupplierId === 'string' ? data.canonicalSupplierId : undefined,
    mergedIntoSupplierId: typeof data.mergedIntoSupplierId === 'string' ? data.mergedIntoSupplierId : null,
    businessName: typeof data.businessName === 'string' ? data.businessName : 'Unnamed supplier',
    phone: typeof data.phone === 'string' ? data.phone : undefined,
    districtId: typeof data.districtId === 'string' ? data.districtId : '',
    districtName: typeof data.districtName === 'string' ? data.districtName : undefined,
    verificationStatus: (data.verificationStatus as SupplierProfile['verificationStatus']) || 'PENDING',
    tehsilCoverage: Array.isArray(data.tehsilCoverage) ? data.tehsilCoverage.filter((value): value is string => typeof value === 'string') : undefined,
    villageCoverage: Array.isArray(data.villageCoverage) ? data.villageCoverage.filter((value): value is string => typeof value === 'string') : undefined,
    serviceCategories: Array.isArray(data.serviceCategories) ? data.serviceCategories.filter((value): value is string => typeof value === 'string') : undefined,
    productKeywords: Array.isArray(data.productKeywords) ? data.productKeywords.filter((value): value is string => typeof value === 'string') : undefined,
    leadIntakeEnabled: typeof data.leadIntakeEnabled === 'boolean' ? data.leadIntakeEnabled : undefined,
    maxOpenLeads: typeof data.maxOpenLeads === 'number' ? data.maxOpenLeads : undefined,
    capacityNotes: typeof data.capacityNotes === 'string' ? data.capacityNotes : undefined,
    approvedAt: convertTimestamp(data.approvedAt),
    approvalNote: typeof data.approvalNote === 'string' ? data.approvalNote : undefined,
    createdAt: convertTimestamp(data.createdAt),
    updatedAt: convertTimestamp(data.updatedAt),
  };
}

function normalizeSupplierPhone(value?: string | null): string {
  if (!value) return '';
  const compact = value.replace(/\s+/g, '');
  return compact.startsWith('+') ? compact : `+91${compact}`;
}

function getSupplierIdentityTimestamp(supplier: AdminSupplierOption): number {
  const value = supplier.updatedAt || supplier.createdAt;
  if (!value) return 0;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function getSupplierIdentityScore(supplier: AdminSupplierOption): number {
  let score = 0;
  if (supplier.canonicalSupplierId && supplier.id === supplier.canonicalSupplierId) score += 100;
  if (!supplier.mergedIntoSupplierId) score += 10;
  if (supplier.verificationStatus === 'APPROVED') score += 5;
  return score;
}

function dedupeApprovedSuppliers(suppliers: AdminSupplierOption[]): AdminSupplierOption[] {
  const grouped = new Map<string, AdminSupplierOption[]>();

  suppliers.forEach((supplier) => {
    const identityKey =
      supplier.canonicalSupplierId ||
      normalizeSupplierPhone(supplier.phone) ||
      supplier.id;
    const existing = grouped.get(identityKey) || [];
    existing.push(supplier);
    grouped.set(identityKey, existing);
  });

  return [...grouped.values()]
    .map((group) =>
      [...group].sort((left, right) => {
        const scoreDifference = getSupplierIdentityScore(right) - getSupplierIdentityScore(left);
        if (scoreDifference !== 0) return scoreDifference;

        const timeDifference = getSupplierIdentityTimestamp(right) - getSupplierIdentityTimestamp(left);
        if (timeDifference !== 0) return timeDifference;

        return left.businessName.localeCompare(right.businessName);
      })[0]
    )
    .filter(Boolean);
}

function normalizeLookupValue(value?: string | null): string {
  return (value || '')
    .toLowerCase()
    .replace(/^mh:/, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function getSupplierAssignmentScore(lead: SalesPipelineLead, supplier: AdminSupplierOption): number {
  const leadDistrict = normalizeLookupValue(lead.leadLocation?.district || lead.farmerProfileSnapshot?.district);
  const supplierDistrict = normalizeLookupValue(supplier.districtName || supplier.districtId);
  const category = normalizeLookupValue(lead.leadCategory);
  const categories = (supplier.serviceCategories || []).map((value) => normalizeLookupValue(value));

  let score = 0;
  if (supplier.leadIntakeEnabled !== false) score += 1;
  if (leadDistrict && supplierDistrict && leadDistrict === supplierDistrict) score += 4;
  if (category && categories.includes(category)) score += 3;
  return score;
}

function getSupplierAssignmentHint(lead: SalesPipelineLead, supplier: AdminSupplierOption): string {
  const leadDistrict = normalizeLookupValue(lead.leadLocation?.district || lead.farmerProfileSnapshot?.district);
  const supplierDistrict = normalizeLookupValue(supplier.districtName || supplier.districtId);
  const category = normalizeLookupValue(lead.leadCategory);
  const categories = (supplier.serviceCategories || []).map((value) => normalizeLookupValue(value));
  const hints: string[] = [];

  if (leadDistrict && supplierDistrict && leadDistrict === supplierDistrict) {
    hints.push('district match');
  }
  if (category && categories.includes(category)) {
    hints.push(`${category} supplier`);
  }
  if (supplier.leadIntakeEnabled === false) {
    hints.push('intake paused');
  }

  return hints.join(' · ');
}

function toLead(id: string, data: Record<string, unknown>): SalesPipelineLead {
  return {
    id,
    userId: String(data.userId || ''),
    conversationId: String(data.conversationId || ''),
    requestNumber: String(data.requestNumber || ''),
    status: (data.status as SalesPipelineLead['status']) || 'initiated',
    source: typeof data.source === 'string' ? data.source : undefined,
    dedupeKey: String(data.dedupeKey || ''),
    productName: String(data.productName || ''),
    normalizedProductName: String(data.normalizedProductName || ''),
    quantity: typeof data.quantity === 'string' ? data.quantity : null,
    unit: typeof data.unit === 'string' ? data.unit : null,
    chatMessageText: String(data.chatMessageText || ''),
    farmerProfileSnapshot: toFarmerProfileSnapshot(data.farmerProfileSnapshot),
    leadCategory: typeof data.leadCategory === 'string' ? data.leadCategory : undefined,
    leadLocation: toLocationSnapshot(data.leadLocation),
    routingStatus: typeof data.routingStatus === 'string' ? data.routingStatus as SalesLeadRoutingStatus : undefined,
    reviewStatus: typeof data.reviewStatus === 'string' ? data.reviewStatus as SalesLeadReviewStatus : undefined,
    recommendationStatus:
      typeof data.recommendationStatus === 'string'
        ? data.recommendationStatus as SalesLeadRecommendationStatus
        : undefined,
    supplierVisibility:
      typeof data.supplierVisibility === 'string'
        ? data.supplierVisibility as SalesLeadSupplierVisibility
        : undefined,
    suggestedSupplier: toSupplierSnapshot(data.suggestedSupplier),
    selectedSupplier: toSupplierSnapshot(data.selectedSupplier),
    assignedSupplier: toSupplierSnapshot(data.assignedSupplier),
    commissionPreview: toCommissionPreview(data.commissionPreview),
    commissionStatus: typeof data.commissionStatus === 'string' ? data.commissionStatus as SalesLeadCommissionStatus : 'preview',
    commissionLedgerEntryId: typeof data.commissionLedgerEntryId === 'string' ? data.commissionLedgerEntryId : null,
    commissionMonthKey: typeof data.commissionMonthKey === 'string' ? data.commissionMonthKey : null,
    commissionApprovedAt: convertTimestamp(data.commissionApprovedAt),
    commissionApprovedByUid: typeof data.commissionApprovedByUid === 'string' ? data.commissionApprovedByUid : null,
    commissionApprovedByEmail: typeof data.commissionApprovedByEmail === 'string' ? data.commissionApprovedByEmail : null,
    commissionPaidAt: convertTimestamp(data.commissionPaidAt),
    commissionPaidByUid: typeof data.commissionPaidByUid === 'string' ? data.commissionPaidByUid : null,
    commissionPaidByEmail: typeof data.commissionPaidByEmail === 'string' ? data.commissionPaidByEmail : null,
    backendProcessingStatus:
      typeof data.backendProcessingStatus === 'string'
        ? data.backendProcessingStatus as SalesLeadBackendProcessingStatus
        : 'pending',
    backendProcessedAt: convertTimestamp(data.backendProcessedAt),
    backendProcessedByUid: typeof data.backendProcessedByUid === 'string' ? data.backendProcessedByUid : null,
    backendProcessedByEmail: typeof data.backendProcessedByEmail === 'string' ? data.backendProcessedByEmail : null,
    closedAt: convertTimestamp(data.closedAt),
    closedByUid: typeof data.closedByUid === 'string' ? data.closedByUid : null,
    closedByEmail: typeof data.closedByEmail === 'string' ? data.closedByEmail : null,
    closedReason: typeof data.closedReason === 'string' ? data.closedReason : null,
    suggestionGeneratedAt: convertTimestamp(data.suggestionGeneratedAt),
    assignmentPublishedAt: convertTimestamp(data.assignmentPublishedAt),
    supplierResponseDeadlineAt: convertTimestamp(data.supplierResponseDeadlineAt),
    supplierRespondedAt: convertTimestamp(data.supplierRespondedAt),
    supplierRejectedReason: typeof data.supplierRejectedReason === 'string' ? data.supplierRejectedReason : null,
    adminFallbackReason: typeof data.adminFallbackReason === 'string' ? data.adminFallbackReason : null,
    lastRoutingUpdatedAt: convertTimestamp(data.lastRoutingUpdatedAt),
    opsStatus: typeof data.opsStatus === 'string' ? data.opsStatus as SalesLeadOpsStatus : 'new',
    opsOwnerUid: typeof data.opsOwnerUid === 'string' ? data.opsOwnerUid : undefined,
    opsOwnerEmail: typeof data.opsOwnerEmail === 'string' ? data.opsOwnerEmail : undefined,
    opsNotes: typeof data.opsNotes === 'string' ? data.opsNotes : undefined,
    firstOpsContactAt: convertTimestamp(data.firstOpsContactAt),
    lastOpsActionAt: convertTimestamp(data.lastOpsActionAt),
    createdAt: convertTimestamp(data.createdAt),
    updatedAt: convertTimestamp(data.updatedAt),
  };
}

const OPS_STATUS_LABELS: Record<SalesLeadOpsStatus, string> = {
  new: 'New',
  claimed: 'Claimed',
  called: 'Called',
  qualified: 'Qualified',
  callback: 'Callback',
  lost: 'Lost',
};

const OPS_STATUS_STYLES: Record<SalesLeadOpsStatus, string> = {
  new: 'bg-slate-100 text-slate-700',
  claimed: 'bg-blue-100 text-blue-700',
  called: 'bg-indigo-100 text-indigo-700',
  qualified: 'bg-emerald-100 text-emerald-700',
  callback: 'bg-amber-100 text-amber-700',
  lost: 'bg-rose-100 text-rose-700',
};

const ROUTING_STATUS_LABELS: Record<SalesLeadRoutingStatus, string> = {
  initiated: 'Initiated',
  suggested_for_supplier: 'Suggested',
  ready_for_assignment: 'Ready',
  supplier_pending: 'Supplier Pending',
  supplier_accepted: 'Supplier Accepted',
  supplier_rejected: 'Supplier Rejected',
  supplier_timeout: 'Supplier Timeout',
  admin_queue: 'Admin Queue',
  admin_claimed: 'Admin Claimed',
  admin_closed: 'Closed',
};

const ROUTING_STATUS_STYLES: Record<SalesLeadRoutingStatus, string> = {
  initiated: 'bg-slate-100 text-slate-700',
  suggested_for_supplier: 'bg-sky-100 text-sky-700',
  ready_for_assignment: 'bg-cyan-100 text-cyan-700',
  supplier_pending: 'bg-violet-100 text-violet-700',
  supplier_accepted: 'bg-emerald-100 text-emerald-700',
  supplier_rejected: 'bg-rose-100 text-rose-700',
  supplier_timeout: 'bg-amber-100 text-amber-700',
  admin_queue: 'bg-orange-100 text-orange-700',
  admin_claimed: 'bg-blue-100 text-blue-700',
  admin_closed: 'bg-gray-100 text-gray-700',
};

const RECOMMENDATION_STATUS_LABELS: Record<SalesLeadRecommendationStatus, string> = {
  pending: 'Pending',
  ready: 'Ready',
  needs_admin_review: 'Needs Review',
  no_match: 'No Match',
};

const RECOMMENDATION_STATUS_STYLES: Record<SalesLeadRecommendationStatus, string> = {
  pending: 'bg-slate-100 text-slate-700',
  ready: 'bg-emerald-100 text-emerald-700',
  needs_admin_review: 'bg-amber-100 text-amber-700',
  no_match: 'bg-rose-100 text-rose-700',
};

const COMMISSION_STATUS_LABELS: Record<SalesLeadCommissionStatus, string> = {
  preview: 'Preview',
  approved: 'Approved',
  paid: 'Paid',
};

const COMMISSION_STATUS_STYLES: Record<SalesLeadCommissionStatus, string> = {
  preview: 'bg-slate-100 text-slate-700',
  approved: 'bg-blue-100 text-blue-700',
  paid: 'bg-emerald-100 text-emerald-700',
};

function getLeadDistrict(lead: SalesPipelineLead): string {
  return lead.leadLocation?.district || lead.farmerProfileSnapshot?.district || 'Unknown district';
}

function getLeadVillageTehsil(lead: SalesPipelineLead): string {
  return [
    lead.leadLocation?.village || lead.farmerProfileSnapshot?.village,
    lead.leadLocation?.tehsil || lead.farmerProfileSnapshot?.tehsil,
  ].filter(Boolean).join(', ') || 'Not available';
}

function getLeadPhone(lead: SalesPipelineLead): string | undefined {
  return lead.farmerProfileSnapshot?.phoneNumber || lead.farmerProfileSnapshot?.mobileNumber;
}

function formatCommission(commission?: LeadCommissionPreview): string {
  if (!commission || commission.amount == null) return 'Pending rule';
  return `₹${commission.amount}${commission.category ? ` · ${commission.category}` : ''}`;
}

function formatCurrencyAmount(amount?: number | null, currency = 'INR'): string {
  const safeAmount = typeof amount === 'number' && Number.isFinite(amount) ? amount : 0;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(safeAmount);
}

function getCurrentMonthKey(): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
  });
  const parts = formatter.formatToParts(new Date());
  const year = parts.find((part) => part.type === 'year')?.value || '0000';
  const month = parts.find((part) => part.type === 'month')?.value || '01';
  return `${year}-${month}`;
}

function formatMonthKey(monthKey?: string | null): string {
  if (!monthKey) return 'Current month';
  const [year, month] = monthKey.split('-');
  const parsed = new Date(`${year}-${month || '01'}-01T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return monthKey;
  return new Intl.DateTimeFormat('en-IN', {
    month: 'long',
    year: 'numeric',
  }).format(parsed);
}

function getRecommendedSupplierLabel(lead: SalesPipelineLead): string {
  if (lead.selectedSupplier?.businessName) return `${lead.selectedSupplier.businessName} (admin selected)`;
  if (lead.suggestedSupplier?.businessName) return lead.suggestedSupplier.businessName;
  return lead.adminFallbackReason === 'no_matching_supplier' ? 'No matching supplier' : 'Pending recommendation';
}

function getSupplierSnapshotLabel(snapshot?: LeadSupplierSnapshot | null): string {
  if (!snapshot) return 'Not set';

  const parts = [snapshot.businessName, snapshot.districtName || snapshot.districtId].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : snapshot.supplierId;
}

function isRecommendationPending(lead: SalesPipelineLead): boolean {
  return !lead.recommendationStatus || lead.recommendationStatus === 'pending' || lead.reviewStatus === 'pending_recommendation';
}

type PipelineStage = 'all' | 'needs_action' | 'supplier_pending' | 'accepted' | 'commission' | 'closed';

const PIPELINE_STAGE_LABELS: Record<PipelineStage, string> = {
  all: 'All',
  needs_action: 'Needs action',
  supplier_pending: 'Supplier pending',
  accepted: 'Accepted',
  commission: 'Commission',
  closed: 'Closed',
};

function getLeadPipelineStage(lead: SalesPipelineLead): PipelineStage {
  const routing = lead.routingStatus || 'initiated';
  if (routing === 'admin_closed') return 'closed';
  if (routing === 'supplier_accepted' || routing === 'admin_claimed') {
    const commission = lead.commissionStatus || 'preview';
    if (commission === 'approved' || commission === 'paid') return 'commission';
    return 'accepted';
  }
  if (routing === 'supplier_pending') return 'supplier_pending';
  return 'needs_action';
}

function getLeadPipelineBadge(lead: SalesPipelineLead): { label: string; className: string } {
  const routing = lead.routingStatus || 'initiated';
  switch (routing) {
    case 'initiated':
      return { label: 'New', className: 'bg-slate-100 text-slate-700' };
    case 'suggested_for_supplier':
      return { label: 'Suggested', className: 'bg-sky-100 text-sky-700' };
    case 'admin_queue':
      return { label: 'Admin queue', className: 'bg-orange-100 text-orange-700' };
    case 'supplier_pending':
      return { label: 'Supplier pending', className: 'bg-violet-100 text-violet-700' };
    case 'supplier_accepted': {
      const commission = lead.commissionStatus || 'preview';
      if (commission === 'paid') return { label: 'Paid', className: 'bg-emerald-100 text-emerald-700' };
      if (commission === 'approved') return { label: 'Approved', className: 'bg-blue-100 text-blue-700' };
      return { label: 'Accepted', className: 'bg-emerald-100 text-emerald-700' };
    }
    case 'supplier_rejected':
      return { label: 'Rejected', className: 'bg-rose-100 text-rose-700' };
    case 'supplier_timeout':
      return { label: 'Timeout', className: 'bg-amber-100 text-amber-700' };
    case 'admin_claimed': {
      const commission = lead.commissionStatus || 'preview';
      if (lead.backendProcessingStatus === 'completed') return { label: 'Processed', className: 'bg-teal-100 text-teal-700' };
      if (commission === 'paid') return { label: 'Paid', className: 'bg-emerald-100 text-emerald-700' };
      if (commission === 'approved') return { label: 'Approved', className: 'bg-blue-100 text-blue-700' };
      return { label: 'Claimed', className: 'bg-blue-100 text-blue-700' };
    }
    case 'admin_closed':
      return { label: 'Closed', className: 'bg-gray-100 text-gray-500' };
    default:
      return { label: routing, className: 'bg-slate-100 text-slate-700' };
  }
}

export default function AdminLeadsClient() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [gateState, setGateState] = useState<AdminGateState>('checking');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [leads, setLeads] = useState<SalesPipelineLead[]>([]);
  const [commissionSummaries, setCommissionSummaries] = useState<CommissionMonthSummary[]>([]);
  const [approvedSuppliers, setApprovedSuppliers] = useState<AdminSupplierOption[]>([]);
  const [suppliersLoading, setSuppliersLoading] = useState(false);
  const [actionLeadId, setActionLeadId] = useState<string | null>(null);
  const [editingLead, setEditingLead] = useState<SalesPipelineLead | null>(null);
  const [selectedLeadIds, setSelectedLeadIds] = useState<string[]>([]);
  const [recoveryBusy, setRecoveryBusy] = useState(false);
  const [bulkAssignBusy, setBulkAssignBusy] = useState(false);
  const [bulkAssignSupplierId, setBulkAssignSupplierId] = useState('');
  const [stageFilter, setStageFilter] = useState<PipelineStage>('all');
  const [editForm, setEditForm] = useState({
    opsStatus: 'new' as SalesLeadOpsStatus,
    quantity: '',
    unit: '',
    opsNotes: '',
    assignToMe: true,
    selectedSupplierId: '',
  });

  useEffect(() => {
    if (!auth) {
      setAuthLoading(false);
      setGateState('signed-out');
      return;
    }

    const unsubscribe = onAuthStateChanged(auth, async (nextUser) => {
      setUser(nextUser);
      setAuthLoading(false);
      setError(null);

      if (!nextUser || !db) {
        setGateState('signed-out');
        setLeads([]);
        return;
      }

      if (nextUser.email && PRIVILEGED_ADMIN_EMAILS.has(nextUser.email.toLowerCase())) {
        setGateState('allowed');
        return;
      }

      setGateState('checking');
      try {
        const adminSnap = await getDoc(doc(db, 'admin_users', nextUser.uid));
        setGateState(adminSnap.exists() ? 'allowed' : 'forbidden');
      } catch (adminError) {
        setGateState('forbidden');
        setError(adminError instanceof Error ? adminError.message : 'Failed to verify admin access');
      }
    });

    return unsubscribe;
  }, []);

  useEffect(() => {
    if (!db || gateState !== 'allowed') {
      if (gateState !== 'allowed') {
        setLeads([]);
      }
      return;
    }

    const leadsQuery = query(
      collection(db, 'sales_pipeline'),
      orderBy('createdAt', 'desc'),
      limit(50),
    );

    const unsubscribe = onSnapshot(
      leadsQuery,
      (snapshot) => {
        setLeads(snapshot.docs.map((leadDoc) => toLead(leadDoc.id, leadDoc.data() as Record<string, unknown>)));
      },
      (snapshotError) => {
        setError(snapshotError.message || 'Failed to load leads');
      },
    );

    return unsubscribe;
  }, [gateState]);

  useEffect(() => {
    if (!db || gateState !== 'allowed') {
      if (gateState !== 'allowed') {
        setApprovedSuppliers([]);
      }
      return;
    }

    let cancelled = false;
    setSuppliersLoading(true);

    getDocs(query(collection(db, 'suppliers'), where('verificationStatus', '==', 'APPROVED')))
      .then((snapshot) => {
        if (cancelled) return;
        const nextSuppliers = dedupeApprovedSuppliers(
          snapshot.docs.map((supplierDoc) => toSupplierProfile(supplierDoc.id, supplierDoc.data() as Record<string, unknown>))
        )
          .sort((left, right) => left.businessName.localeCompare(right.businessName));
        setApprovedSuppliers(nextSuppliers);
      })
      .catch((supplierError) => {
        if (!cancelled) {
          setError(supplierError instanceof Error ? supplierError.message : 'Failed to load approved suppliers');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setSuppliersLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [gateState]);

  useEffect(() => {
    if (!db || gateState !== 'allowed') {
      if (gateState !== 'allowed') {
        setCommissionSummaries([]);
      }
      return;
    }

    const summaryQuery = query(
      collection(db, 'commission_monthly'),
      orderBy('monthKey', 'desc'),
      limit(6),
    );

    const unsubscribe = onSnapshot(
      summaryQuery,
      (snapshot) => {
        setCommissionSummaries(snapshot.docs.map((summaryDoc) => (
          toCommissionMonthSummary(summaryDoc.id, summaryDoc.data() as Record<string, unknown>)
        )));
      },
      (summaryError) => {
        setError(summaryError.message || 'Failed to load commission summaries');
      },
    );

    return unsubscribe;
  }, [gateState]);

  useEffect(() => {
    if (!editingLead) return;
    const latestLead = leads.find((lead) => lead.id === editingLead.id);
    const hasLeadChanged = Boolean(
      latestLead
      && (
        latestLead.updatedAt !== editingLead.updatedAt
        || latestLead.lastRoutingUpdatedAt !== editingLead.lastRoutingUpdatedAt
        || latestLead.commissionStatus !== editingLead.commissionStatus
        || latestLead.backendProcessingStatus !== editingLead.backendProcessingStatus
        || latestLead.closedAt !== editingLead.closedAt
      )
    );
    if (latestLead && hasLeadChanged) {
      setEditingLead(latestLead);
    }
  }, [leads, editingLead]);

  const handleGoogleSignIn = async () => {
    if (!auth || !googleProvider) return;

    setError(null);
    setBusy(true);
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (signInError: unknown) {
      const firebaseError = signInError as { code?: string };
      if (firebaseError.code !== 'auth/popup-closed-by-user') {
        setError(signInError instanceof Error ? signInError.message : 'Google sign-in failed');
      }
    } finally {
      setBusy(false);
    }
  };

  const handleSignOut = async () => {
    if (!auth) return;
    setBusy(true);
    try {
      await signOut(auth);
    } catch (signOutError) {
      setError(signOutError instanceof Error ? signOutError.message : 'Sign out failed');
    } finally {
      setBusy(false);
    }
  };

  const initiatedCount = useMemo(
    () => leads.filter((lead) => lead.status === 'initiated').length,
    [leads],
  );

  const readyRecommendationsCount = useMemo(
    () => leads.filter((lead) => lead.recommendationStatus === 'ready').length,
    [leads],
  );

  const unmatchedCount = useMemo(
    () => leads.filter((lead) => lead.recommendationStatus === 'no_match').length,
    [leads],
  );

  const selectedLeads = useMemo(
    () => leads.filter((lead) => selectedLeadIds.includes(lead.id)),
    [leads, selectedLeadIds],
  );

  const currentMonthSummary = useMemo(() => {
    const monthKey = getCurrentMonthKey();
    return commissionSummaries.find((summary) => summary.monthKey === monthKey) || {
      monthKey,
      approvedCount: 0,
      approvedTotal: 0,
      paidCount: 0,
      paidTotal: 0,
      outstandingTotal: 0,
      currency: 'INR',
    };
  }, [commissionSummaries]);

  const selectedLeadSummary = useMemo(() => {
    if (selectedLeads.length === 0) return 'No leads selected';
    const readyCount = selectedLeads.filter((lead) => lead.recommendationStatus === 'ready').length;
    const unmatched = selectedLeads.length - readyCount;
    return `${selectedLeads.length} selected · ${readyCount} ready · ${unmatched} need review`;
  }, [selectedLeads]);

  const stageCounts = useMemo(() => {
    const counts: Record<PipelineStage, number> = { all: leads.length, needs_action: 0, supplier_pending: 0, accepted: 0, commission: 0, closed: 0 };
    leads.forEach((lead) => { counts[getLeadPipelineStage(lead)] += 1; });
    return counts;
  }, [leads]);

  const filteredLeads = useMemo(
    () => stageFilter === 'all' ? leads : leads.filter((lead) => getLeadPipelineStage(lead) === stageFilter),
    [leads, stageFilter],
  );

  const toggleLeadSelection = (leadId: string) => {
    setSelectedLeadIds((current) => current.includes(leadId)
      ? current.filter((id) => id !== leadId)
      : [...current, leadId]);
  };

  const toggleSelectAllVisible = () => {
    if (selectedLeadIds.length === filteredLeads.length) {
      setSelectedLeadIds([]);
      return;
    }
    setSelectedLeadIds(filteredLeads.map((lead) => lead.id));
  };

  const pendingRecommendationLeadIds = useMemo(
    () => leads
      .filter((lead) => isRecommendationPending(lead))
      .map((lead) => lead.id),
    [leads],
  );

  const rankedSupplierOptions = useMemo(() => {
    if (!editingLead) return [];

    return [...approvedSuppliers].sort((left, right) => {
      const scoreDifference = getSupplierAssignmentScore(editingLead, right) - getSupplierAssignmentScore(editingLead, left);
      if (scoreDifference !== 0) return scoreDifference;
      return left.businessName.localeCompare(right.businessName);
    });
  }, [approvedSuppliers, editingLead]);

  const bulkRankedSupplierOptions = useMemo(() => [...approvedSuppliers].sort((left, right) => {
    const rightScore = selectedLeads.reduce((total, lead) => total + getSupplierAssignmentScore(lead, right), 0);
    const leftScore = selectedLeads.reduce((total, lead) => total + getSupplierAssignmentScore(lead, left), 0);
    const scoreDifference = rightScore - leftScore;
    if (scoreDifference !== 0) return scoreDifference;
    return left.businessName.localeCompare(right.businessName);
  }), [approvedSuppliers, selectedLeads]);

  const openEditModal = (lead: SalesPipelineLead) => {
    setEditingLead(lead);
    setEditForm({
      opsStatus: lead.opsStatus || 'new',
      quantity: lead.quantity || '',
      unit: lead.unit || '',
      opsNotes: lead.opsNotes || '',
      assignToMe: !lead.opsOwnerUid || lead.opsOwnerUid === user?.uid,
      selectedSupplierId: lead.selectedSupplier?.supplierId || lead.assignedSupplier?.supplierId || '',
    });
  };

  const closeEditModal = () => {
    if (actionLeadId) return;
    setEditingLead(null);
  };

  const updateLeadOps = async (leadId: string, payload: Record<string, unknown>) => {
    if (!db || !user) return;

    setError(null);
    setActionLeadId(leadId);
    try {
      await updateDoc(doc(db, 'sales_pipeline', leadId), {
        ...payload,
        updatedAt: serverTimestamp(),
        lastOpsActionAt: serverTimestamp(),
      });
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : 'Failed to update lead');
    } finally {
      setActionLeadId((current) => (current === leadId ? null : current));
    }
  };

  const handleRetryRecommendation = async (leadId: string) => {
    if (!functions) {
      setError('Firebase Functions is not configured in the website client');
      return;
    }

    setError(null);
    setActionLeadId(leadId);
    try {
      const retryCallable = httpsCallable<{ leadId: string }, unknown>(functions, 'retryLeadRecommendation');
      await retryCallable({ leadId });
    } catch (retryError) {
      setError(retryError instanceof Error ? retryError.message : 'Failed to retry recommendation');
    } finally {
      setActionLeadId((current) => (current === leadId ? null : current));
    }
  };

  const handleBackfillPendingRecommendations = async () => {
    if (!functions) {
      setError('Firebase Functions is not configured in the website client');
      return;
    }

    const targetCount = selectedLeadIds.length > 0
      ? selectedLeads.filter((lead) => lead.reviewStatus === 'pending_recommendation').length
      : pendingRecommendationLeadIds.length;
    if (targetCount === 0) {
      setError('There are no pending recommendation leads to backfill right now');
      return;
    }

    setError(null);
    setRecoveryBusy(true);
    try {
      const backfillCallable = httpsCallable<{ limit?: number; leadIds?: string[] }, unknown>(functions, 'backfillPendingLeadRecommendations');
      const pendingSelectedLeadIds = selectedLeads
        .filter((lead) => lead.reviewStatus === 'pending_recommendation')
        .map((lead) => lead.id);
      await backfillCallable(
        pendingSelectedLeadIds.length > 0
          ? { leadIds: pendingSelectedLeadIds }
          : { limit: targetCount }
      );
    } catch (backfillError) {
      setError(backfillError instanceof Error ? backfillError.message : 'Failed to backfill pending recommendations');
    } finally {
      setRecoveryBusy(false);
    }
  };

  const assignLeadsToSupplier = async (leadIds: string[], supplierId: string) => {
    if (!functions) {
      throw new Error('Firebase Functions is not configured in the website client');
    }

    const assignCallable = httpsCallable<{ leadIds: string[]; supplierId: string }, { ok: boolean }>(
      functions,
      'adminAssignLeadsToSupplier',
    );
    await assignCallable({ leadIds, supplierId });
  };

  const handleBulkAssignSelected = async () => {
    if (!user) return;
    if (!bulkAssignSupplierId) {
      setError('Choose a supplier before running bulk assign.');
      return;
    }
    if (selectedLeads.length === 0) {
      setError('Select at least one lead to bulk assign.');
      return;
    }

    const selectedSupplier = approvedSuppliers.find((supplier) => supplier.id === bulkAssignSupplierId);
    if (!selectedSupplier) {
      setError('The selected bulk supplier could not be found. Refresh the page and try again.');
      return;
    }

    setError(null);
    setBulkAssignBusy(true);

    try {
      await assignLeadsToSupplier(selectedLeads.map((lead) => lead.id), selectedSupplier.id);
      setSelectedLeadIds([]);
      setBulkAssignSupplierId('');
    } catch (bulkAssignError) {
      setError(bulkAssignError instanceof Error ? bulkAssignError.message : 'Failed to bulk assign selected leads');
    } finally {
      setBulkAssignBusy(false);
    }
  };

  const handleSubmitEdit = async () => {
    if (!editingLead) return;

    const payload: Record<string, unknown> = {
      opsStatus: editForm.opsStatus,
      quantity: editForm.quantity.trim() || null,
      unit: editForm.unit.trim() || null,
      opsNotes: editForm.opsNotes.trim(),
      opsOwnerUid: editForm.assignToMe ? (user?.uid || null) : (editingLead.opsOwnerUid || null),
      opsOwnerEmail: editForm.assignToMe ? (user?.email || null) : (editingLead.opsOwnerEmail || null),
    };
    let assignedSupplierChanged = false;

    if (editForm.selectedSupplierId) {
      const currentAssignedSupplierId = editingLead.assignedSupplier?.supplierId || editingLead.selectedSupplier?.supplierId || '';
      const selectedSupplier = approvedSuppliers.find((supplier) => supplier.id === editForm.selectedSupplierId);
      if (!selectedSupplier) {
        setError('The selected supplier could not be found. Refresh the page and try again.');
        return;
      }

      if (editForm.selectedSupplierId !== currentAssignedSupplierId) {
        await assignLeadsToSupplier([editingLead.id], selectedSupplier.id);
        assignedSupplierChanged = true;
      }
    }

    if (editForm.opsStatus === 'called' && !editingLead.firstOpsContactAt) {
      payload.firstOpsContactAt = serverTimestamp();
    }

    const normalizedOpsNotes = editForm.opsNotes.trim();
    const targetOwnerUid = editForm.assignToMe ? (user?.uid || null) : (editingLead.opsOwnerUid || null);
    const targetOwnerEmail = editForm.assignToMe ? (user?.email || null) : (editingLead.opsOwnerEmail || null);
    const hasOpsChanges = (
      editForm.opsStatus !== (editingLead.opsStatus || 'new')
      || (editForm.quantity.trim() || null) !== (editingLead.quantity || null)
      || (editForm.unit.trim() || null) !== (editingLead.unit || null)
      || normalizedOpsNotes !== (editingLead.opsNotes || '')
      || targetOwnerUid !== (editingLead.opsOwnerUid || null)
      || targetOwnerEmail !== (editingLead.opsOwnerEmail || null)
      || Boolean(payload.firstOpsContactAt)
    );

    if (hasOpsChanges) {
      await updateLeadOps(editingLead.id, payload);
    } else if (assignedSupplierChanged) {
      setError(null);
    }
    setEditingLead(null);
  };

  const handleLeadWorkflowAction = async (
    workflowAction: 'approve_commission' | 'mark_paid' | 'complete_processing' | 'close_lead',
  ) => {
    if (!functions) {
      setError('Firebase Functions is not configured in the website client');
      return;
    }
    if (!editingLead) {
      return;
    }

    setError(null);
    setActionLeadId(editingLead.id);
    try {
      const workflowCallable = httpsCallable<
        { leadId: string; action: string; closedReason?: string },
        { ok: boolean }
      >(functions, 'adminAdvanceLeadWorkflow');

      await workflowCallable({
        leadId: editingLead.id,
        action: workflowAction,
        closedReason: workflowAction === 'close_lead' ? 'processing_completed' : undefined,
      });
    } catch (workflowError) {
      setError(workflowError instanceof Error ? workflowError.message : 'Failed to advance lead workflow');
    } finally {
      setActionLeadId((current) => (current === editingLead.id ? null : current));
    }
  };

  const editingLeadCommissionStatus = editingLead?.commissionStatus || 'preview';
  const editingLeadBackendProcessingStatus = editingLead?.backendProcessingStatus || 'pending';
  const canApproveCommission = Boolean(
    editingLead
    && ['supplier_accepted', 'admin_claimed'].includes(editingLead.routingStatus || '')
    && editingLeadCommissionStatus === 'preview',
  );
  const canMarkCommissionPaid = Boolean(editingLead && editingLeadCommissionStatus === 'approved');
  const canCompleteProcessing = Boolean(
    editingLead
    && ['supplier_accepted', 'admin_claimed'].includes(editingLead.routingStatus || '')
    && ['approved', 'paid'].includes(editingLeadCommissionStatus)
    && editingLeadBackendProcessingStatus !== 'completed',
  );
  const canCloseLead = Boolean(
    editingLead
    && editingLead.routingStatus !== 'admin_closed'
    && editingLeadBackendProcessingStatus === 'completed'
    && ['approved', 'paid'].includes(editingLeadCommissionStatus),
  );

  const authCard = (
    <Card className="p-6 space-y-4" hover={false}>
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-lg font-semibold text-gray-900">Admin access</p>
          <p className="text-sm text-gray-600">
            Sign in with an allowed admin account. <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded text-gray-700">maswadkar@gmail.com</code> is enabled directly, and other admins can be granted through Firestore <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded text-gray-700">admin_users/&lt;uid&gt;</code>.
          </p>
        </div>
        {(authLoading || busy) && <Loader2 className="w-5 h-5 animate-spin text-primary" />}
      </div>

      {error && (
        <div className="bg-red-50 text-red-700 border border-red-200 rounded-lg px-4 py-3 text-sm">
          {error}
        </div>
      )}

      {gateState === 'signed-out' && (
        <Button
          onClick={handleGoogleSignIn}
          disabled={busy || authLoading}
          className="inline-flex items-center gap-2"
        >
          <Mail className="w-4 h-4" />
          Continue with Google
        </Button>
      )}

      {gateState !== 'signed-out' && (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <ShieldCheck className="w-5 h-5 text-primary" />
            <div>
              <p className="text-sm text-gray-500">Signed in as</p>
              <p className="font-semibold text-gray-900">{formatUser(user)}</p>
            </div>
          </div>
          <Button
            onClick={handleSignOut}
            variant="secondary"
            disabled={busy}
            className="inline-flex items-center gap-2"
          >
            <LogOut className="w-4 h-4" />
            Sign out
          </Button>
        </div>
      )}

      {gateState === 'forbidden' && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 text-sm text-amber-900">
          Your account is signed in, but it is not configured for admin lead access yet. Use
          <code className="text-xs bg-amber-100 px-1.5 py-0.5 rounded text-amber-800">maswadkar@gmail.com</code> or create an <code className="text-xs bg-amber-100 px-1.5 py-0.5 rounded text-amber-800">admin_users/{'{'}user?.uid{'}'}</code> document in Firestore Console to grant access.
        </div>
      )}
    </Card>
  );

  return (
    <Container className="py-16">
      <div className="max-w-6xl mx-auto space-y-8">
        <div className="space-y-3">
          <p className="text-sm font-semibold text-primary uppercase tracking-wide">Admin lead queue</p>
          <h1 className="text-3xl md:text-4xl font-bold text-gray-900">
            Track app-created best-offer leads
          </h1>
          <p className="text-gray-600 max-w-3xl">
            This admin queue supports core ops actions so your team can claim, update, and annotate
            app-created sales pipeline leads directly from the website.
          </p>
        </div>

        {authCard}

        {gateState === 'allowed' && (
          <>
            <div className="grid gap-4 grid-cols-2 lg:grid-cols-4">
              <Card className="p-5 border-l-4 border-l-primary" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Visible leads</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{leads.length}</p>
              </Card>
              <Card className="p-5 border-l-4 border-l-blue-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Initiated</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{initiatedCount}</p>
              </Card>
              <Card className="p-5 border-l-4 border-l-emerald-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Ready recommendations</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{readyRecommendationsCount}</p>
              </Card>
              <Card className="p-5 border-l-4 border-l-amber-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Need admin fallback</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{unmatchedCount}</p>
              </Card>
            </div>

            <div className="grid gap-4 grid-cols-1 sm:grid-cols-3">
              <Card className="p-5 border-l-4 border-l-indigo-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{formatMonthKey(currentMonthSummary.monthKey)} approved</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">
                  {formatCurrencyAmount(currentMonthSummary.approvedTotal, currentMonthSummary.currency)}
                </p>
                <p className="mt-2 text-xs text-gray-400">{currentMonthSummary.approvedCount} approved leads this month</p>
              </Card>
              <Card className="p-5 border-l-4 border-l-emerald-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{formatMonthKey(currentMonthSummary.monthKey)} paid</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">
                  {formatCurrencyAmount(currentMonthSummary.paidTotal, currentMonthSummary.currency)}
                </p>
                <p className="mt-2 text-xs text-gray-400">{currentMonthSummary.paidCount} paid leads this month</p>
              </Card>
              <Card className="p-5 border-l-4 border-l-rose-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Outstanding commission</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">
                  {formatCurrencyAmount(currentMonthSummary.outstandingTotal, currentMonthSummary.currency)}
                </p>
                <p className="mt-2 text-xs text-gray-400">Approved but not marked paid yet</p>
              </Card>
            </div>

            <Card className="p-5" hover={false}>
              <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <p className="text-sm font-semibold uppercase tracking-wide text-primary">Recommendation recovery</p>
                  <p className="mt-2 text-sm text-gray-600">
                    Use this when older leads were created before the recommendation trigger was deployed or when a lead is still stuck in pending recommendation.
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleBackfillPendingRecommendations}
                    disabled={recoveryBusy || pendingRecommendationLeadIds.length === 0}
                    className="inline-flex items-center gap-2"
                  >
                    {recoveryBusy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Wand2 className="h-4 w-4" />}
                    Backfill pending leads
                  </Button>
                  <span className="text-xs text-gray-500">
                    {pendingRecommendationLeadIds.length} currently pending recommendation
                  </span>
                </div>
              </div>
            </Card>

            {/* Pipeline stage filter tabs */}
            <div className="flex flex-wrap items-center gap-2">
              {(Object.keys(PIPELINE_STAGE_LABELS) as PipelineStage[]).map((stage) => (
                <button
                  key={stage}
                  type="button"
                  onClick={() => setStageFilter(stage)}
                  className={`inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                    stageFilter === stage
                      ? 'bg-primary text-white shadow-sm'
                      : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  {PIPELINE_STAGE_LABELS[stage]}
                  <span className={`inline-flex items-center justify-center rounded-full px-1.5 min-w-[20px] text-xs font-bold ${
                    stageFilter === stage ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-500'
                  }`}>
                    {stageCounts[stage]}
                  </span>
                </button>
              ))}
            </div>

            {filteredLeads.length === 0 ? (
              <Card className="p-8 text-center" hover={false}>
                <Sprout className="w-10 h-10 text-primary mx-auto mb-4" />
                <h2 className="text-xl font-semibold text-gray-900 mb-2">
                  {leads.length === 0 ? 'No leads yet' : 'No leads in this stage'}
                </h2>
                <p className="text-gray-600">
                  {leads.length === 0
                    ? 'When app users tap \u201CBest offers\u201D and a lead enters the sales pipeline, it will appear here.'
                    : 'Try switching to a different pipeline stage above.'}
                </p>
              </Card>
            ) : (
              <Card className="p-0 overflow-hidden" hover={false}>
                <div className="overflow-x-auto">
                  <table className="min-w-full border-collapse">
                    <thead className="bg-gray-50/80 border-b border-gray-200 sticky top-0 z-10">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500 w-10">
                          <input
                            type="checkbox"
                            checked={filteredLeads.length > 0 && selectedLeadIds.length === filteredLeads.length}
                            onChange={toggleSelectAllVisible}
                            aria-label="Select all visible leads"
                          />
                        </th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Stage</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Product &amp; Request</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Farmer &amp; Location</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Supplier</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Commission</th>
                        <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Created</th>
                        <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-500">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredLeads.map((lead, index) => {
                        const badge = getLeadPipelineBadge(lead);

                        return (
                          <tr
                            key={lead.id}
                            className={`${index % 2 === 0 ? 'bg-white' : 'bg-gray-50/60'} border-b border-gray-100 align-top cursor-pointer hover:bg-primary/5`}
                            onClick={() => openEditModal(lead)}
                          >
                              <td className="px-4 py-4 whitespace-nowrap" onClick={(event) => event.stopPropagation()}>
                                <input
                                  type="checkbox"
                                  checked={selectedLeadIds.includes(lead.id)}
                                  onChange={() => toggleLeadSelection(lead.id)}
                                  aria-label={`Select lead ${lead.requestNumber}`}
                                />
                              </td>
                              <td className="px-4 py-4 whitespace-nowrap">
                                <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold uppercase ${badge.className}`}>
                                  {badge.label}
                                </span>
                                {lead.opsStatus && lead.opsStatus !== 'new' && (
                                  <span className={`mt-1 block inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase ${OPS_STATUS_STYLES[lead.opsStatus]}`}>
                                    {OPS_STATUS_LABELS[lead.opsStatus]}
                                  </span>
                                )}
                              </td>
                              <td className="px-4 py-4 min-w-[160px]">
                                <div className="text-sm font-semibold text-gray-900">{lead.productName}</div>
                                <div className="mt-0.5 text-xs text-gray-500">#{lead.requestNumber}</div>
                                {lead.quantity && (
                                  <div className="mt-0.5 text-xs text-gray-400">{lead.quantity} {lead.unit || ''}</div>
                                )}
                              </td>
                              <td className="px-4 py-4 min-w-[140px]">
                                <div className="text-sm font-medium text-gray-900">{lead.farmerProfileSnapshot?.name || 'Unknown'}</div>
                                <div className="mt-0.5 text-xs text-gray-500">{getLeadDistrict(lead)}</div>
                              </td>
                              <td className="px-4 py-4 min-w-[180px]">
                                <div className="flex items-center gap-1.5 text-sm text-gray-900">
                                  <Sparkles className="h-3.5 w-3.5 text-primary flex-shrink-0" />
                                  <span className="truncate">{getRecommendedSupplierLabel(lead)}</span>
                                </div>
                                <div className="mt-1">
                                  <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase ${RECOMMENDATION_STATUS_STYLES[lead.recommendationStatus || 'pending']}`}>
                                    {RECOMMENDATION_STATUS_LABELS[lead.recommendationStatus || 'pending']}
                                  </span>
                                </div>
                              </td>
                              <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-700">
                                {formatCommission(lead.commissionPreview)}
                              </td>
                              <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-500">
                                {formatDate(lead.createdAt)}
                              </td>
                              <td className="px-4 py-4 text-right whitespace-nowrap">
                                <div className="flex items-center justify-end gap-2">
                                  {isRecommendationPending(lead) && (
                                    <button
                                      type="button"
                                      onClick={(event) => {
                                        event.stopPropagation();
                                        handleRetryRecommendation(lead.id);
                                      }}
                                      disabled={actionLeadId === lead.id}
                                      className="inline-flex items-center gap-1 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-800 hover:bg-amber-100 disabled:opacity-60"
                                    >
                                      {actionLeadId === lead.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCcw className="h-4 w-4" />}
                                      Retry
                                    </button>
                                  )}
                                  <button
                                    type="button"
                                    onClick={(event) => {
                                      event.stopPropagation();
                                      openEditModal(lead);
                                    }}
                                    className="inline-flex items-center gap-1 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                                  >
                                    <Pencil className="h-4 w-4" />
                                    Edit
                                  </button>
                                </div>
                              </td>
                            </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </Card>
            )}

            {selectedLeadIds.length > 0 && (
              <div className="sticky bottom-6 z-20 mx-4 rounded-2xl border border-primary/20 bg-white shadow-xl">
                <div className="flex flex-col gap-4 px-6 py-4 md:flex-row md:items-center md:justify-between">
                  <div className="flex items-start gap-3">
                    <CheckSquare className="mt-0.5 h-5 w-5 text-primary" />
                    <div>
                      <p className="text-sm font-semibold text-gray-900">Bulk assign queue</p>
                      <p className="text-sm text-gray-600">{selectedLeadSummary}</p>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <select
                      className="min-w-[260px] rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                      value={bulkAssignSupplierId}
                      onChange={(event) => setBulkAssignSupplierId(event.target.value)}
                      disabled={bulkAssignBusy || suppliersLoading || bulkRankedSupplierOptions.length === 0}
                    >
                      <option value="">{suppliersLoading ? 'Loading approved suppliers…' : 'Choose supplier for selected leads'}</option>
                      {bulkRankedSupplierOptions.map((supplier) => {
                        const aggregateHint = selectedLeads.length > 0
                          ? `${selectedLeads.reduce((total, lead) => total + getSupplierAssignmentScore(lead, supplier), 0)} fit points`
                          : '';
                        return (
                          <option key={supplier.id} value={supplier.id}>
                            {[supplier.businessName, supplier.districtName || supplier.districtId, aggregateHint || undefined]
                              .filter(Boolean)
                              .join(' · ')}
                          </option>
                        );
                      })}
                    </select>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setSelectedLeadIds([])}
                      disabled={bulkAssignBusy}
                    >
                      Clear selection
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={handleBackfillPendingRecommendations}
                      disabled={bulkAssignBusy || recoveryBusy || selectedLeads.filter((lead) => isRecommendationPending(lead)).length === 0}
                    >
                      {recoveryBusy ? 'Backfilling…' : 'Backfill selected pending'}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      onClick={handleBulkAssignSelected}
                      disabled={bulkAssignBusy || !bulkAssignSupplierId || selectedLeads.length === 0}
                    >
                      {bulkAssignBusy ? 'Assigning…' : 'Bulk assign selected'}
                    </Button>
                  </div>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {editingLead && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/50 px-4 py-6">
          <div className="w-full max-w-4xl max-h-[90vh] overflow-y-auto rounded-2xl bg-white shadow-2xl">
            <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-gray-200 bg-white px-6 py-5">
              <div>
                <div className="flex items-center gap-3">
                  <p className="text-sm font-semibold uppercase tracking-wide text-primary">Edit lead</p>
                  {(() => {
                    const badge = getLeadPipelineBadge(editingLead);
                    return (
                      <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold uppercase ${badge.className}`}>
                        {badge.label}
                      </span>
                    );
                  })()}
                </div>
                <h2 className="text-2xl font-bold text-gray-900 mt-1">{editingLead.productName}</h2>
                <p className="text-sm text-gray-500 mt-1">{editingLead.requestNumber} · Created {formatDate(editingLead.createdAt)}</p>
              </div>
              <button
                type="button"
                onClick={closeEditModal}
                disabled={actionLeadId === editingLead.id}
                className="rounded-lg border border-gray-200 p-2 text-gray-500 hover:bg-gray-50 disabled:opacity-50"
                aria-label="Close dialog"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="grid gap-6 px-6 py-6 lg:grid-cols-[1.4fr_1fr]">
              <div className="space-y-6">
                {/* Compact status ribbon — replaces the old 6-badge grid */}
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className={`inline-flex rounded-full px-2.5 py-1 font-semibold uppercase ${ROUTING_STATUS_STYLES[editingLead.routingStatus || 'initiated']}`}>
                    {ROUTING_STATUS_LABELS[editingLead.routingStatus || 'initiated']}
                  </span>
                  <span className={`inline-flex rounded-full px-2.5 py-1 font-semibold uppercase ${RECOMMENDATION_STATUS_STYLES[editingLead.recommendationStatus || 'pending']}`}>
                    {RECOMMENDATION_STATUS_LABELS[editingLead.recommendationStatus || 'pending']}
                  </span>
                  {editingLead.opsStatus && editingLead.opsStatus !== 'new' && (
                    <span className={`inline-flex rounded-full px-2.5 py-1 font-semibold uppercase ${OPS_STATUS_STYLES[editingLead.opsStatus]}`}>
                      {OPS_STATUS_LABELS[editingLead.opsStatus]}
                    </span>
                  )}
                  {editingLead.commissionStatus && (
                    <span className={`inline-flex rounded-full px-2.5 py-1 font-semibold uppercase ${COMMISSION_STATUS_STYLES[editingLeadCommissionStatus]}`}>
                      {COMMISSION_STATUS_LABELS[editingLeadCommissionStatus]}
                    </span>
                  )}
                </div>

                <div className="rounded-xl border border-gray-200 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 mb-2">Full inquiry text</p>
                  <p className="text-sm leading-7 text-gray-800 whitespace-pre-wrap break-words">{editingLead.chatMessageText}</p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Ops status</label>
                    <select
                      className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                      value={editForm.opsStatus}
                      onChange={(event) => setEditForm((current) => ({ ...current, opsStatus: event.target.value as SalesLeadOpsStatus }))}
                    >
                      {Object.entries(OPS_STATUS_LABELS).map(([value, label]) => (
                        <option key={value} value={value}>{label}</option>
                      ))}
                    </select>
                  </div>

                  <div className="space-y-2">
                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Assign owner</label>
                    <label className="flex items-center gap-3 rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800">
                      <input
                        type="checkbox"
                        checked={editForm.assignToMe}
                        onChange={(event) => setEditForm((current) => ({ ...current, assignToMe: event.target.checked }))}
                      />
                      Assign this lead to me ({user?.email || 'current admin'})
                    </label>
                  </div>

                  <div className="space-y-2">
                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Quantity</label>
                    <input
                      type="text"
                      className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                      value={editForm.quantity}
                      onChange={(event) => setEditForm((current) => ({ ...current, quantity: event.target.value }))}
                      placeholder="e.g. 50"
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Unit</label>
                    <input
                      type="text"
                      className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                      value={editForm.unit}
                      onChange={(event) => setEditForm((current) => ({ ...current, unit: event.target.value }))}
                      placeholder="e.g. kg"
                    />
                  </div>
                </div>

                {/* Manual supplier review — only when lead hasn't been sent to supplier yet */}
                {(['needs_action'] as PipelineStage[]).includes(getLeadPipelineStage(editingLead)) && (
                <div className="space-y-3 rounded-xl border border-primary/10 bg-primary/5 p-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-primary">Manual supplier review</p>
                    <p className="mt-1 text-sm text-gray-600">
                      If auto recommendation missed the mark, choose the best-fit approved supplier here. Saving will mark the lead as assigned to supplier.
                    </p>
                  </div>

                  <div className="space-y-2">
                    <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Assign supplier</label>
                    <select
                      className="w-full rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                      value={editForm.selectedSupplierId}
                      onChange={(event) => setEditForm((current) => ({ ...current, selectedSupplierId: event.target.value }))}
                      disabled={suppliersLoading || rankedSupplierOptions.length === 0}
                    >
                      <option value="">{suppliersLoading ? 'Loading approved suppliers…' : 'No manual supplier selected'}</option>
                      {rankedSupplierOptions.map((supplier) => {
                        const hint = editingLead ? getSupplierAssignmentHint(editingLead, supplier) : '';
                        return (
                          <option key={supplier.id} value={supplier.id}>
                            {[supplier.businessName, supplier.districtName || supplier.districtId, hint || undefined]
                              .filter(Boolean)
                              .join(' · ')}
                          </option>
                        );
                      })}
                    </select>
                    <p className="text-xs text-gray-500">
                      Top matches are sorted first using district + category fit. Current suggested supplier: {getSupplierSnapshotLabel(editingLead.suggestedSupplier)}.
                    </p>
                  </div>
                </div>
                )}

                <div className="space-y-2">
                  <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Ops notes</label>
                  <textarea
                    className="w-full min-h-[180px] rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                    value={editForm.opsNotes}
                    onChange={(event) => setEditForm((current) => ({ ...current, opsNotes: event.target.value }))}
                    placeholder="Capture farmer intent, follow-up notes, supplier preference, price expectation, callback reason, etc."
                  />
                </div>
              </div>

              <div className="space-y-4">
                {/* Post-processing — only after supplier has accepted or further */}
                {(['accepted', 'commission', 'closed'] as PipelineStage[]).includes(getLeadPipelineStage(editingLead)) && (
                <div className="rounded-xl border border-primary/10 bg-primary/5 p-4">
                  <p className="text-sm font-semibold text-gray-900">Post-processing workflow</p>
                  <p className="mt-2 text-sm text-gray-600">
                    This stage is server-controlled. Approve commission after supplier acceptance, mark backend processing complete, then close the lead.
                  </p>
                  <div className="mt-4 grid gap-2">
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => handleLeadWorkflowAction('approve_commission')}
                      disabled={actionLeadId === editingLead.id || !canApproveCommission}
                    >
                      Approve commission
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => handleLeadWorkflowAction('mark_paid')}
                      disabled={actionLeadId === editingLead.id || !canMarkCommissionPaid}
                    >
                      Mark commission paid
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => handleLeadWorkflowAction('complete_processing')}
                      disabled={actionLeadId === editingLead.id || !canCompleteProcessing}
                    >
                      Complete backend processing
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => handleLeadWorkflowAction('close_lead')}
                      disabled={actionLeadId === editingLead.id || !canCloseLead}
                    >
                      Close lead
                    </Button>
                  </div>
                  <p className="mt-3 text-xs text-gray-500">
                    Monthly totals are booked when commission is approved, not when the supplier first accepts the lead.
                  </p>
                </div>
                )}

                <div className="rounded-xl border border-gray-200 p-4">
                  <p className="text-sm font-semibold text-gray-900 mb-4">Lead details</p>

                  {/* Supplier assignment — always visible */}
                  <div className="space-y-1">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 pb-1">Supplier assignment</p>
                    <dl className="space-y-2.5 text-sm border-l-2 border-blue-200 pl-3">
                      <div>
                        <dt className="text-gray-500">Recommendation</dt>
                        <dd className="font-medium text-gray-900">{getRecommendedSupplierLabel(editingLead)}</dd>
                      </div>
                      {editingLead.assignedSupplier && (
                        <div>
                          <dt className="text-gray-500">Assigned supplier</dt>
                          <dd className="font-medium text-gray-900">{getSupplierSnapshotLabel(editingLead.assignedSupplier)}</dd>
                        </div>
                      )}
                      {editingLead.suggestedSupplier?.matchSummary && (
                        <div>
                          <dt className="text-gray-500">Match summary</dt>
                          <dd className="font-medium text-gray-900">{editingLead.suggestedSupplier.matchSummary}</dd>
                        </div>
                      )}
                    </dl>
                  </div>

                  {/* Commission — only after supplier accepted */}
                  {(['accepted', 'commission', 'closed'] as PipelineStage[]).includes(getLeadPipelineStage(editingLead)) && (
                  <>
                  <hr className="my-4 border-gray-100" />
                  <div className="space-y-1">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 pb-1">Commission</p>
                    <dl className="space-y-2.5 text-sm border-l-2 border-emerald-200 pl-3">
                      <div>
                        <dt className="text-gray-500">Commission preview</dt>
                        <dd className="font-medium text-gray-900">{formatCommission(editingLead.commissionPreview)}</dd>
                      </div>
                      <div>
                        <dt className="text-gray-500">Lifecycle</dt>
                        <dd className="font-medium text-gray-900">{COMMISSION_STATUS_LABELS[editingLeadCommissionStatus]}</dd>
                      </div>
                      {editingLead.commissionMonthKey && (
                        <div>
                          <dt className="text-gray-500">Month</dt>
                          <dd className="font-medium text-gray-900">{formatMonthKey(editingLead.commissionMonthKey)}</dd>
                        </div>
                      )}
                      {editingLead.commissionApprovedAt && (
                        <div>
                          <dt className="text-gray-500">Approved</dt>
                          <dd className="font-medium text-gray-900">{formatDate(editingLead.commissionApprovedAt)}</dd>
                        </div>
                      )}
                      {editingLead.commissionPaidAt && (
                        <div>
                          <dt className="text-gray-500">Paid</dt>
                          <dd className="font-medium text-gray-900">{formatDate(editingLead.commissionPaidAt)}</dd>
                        </div>
                      )}
                    </dl>
                  </div>
                  </>
                  )}

                  <hr className="my-4 border-gray-100" />

                  {/* Farmer & location — always visible */}
                  <div className="space-y-1">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 pb-1">Farmer &amp; location</p>
                    <dl className="space-y-2.5 text-sm border-l-2 border-amber-200 pl-3">
                      <div>
                        <dt className="text-gray-500">Farmer</dt>
                        <dd className="font-medium text-gray-900">{editingLead.farmerProfileSnapshot?.name || 'Unknown farmer'}</dd>
                      </div>
                      <div>
                        <dt className="text-gray-500">District</dt>
                        <dd className="font-medium text-gray-900">{getLeadDistrict(editingLead)}</dd>
                      </div>
                      <div>
                        <dt className="text-gray-500">Village / Tehsil</dt>
                        <dd className="font-medium text-gray-900">{getLeadVillageTehsil(editingLead)}</dd>
                      </div>
                      <div>
                        <dt className="text-gray-500">Phone</dt>
                        <dd className="font-medium text-gray-900 flex items-center gap-2">
                          <Phone className="w-4 h-4 text-gray-400" />
                          {getLeadPhone(editingLead) || 'Phone not captured in snapshot'}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  {/* Timeline — only events that actually happened */}
                  {(() => {
                    const events: Array<{ label: string; value: string }> = [];
                    if (editingLead.opsOwnerEmail) events.push({ label: 'Owner', value: editingLead.opsOwnerEmail });
                    if (editingLead.suggestionGeneratedAt) events.push({ label: 'Suggestion generated', value: formatDate(editingLead.suggestionGeneratedAt) });
                    if (editingLead.assignmentPublishedAt) events.push({ label: 'Assignment published', value: formatDate(editingLead.assignmentPublishedAt) });
                    if (editingLead.supplierResponseDeadlineAt) events.push({ label: 'Supplier deadline', value: formatDate(editingLead.supplierResponseDeadlineAt) });
                    if (editingLead.supplierRespondedAt) events.push({ label: 'Supplier responded', value: formatDate(editingLead.supplierRespondedAt) });
                    if (editingLead.firstOpsContactAt) events.push({ label: 'First contact', value: formatDate(editingLead.firstOpsContactAt) });
                    if (editingLead.closedAt) events.push({ label: 'Closed at', value: formatDate(editingLead.closedAt) });
                    if (editingLead.closedReason) events.push({ label: 'Closed reason', value: editingLead.closedReason });
                    if (editingLead.adminFallbackReason) events.push({ label: 'Fallback reason', value: editingLead.adminFallbackReason });
                    if (editingLead.supplierRejectedReason) events.push({ label: 'Rejection reason', value: editingLead.supplierRejectedReason });
                    if (events.length === 0) return null;
                    return (
                      <>
                        <hr className="my-4 border-gray-100" />
                        <div className="space-y-1">
                          <p className="text-[11px] font-semibold uppercase tracking-wider text-gray-400 pb-1">Timeline</p>
                          <dl className="space-y-2.5 text-sm border-l-2 border-slate-200 pl-3">
                            {events.map((event) => (
                              <div key={event.label}>
                                <dt className="text-gray-500">{event.label}</dt>
                                <dd className="font-medium text-gray-900">{event.value}</dd>
                              </div>
                            ))}
                          </dl>
                        </div>
                      </>
                    );
                  })()}
                </div>

                {!getLeadPhone(editingLead) && (
                  <div className="flex items-start gap-2 rounded-xl bg-amber-50 border border-amber-200 px-4 py-3 text-sm text-amber-900">
                    <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                    <span>This dashboard only shows what the current lead snapshot contains.</span>
                  </div>
                )}
              </div>
            </div>

            <div className="sticky bottom-0 flex items-center justify-end gap-3 border-t border-gray-200 bg-white px-6 py-4">
              <Button
                type="button"
                variant="outline"
                onClick={closeEditModal}
                disabled={actionLeadId === editingLead.id}
              >
                Cancel
              </Button>
              <Button
                type="button"
                onClick={handleSubmitEdit}
                disabled={actionLeadId === editingLead.id}
              >
                {actionLeadId === editingLead.id ? 'Submitting…' : 'Submit'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </Container>
  );
}
