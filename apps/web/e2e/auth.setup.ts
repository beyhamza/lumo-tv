import { randomUUID } from "node:crypto";
import { mkdirSync, readdirSync } from "node:fs";
import path from "node:path";
import { expect, test as setup } from "@playwright/test";
import { sessionPathFor } from "./support/stack";

/**
 * Creates one account per journey spec file and saves each cookie jar.
 *
 * It signs up **through the form**, not through a fixture that forges a
 * cookie. Two reasons, and the second is the one that matters: a forged cookie
 * would drift the day the session format changes, and signing up here means the
 * registration path itself is exercised on every run — server action, API,
 * Argon2id, Postgres, and the encrypted cookie coming back.
 *
 * <h2>Why one account per file, and not one for the whole project</h2>
 *
 * The `journey` project used to share a single account between every
 * `*.journey.spec.ts`. That was fine until specs started registering more than
 * one source: the free plan caps an account at a small number of sources
 * (`LUMO_PLANS_FREE_MAX_SOURCES`), several files need two or three at once, and
 * `fullyParallel` made the race for the last slot fail in a different file on
 * every run. A spec's sources are now its own: the file list is read from disk
 * so a new `*.journey.spec.ts` cannot be forgotten, and each gets its account
 * and its `.e2e/sessions/<clé>.json`.
 *
 * The accounts are new on every run, because the database is thrown away with
 * the stack. Nothing here depends on state left by a previous run, which is the
 * property that makes the suite safe to run twice in a row.
 */

// This file sits next to the specs it signs accounts for. `__dirname` is
// available because Playwright loads configuration and setup files as CommonJS
// (apps/web has no `"type": "module"`).
const specDir = __dirname;

/** The `<clé>` of every journey spec, which is also its session key. */
const journeyKeys = readdirSync(specDir)
  // `journey.spec.ts` (not `<...>.journey.spec.ts`) is the one file whose name
  // has no dotted prefix, so it needs naming explicitly or its account would
  // never be created and every test in it would fail on a missing session.
  .filter((name) => name === "journey.spec.ts" || name.endsWith(".journey.spec.ts"))
  .map((name) =>
    name.endsWith(".journey.spec.ts")
      ? name.replace(/\.journey\.spec\.ts$/, "")
      : name.replace(/\.spec\.ts$/, ""),
  )
  .sort();

for (const key of journeyKeys) {
  setup(`un compte est créé pour « ${key} »`, async ({ page }) => {
    // example.com and friends are reserved for documentation; nothing here can
    // reach a real mailbox even if the API ever really sent that email.
    //
    // The address only has to be unique, and the account is identified by its
    // session file, so the key is deliberately NOT in the local part:
    // `@Email` (Hibernate Validator) caps the local part at 64 characters, and
    // `e2e-s9-07-gd14-locales-keyboard-<uuid>` is 68 — a rejection nothing in
    // the test's own view would explain.
    const email = `e2e-${randomUUID()}@test.example`;
    // Ten characters is the floor the form enforces before enabling the button
    // (US-01); this is comfortably past it.
    const password = randomUUID();

    await page.goto("/fr/register");

    const emailField = page.locator('input[name="email"]');
    const passwordField = page.locator('input[name="password"]');
    const submit = page.locator('button[type="submit"]');

    // The email input is uncontrolled and the password input is controlled, so
    // a fill that races React's hydration can be wiped: the email (filled
    // first) reverts to the server-rendered empty value while the password —
    // whose `onChange` only fires once hydrated — is what finally un-disables
    // the submit button. A submit in that window runs with no email and comes
    // back `VALIDATION_FAILED`. Retrying the whole fill is safe (the values are
    // idempotent) and the loop exits as soon as the form is interactive.
    await expect(async () => {
      await emailField.fill(email);
      await passwordField.fill(password);
      await expect(emailField).toHaveValue(email);
      await expect(submit).toBeEnabled();
    }).toPass({ timeout: 15_000 });

    await submit.click();

    // The action redirects to /app on success. Landing anywhere else means the
    // API refused, and the assertion below says so before any other test fails
    // for a reason that looks unrelated.
    await expect(page).toHaveURL(/\/fr\/app$/);

    const target = sessionPathFor(key);
    mkdirSync(path.dirname(target), { recursive: true });
    await page.context().storageState({ path: target });
  });
}
