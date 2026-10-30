import { useCallback, useEffect, useRef, useState } from "react";
import { execute, subscribeLock } from "./lib/ipc";
import { errorMessage, type Call, type Status } from "./lib/types";
import Auth from "./screens/Auth";
import Dashboard from "./screens/Dashboard";
export default function App() {
  const [status, setStatus] = useState<Status | null>(null),
    [error, setError] = useState(""),
    [toast, setToast] = useState("");
  const epoch = useRef(0);
  const unlocked = useRef(false);
  const discard = useCallback(() => {
    epoch.current++;
    unlocked.current = false;
    setStatus((s) => (s ? { ...s, unlocked: false } : s));
    setToast("");
    document.documentElement.dataset.theme = "dark";
  }, []);
  const call: Call = useCallback(async <T,>(request: unknown) => {
    const started = epoch.current;
    const result = await execute<T>(request);
    if (started !== epoch.current)
      throw Error("Session changed. Unlock your vault to continue.");
    return result;
  }, []);
  const refresh = useCallback(async () => {
    const started = epoch.current;
    const next = await execute<Status>({ action: "status" });
    if (started !== epoch.current) return;
    if (unlocked.current && !next.unlocked) discard();
    unlocked.current = next.unlocked;
    setStatus(next);
  }, [discard]);
  useEffect(() => {
    void refresh().catch((e) => setError(errorMessage(e)));
    let disposed = false;
    let unsubscribe: undefined | (() => void);
    void subscribeLock(discard)
      .then((fn) => {
        if (disposed) fn();
        else unsubscribe = fn;
      })
      .catch(() => {});
    const timer = window.setInterval(() => {
      void refresh().catch(() => {});
    }, 2000);
    return () => {
      disposed = true;
      unsubscribe?.();
      window.clearInterval(timer);
    };
  }, [refresh, discard]);
  const lock = useCallback(() => {
    discard();
    void execute({ action: "lock" }).catch((e) => setError(errorMessage(e)));
  }, [discard]);
  useEffect(() => {
    let last = 0;
    const activity = () => {
      const n = Date.now();
      if (unlocked.current && n - last > 2000) {
        last = n;
        void call({ action: "activity" }).catch(() => {});
      }
    };
    const hidden = () => {
      if (document.hidden && unlocked.current) lock();
    };
    window.addEventListener("pointerdown", activity);
    window.addEventListener("keydown", activity);
    document.addEventListener("visibilitychange", hidden);
    return () => {
      window.removeEventListener("pointerdown", activity);
      window.removeEventListener("keydown", activity);
      document.removeEventListener("visibilitychange", hidden);
    };
  }, [call, lock]);
  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(""), 4500);
    return () => window.clearTimeout(t);
  }, [toast]);
  if (error)
    return (
      <main className="startup-error">
        <img src="/red-moon.svg" alt="Red Moon" />
        <h1>Vault unavailable</h1>
        <p role="alert">{error}</p>
        <p>
          Open Red Moon as a desktop application. The browser preview cannot
          access vault storage or native security features.
        </p>
        <button
          onClick={() => {
            setError("");
            void refresh().catch((e) => setError(errorMessage(e)));
          }}
        >
          Retry
        </button>
      </main>
    );
  if (!status)
    return (
      <main className="startup-error" aria-live="polite">
        <img src="/red-moon.svg" alt="Red Moon" />
        <p>Opening Red Moon…</p>
      </main>
    );
  return (
    <>
      {status.unlocked ? (
        <Dashboard
          key={status.generation}
          call={call}
          onLock={lock}
          onRestored={refresh}
          notify={setToast}
        />
      ) : (
        <Auth exists={status.exists} call={call} onUnlocked={refresh} />
      )}{" "}
      {toast && (
        <div role="status" className="toast">
          {toast}
        </div>
      )}
    </>
  );
}
