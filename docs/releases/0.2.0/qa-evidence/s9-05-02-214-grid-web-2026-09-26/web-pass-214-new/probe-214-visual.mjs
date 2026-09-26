import { createRequire } from "node:module";
const require = createRequire(process.cwd() + "/package.json");
const { chromium } = require("@playwright/test");
const BASE = "http://localhost:3001";
const email = "qa.manual.20260926@test.example", password = process.env.QA_PASS;
const SOURCE = "de5b176c-8754-4553-a9e3-c22719cd6e10";
const OUT = ".";
const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1440, height: 900 } });
await p.goto(`${BASE}/fr/login`, { waitUntil: "domcontentloaded" });
await p.locator('input[name="email"]').fill(email);
await p.locator('input[name="password"]').fill(password);
await p.locator('button[type="submit"]').click();
await p.waitForURL(/\/fr\/app$/, { timeout: 30000 });
await p.goto(`${BASE}/fr/app/sources/${SOURCE}/channels?view=guide`, { waitUntil: "domcontentloaded" });
await p.waitForSelector('div[role="table"]', { timeout: 20000 });
await p.waitForTimeout(900);
const info = await p.evaluate(() => {
  const vis = (el) => { const r = el.getBoundingClientRect(); return r.left >= 0 && r.right <= window.innerWidth; };
  const links = [...document.querySelectorAll("a,button")];
  const pick = (re) => links.filter((a) => re.test(a.textContent || "")).map((a) => ({ text: a.textContent.trim().slice(0, 30), x: Math.round(a.getBoundingClientRect().left), visible: vis(a) }));
  const tabs = [...document.querySelectorAll('[role="tab"],[data-day-tab]')].map((t) => t.textContent.trim());
  return {
    now: pick(/Maintenant/),
    seeChannels: pick(/Voir les chaînes|Toutes les chaînes/),
    tabs,
    gridMinWidth0: document.querySelector('div.min-w-0') !== null,
    hourColPx: (() => { const ths=[...document.querySelectorAll('div[role="columnheader"]')]; return ths.length>1?Math.round(ths[1].getBoundingClientRect().left-ths[0].getBoundingClientRect().left):null; })(),
  };
});
await p.screenshot({ path: `${OUT}/g214-new-01-guide-1440.png` });
console.log(JSON.stringify(info, null, 2));
await b.close();
