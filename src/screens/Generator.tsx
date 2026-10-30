import { useState } from "react";
import { Copy, RefreshCw } from "lucide-react";
import { Section } from "../components/Field";
import { errorMessage, strength, type Call } from "../lib/types";
export interface Options {
  length: number;
  lowercase: boolean;
  uppercase: boolean;
  digits: boolean;
  symbols: boolean;
}
export const defaultOptions = (): Options => ({
  length: 24,
  lowercase: true,
  uppercase: true,
  digits: true,
  symbols: true,
});
export function GenerationControls({
  value,
  onChange,
}: {
  value: Options;
  onChange: (o: Options) => void;
}) {
  return (
    <>
      <label className="field">
        <span>
          Length <strong>{value.length}</strong>
        </span>
        <input
          type="range"
          min={12}
          max={128}
          value={value.length}
          onChange={(e) =>
            onChange({ ...value, length: Number(e.target.value) })
          }
        />
      </label>
      <div className="character-types">
        {(["lowercase", "uppercase", "digits", "symbols"] as const).map((k) => (
          <label className="check" key={k}>
            <input
              type="checkbox"
              checked={value[k]}
              onChange={(e) => onChange({ ...value, [k]: e.target.checked })}
            />
            {
              {
                lowercase: "a–z",
                uppercase: "A–Z",
                digits: "0–9",
                symbols: "!@#$",
              }[k]
            }
          </label>
        ))}
      </div>
    </>
  );
}
export default function Generator({
  call,
  notify,
}: {
  call: Call;
  notify: (s: string) => void;
}) {
  const [options, setOptions] = useState(defaultOptions),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <Section eyebrow="UNPREDICTABLE BY NATURE" title="Password generator">
      <p className="lead">One password. One purpose.</p>
      <div className="generator-panel">
        <div className="generated-password" aria-live="polite">
          {password || (
            <span className="muted">Generate something unguessable.</span>
          )}
        </div>
        <p className="hint">
          {password
            ? strength(password)
            : "Generated in Rust with operating-system randomness."}
        </p>
        <GenerationControls value={options} onChange={setOptions} />
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="actions">
          <button
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError("");
              try {
                const r = await call<{ password: string }>({
                  action: "generate",
                  options,
                });
                setPassword(r.password);
              } catch (e) {
                setError(errorMessage(e));
              } finally {
                setBusy(false);
              }
            }}
          >
            <RefreshCw size={16} />
            {busy ? "Generating…" : "Generate password"}
          </button>
          <button
            className="secondary"
            disabled={!password}
            onClick={async () => {
              try {
                await call({ action: "copy_generated", password });
                notify("Copied. Clipboard cleanup scheduled in 20 seconds.");
              } catch (e) {
                setError(errorMessage(e));
              }
            }}
          >
            <Copy size={16} />
            Copy password
          </button>
        </div>
        <p className="muted small">
          Use a unique password for every account. Length and randomness matter
          more than visual complexity. Strength feedback is an estimate.
        </p>
      </div>
    </Section>
  );
}
