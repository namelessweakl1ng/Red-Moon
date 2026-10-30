import { useState } from "react";
import { Archive, ArrowDownToLine, ShieldAlert } from "lucide-react";
import type { Call } from "../lib/types";
import { errorMessage } from "../lib/types";
import { Field, Section } from "../components/Field";
export function Restore({
  call,
  exists,
  onRestored,
}: {
  call: Call;
  exists: boolean;
  onRestored: () => Promise<void>;
}) {
  const [password, setPassword] = useState(""),
    [current, setCurrent] = useState(""),
    [confirmed, setConfirmed] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setError("");
        setBusy(true);
        try {
          const r = await call<{ cancelled?: boolean }>({
            action: "restore",
            password,
            current: exists ? current : null,
          });
          if (!r.cancelled) {
            setPassword("");
            setCurrent("");
            await onRestored();
          }
        } catch (e) {
          setError(errorMessage(e));
        } finally {
          setBusy(false);
        }
      }}
    >
      <h2>Restore encrypted backup</h2>
      <p className="muted">
        Choose a .rm file. Its password may be different from your current
        master password.
      </p>
      {exists && (
        <Field
          label="Current master password"
          type="password"
          autoComplete="off"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          required
        />
      )}
      <Field
        label="Backup master password"
        type="password"
        autoComplete="off"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
      />
      <label className="check">
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => setConfirmed(e.target.checked)}
        />
        I understand this replaces my vault. An encrypted safety copy will be
        kept when replacing an existing vault.
      </label>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <button type="submit" className="secondary" disabled={busy || !confirmed}>
        {busy ? "Validating backup…" : "Choose backup and restore"}
      </button>
    </form>
  );
}
export default function Backups({
  call,
  onRestored,
  notify,
}: {
  call: Call;
  onRestored: () => Promise<void>;
  notify: (s: string) => void;
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <Section eyebrow="KEEP A SECOND COPY" title="Backups">
      <p className="lead">Encrypted here. Protected everywhere.</p>
      <div className="settings-grid">
        <div>
          <Archive className="section-icon" />
          <h2>Create encrypted backup</h2>
          <p className="muted">
            Save a portable, fully encrypted copy to a location you control.
            Store it on a separate device.
          </p>
          <button
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError("");
              try {
                const r = await call<{ cancelled?: boolean }>({
                  action: "backup",
                });
                if (!r.cancelled) notify("Encrypted backup saved.");
              } catch (e) {
                setError(errorMessage(e));
              } finally {
                setBusy(false);
              }
            }}
          >
            <ArrowDownToLine size={16} />
            {busy ? "Saving…" : "Save encrypted backup"}
          </button>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <div className="notice">
            <ShieldAlert size={18} />
            <p>
              Existing backups keep their original password after you change
              your master password. There is no recovery service if you forget
              it.
            </p>
          </div>
        </div>
        <Restore call={call} exists onRestored={onRestored} />
      </div>
    </Section>
  );
}
