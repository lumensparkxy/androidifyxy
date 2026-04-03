"use client";

import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import {
  onAuthStateChanged,
  signInWithPopup,
  signInWithPhoneNumber,
  signOut,
  User,
  ConfirmationResult,
} from "firebase/auth";
import {
  auth,
  googleProvider,
  RecaptchaVerifier,
  db,
  functions,
} from "@/lib/firebase";
import { httpsCallable } from "firebase/functions";
import {
  addDoc,
  collection,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  where,
} from "firebase/firestore";
import { Container } from "@/components/Container";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import {
  ShieldCheck,
  Phone,
  Mail,
  Loader2,
  LogOut,
  CheckCircle2,
  AlertTriangle,
  Plus,
  Tag,
  Inbox,
  RefreshCcw,
  CheckCheck,
  XCircle,
  ChevronDown,
  ChevronUp,
} from "lucide-react";

const PRIVILEGED_SUPPLIER_PHONES = new Set(["+919493513382"]);

function normalizePhone(value: string | null | undefined): string {
  if (!value) return "";
  const compact = value.replace(/\s+/g, "");
  return compact.startsWith("+") ? compact : `+91${compact}`;
}

function isPrivilegedSupplierUser(user: User | null, phoneValue?: string | null): boolean {
  const phoneCandidates = [normalizePhone(user?.phoneNumber), normalizePhone(phoneValue)];
  return phoneCandidates.some((phone) => phone && PRIVILEGED_SUPPLIER_PHONES.has(phone));
}

function useAuthState() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!auth) {
      setLoading(false);
      return;
    }
    const unsub = onAuthStateChanged(auth, (next) => {
      setUser(next);
      setLoading(false);
    });
    return unsub;
  }, []);

  return { user, loading };
}

function formatUser(user: User | null): string {
  if (!user) return "";
  return user.displayName || user.phoneNumber || user.email || "Supplier";
}

type VerificationStatus = "PENDING" | "APPROVED" | "REJECTED";
type OfferStatus = "DRAFT" | "ACTIVE" | "INACTIVE" | "EXPIRED";

interface SupplierProfile {
  ownerUid: string;
  canonicalSupplierId?: string;
  mergedIntoSupplierId?: string | null;
  businessName: string;
  phone?: string;
  districtId: string;
  districtName?: string;
  verificationStatus: VerificationStatus;
  createdAt?: string;
  updatedAt?: string;
}

type SupplierProfileCandidate = {
  id: string;
  data: SupplierProfile;
};

interface Offer {
  id: string;
  productName: string;
  category: string;
  priceRetail?: number;
  packSize?: number | null;
  packUnit?: string;
  status: OfferStatus;
  supplierApproved?: boolean;
}

interface SupplierLeadInboxItem {
  id: string;
  requestNumber: string;
  productName: string;
  quantity?: string | null;
  unit?: string | null;
  chatMessageText: string;
  leadCategory?: string | null;
  routingStatus?: string | null;
  reviewStatus?: string | null;
  recommendationStatus?: string | null;
  supplierVisibility?: string | null;
  contactUnlocked?: boolean;
  farmerProfileSnapshot?: {
    name?: string | null;
    village?: string | null;
    tehsil?: string | null;
    district?: string | null;
    phoneNumber?: string | null;
    email?: string | null;
  } | null;
  leadLocation?: {
    district?: string | null;
    tehsil?: string | null;
    village?: string | null;
  } | null;
  commissionPreview?: {
    category?: string | null;
    amount?: number | null;
    currency?: string | null;
  } | null;
  assignmentPublishedAt?: string;
  supplierRespondedAt?: string;
  supplierRejectedReason?: string | null;
  lastRoutingUpdatedAt?: string;
}

