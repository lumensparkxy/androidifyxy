"use client";

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, signInWithPopup, signOut, User } from 'firebase/auth';
import { httpsCallable } from 'firebase/functions';
import { doc, getDoc } from 'firebase/firestore';
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Database,
  ListChecks,
  Loader2,
  LogOut,
  Mail,
  PlayCircle,
  RefreshCcw,
  Save,
  ShieldCheck,
  XCircle,
} from 'lucide-react';
import { auth, db, functions, googleProvider } from '@/lib/firebase';
import { Button, Card, Container } from '@/components';
import type { AffiliateProductRegistryEntry } from '@/lib/types';

type AdminGateState = 'checking' | 'allowed' | 'forbidden' | 'signed-out';

type RegistryFormState = {
  entryId: string;
  provider: 'amazon';
  productName: string;
  specialLink: string;
  isActive: boolean;
};

type BackfillActionState = 'preview' | 'apply' | null;

type AffiliateRegistryBackfillRow = {
  action: 'create' | 'skip_existing' | 'conflict';
  reason: string;
  entryId: string;
  provider: 'amazon';
  productName: string;
  normalizedProductName: string;
  specialLink?: string | null;
  sourceLeadCount: number;
  distinctLinkCount: number;
  latestAppMessageSentAt?: string | null;
  existingIsActive?: boolean | null;
  existingSpecialLink?: string | null;
};

type AffiliateRegistryBackfillResult = {
  ok: boolean;
  dryRun: boolean;
  limit: number;
  scannedLeadCount: number;
  eligibleLeadCount: number;
  sourceProductCount: number;
  safeCreateCount: number;
  skippedExistingCount: number;
  conflictCount: number;
  createdCount: number;
  previewRows: AffiliateRegistryBackfillRow[];
  createdEntries?: AffiliateProductRegistryEntry[];
};

const PRIVILEGED_ADMIN_EMAILS = new Set(['maswadkar@gmail.com', 'neophilex@gmail.com']);

function formatUser(user: User | null): string {
  if (!user) return '';
  return user.displayName || user.email || user.phoneNumber || 'Admin';
}

function formatDate(value?: string): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function toRegistryEntry(record: Record<string, unknown>): AffiliateProductRegistryEntry | null {
  const id = typeof record.id === 'string' ? record.id : '';
  if (!id) return null;

  return {
    id,
    provider: record.provider === 'amazon' ? 'amazon' : 'amazon',
    productName: typeof record.productName === 'string' ? record.productName : '',
    normalizedProductName: typeof record.normalizedProductName === 'string' ? record.normalizedProductName : '',
    specialLink: typeof record.specialLink === 'string' ? record.specialLink : '',
    asin: typeof record.asin === 'string' ? record.asin : null,
    isActive: record.isActive !== false,
    createdAt: typeof record.createdAt === 'string' ? record.createdAt : undefined,
    updatedAt: typeof record.updatedAt === 'string' ? record.updatedAt : undefined,
    createdByUid: typeof record.createdByUid === 'string' ? record.createdByUid : null,
    createdByEmail: typeof record.createdByEmail === 'string' ? record.createdByEmail : null,
    updatedByUid: typeof record.updatedByUid === 'string' ? record.updatedByUid : null,
    updatedByEmail: typeof record.updatedByEmail === 'string' ? record.updatedByEmail : null,
    lastMatchedAt: typeof record.lastMatchedAt === 'string' ? record.lastMatchedAt : undefined,
    lastAutoAppMessageAt: typeof record.lastAutoAppMessageAt === 'string' ? record.lastAutoAppMessageAt : undefined,
    replacedByEntryId: typeof record.replacedByEntryId === 'string' ? record.replacedByEntryId : null,
  };
}

function getInitialFormState(): RegistryFormState {
  return {
    entryId: '',
    provider: 'amazon',
    productName: '',
    specialLink: '',
    isActive: true,
  };
}

