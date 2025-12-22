"use client";

import { useEffect, useMemo, useRef, useState } from "react";
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
} from "@/lib/firebase";
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
} from "lucide-react";

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
  businessName: string;
  phone?: string;
  districtId: string;
  districtName?: string;
  verificationStatus: VerificationStatus;
}

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
      setOffers([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign out failed");
    } finally {
      setBusy(false);
    }
  };

  const loadProfile = async (uid: string) => {
    if (!db) return;
    setProfileLoading(true);
    try {
      const snap = await getDoc(doc(db, "suppliers", uid));
      if (snap.exists()) {
        const data = snap.data() as SupplierProfile;
        setProfile(data);
        setBusinessName(data.businessName || "");
        setPhone(data.phone || "");
        setDistrictId(data.districtId || "");
        setDistrictName(data.districtName || "");
      } else {
        setProfile(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load profile");
    } finally {
      setProfileLoading(false);
    }
  };

  useEffect(() => {
    if (!db) return;
    if (user?.uid) {
      loadProfile(user.uid);
      const q = query(
        collection(db, "offers"),
        where("supplierId", "==", user.uid),
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
    } else {
      setProfile(null);
      setOffers([]);
    }
  }, [user?.uid]);

  const handleSaveProfile = async () => {
    if (!user || !db) return;
    if (!businessName || !districtId) {
      setError("Business name and district are required");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const verificationStatus: VerificationStatus =
        profile?.verificationStatus || "PENDING";
      await setDoc(
        doc(db, "suppliers", user.uid),
        {
          ownerUid: user.uid,
          businessName,
          phone,
          districtId,
          districtName,
          verificationStatus,
          updatedAt: serverTimestamp(),
          ...(profile ? {} : { createdAt: serverTimestamp() }),
        },
        { merge: true }
      );
      await loadProfile(user.uid);
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
      const priceNumber = Number(offerPrice) || 0;
      const packNumber = offerPackSize ? Number(offerPackSize) : null;
      const npkKey = offerNpk
        ? offerNpk.replace(/\s+/g, "").replace(/:/g, "-").toLowerCase()
        : undefined;
      const priceNormalized =
        packNumber && packNumber > 0 ? priceNumber / packNumber : priceNumber;
      await addDoc(collection(db, "offers"), {
        supplierId: user.uid,
        supplierName: profile.businessName,
        supplierApproved: false,
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
        status: "DRAFT",
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

  const actionArea = useMemo(() => {
    if (loading) {
      return (
        <div className="flex items-center gap-2 text-gray-600">
          <Loader2 className="w-5 h-5 animate-spin" />
          Checking session...
        </div>
      );
    }

    if (user) {
      const isProfileComplete = profile && profile.businessName && profile.districtId;
      const isApproved = profile?.verificationStatus === "APPROVED";

      // Hide this card entirely when approved
      if (isApproved) return null;

      return (
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
                <li>Wait for admin approval (done manually via console for now).</li>
                <li>Add offers (max 20 active). Activation will be enforced via backend.</li>
              </ul>
            </div>
          )}
          {isProfileComplete && !isApproved && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 text-sm text-amber-800">
              <p className="font-semibold mb-1">Awaiting approval</p>
              <p className="text-amber-900">
                Your profile is complete. Please wait for admin approval before adding offers.
              </p>
            </div>
          )}
        </div>
      );
    }

    return (
      <div className="space-y-4">
        <Button
          onClick={handleGoogleSignIn}
          disabled={busy}
          className="w-full inline-flex items-center justify-center gap-2"
        >
          <Mail className="w-4 h-4" />
          Continue with Google
        </Button>

        <div className="relative">
          <div className="absolute inset-y-0 left-0 flex items-center">
            <span className="text-gray-400 text-sm">Phone OTP</span>
          </div>
          <div className="flex flex-col gap-2 pt-5">
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
      </div>
    );
  }, [busy, loading, otp, phoneNumber, phoneStep, user, profile]);

  return (
    <Container className="py-16">
      <div className="max-w-3xl mx-auto space-y-8">
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
              <div className="space-y-2">
                <label className="text-sm text-gray-600">Business name</label>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  value={businessName}
                  onChange={(e) => setBusinessName(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-600">Phone</label>
                <input
                  type="tel"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-600">
                  District ID (e.g., MH:pune)
                </label>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  placeholder="MH:pune"
                  value={districtId}
                  onChange={(e) => setDistrictId(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-600">
                  District name (display)
                </label>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
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
              <div className="space-y-2">
                <label className="text-sm text-gray-600">Product name</label>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  value={offerName}
                  onChange={(e) => setOfferName(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-600">Category</label>
                <select
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
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
              <div className="space-y-2">
                <label className="text-sm text-gray-600">Price (retail)</label>
                <input
                  type="number"
                  min="0"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  value={offerPrice}
                  onChange={(e) => setOfferPrice(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="grid grid-cols-3 gap-2">
                <div className="col-span-2 space-y-2">
                  <label className="text-sm text-gray-600">Pack size</label>
                  <input
                    type="number"
                    min="0"
                    className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                    value={offerPackSize}
                    onChange={(e) => setOfferPackSize(e.target.value)}
                    disabled={busy}
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm text-gray-600">Unit</label>
                  <select
                    className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
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
              <div className="space-y-2">
                <label className="text-sm text-gray-600">
                  NPK (optional, e.g., 26:26:0)
                </label>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
                  value={offerNpk}
                  onChange={(e) => setOfferNpk(e.target.value)}
                  disabled={busy}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm text-gray-600">
                  Keywords (comma separated)
                </label>
                <input
                  type="text"
                  className="w-full rounded-lg border border-gray-200 px-4 py-3 focus:border-primary focus:ring-2 focus:ring-primary/20 outline-none"
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
              <p className="text-sm text-gray-600">
                Drafts are saved. Publishing/activation is handled by backend
                and enforces the 20-active limit.
              </p>
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
            <div className="space-y-3">
              {offers.map((o) => (
                <div
                  key={o.id}
                  className="flex flex-col md:flex-row md:items-center md:justify-between gap-2 border border-gray-100 rounded-lg p-3"
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <Tag className="w-4 h-4 text-primary" />
                      <span className="font-semibold text-gray-900">
                        {o.productName}
                      </span>
                      <span className="text-xs uppercase bg-gray-100 text-gray-700 px-2 py-1 rounded">
                        {o.category}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 flex flex-wrap gap-3">
                      {typeof o.priceRetail === "number" && (
                        <span>₹{o.priceRetail}</span>
                      )}
                      {o.packSize && o.packUnit && (
                        <span>
                          {o.packSize} {o.packUnit}
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <span
                      className={`text-xs font-semibold px-3 py-1 rounded-full ${
                        o.status === "ACTIVE"
                          ? "bg-green-50 text-green-700"
                          : o.status === "DRAFT"
                          ? "bg-amber-50 text-amber-700"
                          : "bg-gray-100 text-gray-700"
                      }`}
                    >
                      {o.status}
                    </span>
                    {o.supplierApproved && o.status === "ACTIVE" && (
                      <span className="text-xs text-green-700 bg-green-50 border border-green-200 px-2 py-1 rounded">
                        Approved
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </Card>
        )}

        {/* Sign out at the bottom */}
        {user && (
          <div className="pt-8 border-t border-gray-200">
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
      </div>

      {/* Invisible reCAPTCHA host */}
      <div id="recaptcha-container" className="hidden" />
    </Container>
  );
}