function formatDate(value?: string | null): string {
  if (!value) return "Pending";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function formatLeadQuantity(lead: SupplierLeadInboxItem): string {
  return lead.quantity ? `${lead.quantity} ${lead.unit || ""}`.trim() : "Not specified";
}

function formatLeadLocation(lead: SupplierLeadInboxItem): string {
  return [
    lead.leadLocation?.village || lead.farmerProfileSnapshot?.village,
    lead.leadLocation?.tehsil || lead.farmerProfileSnapshot?.tehsil,
    lead.leadLocation?.district || lead.farmerProfileSnapshot?.district,
  ].filter(Boolean).join(", ") || "Location not captured";
}

function formatLeadCommission(lead: SupplierLeadInboxItem): string {
  const amount = lead.commissionPreview?.amount;
  if (amount == null) return "Pending";
  return `₹${amount}`;
}

function formatLeadStatusLabel(status?: string | null): string {
  return status?.replace(/_/g, " ") || "pending";
}

function truncateText(value?: string | null, maxLength = 120): string {
  if (!value) return "No inquiry text captured";
  if (value.length <= maxLength) return value;
  return `${value.slice(0, maxLength).trimEnd()}...`;
}

function getLeadStatusClass(status?: string | null): string {
  switch (status) {
    case "supplier_accepted":
      return "bg-emerald-50 text-emerald-700 border border-emerald-200";
    case "supplier_rejected":
      return "bg-rose-50 text-rose-700 border border-rose-200";
    case "supplier_timeout":
      return "bg-amber-50 text-amber-700 border border-amber-200";
    default:
      return "bg-violet-50 text-violet-700 border border-violet-200";
  }
}

export default function SupplierClient() {
  const { user, loading } = useAuthState();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [phoneNumber, setPhoneNumber] = useState("");
  const [otp, setOtp] = useState("");
  const [phoneStep, setPhoneStep] = useState<"enter" | "verify">("enter");
  const [confirmation, setConfirmation] = useState<ConfirmationResult | null>(null);

  const recaptchaRef = useRef<RecaptchaVerifier | null>(null);

  const [profile, setProfile] = useState<SupplierProfile | null>(null);
  const [profileDocId, setProfileDocId] = useState<string | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [businessName, setBusinessName] = useState("");
  const [phone, setPhone] = useState("");
  const [districtId, setDistrictId] = useState("");
  const [districtName, setDistrictName] = useState("");

  const [offerName, setOfferName] = useState("");
  const [offerCategory, setOfferCategory] = useState("fertilizer");
  const [offerPrice, setOfferPrice] = useState("");
  const [offerPackSize, setOfferPackSize] = useState("");
  const [offerPackUnit, setOfferPackUnit] = useState("kg");
  const [offerNpk, setOfferNpk] = useState("");
  const [offerKeywords, setOfferKeywords] = useState("");

  const [offers, setOffers] = useState<Offer[]>([]);
  const [assignedLeads, setAssignedLeads] = useState<SupplierLeadInboxItem[]>([]);
  const [assignedLeadsLoading, setAssignedLeadsLoading] = useState(false);
  const [leadActionId, setLeadActionId] = useState<string | null>(null);
  const [expandedLeadId, setExpandedLeadId] = useState<string | null>(null);
  const [rejectLeadId, setRejectLeadId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const resetOfferForm = () => {
    setOfferName("");
    setOfferCategory("fertilizer");
    setOfferPrice("");
    setOfferPackSize("");
    setOfferPackUnit("kg");
    setOfferNpk("");
    setOfferKeywords("");
  };

  const ensureRecaptcha = () => {
    if (typeof window === "undefined" || !auth) return null;
    if (!recaptchaRef.current) {
      recaptchaRef.current = new RecaptchaVerifier(auth, "recaptcha-container", {
        size: "invisible",
      });
    }
    return recaptchaRef.current;
  };

  const handleGoogleSignIn = async () => {
    if (!auth || !googleProvider) return;
    setError(null);
    setBusy(true);
    try {
      await signInWithPopup(auth, googleProvider);
    } catch (err: unknown) {
      // Silently ignore if user closed the popup - no action needed
      const firebaseError = err as { code?: string };
      if (firebaseError.code === "auth/popup-closed-by-user") {
        setBusy(false);
        return;
      }
      setError(err instanceof Error ? err.message : "Google sign-in failed");
    } finally {
      setBusy(false);
    }
  };

  const handleSendOtp = async () => {
    if (!auth) return;
    if (!phoneNumber) {
      setError("Enter a valid phone number");
      return;
    }
    setError(null);
    setBusy(true);
    try {
      // Remove all spaces and normalize the phone number
      let normalizedPhone = phoneNumber.replace(/\s+/g, "");
      // If no + prefix, assume Indian number and add +91
      if (!normalizedPhone.startsWith("+")) {
        normalizedPhone = "+91" + normalizedPhone;
      }
      const verifier = ensureRecaptcha();
      if (!verifier) throw new Error("reCAPTCHA not ready");
      const result = await signInWithPhoneNumber(auth, normalizedPhone, verifier);
      setConfirmation(result);
      setPhoneStep("verify");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to send OTP");
    } finally {
      setBusy(false);
    }
  };

  const handleVerifyOtp = async () => {
    if (!confirmation) {
      setError("Request an OTP first.");
      return;
    }
    if (!otp) {
      setError("Enter the 6-digit OTP.");
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await confirmation.confirm(otp);
      setOtp("");
      setPhoneStep("enter");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid OTP");
    } finally {
      setBusy(false);
    }
  };

  const handleSignOut = async () => {
    if (!auth) return;
    setBusy(true);
    try {
      await signOut(auth);
      setProfile(null);
      setProfileDocId(null);
      setOffers([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign out failed");
    } finally {
      setBusy(false);
    }
  };

  const loadProfile = useCallback(async (uid: string) => {
    if (!db) return;
    setProfileLoading(true);
    try {
      let selectedCandidate: SupplierProfileCandidate | null = null;

      if (functions) {
        const getProfileCallable = httpsCallable<
          Record<string, never>,
          { ok: boolean; supplierId?: string | null; profile?: SupplierProfile | null }
        >(functions, "getCurrentSupplierProfile");
        const result = await getProfileCallable({});
        if (result.data?.profile) {
          selectedCandidate = {
            id: result.data.supplierId || uid,
            data: result.data.profile,
          };
        } else {
          setProfileDocId(result.data?.supplierId || uid);
        }
      } else {
        const directSnapshot = await getDoc(doc(db, "suppliers", uid));
        if (directSnapshot.exists()) {
          selectedCandidate = {
            id: directSnapshot.id,
            data: directSnapshot.data() as SupplierProfile,
          };
        }
      }

      if (selectedCandidate) {
        setProfileDocId(selectedCandidate.id);
        setProfile(selectedCandidate.data);
        setBusinessName(selectedCandidate.data.businessName || "");
        setPhone(selectedCandidate.data.phone || "");
        setDistrictId(selectedCandidate.data.districtId || "");
        setDistrictName(selectedCandidate.data.districtName || "");
      } else {
        setProfileDocId(uid);
        setProfile(null);
        setBusinessName("");
        setPhone(normalizePhone(user?.phoneNumber));
        setDistrictId("");
        setDistrictName("");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load profile");
    } finally {
      setProfileLoading(false);
    }
  }, [user?.phoneNumber]);

  const loadAssignedLeads = useCallback(async () => {
    if (!functions || !user || profile?.verificationStatus !== "APPROVED") {
      setAssignedLeads([]);
      return;
    }

    setAssignedLeadsLoading(true);
    setError(null);
    try {
      const listLeadsCallable = httpsCallable<{ limit: number }, { ok: boolean; leads: SupplierLeadInboxItem[] }>(functions, "listSupplierAssignedLeads");
      const result = await listLeadsCallable({ limit: 25 });
      setAssignedLeads(result.data?.leads || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load assigned leads");
    } finally {
      setAssignedLeadsLoading(false);
    }
  }, [profile?.verificationStatus, user]);

  useEffect(() => {
    if (!db) return;
    if (user?.uid) {
      void loadProfile(user.uid);
    } else {
      setProfile(null);
      setProfileDocId(null);
      setOffers([]);
    }
  }, [loadProfile, user?.uid]);

  useEffect(() => {
    if (!db || !profileDocId) {
      setOffers([]);
      return;
    }

    const q = query(
      collection(db, "offers"),
      where("supplierId", "==", profileDocId),
      orderBy("createdAt", "desc")
    );
    const unsub = onSnapshot(q, (snap) => {
      const next = snap.docs.map((d) => ({
        id: d.id,
        ...(d.data() as Omit<Offer, "id">),
      }));
      setOffers(next);
    });
    return () => unsub();
  }, [profileDocId]);

  useEffect(() => {
    if (!user || profile?.verificationStatus !== "APPROVED") {
      setAssignedLeads([]);
      return;
    }

    void loadAssignedLeads();
  }, [loadAssignedLeads, profile?.verificationStatus, user]);

  const handleSaveProfile = async () => {
    if (!user || !db) return;
    if (!businessName || !districtId) {
      setError("Business name and district are required");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const isPrivilegedSupplier = isPrivilegedSupplierUser(user, phone);
      const verificationStatus: VerificationStatus =
        profile?.verificationStatus === "APPROVED" || isPrivilegedSupplier
          ? "APPROVED"
          : (profile?.verificationStatus || "PENDING");
      const targetSupplierDocId = profileDocId || user.uid;
      await setDoc(
        doc(db, "suppliers", targetSupplierDocId),
        {
          ownerUid: user.uid,
          canonicalSupplierId: user.uid,
          mergedIntoSupplierId: null,
          businessName,
          phone: normalizePhone(phone || user.phoneNumber),
          districtId,
          districtName,
          verificationStatus,
          updatedAt: serverTimestamp(),
          ...(profile ? {} : { createdAt: serverTimestamp() }),
        },
        { merge: true }
      );
      setProfileDocId(targetSupplierDocId);
      await loadProfile(targetSupplierDocId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save profile");
    } finally {
      setBusy(false);
    }
  };

  const handleCreateOffer = async () => {
    if (!user || !profile || !db) return;
    if (profile.verificationStatus !== "APPROVED") {
      setError("You must be approved before adding offers.");
      return;
    }
    if (!offerName || !offerPrice) {
      setError("Product name and price are required");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const isPrivilegedSupplier = isPrivilegedSupplierUser(user, profile.phone);
      const supplierId = profile.canonicalSupplierId || profileDocId || user.uid;
      const priceNumber = Number(offerPrice) || 0;
      const packNumber = offerPackSize ? Number(offerPackSize) : null;
      const npkKey = offerNpk
        ? offerNpk.replace(/\s+/g, "").replace(/:/g, "-").toLowerCase()
        : undefined;
      const priceNormalized =
        packNumber && packNumber > 0 ? priceNumber / packNumber : priceNumber;
      await addDoc(collection(db, "offers"), {
        supplierId,
        supplierName: profile.businessName,
        supplierApproved: isPrivilegedSupplier,
        districtId: profile.districtId,
        category: offerCategory,
        productName: offerName,
        npkRaw: offerNpk || null,
        npkKey: npkKey || null,
        keywords: offerKeywords
          ? offerKeywords
              .split(",")
              .map((k) => k.trim())
              .filter(Boolean)
          : [],
        priceRetail: priceNumber,
        priceNormalized,
        packSize: packNumber,
        packUnit: offerPackUnit,
        status: isPrivilegedSupplier ? "ACTIVE" : "DRAFT",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      resetOfferForm();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create offer");
    } finally {
      setBusy(false);
    }
  };

  const handleSupplierLeadResponse = async (leadId: string, action: "accept" | "reject") => {
    if (!functions) {
      setError("Firebase Functions is not configured in the website client");
      return;
    }

    if (action === "reject" && !rejectReason.trim()) {
      setError("Please add a short reason before rejecting the lead.");
      return;
    }

    setError(null);
    setLeadActionId(leadId);
    try {
      const respondCallable = httpsCallable<
        { leadId: string; action: "accept" | "reject"; rejectionReason?: string },
        { ok: boolean; lead: SupplierLeadInboxItem }
      >(functions, "respondToSupplierLead");
      const result = await respondCallable({
        leadId,
        action,
        rejectionReason: action === "reject" ? rejectReason.trim() : undefined,
      });

      if (result.data?.lead) {
        setAssignedLeads((current) => current.map((lead) => (lead.id === leadId ? result.data.lead : lead)));
      }

      if (action === "reject") {
        setRejectLeadId(null);
        setRejectReason("");
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : `Failed to ${action} lead`;
      await loadAssignedLeads();
      setError(message);
    } finally {
      setLeadActionId(null);
    }
  };

  let actionArea: React.ReactNode = null;

  if (loading) {
    actionArea = (
      <div className="flex items-center gap-2 text-gray-600">
        <Loader2 className="w-5 h-5 animate-spin" />
        Checking session...
      </div>
    );
  } else if (user) {
    const isProfileComplete = profile && profile.businessName && profile.districtId;
    const isApproved = profile?.verificationStatus === "APPROVED";
    const isPrivilegedSupplier = isPrivilegedSupplierUser(user, profile?.phone || phone);

    if (!isApproved) {
      actionArea = (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <ShieldCheck className="w-6 h-6 text-primary" />
            <div>
              <p className="text-sm text-gray-500">Signed in as</p>
              <p className="font-semibold text-gray-900">{formatUser(user)}</p>
            </div>
          </div>
          {!isProfileComplete && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 text-sm text-amber-800">
              <p className="font-semibold mb-1">Next steps</p>
              <ul className="list-disc list-inside space-y-1 text-amber-900">
                <li>Complete your supplier profile (district, business name, phone).</li>
                <li>{isPrivilegedSupplier ? "This phone number is allowlisted and will be approved automatically after save." : "Wait for admin approval (done manually via console for now)."}</li>
                <li>Add offers (max 20 active). Activation will be enforced via backend.</li>
              </ul>
            </div>
          )}
          {isProfileComplete && !isApproved && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 text-sm text-amber-800">
              <p className="font-semibold mb-1">{isPrivilegedSupplier ? "Auto-approval ready" : "Awaiting approval"}</p>
              <p className="text-amber-900">
                {isPrivilegedSupplier
                  ? "Save your supplier profile once and this account will become approved immediately."
                  : "Your profile is complete. Please wait for admin approval before adding offers."}
              </p>
            </div>
          )}
        </div>
      );
    }
  } else {
    actionArea = (
      <div className="space-y-4">
        <Button
          onClick={handleGoogleSignIn}
          disabled={busy}
          className="w-full inline-flex items-center justify-center gap-2"
        >
          <Mail className="w-4 h-4" />
          Continue with Google
        </Button>

        <div className="flex items-center gap-3 py-1">
          <div className="flex-1 border-t border-gray-200" />
          <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">or use phone</span>
          <div className="flex-1 border-t border-gray-200" />
        </div>

        <div className="flex flex-col gap-3">
            {phoneStep === "enter" ? (
              <>
                <input
                  type="tel"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  placeholder="Enter phone (+91...)"
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  disabled={busy}
                />
                <Button
                  onClick={handleSendOtp}
                  disabled={busy}
                  variant="secondary"
                  className="inline-flex items-center gap-2"
                >
                  <Phone className="w-4 h-4" />
                  Send OTP
                </Button>
              </>
            ) : (
              <>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none tracking-[0.3em] uppercase"
                  placeholder="Enter OTP"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value)}
                  disabled={busy}
                />
                <div className="flex items-center gap-2">
                  <Button
                    onClick={handleVerifyOtp}
                    disabled={busy}
                    className="inline-flex items-center gap-2"
                  >
                    <ShieldCheck className="w-4 h-4" />
                    Verify & Sign in
                  </Button>
                  <Button
                    onClick={() => {
                      setPhoneStep("enter");
                      setOtp("");
                      setConfirmation(null);
                    }}
                    variant="secondary"
                    disabled={busy}
                  >
                    Start over
                  </Button>
                </div>
              </>
            )}
        </div>
      </div>
    );
  }

  const contentWidthClass =
    user && profile?.verificationStatus === "APPROVED" ? "max-w-6xl" : "max-w-3xl";

  return (
    <Container className="py-16">
      <div className={`${contentWidthClass} mx-auto space-y-8`}>
        <div className="space-y-3 text-center">
          <p className="text-sm font-semibold text-primary uppercase tracking-wide">
            Supplier Portal (beta)
          </p>
          <h1 className="text-3xl md:text-4xl font-bold text-gray-900">
            List your seasonal best products
          </h1>
          {profile?.verificationStatus !== "APPROVED" && (
            <p className="text-gray-600 max-w-2xl mx-auto">
              Sign in with Google or Phone OTP to manage up to 20 active offers.
              Admin approvals are handled manually right now; your offers go live
              only after approval and activation.
            </p>
          )}
        </div>

        {actionArea && (
          <Card className="p-6 space-y-4">
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-1">
                <p className="text-lg font-semibold text-gray-900">
                  Access the portal
                </p>
                <p className="text-sm text-gray-600">
                  Secure sign-in with Google or Phone OTP.
                </p>
              </div>
              {busy && <Loader2 className="w-5 h-5 animate-spin text-primary" />}
            </div>

            {error && (
              <div className="bg-red-50 text-red-700 border border-red-200 rounded-lg px-4 py-3 text-sm">
                {error}
              </div>
            )}

            {actionArea}
          </Card>
        )}

        {user && profile?.verificationStatus !== "APPROVED" && (
          <Card className="p-6 space-y-6">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-lg font-semibold text-gray-900">
                  Supplier profile
                </p>
                <p className="text-sm text-gray-600">
                  Update your business details. Admin approval is required
                  before offers go live.
                </p>
              </div>
              {profileLoading && (
                <Loader2 className="w-4 h-4 animate-spin text-primary" />
              )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Business name</label>
                <input
                  type="text"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  value={businessName}
                  onChange={(e) => setBusinessName(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Phone</label>
                <input
                  type="tel"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">District ID</label>
                <input
                  type="text"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  placeholder="e.g. MH:pune"
                  value={districtId}
                  onChange={(e) => setDistrictId(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">District name (display)</label>
                <input
                  type="text"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  placeholder="Pune, Maharashtra"
                  value={districtName}
                  onChange={(e) => setDistrictName(e.target.value)}
                  disabled={busy}
                />
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Button
                onClick={handleSaveProfile}
                disabled={busy}
                className="inline-flex items-center gap-2"
              >
                <CheckCircle2 className="w-4 h-4" />
                Save profile
              </Button>
              {profile ? (
                <span className="text-sm text-amber-700 bg-amber-50 border border-amber-200 px-3 py-2 rounded-lg inline-flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4" />{" "}
                  {profile.verificationStatus || "Pending"}
                </span>
              ) : (
                <span className="text-sm text-gray-600">
                  No profile found yet
                </span>
              )}
            </div>
          </Card>
        )}

        {user && profile && profile.verificationStatus === "APPROVED" && (
          <Card className="p-6 space-y-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
              <div>
                <p className="text-lg font-semibold text-gray-900">Assigned leads</p>
                <p className="text-sm text-gray-600">
                  Review assigned inquiries here. Contact details unlock after you accept a lead.
                </p>
              </div>
              <Button
                onClick={() => void loadAssignedLeads()}
                variant="secondary"
                disabled={assignedLeadsLoading || !!leadActionId}
                className="inline-flex items-center gap-2"
              >
                <RefreshCcw className={`w-4 h-4 ${assignedLeadsLoading ? "animate-spin" : ""}`} />
                Refresh leads
              </Button>
            </div>

            {assignedLeadsLoading ? (
              <div className="flex items-center gap-2 text-sm text-gray-600">
                <Loader2 className="w-4 h-4 animate-spin" />
                Loading assigned leads...
              </div>
            ) : assignedLeads.length === 0 ? (
              <div className="rounded-xl border border-dashed border-gray-200 px-4 py-6 text-sm text-gray-600">
                <div className="flex items-center gap-2 font-medium text-gray-900">
                  <Inbox className="w-4 h-4 text-primary" />
                  No assigned leads right now
                </div>
                <p className="mt-2">
                  When admin assigns a sales lead to this supplier account, it will appear here.
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="rounded-2xl border border-slate-200 bg-white p-1">
                  <div className="grid gap-3 sm:grid-cols-3">
                    <div className="rounded-xl bg-slate-50 px-4 py-3 border-l-4 border-l-primary">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Open inbox</p>
                      <p className="mt-1 text-2xl font-bold text-slate-900">{assignedLeads.length}</p>
                    </div>
                    <div className="rounded-xl bg-slate-50 px-4 py-3 border-l-4 border-l-amber-400">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Awaiting action</p>
                      <p className="mt-1 text-2xl font-bold text-slate-900">
                        {assignedLeads.filter((lead) => lead.routingStatus === "supplier_pending").length}
                      </p>
                    </div>
                    <div className="rounded-xl bg-slate-50 px-4 py-3 border-l-4 border-l-emerald-400">
                      <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Accepted</p>
                      <p className="mt-1 text-2xl font-bold text-slate-900">
                        {assignedLeads.filter((lead) => lead.routingStatus === "supplier_accepted").length}
                      </p>
                    </div>
                  </div>
                </div>

                <div className="hidden md:block overflow-hidden rounded-2xl border border-gray-200">
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200">
                      <thead className="bg-slate-50">
                        <tr className="text-left text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                          <th className="px-4 py-3">Lead</th>
                          <th className="px-4 py-3">Inquiry</th>
                          <th className="px-4 py-3">Quantity</th>
                          <th className="px-4 py-3">Location</th>
                          <th className="px-4 py-3">Commission</th>
                          <th className="px-4 py-3">Timeline</th>
                          <th className="px-4 py-3">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-200 bg-white">
                        {assignedLeads.map((lead) => {
                          const canAccept = lead.routingStatus === "supplier_pending";
                          const canReject =
                            lead.routingStatus === "supplier_pending" ||
                            lead.routingStatus === "supplier_accepted";
                          const isRejectingThisLead = rejectLeadId === lead.id;
                          const isExpanded = expandedLeadId === lead.id;

                          return (
                            <Fragment key={lead.id}>
                              <tr key={lead.id} className="align-top">
                                <td className="px-4 py-4">
                                  <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-2">
                                      <span className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                                        {lead.requestNumber || "Lead"}
                                      </span>
                                      <span
                                        className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase ${getLeadStatusClass(
                                          lead.routingStatus
                                        )}`}
                                      >
                                        {formatLeadStatusLabel(lead.routingStatus)}
                                      </span>
                                      {lead.contactUnlocked && (
                                        <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-[11px] font-semibold uppercase text-emerald-700">
                                          Contact unlocked
                                        </span>
                                      )}
                                    </div>
                                    <div>
                                      <p className="font-semibold text-gray-900">{lead.productName}</p>
                                      <p className="text-xs text-gray-500">
                                        {lead.leadCategory || "Other"} lead
                                      </p>
                                    </div>
                                  </div>
                                </td>
                                <td className="px-4 py-4">
                                  <p className="max-w-sm text-sm leading-6 text-gray-700">
                                    {truncateText(lead.chatMessageText, 110)}
                                  </p>
                                </td>
                                <td className="px-4 py-4 text-sm text-gray-700">
                                  {formatLeadQuantity(lead)}
                                </td>
                                <td className="px-4 py-4 text-sm text-gray-700">
                                  {formatLeadLocation(lead)}
                                </td>
                                <td className="px-4 py-4">
                                  <p className="font-semibold text-gray-900">
                                    {formatLeadCommission(lead)}
                                  </p>
                                  <p className="text-xs text-gray-500">
                                    Review: {lead.reviewStatus || "pending"}
                                  </p>
                                </td>
                                <td className="px-4 py-4 text-sm text-gray-600">
                                  <p>Assigned {formatDate(lead.assignmentPublishedAt)}</p>
                                  <p className="mt-1">
                                    Updated {formatDate(lead.lastRoutingUpdatedAt || lead.assignmentPublishedAt)}
                                  </p>
                                  {lead.supplierRespondedAt && (
                                    <p className="mt-1">
                                      Responded {formatDate(lead.supplierRespondedAt)}
                                    </p>
                                  )}
                                </td>
                                <td className="px-4 py-4">
                                  <div className="flex min-w-[13rem] flex-col items-start gap-2">
                                    <Button
                                      onClick={() =>
                                        setExpandedLeadId((current) =>
                                          current === lead.id ? null : lead.id
                                        )
                                      }
                                      variant="secondary"
                                      size="sm"
                                      className="inline-flex items-center gap-2"
                                    >
                                      {isExpanded ? (
                                        <ChevronUp className="w-4 h-4" />
                                      ) : (
                                        <ChevronDown className="w-4 h-4" />
                                      )}
                                      {isExpanded ? "Hide details" : "View details"}
                                    </Button>

                                    {canAccept && (
                                      <Button
                                        onClick={() =>
                                          void handleSupplierLeadResponse(lead.id, "accept")
                                        }
                                        disabled={leadActionId === lead.id}
                                        size="sm"
                                        className="inline-flex items-center gap-2"
                                      >
                                        {leadActionId === lead.id ? (
                                          <Loader2 className="w-4 h-4 animate-spin" />
                                        ) : (
                                          <CheckCheck className="w-4 h-4" />
                                        )}
                                        Accept
                                      </Button>
                                    )}

                                    {canReject && !isRejectingThisLead && (
                                      <Button
                                        onClick={() => {
                                          setRejectLeadId(lead.id);
                                          setRejectReason(lead.supplierRejectedReason || "");
                                          setExpandedLeadId(lead.id);
                                        }}
                                        variant="secondary"
                                        size="sm"
                                        disabled={leadActionId === lead.id}
                                        className="inline-flex items-center gap-2"
                                      >
                                        <XCircle className="w-4 h-4" />
                                        Reject
                                      </Button>
                                    )}
                                  </div>
                                </td>
                              </tr>

                              {isExpanded && (
                                <tr key={`${lead.id}-details`} className="bg-slate-50/70">
                                  <td colSpan={7} className="px-4 py-4">
                                    <div className="grid gap-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
                                      <div className="rounded-2xl border border-slate-200 bg-white p-4">
                                        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                                          Full inquiry
                                        </p>
                                        <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">
                                          {lead.chatMessageText || "No inquiry text captured"}
                                        </p>
                                      </div>
                                      <div className="space-y-4">
                                        <div className="rounded-2xl border border-slate-200 bg-white p-4">
                                          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                                            Farmer details
                                          </p>
                                          <dl className="mt-3 space-y-2 text-sm text-slate-700">
                                            <div className="flex justify-between gap-4">
                                              <dt className="text-slate-500">Name</dt>
                                              <dd className="text-right font-medium text-slate-900">
                                                {lead.farmerProfileSnapshot?.name || "Unknown farmer"}
                                              </dd>
                                            </div>
                                            <div className="flex justify-between gap-4">
                                              <dt className="text-slate-500">Location</dt>
                                              <dd className="text-right font-medium text-slate-900">
                                                {formatLeadLocation(lead)}
                                              </dd>
                                            </div>
                                            <div className="flex justify-between gap-4">
                                              <dt className="text-slate-500">Phone</dt>
                                              <dd className="text-right font-medium text-slate-900">
                                                {lead.farmerProfileSnapshot?.phoneNumber || "Hidden until accepted"}
                                              </dd>
                                            </div>
                                            {lead.farmerProfileSnapshot?.email && (
                                              <div className="flex justify-between gap-4">
                                                <dt className="text-slate-500">Email</dt>
                                                <dd className="text-right font-medium text-slate-900">
                                                  {lead.farmerProfileSnapshot.email}
                                                </dd>
                                              </div>
                                            )}
                                          </dl>
                                        </div>

                                        {lead.supplierRejectedReason && (
                                          <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                                            <span className="font-semibold">Rejection reason:</span>{" "}
                                            {lead.supplierRejectedReason}
                                          </div>
                                        )}

                                        {isRejectingThisLead && (
                                          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 space-y-3">
                                            <label className="block text-sm font-medium text-amber-900">
                                              Tell admin why you’re rejecting this lead
                                            </label>
                                            <textarea
                                              className="w-full rounded-lg border border-amber-200 bg-white px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                                              rows={3}
                                              placeholder="Example: Out of coverage area, product unavailable, or capacity full"
                                              value={rejectReason}
                                              onChange={(e) => setRejectReason(e.target.value)}
                                              disabled={leadActionId === lead.id}
                                            />
                                            <div className="flex flex-wrap items-center gap-3">
                                              <Button
                                                onClick={() =>
                                                  void handleSupplierLeadResponse(lead.id, "reject")
                                                }
                                                disabled={leadActionId === lead.id}
                                                variant="secondary"
                                                size="sm"
                                                className="inline-flex items-center gap-2"
                                              >
                                                {leadActionId === lead.id ? (
                                                  <Loader2 className="w-4 h-4 animate-spin" />
                                                ) : (
                                                  <XCircle className="w-4 h-4" />
                                                )}
                                                Confirm reject
                                              </Button>
                                              <Button
                                                onClick={() => {
                                                  setRejectLeadId(null);
                                                  setRejectReason("");
                                                }}
                                                variant="secondary"
                                                size="sm"
                                                disabled={leadActionId === lead.id}
                                              >
                                                Cancel
                                              </Button>
                                            </div>
                                          </div>
                                        )}
                                      </div>
                                    </div>
                                  </td>
                                </tr>
                              )}
                            </Fragment>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>

                <div className="space-y-3 md:hidden">
                  {assignedLeads.map((lead) => {
                    const canAccept = lead.routingStatus === "supplier_pending";
                    const canReject =
                      lead.routingStatus === "supplier_pending" ||
                      lead.routingStatus === "supplier_accepted";
                    const isRejectingThisLead = rejectLeadId === lead.id;
                    const isExpanded = expandedLeadId === lead.id;

                    return (
                      <div key={lead.id} className="rounded-2xl border border-gray-200 bg-white p-4">
                        <div className="flex items-start justify-between gap-3">
                          <div className="space-y-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                                {lead.requestNumber || "Lead"}
                              </span>
                              <span
                                className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase ${getLeadStatusClass(
                                  lead.routingStatus
                                )}`}
                              >
                                {formatLeadStatusLabel(lead.routingStatus)}
                              </span>
                            </div>
                            <div>
                              <p className="font-semibold text-gray-900">{lead.productName}</p>
                              <p className="text-sm text-gray-600">
                                {formatLeadQuantity(lead)} · {formatLeadCommission(lead)}
                              </p>
                            </div>
                          </div>

                          <Button
                            onClick={() =>
                              setExpandedLeadId((current) =>
                                current === lead.id ? null : lead.id
                              )
                            }
                            variant="secondary"
                            size="sm"
                            className="inline-flex items-center gap-2"
                          >
                            {isExpanded ? (
                              <ChevronUp className="w-4 h-4" />
                            ) : (
                              <ChevronDown className="w-4 h-4" />
                            )}
                            Details
                          </Button>
                        </div>

                        <div className="mt-4 grid gap-3 rounded-xl bg-slate-50 p-3 text-sm text-slate-700">
                          <div className="flex justify-between gap-3">
                            <span className="text-slate-500">Location</span>
                            <span className="text-right font-medium text-slate-900">
                              {formatLeadLocation(lead)}
                            </span>
                          </div>
                          <div className="flex justify-between gap-3">
                            <span className="text-slate-500">Updated</span>
                            <span className="text-right font-medium text-slate-900">
                              {formatDate(lead.lastRoutingUpdatedAt || lead.assignmentPublishedAt)}
                            </span>
                          </div>
                          <div className="flex justify-between gap-3">
                            <span className="text-slate-500">Review</span>
                            <span className="text-right font-medium text-slate-900">
                              {lead.reviewStatus || "pending"}
                            </span>
                          </div>
                        </div>

                        <div className="mt-4 flex flex-wrap gap-2">
                          {canAccept && (
                            <Button
                              onClick={() => void handleSupplierLeadResponse(lead.id, "accept")}
                              disabled={leadActionId === lead.id}
                              size="sm"
                              className="inline-flex items-center gap-2"
                            >
                              {leadActionId === lead.id ? (
                                <Loader2 className="w-4 h-4 animate-spin" />
                              ) : (
                                <CheckCheck className="w-4 h-4" />
                              )}
                              Accept
                            </Button>
                          )}

                          {canReject && !isRejectingThisLead && (
                            <Button
                              onClick={() => {
                                setRejectLeadId(lead.id);
                                setRejectReason(lead.supplierRejectedReason || "");
                                setExpandedLeadId(lead.id);
                              }}
                              variant="secondary"
                              size="sm"
                              disabled={leadActionId === lead.id}
                              className="inline-flex items-center gap-2"
                            >
                              <XCircle className="w-4 h-4" />
                              Reject
                            </Button>
                          )}
                        </div>

                        {isExpanded && (
                          <div className="mt-4 space-y-4">
                            <div className="rounded-xl border border-slate-200 bg-white p-4">
                              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                                Full inquiry
                              </p>
                              <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">
                                {lead.chatMessageText || "No inquiry text captured"}
                              </p>
                            </div>

                            <div className="rounded-xl border border-slate-200 bg-white p-4">
                              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">
                                Farmer details
                              </p>
                              <dl className="mt-3 space-y-2 text-sm text-slate-700">
                                <div className="flex justify-between gap-4">
                                  <dt className="text-slate-500">Name</dt>
                                  <dd className="text-right font-medium text-slate-900">
                                    {lead.farmerProfileSnapshot?.name || "Unknown farmer"}
                                  </dd>
                                </div>
                                <div className="flex justify-between gap-4">
                                  <dt className="text-slate-500">Phone</dt>
                                  <dd className="text-right font-medium text-slate-900">
                                    {lead.farmerProfileSnapshot?.phoneNumber || "Hidden until accepted"}
                                  </dd>
                                </div>
                                {lead.farmerProfileSnapshot?.email && (
                                  <div className="flex justify-between gap-4">
                                    <dt className="text-slate-500">Email</dt>
                                    <dd className="text-right font-medium text-slate-900">
                                      {lead.farmerProfileSnapshot.email}
                                    </dd>
                                  </div>
                                )}
                              </dl>
                            </div>

                            {lead.supplierRejectedReason && (
                              <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                                <span className="font-semibold">Rejection reason:</span>{" "}
                                {lead.supplierRejectedReason}
                              </div>
                            )}

                            {isRejectingThisLead && (
                              <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 space-y-3">
                                <label className="block text-sm font-medium text-amber-900">
                                  Tell admin why you’re rejecting this lead
                                </label>
                                <textarea
                                  className="w-full rounded-lg border border-amber-200 bg-white px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                                  rows={3}
                                  placeholder="Example: Out of coverage area, product unavailable, or capacity full"
                                  value={rejectReason}
                                  onChange={(e) => setRejectReason(e.target.value)}
                                  disabled={leadActionId === lead.id}
                                />
                                <div className="flex flex-wrap items-center gap-3">
                                  <Button
                                    onClick={() => void handleSupplierLeadResponse(lead.id, "reject")}
                                    disabled={leadActionId === lead.id}
                                    variant="secondary"
                                    size="sm"
                                    className="inline-flex items-center gap-2"
                                  >
                                    {leadActionId === lead.id ? (
                                      <Loader2 className="w-4 h-4 animate-spin" />
                                    ) : (
                                      <XCircle className="w-4 h-4" />
                                    )}
                                    Confirm reject
                                  </Button>
                                  <Button
                                    onClick={() => {
                                      setRejectLeadId(null);
                                      setRejectReason("");
                                    }}
                                    variant="secondary"
                                    size="sm"
                                    disabled={leadActionId === lead.id}
                                  >
                                    Cancel
                                  </Button>
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </Card>
        )}

        {user && profile && profile.verificationStatus === "APPROVED" && (
          <Card className="p-6 space-y-6">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-lg font-semibold text-gray-900">
                  Create an offer (draft)
                </p>
                <p className="text-sm text-gray-600">
                  Drafts are saved in Firestore; activation is gated by backend
                  to enforce the 20-active limit.
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Product name</label>
                <input
                  type="text"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  value={offerName}
                  onChange={(e) => setOfferName(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Category</label>
                <select
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  value={offerCategory}
                  onChange={(e) => setOfferCategory(e.target.value)}
                  disabled={busy}
                >
                  <option value="fertilizer">Fertilizer</option>
                  <option value="pesticide">Pesticide</option>
                  <option value="seed">Seed</option>
                  <option value="other">Other</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Price (retail ₹)</label>
                <input
                  type="number"
                  min="0"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  value={offerPrice}
                  onChange={(e) => setOfferPrice(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="grid grid-cols-3 gap-2">
                <div className="col-span-2 space-y-1.5">
                  <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Pack size</label>
                  <input
                    type="number"
                    min="0"
                    className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                    value={offerPackSize}
                    onChange={(e) => setOfferPackSize(e.target.value)}
                    disabled={busy}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Unit</label>
                  <select
                    className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                    value={offerPackUnit}
                    onChange={(e) => setOfferPackUnit(e.target.value)}
                    disabled={busy}
                  >
                    <option value="kg">kg</option>
                    <option value="g">g</option>
                    <option value="l">l</option>
                    <option value="ml">ml</option>
                  </select>
                </div>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">NPK (optional)</label>
                <input
                  type="text"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  placeholder="e.g. 26:26:0"
                  value={offerNpk}
                  onChange={(e) => setOfferNpk(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold uppercase tracking-wide text-gray-500">Keywords</label>
                <input
                  type="text"
                  className="w-full rounded-xl border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
                  placeholder="Comma separated"
                  value={offerKeywords}
                  onChange={(e) => setOfferKeywords(e.target.value)}
                  disabled={busy}
                />
              </div>
            </div>

            <div className="space-y-3 text-center">
              <Button
                onClick={handleCreateOffer}
                disabled={busy}
                className="inline-flex items-center gap-2"
              >
                <Plus className="w-4 h-4" />
                Save draft offer
              </Button>
            </div>
          </Card>
        )}

        {user && offers.length > 0 && (
          <Card className="p-6 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-lg font-semibold text-gray-900">
                  Your offers
                </p>
                <p className="text-sm text-gray-600">
                  Newest first. Status changes to ACTIVE will be handled by
                  backend publishing.
                </p>
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {offers.map((o) => (
                <div
                  key={o.id}
                  className="rounded-xl border border-gray-100 bg-white p-4 shadow-sm hover:shadow-md transition-shadow"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10">
                        <Tag className="w-4 h-4 text-primary" />
                      </div>
                      <div>
                        <p className="font-semibold text-gray-900 leading-tight">{o.productName}</p>
                        <p className="text-xs text-gray-500 capitalize">{o.category}</p>
                      </div>
                    </div>
                    <div className="flex flex-col items-end gap-1.5">
                      <span
                        className={`text-xs font-semibold px-2.5 py-1 rounded-full ${
                          o.status === "ACTIVE"
                            ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                            : o.status === "DRAFT"
                            ? "bg-amber-50 text-amber-700 border border-amber-200"
                            : "bg-gray-50 text-gray-600 border border-gray-200"
                        }`}
                      >
                        {o.status}
                      </span>
                      {o.supplierApproved && o.status === "ACTIVE" && (
                        <span className="text-[11px] text-emerald-600 font-medium">Verified</span>
                      )}
                    </div>
                  </div>
                  {(typeof o.priceRetail === "number" || (o.packSize && o.packUnit)) && (
                    <div className="mt-3 flex items-baseline gap-3 text-sm border-t border-gray-50 pt-3">
                      {typeof o.priceRetail === "number" && (
                        <span className="font-semibold text-gray-900">₹{o.priceRetail}</span>
                      )}
                      {o.packSize && o.packUnit && (
                        <span className="text-gray-500">{o.packSize} {o.packUnit}</span>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </Card>
        )}

        {/* Sign out at the bottom */}
        {user && (
          <div className="flex items-center justify-between rounded-xl border border-gray-100 bg-gray-50/60 px-5 py-4">
            <div className="flex items-center gap-3">
              <ShieldCheck className="w-5 h-5 text-primary/60" />
              <span className="text-sm text-gray-600">{formatUser(user)}</span>
            </div>
            <Button
              onClick={handleSignOut}
              variant="outline"
              size="sm"
              disabled={busy}
              className="inline-flex items-center gap-2"
            >
              <LogOut className="w-4 h-4" />
              Sign out
            </Button>
          </div>
        )}
      </div>

      {/* Invisible reCAPTCHA host */}
      <div id="recaptcha-container" className="hidden" />
    </Container>
  );
}
