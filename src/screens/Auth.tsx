import { useState } from "react";
import { ArrowRight, LockKeyhole, ShieldCheck } from "lucide-react";
import type { Call } from "../lib/types";
import { errorMessage, strength } from "../lib/types";
import { Field } from "../components/Field";
import { Restore } from "./Backups";
export default function Auth({
  exists,
  call,
  onUnlocked,
}: {
  exists: boolean;
  call: Call;
  onUnlocked: () => Promise<void>;
}) {
  const [password, setPassword] = useState(""),
    [confirm, setConfirm] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [restore, setRestore] = useState(false);
  return (
    <div className="auth-shell">
      <aside className="auth-art">
        <div className="brand">
          <img src="/red-moon.svg" alt="" />
          <span>RED MOON</span>
        </div>
        <div className="lunar-mark">
          <img src="/red-moon.svg" alt="Crimson crescent with a keyhole" />
        </div>
        <p className="eyebrow">PRIVATE BY DESIGN</p>
        <p className="art-statement">
          A quiet place
          <br />
          for your secrets.
        </p>
        <p className="muted">No accounts. No cloud. Only you.</p>
        <div className="auth-foot">
          <span className="red-dot" /> OFFLINE · LOCAL · ENCRYPTED
        </div>
      </aside>
      <main className="auth-main">
        <div className="auth-form">
          <p className="eyebrow">
            {restore
              ? "VAULT RECOVERY"
              : exists
                ? "WELCOME BACK"
                : "INTRODUCING RED MOON"}
          </p>
          <h1>
            {restore
              ? "Bring your vault home."
              : exists
                ? "Unlock your vault."
                : "Your secrets. Your control."}
          </h1>
          <p className="muted">
            {restore
              ? "Restore only from an encrypted Red Moon backup."
              : exists
                ? "Your encrypted vault stays on this device."
                : "Create a master password. Everything in your vault is encrypted before it reaches your disk."}
          </p>
          {restore ? (
            <>
              <Restore call={call} exists={exists} onRestored={onUnlocked} />
              <button className="text-button" onClick={() => setRestore(false)}>
                Back to {exists ? "unlock" : "setup"}
              </button>
            </>
          ) : (
            <>
              <form
                onSubmit={async (e) => {
                  e.preventDefault();
                  setError("");
                  if (!exists && password !== confirm) {
                    setError("Master passwords must match.");
                    return;
                  }
                  if (!exists && [...password].length < 14) {
                    setError(
                      "Use at least 14 characters. A long passphrase works well.",
                    );
                    return;
                  }
                  setBusy(true);
                  try {
                    await call({
                      action: exists ? "unlock" : "create",
                      password,
                    });
                    setPassword("");
                    setConfirm("");
                    await onUnlocked();
                  } catch (e) {
                    setError(errorMessage(e));
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                <Field
                  label="Master password"
                  type="password"
                  autoFocus
                  autoComplete={exists ? "current-password" : "new-password"}
                  maxLength={1024}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  disabled={busy}
                />
                {!exists && (
                  <>
                    <p className="hint">{strength(password)}</p>
                    <Field
                      label="Confirm master password"
                      type="password"
                      autoComplete="new-password"
                      value={confirm}
                      onChange={(e) => setConfirm(e.target.value)}
                      required
                      disabled={busy}
                    />
                  </>
                )}
                {error && (
                  <p className="error" role="alert">
                    {error}
                  </p>
                )}
                <button className="primary wide" disabled={busy} type="submit">
                  {busy
                    ? "Deriving encryption key…"
                    : exists
                      ? "Unlock vault"
                      : "Create encrypted vault"}
                  <ArrowRight size={17} />
                </button>
              </form>
              <div className="auth-warning">
                <LockKeyhole size={17} />
                <p>
                  If you forget your master password, your vault may be
                  permanently unrecoverable. Red Moon cannot reset it.
                </p>
              </div>
              <button
                className="text-button"
                onClick={() => {
                  setPassword("");
                  setConfirm("");
                  setRestore(true);
                }}
              >
                Restore an encrypted backup
              </button>
            </>
          )}
          <div className="crypto-caption">
            <ShieldCheck size={14} /> ARGON2ID + XCHACHA20-POLY1305
          </div>
        </div>
      </main>
    </div>
  );
}
