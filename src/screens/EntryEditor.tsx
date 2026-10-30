import { useEffect, useRef, useState } from "react";
import { X, RefreshCw, Eye, EyeOff } from "lucide-react";
import { Field } from "../components/Field";
import { GenerationControls, defaultOptions } from "./Generator";
import { errorMessage, strength, type Call, type Entry } from "../lib/types";
export default function EntryEditor({
  initial,
  call,
  onClose,
  onSaved,
}: {
  initial: Entry;
  call: Call;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [entry, setEntry] = useState(initial),
    [tags, setTags] = useState(initial.tags.join(", ")),
    [visible, setVisible] = useState(false),
    [generating, setGenerating] = useState(false),
    [options, setOptions] = useState(defaultOptions),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const root = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const old = document.activeElement as HTMLElement | null;
    root.current?.querySelector("input")?.focus();
    return () => old?.focus();
  }, []);
  const update = (key: keyof Entry, value: string) =>
    setEntry({ ...entry, [key]: value });
  return (
    <div
      className="modal-backdrop"
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <div
        ref={root}
        role="dialog"
        aria-modal="true"
        aria-labelledby="editor-title"
        className="editor"
        onKeyDown={(e) => {
          if (e.key === "Escape" && !busy) onClose();
          if (e.key === "Tab") {
            const focusable = root.current?.querySelectorAll<HTMLElement>(
              "button:not(:disabled),input:not(:disabled),textarea",
            );
            if (!focusable?.length) return;
            const first = focusable[0],
              last = focusable[focusable.length - 1];
            if (e.shiftKey && document.activeElement === first) {
              e.preventDefault();
              last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault();
              first.focus();
            }
          }
        }}
      >
        <header>
          <div>
            <p className="eyebrow">ENCRYPTED ON SAVE</p>
            <h2 id="editor-title">
              {entry.id ? "Edit password" : "New password"}
            </h2>
          </div>
          <button
            className="icon-button"
            aria-label="Close editor"
            disabled={busy}
            onClick={onClose}
          >
            <X size={20} />
          </button>
        </header>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError("");
            try {
              await call({
                action: "save",
                entry: {
                  ...entry,
                  tags: tags
                    .split(",")
                    .map((s) => s.trim())
                    .filter(Boolean),
                },
              });
              await onSaved();
              onClose();
            } catch (e) {
              setError(errorMessage(e));
            } finally {
              setBusy(false);
            }
          }}
        >
          <Field
            label="Website or app name"
            value={entry.title}
            maxLength={512}
            onChange={(e) => update("title", e.target.value)}
            required
          />
          <Field
            label="Username or email"
            value={entry.username}
            maxLength={4096}
            onChange={(e) => update("username", e.target.value)}
            autoComplete="off"
          />
          <div className="password-input">
            <Field
              label="Password"
              type={visible ? "text" : "password"}
              value={entry.password}
              maxLength={4096}
              onChange={(e) => update("password", e.target.value)}
              autoComplete="new-password"
            />
            <button
              type="button"
              className="icon-button"
              aria-label={visible ? "Hide password" : "Show password"}
              onClick={() => setVisible(!visible)}
            >
              {visible ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
          <p className="hint">{strength(entry.password)}</p>
          <button
            type="button"
            className="text-button"
            onClick={() => setGenerating(!generating)}
          >
            <RefreshCw size={14} />
            Generate a password
          </button>
          {generating && (
            <div className="inline-generator">
              <GenerationControls value={options} onChange={setOptions} />
              <button
                type="button"
                className="secondary"
                onClick={async () => {
                  try {
                    const r = await call<{ password: string }>({
                      action: "generate",
                      options,
                    });
                    update("password", r.password);
                  } catch (e) {
                    setError(errorMessage(e));
                  }
                }}
              >
                Generate and use
              </button>
            </div>
          )}
          <Field
            label="URL"
            value={entry.url}
            maxLength={4096}
            placeholder="https://example.com"
            onChange={(e) => update("url", e.target.value)}
          />
          <div className="form-row">
            <Field
              label="Category"
              value={entry.category}
              maxLength={256}
              onChange={(e) => update("category", e.target.value)}
            />
            <Field
              label="Tags (comma separated)"
              value={tags}
              onChange={(e) => setTags(e.target.value)}
            />
          </div>
          <label className="field">
            <span>Secure notes</span>
            <textarea
              rows={4}
              value={entry.notes}
              maxLength={65536}
              onChange={(e) => update("notes", e.target.value)}
            />
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={entry.favorite}
              onChange={(e) =>
                setEntry({ ...entry, favorite: e.target.checked })
              }
            />
            Add to favorites
          </label>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <footer>
            <button
              className="secondary"
              type="button"
              disabled={busy}
              onClick={onClose}
            >
              Cancel
            </button>
            <button type="submit" disabled={busy}>
              {busy ? "Encrypting…" : "Save password"}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
