export interface Status {
  exists: boolean;
  unlocked: boolean;
  generation: number;
}
export interface Settings {
  auto_lock_seconds: number;
  theme: "dark" | "contrast" | "system";
}
export interface EntrySummary {
  id: string;
  title: string;
  username: string;
  url: string;
  category: string;
  tags: string[];
  favorite: boolean;
  created_at: number;
  modified_at: number;
  duplicate: boolean;
}
export interface Entry extends Omit<EntrySummary, "duplicate"> {
  password: string;
  notes: string;
}
export interface Snapshot {
  entries: EntrySummary[];
  settings: Settings;
  generation: number;
}
export type Call = <T>(request: unknown) => Promise<T>;
export const emptyEntry = (): Entry => ({
  id: "",
  title: "",
  username: "",
  password: "",
  url: "",
  category: "",
  tags: [],
  notes: "",
  favorite: false,
  created_at: 0,
  modified_at: 0,
});
export const errorMessage = (e: unknown) =>
  e instanceof Error
    ? e.message
    : typeof e === "string"
      ? e
      : "The operation could not be completed.";
export const strength = (password: string) => {
  if (!password) return "Use a long, unique password or passphrase.";
  if (password.length < 12)
    return "Short — use at least 14 characters for a passphrase.";
  if (
    /^(.)\1+$/.test(password) ||
    /password|qwerty|123456|letmein/i.test(password)
  )
    return "Predictable — avoid common words and sequences.";
  if (password.length >= 20)
    return "Long — make sure it is unique and unpredictable.";
  return "Moderate length — a longer random password is recommended.";
};
