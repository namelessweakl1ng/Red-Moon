import { useState } from "react";
import { ShieldCheck, KeyRound } from "lucide-react";
import { Field, Section } from "../components/Field";
import {
  errorMessage,
  type Call,
  type Settings as Preferences,
} from "../lib/types";
export default function Settings({
  settings,
  call,
  onChanged,
  notify,
}: {
  settings: Preferences;
  call: Call;
  onChanged: () => Promise<void>;
  notify: (s: string) => void;
}) {
  const [timeout, setTimeout] = useState(settings.auto_lock_seconds),
    [theme, setTheme] = useState(settings.theme),
    [current, setCurrent] = useState(""),
    [password, setPassword] = useState(""),
    [confirmation, setConfirmation] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <Section eyebrow="YOUR VAULT, YOUR RULES" title="Settings">
      <div className="settings-grid">
        <div>
          <h2>Security & appearance</h2>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError("");
              try {
                await call({
                  action: "settings",
                  settings: { auto_lock_seconds: timeout, theme },
                });
                await onChanged();
                notify("Preferences saved inside your encrypted vault.");
              } catch (e) {
                setError(errorMessage(e));
              } finally {
                setBusy(false);
              }
            }}
          >
            <label className="field">
              <span>Automatically lock after</span>
              <select
                value={timeout}
                onChange={(e) => setTimeout(Number(e.target.value))}
              >
                {[
                  [30, "30 seconds"],
                  [60, "1 minute"],
                  [300, "5 minutes (default)"],
                  [600, "10 minutes"],
                  [1800, "30 minutes"],
                  [3600, "1 hour"],
                ].map(([n, label]) => (
                  <option key={n} value={n}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Theme</span>
              <select
                value={theme}
                onChange={(e) =>
                  setTheme(e.target.value as Preferences["theme"])
                }
              >
                <option value="dark">Red Moon dark</option>
                <option value="contrast">High contrast</option>
                <option value="system">System contrast preference</option>
              </select>
            </label>
            <p className="muted small">
              The vault also locks when this app leaves the foreground. Native
              lock and suspend hooks provide additional protection on Windows.
            </p>
            <button disabled={busy}>Save preferences</button>
          </form>
          <div className="security-info">
            <ShieldCheck size={20} />
            <h2>Built to stay local</h2>
            <p>
              No accounts, synchronization, analytics or network services. Your
              entire vault is encrypted with Argon2id and XChaCha20-Poly1305.
            </p>
            <p>
              A locked vault protects against file theft. Malware, keyloggers,
              clipboard managers and a compromised operating system can still
              expose your secrets. This software has not been independently
              audited.
            </p>
            <p>
              Never share your master password. There is no reset or recovery
              backdoor.
            </p>
          </div>
        </div>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setError("");
            if (password !== confirmation) {
              setError("New master passwords must match.");
              return;
            }
            setBusy(true);
            try {
              await call({ action: "change_password", current, new: password });
              setCurrent("");
              setPassword("");
              setConfirmation("");
              notify(
                "Master password changed. Older backups retain their original password.",
              );
            } catch (e) {
              setError(errorMessage(e));
            } finally {
              setBusy(false);
            }
          }}
        >
          <KeyRound className="section-icon" />
          <h2>Change master password</h2>
          <p className="muted">
            A fresh salt and encryption key will protect your vault. Keep old
            backup passwords until those backups are replaced.
          </p>
          <Field
            label="Current master password"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            required
          />
          <Field
            label="New master password"
            type="password"
            autoComplete="new-password"
            minLength={14}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Field
            label="Confirm new master password"
            type="password"
            autoComplete="new-password"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            required
          />
          <button disabled={busy}>
            {busy ? "Working…" : "Change master password"}
          </button>
        </form>
      </div>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
    </Section>
  );
}
