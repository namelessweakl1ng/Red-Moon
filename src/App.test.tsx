import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { test, expect, vi, beforeEach } from "vitest";
import App from "./App";
const execute = vi.hoisted(() => vi.fn());
vi.mock("./lib/ipc", () => ({ execute, subscribeLock: async () => () => {} }));
beforeEach(() => {
  execute.mockReset();
  execute.mockImplementation(async (r: { action: string }) =>
    r.action === "status"
      ? { exists: false, unlocked: false, generation: 0 }
      : {},
  );
});
test("setup requires matching passphrases and explains recovery", async () => {
  render(<App />);
  await screen.findByRole("heading", { name: "Your secrets. Your control." });
  expect(screen.getByText(/forget.*master password/i)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Master password"), {
    target: { value: "long enough passphrase" },
  });
  fireEvent.change(screen.getByLabelText("Confirm master password"), {
    target: { value: "different passphrase" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Create encrypted vault" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(/match/i);
  expect(
    execute.mock.calls.filter(([r]) => r.action === "create"),
  ).toHaveLength(0);
});
test("unlock masks input and reports backend authentication failure", async () => {
  execute.mockImplementation(async (r: { action: string }) => {
    if (r.action === "status")
      return { exists: true, unlocked: false, generation: 0 };
    if (r.action === "unlock")
      throw Error("Incorrect password or damaged vault.");
    return {};
  });
  render(<App />);
  const input = await screen.findByLabelText("Master password");
  expect(input).toHaveAttribute("type", "password");
  fireEvent.change(input, { target: { value: "wrong" } });
  fireEvent.click(screen.getByRole("button", { name: "Unlock vault" }));
  await waitFor(() =>
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Incorrect password or damaged vault.",
    ),
  );
});
const entry = {
  id: "entry-1",
  title: "Personal email",
  username: "alice@example.test",
  url: "https://example.test",
  category: "Personal",
  tags: ["mail"],
  favorite: false,
  created_at: 1700000000,
  modified_at: 1700000000,
  duplicate: false,
};
const snapshot = {
  entries: [entry],
  settings: { auto_lock_seconds: 300, theme: "dark" },
  generation: 1,
};
test("passwords remain masked and username copying does not reveal them", async () => {
  execute.mockImplementation(async (r: { action: string }) => {
    if (r.action === "status")
      return { exists: true, unlocked: true, generation: 1 };
    if (r.action === "list") return snapshot;
    if (r.action === "detail") return { notes: "Private notes", generation: 1 };
    return {};
  });
  render(<App />);
  fireEvent.click(
    await screen.findByRole("button", { name: /Personal email/ }),
  );
  expect(screen.getByLabelText("Password details")).toHaveTextContent(
    "••••••••",
  );
  fireEvent.click(screen.getByRole("button", { name: "Copy username" }));
  await waitFor(() =>
    expect(screen.getByRole("status")).toHaveTextContent("Username copied."),
  );
  expect(
    execute.mock.calls.filter(([r]) => r.action === "reveal"),
  ).toHaveLength(0);
  expect(
    execute.mock.calls.filter(([r]) => r.action === "copy_username")[0][0].id,
  ).toBe("entry-1");
});
test("late reveal response cannot return secrets to a locked screen", async () => {
  let finish: ((r: unknown) => void) | undefined;
  let locked = false;
  execute.mockImplementation(async (r: { action: string }) => {
    if (r.action === "status")
      return { exists: true, unlocked: !locked, generation: 1 };
    if (r.action === "list") return snapshot;
    if (r.action === "detail") return { notes: "Private notes", generation: 1 };
    if (r.action === "reveal")
      return new Promise((resolve) => {
        finish = resolve;
      });
    if (r.action === "lock") {
      locked = true;
      return null;
    }
    return {};
  });
  render(<App />);
  fireEvent.click(
    await screen.findByRole("button", { name: /Personal email/ }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Reveal password" }));
  await waitFor(() => expect(finish).toBeDefined());
  fireEvent.click(screen.getByRole("button", { name: "Lock vault" }));
  await screen.findByRole("button", { name: "Unlock vault" });
  finish?.({ password: "must-never-render", generation: 1 });
  await waitFor(() =>
    expect(screen.queryByText("must-never-render")).not.toBeInTheDocument(),
  );
  expect(screen.queryByLabelText("Password details")).not.toBeInTheDocument();
});
test("search filters accounts and empty state offers a real entry form", async () => {
  execute.mockImplementation(async (r: { action: string }) => {
    if (r.action === "status")
      return { exists: true, unlocked: true, generation: 1 };
    if (r.action === "list") return snapshot;
    return {};
  });
  render(<App />);
  await screen.findByRole("button", { name: /Personal email/ });
  fireEvent.change(screen.getByRole("textbox", { name: "Search passwords" }), {
    target: { value: "no match" },
  });
  expect(
    screen.queryByRole("button", { name: /Personal email/ }),
  ).not.toBeInTheDocument();
  expect(screen.getByText("No matching passwords")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "New password" }));
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  expect(screen.getByLabelText("Password")).toHaveAttribute("type", "password");
});
