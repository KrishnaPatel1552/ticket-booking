import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import App from "./App";
import { signIn } from "./api";
import "./index.css";

vi.mock("./api", () => ({
  signIn: vi.fn(),
  searchEvents: vi.fn(),
  fetchSeatMap: vi.fn(),
  fetchWaitlist: vi.fn(),
  joinWaitlist: vi.fn(),
  holdSeats: vi.fn(),
  attachPromoCode: vi.fn(),
  checkout: vi.fn(),
  fetchOrder: vi.fn(),
  fetchSaleTerms: vi.fn(),
  cancelTickets: vi.fn(),
}));

afterEach(() => {
  cleanup();
});

test("the signed-out screen renders without calling the server", () => {
  render(<App />);

  expect(screen.getByRole("button", { name: "Sign in" })).toBeTruthy();
  expect(vi.mocked(signIn)).not.toHaveBeenCalled();
});

test("a failed sign-in stays on the card and shows the failure under the button", async () => {
  vi.mocked(signIn).mockRejectedValueOnce(new Error("rejected"));
  const user = userEvent.setup();
  render(<App />);

  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  const failure = await screen.findByText("That email and password did not sign you in.");
  const button = screen.getByRole("button", { name: "Sign in" });
  expect(button.compareDocumentPosition(failure) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
    Node.DOCUMENT_POSITION_FOLLOWING,
  );
  expect(screen.getByLabelText("Email")).toBeTruthy();
  expect(screen.queryByLabelText("Search events by name")).toBeNull();
});

test("a successful sign-in opens the events screen", async () => {
  vi.mocked(signIn).mockResolvedValueOnce({ email: "ada@example.com", role: "customer" });
  const user = userEvent.setup();
  render(<App />);

  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByLabelText("Search events by name")).toBeTruthy();
  expect(screen.queryByLabelText("Password")).toBeNull();
  expect(screen.queryByText("That email and password did not sign you in.")).toBeNull();
});

test("the signed-out screen is the Harbor Hall sign-in card", () => {
  render(<App />);

  expect(screen.getByText("Harbor Hall")).toBeTruthy();
  expect(screen.getByLabelText("Email")).toBeTruthy();
  expect(screen.getByLabelText("Password")).toBeTruthy();
  expect(screen.getAllByRole("button")).toHaveLength(1);

  const button = screen.getByRole("button", { name: "Sign in" });
  const style = getComputedStyle(button);
  expect(Number.parseFloat(style.minHeight)).toBeGreaterThanOrEqual(44);
  expect(Number.parseFloat(style.minWidth)).toBeGreaterThanOrEqual(44);

  const page = document.body.textContent ?? "";
  for (const forbidden of [
    "42°21'30\"N",
    "71°03'10\"W",
    "PORTAL // ACCESS",
    "VENUE TIER",
    "Live Gateway",
    "customer ID",
    "Authorized patron access",
    "Show",
    "HARBOR HALL VENUE SYSTEMS",
    "PROD // ONLINE",
    "encryption",
  ]) {
    expect(page).not.toContain(forbidden);
  }
});
