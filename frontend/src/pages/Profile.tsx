// ── SmartHire · frontend/src/pages/Profile.tsx ──
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { authApi } from "../utils/api";
import { useAuthStore } from "../store/authStore";
import toast from "react-hot-toast";
import { User, Key, Shield, Copy, Check, Eye, EyeOff, Trash2, Download } from "lucide-react";
import { gdprApi } from "../utils/api";

function Section({ title, icon: Icon, children }: {
  title: string; icon: React.ElementType; children: React.ReactNode;
}) {
  return (
    <div className="bg-[#111827] border border-[#1F2937] rounded-xl overflow-hidden">
      <div className="flex items-center gap-2.5 px-5 py-4 border-b border-[#1F2937]">
        <Icon size={15} className="text-indigo-400" />
        <h2 className="text-sm font-medium text-white">{title}</h2>
      </div>
      <div className="px-5 py-5 space-y-4">{children}</div>

      {/* GDPR */}
      <Section title="Privacy & Data" icon={Trash2}>
        <div className="space-y-3">
          <button
            onClick={async () => {
              const r = await gdprApi.exportMyData();
              const blob = new Blob([JSON.stringify(r, null, 2)], { type: "application/json" });
              const url = URL.createObjectURL(blob);
              const a = document.createElement("a"); a.href = url;
              a.download = "smarthire-my-data.json"; a.click();
              URL.revokeObjectURL(url);
            }}
            className="flex items-center gap-2 w-full px-4 py-2.5 bg-[#0B0F1A] border border-[#374151] hover:border-indigo-500 text-gray-300 text-sm rounded-lg transition-colors">
            <Download size={14} className="text-indigo-400" />
            Download a copy of my data
          </button>

          <div className="border border-red-900/50 rounded-lg p-4 space-y-2">
            <p className="text-xs font-medium text-red-400">Danger Zone</p>
            <p className="text-xs text-gray-500 leading-relaxed">
              Requesting erasure will anonymise all your personal data immediately.
              This action is permanent and cannot be undone. Your application history
              will be retained in anonymised form for audit purposes.
            </p>
            <button
              onClick={async () => {
                if (!window.confirm(
                  "Are you sure you want to permanently delete your personal data? " +
                  "This cannot be undone.")) return;
                try {
                  await gdprApi.eraseMe("User requested erasure from profile settings");
                  window.location.href = "/login";
                } catch {}
              }}
              className="flex items-center gap-2 px-3 py-1.5 bg-red-900/30 hover:bg-red-900/50 border border-red-800 text-red-400 text-xs rounded-lg transition-colors">
              <Trash2 size={12} /> Request data erasure
            </button>
          </div>
        </div>
      </Section>

    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-2 border-b border-[#1F2937]/50 last:border-0">
      <span className="text-sm text-gray-500">{label}</span>
      <span className="text-sm text-gray-200">{value}</span>

      {/* GDPR */}
      <Section title="Privacy & Data" icon={Trash2}>
        <div className="space-y-3">
          <button
            onClick={async () => {
              const r = await gdprApi.exportMyData();
              const blob = new Blob([JSON.stringify(r, null, 2)], { type: "application/json" });
              const url = URL.createObjectURL(blob);
              const a = document.createElement("a"); a.href = url;
              a.download = "smarthire-my-data.json"; a.click();
              URL.revokeObjectURL(url);
            }}
            className="flex items-center gap-2 w-full px-4 py-2.5 bg-[#0B0F1A] border border-[#374151] hover:border-indigo-500 text-gray-300 text-sm rounded-lg transition-colors">
            <Download size={14} className="text-indigo-400" />
            Download a copy of my data
          </button>

          <div className="border border-red-900/50 rounded-lg p-4 space-y-2">
            <p className="text-xs font-medium text-red-400">Danger Zone</p>
            <p className="text-xs text-gray-500 leading-relaxed">
              Requesting erasure will anonymise all your personal data immediately.
              This action is permanent and cannot be undone. Your application history
              will be retained in anonymised form for audit purposes.
            </p>
            <button
              onClick={async () => {
                if (!window.confirm(
                  "Are you sure you want to permanently delete your personal data? " +
                  "This cannot be undone.")) return;
                try {
                  await gdprApi.eraseMe("User requested erasure from profile settings");
                  window.location.href = "/login";
                } catch {}
              }}
              className="flex items-center gap-2 px-3 py-1.5 bg-red-900/30 hover:bg-red-900/50 border border-red-800 text-red-400 text-xs rounded-lg transition-colors">
              <Trash2 size={12} /> Request data erasure
            </button>
          </div>
        </div>
      </Section>

    </div>
  );
}

export default function Profile() {
  const { user, accessToken } = useAuthStore();

  const [currentPwd,  setCurrentPwd]  = useState("");
  const [newPwd,      setNewPwd]      = useState("");
  const [confirmPwd,  setConfirmPwd]  = useState("");
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew,     setShowNew]     = useState(false);
  const [tokenCopied, setTokenCopied] = useState(false);
  const [pwdErrors,   setPwdErrors]   = useState<Record<string, string>>({});

  const changePwdMutation = useMutation({
    mutationFn: () => authApi.changePassword({ currentPassword: currentPwd, newPassword: newPwd }),
    onSuccess: () => {
      toast.success("Password updated successfully");
      setCurrentPwd(""); setNewPwd(""); setConfirmPwd("");
    },
    onError: (err: any) => toast.error(err?.response?.data?.error ?? "Failed to update password"),
  });

  const handleCopyToken = () => {
    if (!accessToken) return;
    navigator.clipboard.writeText(accessToken);
    setTokenCopied(true);
    setTimeout(() => setTokenCopied(false), 2000);
    toast.success("Token copied to clipboard");
  };

  const validatePassword = (): boolean => {
    const e: Record<string, string> = {};
    if (!currentPwd) e.currentPwd = "Current password is required";
    if (newPwd.length < 8) e.newPwd = "New password must be at least 8 characters";
    if (!/[A-Z]/.test(newPwd)) e.newPwd = "Must contain at least one uppercase letter";
    if (!/[0-9]/.test(newPwd)) e.newPwd = "Must contain at least one number";
    if (newPwd !== confirmPwd) e.confirmPwd = "Passwords do not match";
    if (newPwd === currentPwd) e.newPwd = "New password must differ from current password";
    setPwdErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleChangePassword = () => {
    if (!validatePassword()) return;
    changePwdMutation.mutate();
  };

  const inputCls = "w-full bg-[#0B0F1A] border border-[#374151] rounded-lg px-3 py-2 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-indigo-500 transition-colors";

  // Password strength indicator
  const strength = (() => {
    let s = 0;
    if (newPwd.length >= 8)       s++;
    if (/[A-Z]/.test(newPwd))     s++;
    if (/[0-9]/.test(newPwd))     s++;
    if (/[^A-Za-z0-9]/.test(newPwd)) s++;
    return s;
  })();
  const strengthLabel = ["", "Weak", "Fair", "Good", "Strong"][strength];
  const strengthColor = ["", "bg-red-500", "bg-yellow-500", "bg-blue-500", "bg-emerald-500"][strength];

  return (
    <div className="max-w-xl space-y-5">
      <h1 className="text-xl font-semibold text-white">Account Settings</h1>

      {/* Account Info */}
      <Section title="Account" icon={User}>
        <InfoRow label="Name"       value={`${user?.firstName} ${user?.lastName}`} />
        <InfoRow label="Email"      value={user?.email ?? "—"} />
        <InfoRow label="Role"       value={user?.role ?? "—"} />
        <InfoRow label="Last login" value={user?.lastLogin
          ? new Date(user.lastLogin).toLocaleString("en-IN") : "—"} />
      </Section>

      {/* API Access */}
      <Section title="API Access" icon={Key}>
        <div>
          <label className="block text-xs text-gray-500 mb-1.5">Current Access Token</label>
          <div className="flex gap-2">
            <div className="flex-1 bg-[#0B0F1A] border border-[#374151] rounded-lg px-3 py-2 text-xs text-gray-500 font-mono truncate">
              {accessToken ? accessToken.substring(0, 40) + "…" : "No token — please log in again"}
            </div>
            <button onClick={handleCopyToken}
              className="flex items-center gap-1.5 px-3 py-2 bg-[#1F2937] hover:bg-[#374151] text-gray-300 text-xs rounded-lg transition-colors">
              {tokenCopied ? <Check size={13} className="text-emerald-400" /> : <Copy size={13} />}
              {tokenCopied ? "Copied" : "Copy"}
            </button>
          </div>
          <p className="text-xs text-gray-600 mt-1.5">
            Token expires in 15 minutes. Use <code className="text-indigo-400">POST /api/v1/auth/refresh</code> for a new one.
          </p>
        </div>

        <div className="bg-indigo-900/20 border border-indigo-800/50 rounded-lg p-3">
          <p className="text-xs text-indigo-300">
            <strong>Swagger UI:</strong> Visit <a href="/docs" target="_blank" rel="noreferrer"
              className="underline hover:text-white">/docs</a> and click <em>Authorize</em> to test all endpoints interactively.
          </p>
        </div>
      </Section>

      {/* Change Password */}
      <Section title="Change Password" icon={Shield}>
        <div>
          <label className="block text-xs text-gray-400 mb-1.5">Current Password</label>
          <div className="relative">
            <input type={showCurrent ? "text" : "password"} value={currentPwd}
              onChange={e => setCurrentPwd(e.target.value)} placeholder="Enter current password"
              className={inputCls + " pr-10"} />
            <button onClick={() => setShowCurrent(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300">
              {showCurrent ? <EyeOff size={14} /> : <Eye size={14} />}
            </button>
          </div>
          {pwdErrors.currentPwd && <p className="text-xs text-red-400 mt-1">{pwdErrors.currentPwd}</p>}
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1.5">New Password</label>
          <div className="relative">
            <input type={showNew ? "text" : "password"} value={newPwd}
              onChange={e => setNewPwd(e.target.value)} placeholder="Min 8 chars, 1 uppercase, 1 number"
              className={inputCls + " pr-10"} />
            <button onClick={() => setShowNew(v => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-300">
              {showNew ? <EyeOff size={14} /> : <Eye size={14} />}
            </button>
          </div>
          {newPwd.length > 0 && (
            <div className="flex items-center gap-2 mt-1.5">
              <div className="flex-1 h-1 bg-[#1F2937] rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${strengthColor}`}
                  style={{ width: `${(strength / 4) * 100}%` }} />
              </div>
              <span className="text-xs text-gray-500">{strengthLabel}</span>
            </div>
          )}
          {pwdErrors.newPwd && <p className="text-xs text-red-400 mt-1">{pwdErrors.newPwd}</p>}
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1.5">Confirm New Password</label>
          <input type="password" value={confirmPwd}
            onChange={e => setConfirmPwd(e.target.value)} placeholder="Repeat new password"
            className={inputCls} />
          {pwdErrors.confirmPwd && <p className="text-xs text-red-400 mt-1">{pwdErrors.confirmPwd}</p>}
        </div>

        <button onClick={handleChangePassword} disabled={changePwdMutation.isPending}
          className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-sm font-medium rounded-lg transition-colors">
          {changePwdMutation.isPending ? "Updating…" : "Update Password"}
        </button>
      </Section>

      {/* GDPR */}
      <Section title="Privacy & Data" icon={Trash2}>
        <div className="space-y-3">
          <button
            onClick={async () => {
              const r = await gdprApi.exportMyData();
              const blob = new Blob([JSON.stringify(r, null, 2)], { type: "application/json" });
              const url = URL.createObjectURL(blob);
              const a = document.createElement("a"); a.href = url;
              a.download = "smarthire-my-data.json"; a.click();
              URL.revokeObjectURL(url);
            }}
            className="flex items-center gap-2 w-full px-4 py-2.5 bg-[#0B0F1A] border border-[#374151] hover:border-indigo-500 text-gray-300 text-sm rounded-lg transition-colors">
            <Download size={14} className="text-indigo-400" />
            Download a copy of my data
          </button>

          <div className="border border-red-900/50 rounded-lg p-4 space-y-2">
            <p className="text-xs font-medium text-red-400">Danger Zone</p>
            <p className="text-xs text-gray-500 leading-relaxed">
              Requesting erasure will anonymise all your personal data immediately.
              This action is permanent and cannot be undone. Your application history
              will be retained in anonymised form for audit purposes.
            </p>
            <button
              onClick={async () => {
                if (!window.confirm(
                  "Are you sure you want to permanently delete your personal data? " +
                  "This cannot be undone.")) return;
                try {
                  await gdprApi.eraseMe("User requested erasure from profile settings");
                  window.location.href = "/login";
                } catch {}
              }}
              className="flex items-center gap-2 px-3 py-1.5 bg-red-900/30 hover:bg-red-900/50 border border-red-800 text-red-400 text-xs rounded-lg transition-colors">
              <Trash2 size={12} /> Request data erasure
            </button>
          </div>
        </div>
      </Section>

    </div>
  );
}
