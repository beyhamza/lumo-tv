import { createRequire } from "node:module";

const require = createRequire(process.cwd() + "/package.json");
const { chromium } = require("@playwright/test");

const BASE = process.env.WEB_BASE ?? "http://localhost:3000";
const email = "qa.mobile.cold.20260926@test.example";
const password = process.env.QA_PASS;
const out = process.env.OUT ?? ".";
const tag = process.env.TAG ?? "main";

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

await page.goto(`${BASE}/fr/login`, { waitUntil: "domcontentloaded" });
await page.locator('input[name="email"]').fill(email);
await page.locator('input[name="password"]').fill(password);
await page.locator('button[type="submit"]').click();
await page.waitForURL(/\/fr\/app$/, { timeout: 30000 });
// Laisser la lecture des rails se terminer (continu/favoris/récentes).
await page.waitForTimeout(2500);

const links = await page.locator("a").allInnerTexts();
const hasAll = await page
  .getByRole("link", { name: "Toutes les chaînes", exact: true })
  .count();
const hasGuide = await page
  .getByRole("link", { name: "Guide TV", exact: true })
  .count();

await page.screenshot({ path: `${out}/web-cold-${tag}.png`, fullPage: true });
console.log(JSON.stringify({ tag, url: page.url(), hasAll, hasGuide, links }, null, 2));

await browser.close();
