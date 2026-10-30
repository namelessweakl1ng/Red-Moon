import { useCallback, useEffect, useRef, useState } from "react";
import {
  Archive,
  ArrowUpRight,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  Folder,
  KeyRound,
  LockKeyhole,
  Plus,
  Search,
  Settings as SettingsIcon,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  Pencil,
  X,
} from "lucide-react";
import type { Call, Entry, EntrySummary, Snapshot } from "../lib/types";
import { emptyEntry, errorMessage } from "../lib/types";
import Generator from "./Generator";
import Settings from "./Settings";
import Backups from "./Backups";
import EntryEditor from "./EntryEditor";
type Page =
  "all" | "favorites" | "categories" | "generator" | "backups" | "settings";
const date = (n: number) =>
  new Date(n * 1000).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
export default function Dashboard({
  call,
  onLock,
  onRestored,
  notify,
}: {
  call: Call;
  onLock: () => void;
  onRestored: () => Promise<void>;
  notify: (s: string) => void;
}) {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null),
    [page, setPage] = useState<Page>("all"),
    [query, setQuery] = useState(""),
    [category, setCategory] = useState(""),
    [selected, setSelected] = useState<string | null>(null),
    [notes, setNotes] = useState(""),
    [revealed, setRevealed] = useState(""),
    [editor, setEditor] = useState<Entry | null>(null),
    [deleting, setDeleting] = useState<EntrySummary | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const detailRequest = useRef(0);
  const selection = useRef(selected);
  const load = useCallback(async () => {
    const s = await call<Snapshot>({ action: "list" });
    setSnapshot(s);
  }, [call]);
  useEffect(() => {
    void call<Snapshot>({ action: "list" })
      .then(setSnapshot)
      .catch((e) => setError(errorMessage(e)));
  }, [call]);
  useEffect(() => {
    document.documentElement.dataset.theme = snapshot?.settings.theme ?? "dark";
  }, [snapshot?.settings.theme]);
  const choose = (id: string | null) => {
    if (selection.current === id) return;
    selection.current = id;
    detailRequest.current++;
    setNotes("");
    setRevealed("");
    setSelected(id);
  };
  useEffect(() => {
    const request = ++detailRequest.current;
    if (selected)
      void call<{ notes: string }>({ action: "detail", id: selected })
        .then((r) => {
          if (request === detailRequest.current) setNotes(r.notes);
        })
        .catch((e) => {
          if (request === detailRequest.current) setError(errorMessage(e));
        });
    return () => {
      if (detailRequest.current === request)
        detailRequest.current = request + 1;
    };
  }, [selected, call]);
  const entries = snapshot?.entries ?? [];
  const categories = [
    ...new Set(entries.map((e) => e.category).filter(Boolean)),
  ].sort();
  const filtered = entries.filter(
    (e) =>
      (page !== "favorites" || e.favorite) &&
      (page !== "categories" || !category || e.category === category) &&
      [e.title, e.username, e.url, e.category, ...e.tags]
        .join(" ")
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  const entry = entries.find((e) => e.id === selected);
  const run = async (operation: () => Promise<void>) => {
    setBusy(true);
    setError("");
    try {
      await operation();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  const copy = async (password: boolean) => {
    if (!entry) return;
    await call({
      action: password ? "copy_password" : "copy_username",
      id: entry.id,
    });
    notify(
      password
        ? "Password copied. Cleanup scheduled in 20 seconds."
        : "Username copied.",
    );
  };
  const edit = async () => {
    if (!entry) return;
    const id = entry.id;
    const r = await call<{ password: string }>({ action: "reveal", id });
    if (selection.current === id)
      setEditor({ ...entry, password: r.password, notes });
  };
  const nav: [Page, string, typeof KeyRound][] = [
    ["all", "All passwords", KeyRound],
    ["favorites", "Favorites", Star],
    ["categories", "Categories", Folder],
    ["generator", "Password generator", Sparkles],
    ["backups", "Backups", Archive],
    ["settings", "Settings", SettingsIcon],
  ];
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <img src="/red-moon.svg" alt="" />
          <span>RED MOON</span>
        </div>
        <p className="sidebar-caption">PERSONAL VAULT</p>
        <nav aria-label="Main navigation">
          {nav.map(([id, label, Icon]) => (
            <button
              key={id}
              className={page === id ? "nav-item active" : "nav-item"}
              onClick={() => {
                setPage(id);
                setError("");
              }}
            >
              <Icon size={17} />
              <span>{label}</span>
              {id === "all" && <small>{entries.length}</small>}
              {id === "favorites" && (
                <small>{entries.filter((e) => e.favorite).length}</small>
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="local-indicator">
            <span className="red-dot" />
            <span>LOCAL & ENCRYPTED</span>
          </div>
          <button className="nav-item lock-button" onClick={onLock}>
            <LockKeyhole size={17} />
            Lock vault
          </button>
          <p>YOUR SECRETS. YOUR CONTROL.</p>
        </div>
      </aside>
      <main className="workspace">
        <header className="topbar">
          <span>
            Vault <ChevronRight size={12} />{" "}
            {nav.find(([id]) => id === page)?.[1]}
          </span>
          <div>
            <ShieldCheck size={14} />
            <span>Stored on this device</span>
          </div>
        </header>
        {error && (
          <div role="alert" className="error banner">
            {error}
            <button
              className="icon-button"
              aria-label="Dismiss error"
              onClick={() => setError("")}
            >
              <X size={14} />
            </button>
          </div>
        )}
        {(["all", "favorites", "categories"] as Page[]).includes(page) ? (
          <>
            <div className="vault-heading">
              <div>
                <p className="eyebrow">YOUR PRIVATE SPACE</p>
                <h1>
                  {page === "favorites"
                    ? "Favorites"
                    : page === "categories"
                      ? "Categories"
                      : "All passwords"}
                  <span className="heading-count">{filtered.length}</span>
                </h1>
                <p className="muted">
                  A little less to remember. A lot more peace of mind.
                </p>
              </div>
              <button disabled={busy} onClick={() => setEditor(emptyEntry())}>
                <Plus size={17} />
                New password
              </button>
            </div>
            <div className="vault-toolbar">
              <label className="search">
                <Search size={17} />
                <input
                  aria-label="Search passwords"
                  placeholder="Search names, usernames, tags…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
                <kbd>/</kbd>
              </label>
              {page === "categories" && (
                <select
                  aria-label="Filter category"
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                >
                  <option value="">All categories</option>
                  {categories.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
              )}
              <span className="muted small">
                {entries.length} saved · encrypted at rest
              </span>
            </div>
            <div className="vault-columns">
              <section className="entry-list" aria-label="Saved passwords">
                <div className="list-label">
                  <span>ACCOUNT</span>
                  <span>
                    {page === "favorites" ? "FAVORITES" : "ALL ITEMS"}
                  </span>
                </div>
                {!snapshot ? (
                  <p className="empty">Opening your vault…</p>
                ) : filtered.length === 0 ? (
                  <div className="empty">
                    <KeyRound size={28} />
                    <h2>
                      {entries.length
                        ? "No matching passwords"
                        : "Start with your first secret."}
                    </h2>
                    <p>
                      {entries.length
                        ? "Try a different search or filter."
                        : "Add an account. Red Moon will keep it encrypted on this device."}
                    </p>
                    {!entries.length && (
                      <button
                        className="secondary"
                        onClick={() => setEditor(emptyEntry())}
                      >
                        <Plus size={16} />
                        Add a password
                      </button>
                    )}
                  </div>
                ) : (
                  filtered.map((e) => (
                    <button
                      className={`entry-row ${selected === e.id ? "selected" : ""}`}
                      key={e.id}
                      onClick={() => choose(e.id)}
                    >
                      <span className="account-initial" aria-hidden="true">
                        {e.title.charAt(0).toUpperCase()}
                      </span>
                      <span className="entry-text">
                        <strong>{e.title}</strong>
                        <small>{e.username || "No username"}</small>
                      </span>
                      {e.duplicate && (
                        <span
                          className="duplicate-mark"
                          title="Possible duplicate"
                        >
                          !
                        </span>
                      )}
                      {e.favorite ? (
                        <Star
                          size={14}
                          className="favorite-star"
                          fill="currentColor"
                        />
                      ) : (
                        <ChevronRight size={15} />
                      )}
                    </button>
                  ))
                )}
              </section>
              <section className="entry-detail" aria-label="Password details">
                {entry ? (
                  <>
                    <header className="detail-heading">
                      <span
                        className="account-initial large"
                        aria-hidden="true"
                      >
                        {entry.title.charAt(0).toUpperCase()}
                      </span>
                      <div>
                        <p className="eyebrow">
                          {entry.category || "UNCATEGORIZED"}
                        </p>
                        <h2>{entry.title}</h2>
                      </div>
                      <button
                        aria-label={
                          entry.favorite
                            ? "Remove from favorites"
                            : "Add to favorites"
                        }
                        className="icon-button"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await call({ action: "favorite", id: entry.id });
                            await load();
                          })
                        }
                      >
                        <Star
                          size={18}
                          fill={entry.favorite ? "currentColor" : "none"}
                        />
                      </button>
                    </header>
                    {entry.duplicate && (
                      <p className="notice compact">
                        Possible duplicate: another entry has the same name,
                        username and URL.
                      </p>
                    )}
                    <div className="detail-field">
                      <span>USERNAME</span>
                      <div>
                        <p>{entry.username || "—"}</p>
                        <button
                          className="icon-button"
                          aria-label="Copy username"
                          onClick={() => void run(() => copy(false))}
                        >
                          <Copy size={16} />
                        </button>
                      </div>
                    </div>
                    <div className="detail-field">
                      <span>PASSWORD</span>
                      <div>
                        <p className="secret-value">
                          {revealed || "••••••••••••••••"}
                        </p>
                        <button
                          className="icon-button"
                          aria-label={
                            revealed ? "Hide password" : "Reveal password"
                          }
                          onClick={() =>
                            void run(async () => {
                              if (revealed) {
                                setRevealed("");
                                return;
                              }
                              const id = entry.id;
                              const request = detailRequest.current;
                              const r = await call<{ password: string }>({
                                action: "reveal",
                                id,
                              });
                              if (
                                selection.current === id &&
                                request === detailRequest.current
                              )
                                setRevealed(r.password);
                            })
                          }
                        >
                          {revealed ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                        <button
                          className="icon-button"
                          aria-label="Copy password"
                          onClick={() => void run(() => copy(true))}
                        >
                          <Copy size={16} />
                        </button>
                      </div>
                    </div>
                    <div className="detail-field">
                      <span>WEBSITE</span>
                      <p className="url-text">
                        {entry.url || "—"}
                        {entry.url && <ArrowUpRight size={14} />}
                      </p>
                    </div>
                    <div className="detail-field">
                      <span>TAGS</span>
                      <div className="tags">
                        {entry.tags.length ? (
                          entry.tags.map((t) => <span key={t}>{t}</span>)
                        ) : (
                          <p className="muted">—</p>
                        )}
                      </div>
                    </div>
                    <div className="detail-field">
                      <span>SECURE NOTES</span>
                      <p className="notes">{notes || "No notes added."}</p>
                    </div>
                    <div className="entry-dates">
                      <span>Created {date(entry.created_at)}</span>
                      <span>Modified {date(entry.modified_at)}</span>
                    </div>
                    <div className="detail-actions">
                      <button
                        className="secondary"
                        disabled={busy}
                        onClick={() => void run(edit)}
                      >
                        <Pencil size={15} />
                        Edit entry
                      </button>
                      <button
                        className="icon-button danger"
                        aria-label="Delete entry"
                        disabled={busy}
                        onClick={() => setDeleting(entry)}
                      >
                        <Trash2 size={17} />
                      </button>
                    </div>
                  </>
                ) : (
                  <div className="detail-placeholder">
                    <img src="/red-moon.svg" alt="" />
                    <h2>Your secrets stay yours.</h2>
                    <p>
                      Select an account to see its details.
                      <br />
                      Passwords remain hidden until you reveal them.
                    </p>
                    <span className="eyebrow">NO CLOUD. NO COMPROMISE.</span>
                  </div>
                )}
              </section>
            </div>
            <footer className="vault-footer">
              <ShieldCheck size={13} />
              <span>
                Complete vault encryption · Argon2id + XChaCha20-Poly1305
              </span>
              <span className="desktop-only">Red Moon / 01</span>
            </footer>
          </>
        ) : page === "generator" ? (
          <Generator call={call} notify={notify} />
        ) : page === "backups" ? (
          <Backups
            call={call}
            onRestored={async () => {
              await onRestored();
              await load();
              choose(null);
            }}
            notify={notify}
          />
        ) : (
          snapshot && (
            <Settings
              settings={snapshot.settings}
              call={call}
              onChanged={load}
              notify={notify}
            />
          )
        )}
      </main>
      {editor && (
        <EntryEditor
          initial={editor}
          call={call}
          onClose={() => setEditor(null)}
          onSaved={load}
        />
      )}{" "}
      {deleting && (
        <div className="modal-backdrop">
          <div
            className="confirmation"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="delete-title"
          >
            <h2 id="delete-title">Delete {deleting.title}?</h2>
            <p>
              This removes the entry from your vault. Existing encrypted backups
              may still contain it.
            </p>
            <div className="actions">
              <button
                autoFocus
                className="secondary"
                onClick={() => setDeleting(null)}
              >
                Keep entry
              </button>
              <button
                disabled={busy}
                onClick={() =>
                  void run(async () => {
                    await call({ action: "delete", id: deleting.id });
                    choose(null);
                    setDeleting(null);
                    await load();
                  })
                }
              >
                Delete permanently
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