function formatBackfillAction(action: AffiliateRegistryBackfillRow['action']): string {
  if (action === 'create') return 'Create';
  if (action === 'skip_existing') return 'Skip';
  return 'Review';
}

function formatBackfillReason(reason: string): string {
  switch (reason) {
    case 'single_historical_link':
      return 'Single link';
    case 'existing_registry_match':
      return 'Already registered';
    case 'multiple_historical_links':
      return 'Multiple links';
    case 'existing_registry_differs':
      return 'Existing differs';
    case 'existing_registry_inactive':
      return 'Existing archived';
    case 'existing_registry_created_during_backfill':
      return 'Created elsewhere';
    default:
      return reason.replace(/_/g, ' ');
  }
}

function getBackfillActionClass(action: AffiliateRegistryBackfillRow['action']): string {
  if (action === 'create') return 'bg-emerald-100 text-emerald-700';
  if (action === 'skip_existing') return 'bg-gray-100 text-gray-700';
  return 'bg-amber-100 text-amber-800';
}

export default function AffiliateLinksClient() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(true);
  const [gateState, setGateState] = useState<AdminGateState>('checking');
  const [busy, setBusy] = useState(false);
  const [entriesLoading, setEntriesLoading] = useState(false);
  const [entries, setEntries] = useState<AffiliateProductRegistryEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);
  const [actionEntryId, setActionEntryId] = useState<string | null>(null);
  const [form, setForm] = useState<RegistryFormState>(getInitialFormState);
  const [backfillAction, setBackfillAction] = useState<BackfillActionState>(null);
  const [backfillResult, setBackfillResult] = useState<AffiliateRegistryBackfillResult | null>(null);

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
        setEntries([]);
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

  const loadEntries = useCallback(async () => {
    if (gateState !== 'allowed') {
      setEntries([]);
      return;
    }
    if (!functions) {
      throw new Error('Firebase Functions is not configured in the website client');
    }

    setEntriesLoading(true);
    try {
      const listCallable = httpsCallable<{ limit?: number }, { ok: boolean; entries: Record<string, unknown>[] }>(
        functions,
        'listAffiliateProductRegistry',
      );
      const response = await listCallable({ limit: 200 });
      const nextEntries = Array.isArray(response.data?.entries)
        ? response.data.entries
            .map((entryRecord) => {
              if (!entryRecord || typeof entryRecord !== 'object') {
                return null;
              }
              return toRegistryEntry(entryRecord as Record<string, unknown>);
            })
            .filter((entry): entry is AffiliateProductRegistryEntry => Boolean(entry))
        : [];
      setEntries(nextEntries);
    } finally {
      setEntriesLoading(false);
    }
  }, [gateState]);

  useEffect(() => {
    if (gateState !== 'allowed') {
      setEntries([]);
      return;
    }

    void loadEntries().catch((loadError) => {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load affiliate registry');
    });
  }, [gateState, loadEntries]);

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

  const resetForm = () => {
    setForm(getInitialFormState());
  };

  const startEditing = (entry: AffiliateProductRegistryEntry) => {
    setSuccessNotice(null);
    setForm({
      entryId: entry.id,
      provider: 'amazon',
      productName: entry.productName,
      specialLink: entry.specialLink,
      isActive: entry.isActive,
    });
  };

  const saveEntry = useCallback(async (nextForm: RegistryFormState) => {
    if (!functions) {
      throw new Error('Firebase Functions is not configured in the website client');
    }

    const saveCallable = httpsCallable<
      {
        entryId?: string;
        provider: 'amazon';
        productName: string;
        specialLink: string;
        isActive: boolean;
      },
      { ok: boolean }
    >(functions, 'upsertAffiliateProductRegistryEntry');

    await saveCallable({
      entryId: nextForm.entryId || undefined,
      provider: nextForm.provider,
      productName: nextForm.productName,
      specialLink: nextForm.specialLink,
      isActive: nextForm.isActive,
    });
  }, []);

  const handleSave = async () => {
    const trimmedProductName = form.productName.trim();
    const trimmedSpecialLink = form.specialLink.trim();
    if (!trimmedProductName) {
      setError('Product name is required. This is the exact product key that auto-matching will use.');
      return;
    }
    if (!trimmedSpecialLink) {
      setError('Affiliate link is required.');
      return;
    }

    setError(null);
    setSuccessNotice(null);
    setActionEntryId(form.entryId || '__new__');
    try {
      await saveEntry({
        ...form,
        productName: trimmedProductName,
        specialLink: trimmedSpecialLink,
      });
      await loadEntries();
      setSuccessNotice(
        form.entryId
          ? 'Affiliate registry entry updated. New matching leads will use it automatically.'
          : 'Affiliate registry entry created. New exact product matches can now auto-send the app handoff.',
      );
      resetForm();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Failed to save affiliate registry entry');
    } finally {
      setActionEntryId(null);
    }
  };

  const handleToggleActive = async (entry: AffiliateProductRegistryEntry) => {
    setError(null);
    setSuccessNotice(null);
    setActionEntryId(entry.id);
    try {
      await saveEntry({
        entryId: entry.id,
        provider: 'amazon',
        productName: entry.productName,
        specialLink: entry.specialLink,
        isActive: !entry.isActive,
      });
      await loadEntries();
      setSuccessNotice(
        !entry.isActive
          ? 'Affiliate registry entry re-activated.'
          : 'Affiliate registry entry archived. It will no longer auto-match new leads.',
      );
      if (form.entryId === entry.id) {
        setForm((current) => ({ ...current, isActive: !entry.isActive }));
      }
    } catch (toggleError) {
      setError(toggleError instanceof Error ? toggleError.message : 'Failed to update affiliate registry entry');
    } finally {
      setActionEntryId(null);
    }
  };

  const runSalesPipelineBackfill = useCallback(async (dryRun: boolean) => {
    if (!functions) {
      throw new Error('Firebase Functions is not configured in the website client');
    }

    setError(null);
    setSuccessNotice(null);
    setBackfillAction(dryRun ? 'preview' : 'apply');
    try {
      const backfillCallable = httpsCallable<
        { dryRun: boolean; limit?: number },
        AffiliateRegistryBackfillResult
      >(functions, 'backfillAffiliateProductRegistryFromSalesPipeline');
      const response = await backfillCallable({ dryRun, limit: 300 });
      setBackfillResult(response.data);

      if (dryRun) {
        setSuccessNotice(
          `Backfill preview ready: ${response.data.safeCreateCount} safe, ${response.data.skippedExistingCount} existing, ${response.data.conflictCount} for review.`,
        );
      } else {
        await loadEntries();
        setSuccessNotice(
          `Backfill applied: ${response.data.createdCount} created, ${response.data.skippedExistingCount} existing, ${response.data.conflictCount} for review.`,
        );
      }
    } catch (backfillError) {
      setError(backfillError instanceof Error ? backfillError.message : 'Affiliate registry backfill failed');
    } finally {
      setBackfillAction(null);
    }
  }, [loadEntries]);

  const activeCount = useMemo(
    () => entries.filter((entry) => entry.isActive).length,
    [entries],
  );
  const backfillRows = backfillResult?.previewRows || [];

  const authCard = (
    <Card className="p-6 space-y-4" hover={false}>
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-lg font-semibold text-gray-900">Affiliate registry access</p>
          <p className="text-sm text-gray-600">
            Sign in with an allowed admin account. The registry is server-managed and powers exact product-name affiliate auto-matching.
          </p>
        </div>
        {(authLoading || busy) && <Loader2 className="w-5 h-5 animate-spin text-primary" />}
      </div>

      {error && (
        <div className="bg-red-50 text-red-700 border border-red-200 rounded-lg px-4 py-3 text-sm">
          {error}
        </div>
      )}

      {successNotice && (
        <div className="bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-lg px-4 py-3 text-sm">
          {successNotice}
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
          Your account is signed in, but it is not configured for this admin screen yet.
        </div>
      )}
    </Card>
  );

  return (
    <Container className="py-16">
      <div className="max-w-6xl mx-auto space-y-8">
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div className="space-y-3">
            <p className="text-sm font-semibold text-primary uppercase tracking-wide">Affiliate registry</p>
            <h1 className="text-3xl md:text-4xl font-bold text-gray-900">
              Manage product-to-affiliate auto-matching
            </h1>
            <p className="text-gray-600 max-w-3xl">
              These entries are exact-match only. When a new lead normalizes to the same product name, the backend can attach the affiliate link automatically and auto-send the app handoff.
            </p>
          </div>
          <Link
            href="/admin/leads"
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 transition-colors hover:bg-gray-50"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to leads
          </Link>
        </div>

        {authCard}

        {gateState === 'allowed' && (
          <>
            <div className="grid gap-4 md:grid-cols-3">
              <Card className="p-5 border-l-4 border-l-primary" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Registry entries</p>
                <div className="mt-2 flex items-center gap-2">
                  <p className="text-3xl font-bold text-gray-900">{entries.length}</p>
                  {entriesLoading && <Loader2 className="h-5 w-5 animate-spin text-primary" />}
                </div>
              </Card>
              <Card className="p-5 border-l-4 border-l-emerald-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Active exact matches</p>
                <p className="mt-2 text-3xl font-bold text-gray-900">{activeCount}</p>
              </Card>
              <Card className="p-5 border-l-4 border-l-blue-400" hover={false}>
                <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Automation behavior</p>
                <p className="mt-2 text-sm font-medium text-gray-900">Auto-send app handoff only</p>
                <p className="mt-2 text-xs text-gray-500">WhatsApp stays manual for now.</p>
              </Card>
            </div>

            <Card className="p-6 space-y-5" hover={false}>
              <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <Database className="h-5 w-5 text-primary" />
                    <p className="text-lg font-semibold text-gray-900">Sales-pipeline backfill</p>
                  </div>
                  <p className="mt-1 max-w-3xl text-sm text-gray-600">
                    Populate exact-match entries from Amazon affiliate links already sent in app. Conflicts are reported for review and are not written.
                  </p>
                </div>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => void runSalesPipelineBackfill(true)}
                    disabled={Boolean(backfillAction)}
                    className="inline-flex items-center gap-2"
                  >
                    {backfillAction === 'preview'
                      ? <Loader2 className="h-4 w-4 animate-spin" />
                      : <ListChecks className="h-4 w-4" />}
                    Preview
                  </Button>
                  <Button
                    type="button"
                    onClick={() => void runSalesPipelineBackfill(false)}
                    disabled={Boolean(backfillAction)}
                    className="inline-flex items-center gap-2"
                  >
                    {backfillAction === 'apply'
                      ? <Loader2 className="h-4 w-4 animate-spin" />
                      : <PlayCircle className="h-4 w-4" />}
                    Apply safe entries
                  </Button>
                </div>
              </div>

              {backfillResult && (
                <>
                  <div className="grid gap-3 md:grid-cols-5">
                    <div className="rounded-xl border border-gray-200 px-4 py-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Scanned</p>
                      <p className="mt-1 text-2xl font-bold text-gray-900">{backfillResult.scannedLeadCount}</p>
                    </div>
                    <div className="rounded-xl border border-gray-200 px-4 py-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Eligible</p>
                      <p className="mt-1 text-2xl font-bold text-gray-900">{backfillResult.eligibleLeadCount}</p>
                    </div>
                    <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-emerald-700">Safe</p>
                      <p className="mt-1 text-2xl font-bold text-emerald-800">{backfillResult.safeCreateCount}</p>
                    </div>
                    <div className="rounded-xl border border-gray-200 px-4 py-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-gray-500">Existing</p>
                      <p className="mt-1 text-2xl font-bold text-gray-900">{backfillResult.skippedExistingCount}</p>
                    </div>
                    <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
                      <p className="text-xs font-medium uppercase tracking-wide text-amber-700">Review</p>
                      <p className="mt-1 text-2xl font-bold text-amber-800">{backfillResult.conflictCount}</p>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-3 text-sm text-gray-600">
                    <span className="inline-flex items-center gap-2">
                      <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                      Created this run: {backfillResult.createdCount}
                    </span>
                    {backfillResult.conflictCount > 0 && (
                      <span className="inline-flex items-center gap-2">
                        <AlertTriangle className="h-4 w-4 text-amber-600" />
                        Resolve review rows manually before activation
                      </span>
                    )}
                  </div>

                  {backfillRows.length > 0 && (
                    <div className="overflow-x-auto rounded-xl border border-gray-200">
                      <table className="min-w-full border-collapse">
                        <thead className="bg-gray-50/80 border-b border-gray-200">
                          <tr>
                            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Action</th>
                            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Product</th>
                            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Source</th>
                            <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Link</th>
                          </tr>
                        </thead>
                        <tbody>
                          {backfillRows.map((row) => (
                            <tr key={`${row.entryId}-${row.action}-${row.reason}`} className="border-b border-gray-100 align-top last:border-0">
                              <td className="px-4 py-4 whitespace-nowrap">
                                <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold uppercase ${getBackfillActionClass(row.action)}`}>
                                  {formatBackfillAction(row.action)}
                                </span>
                                <div className="mt-2 text-xs text-gray-500">{formatBackfillReason(row.reason)}</div>
                              </td>
                              <td className="px-4 py-4 min-w-[220px]">
                                <div className="text-sm font-semibold text-gray-900">{row.productName}</div>
                                <div className="mt-1 text-xs text-gray-500">{row.normalizedProductName}</div>
                              </td>
                              <td className="px-4 py-4 text-sm text-gray-600 whitespace-nowrap">
                                <div>{row.sourceLeadCount} lead{row.sourceLeadCount === 1 ? '' : 's'}</div>
                                <div className="mt-1 text-xs text-gray-500">Latest {formatDate(row.latestAppMessageSentAt || undefined)}</div>
                              </td>
                              <td className="px-4 py-4 min-w-[260px] text-sm text-gray-600">
                                {row.specialLink ? (
                                  <a href={row.specialLink} target="_blank" rel="noreferrer" className="break-all text-primary hover:underline">
                                    {row.specialLink}
                                  </a>
                                ) : (
                                  <span>{row.distinctLinkCount} distinct links</span>
                                )}
                                {row.existingSpecialLink && row.existingSpecialLink !== row.specialLink && (
                                  <div className="mt-2 text-xs text-gray-500 break-all">
                                    Existing: {row.existingSpecialLink}
                                  </div>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              )}
            </Card>

            <Card className="p-6 space-y-5" hover={false}>
              <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <div>
                  <p className="text-lg font-semibold text-gray-900">
                    {form.entryId ? 'Edit affiliate registry entry' : 'Add affiliate registry entry'}
                  </p>
                  <p className="mt-1 text-sm text-gray-600">
                    Use the product name farmers are most likely to request. Matching is based on the normalized product name, so spacing and casing do not matter.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setError(null);
                    setSuccessNotice(null);
                    resetForm();
                  }}
                  className="inline-flex items-center gap-2 text-sm font-medium text-gray-500 hover:text-gray-700"
                >
                  <RefreshCcw className="h-4 w-4" />
                  Reset form
                </button>
              </div>

              <div className="grid gap-4 md:grid-cols-[1fr_1.4fr]">
                <div className="space-y-2">
                  <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Product name</label>
                  <input
                    type="text"
                    className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                    value={form.productName}
                    onChange={(event) => setForm((current) => ({ ...current, productName: event.target.value }))}
                    placeholder="e.g. Neem Spray"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Affiliate link</label>
                  <input
                    type="url"
                    className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                    value={form.specialLink}
                    onChange={(event) => setForm((current) => ({ ...current, specialLink: event.target.value }))}
                    placeholder="https://www.amazon.in/dp/...?...tag=yourtag-21"
                  />
                </div>
              </div>

              <label className="inline-flex items-center gap-3 rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(event) => setForm((current) => ({ ...current, isActive: event.target.checked }))}
                />
                Keep this entry active for auto-matching
              </label>

              <div className="flex flex-wrap items-center gap-3">
                <Button
                  type="button"
                  onClick={handleSave}
                  disabled={Boolean(actionEntryId)}
                  className="inline-flex items-center gap-2"
                >
                  {actionEntryId ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                  {form.entryId ? 'Save changes' : 'Create entry'}
                </Button>
                {form.entryId && (
                  <Button
                    type="button"
                    variant="outline"
                    onClick={resetForm}
                    disabled={Boolean(actionEntryId)}
                  >
                    Cancel editing
                  </Button>
                )}
              </div>
            </Card>

            <Card className="p-0 overflow-hidden" hover={false}>
              <div className="overflow-x-auto">
                <table className="min-w-full border-collapse">
                  <thead className="bg-gray-50/80 border-b border-gray-200">
                    <tr>
                      <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Product</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Normalized key</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Status</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Usage</th>
                      <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">Updated</th>
                      <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-gray-500">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-6 py-10 text-center text-sm text-gray-500">
                          No affiliate registry entries yet.
                        </td>
                      </tr>
                    ) : entries.map((entry, index) => (
                      <tr key={entry.id} className={`${index % 2 === 0 ? 'bg-white' : 'bg-gray-50/60'} border-b border-gray-100 align-top`}>
                        <td className="px-4 py-4 min-w-[220px]">
                          <div className="text-sm font-semibold text-gray-900">{entry.productName}</div>
                          <a
                            href={entry.specialLink}
                            target="_blank"
                            rel="noreferrer"
                            className="mt-1 block text-xs text-primary break-all hover:underline"
                          >
                            {entry.specialLink}
                          </a>
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-600">{entry.normalizedProductName}</td>
                        <td className="px-4 py-4 whitespace-nowrap">
                          <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold uppercase ${entry.isActive ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-600'}`}>
                            {entry.isActive ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                            {entry.isActive ? 'Active' : 'Archived'}
                          </span>
                          {entry.asin && (
                            <div className="mt-2 text-xs text-gray-500">ASIN: {entry.asin}</div>
                          )}
                        </td>
                        <td className="px-4 py-4 text-sm text-gray-600 min-w-[180px]">
                          <div>Last matched: {formatDate(entry.lastMatchedAt)}</div>
                          <div className="mt-1">Last auto app send: {formatDate(entry.lastAutoAppMessageAt)}</div>
                        </td>
                        <td className="px-4 py-4 text-sm text-gray-600 whitespace-nowrap">
                          <div>{formatDate(entry.updatedAt)}</div>
                          <div className="mt-1 text-xs text-gray-500">{entry.updatedByEmail || entry.createdByEmail || 'System'}</div>
                        </td>
                        <td className="px-4 py-4 text-right whitespace-nowrap">
                          <div className="flex items-center justify-end gap-2">
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => startEditing(entry)}
                              disabled={Boolean(actionEntryId)}
                            >
                              Edit
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => void handleToggleActive(entry)}
                              disabled={Boolean(actionEntryId)}
                            >
                              {actionEntryId === entry.id
                                ? 'Saving…'
                                : entry.isActive
                                  ? 'Archive'
                                  : 'Activate'}
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </>
        )}
      </div>
    </Container>
  );
}
