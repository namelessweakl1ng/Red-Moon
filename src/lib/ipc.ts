import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
export const execute = <T>(request: unknown): Promise<T> =>
  invoke<T>("execute", { request });
export const subscribeLock = (callback: () => void) =>
  listen("vault-locked", callback);
