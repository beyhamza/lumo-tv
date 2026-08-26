import { randomUUID } from "node:crypto";
import { expect, test as setup } from "@playwright/test";
import { SESSION_FILE } from "./support/stack";

/**
 * Creates an account and saves its cookie jar for the authenticated project.
 *
 * It signs up **through the form**, not through a fixture that forges a
 * cookie. Two reasons, and the second is the one that matters: a forged cookie
 * would drift the day the session format changes, and signing up here means the
 * registration path itself is exercised on every run — server action, API,
 * Argon2id, Postgres, and the encrypted cookie coming back.
 *
 * The account is new on every run, because the database is thrown away with the
 * stack. Nothing here depends on state left by a previous run, which is the
 * property that makes the suite safe to run twice in a row.
 */
setup("un compte est créé et sa session enregistrée", async ({ page }) => {
  // example.com and friends are reserved for documentation; nothing here can
  // reach a real mailbox even if the API ever really sent that email.
  const email = `e2e-${randomUUID()}@test.example`;

  await page.goto("/fr/register");

  await page.locator('input[name="email"]').fill(email);
  // Ten characters is the floor the form enforces before enabling the button
  // (US-01); this is comfortably past it.
  await page.locator('input[name="password"]').fill(randomUUID());
  await page.locator('button[type="submit"]').click();

  // The action redirects to /app on success. Landing anywhere else means the
  // API refused, and the assertion below says so before any other test fails
  // for a reason that looks unrelated.
  await expect(page).toHaveURL(/\/fr\/app$/);

  await page.context().storageState({ path: SESSION_FILE });
});
