import { createRequire } from "node:module";
const require = createRequire(process.cwd() + "/package.json");
const { chromium } = require("@playwright/test");
const BASE = "http://localhost:3001";
const email = "qa.manual.20260926@test.example", password = process.env.QA_PASS;
const SOURCE = "de5b176c-8754-4553-a9e3-c22719cd6e10";
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
await p.goto(`${BASE}/fr/login`, { waitUntil: "domcontentloaded" });
await p.locator('input[name="email"]').fill(email);
await p.locator('input[name="password"]').fill(password);
await p.locator('button[type="submit"]').click();
await p.waitForURL(/\/fr\/app$/, { timeout: 30000 });
await p.goto(`${BASE}/fr/app/sources/${SOURCE}/channels?view=guide`, { waitUntil: "domcontentloaded" });
await p.waitForSelector('div[role="table"]', { timeout: 20000 });
await p.waitForTimeout(600);
const probe = await p.evaluate(() => {
  const doc = document.documentElement;
  const sc = document.querySelector('div[role="table"]').closest(".overflow-x-auto");
  const item = sc.parentElement.parentElement; // the grid item wrapper
  const now = [...document.querySelectorAll("a")].find((a) => /Maintenant/.test(a.textContent));
  const before = { docScrollWidth: doc.scrollWidth, scrollerClient: sc.clientWidth, scrollerScroll: sc.scrollWidth, nowX: Math.round(now.getBoundingClientRect().left) };
  item.style.minWidth = "0px";
  return new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(() => {
    r({ before, itemClass: item.className, after: { docScrollWidth: doc.scrollWidth, scrollerClient: sc.clientWidth, scrollerScroll: sc.scrollWidth, nowX: Math.round(now.getBoundingClientRect().left), gridScrollable: sc.scrollWidth > sc.clientWidth } });
  })));
});
await p.screenshot({ path: "g214-04-minwidth0-probe.png" });
console.log(JSON.stringify(probe, null, 2));
await b.close();
