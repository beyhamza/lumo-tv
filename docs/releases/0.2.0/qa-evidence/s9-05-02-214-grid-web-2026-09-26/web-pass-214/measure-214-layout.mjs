import { createRequire } from "node:module";
const require = createRequire(process.cwd() + "/package.json");
const { chromium } = require("@playwright/test");

const BASE = process.env.WEB_BASE ?? "http://localhost:3001";
const out = process.env.OUT ?? ".";
const email = "qa.manual.20260926@test.example";
const password = process.env.QA_PASS;
const SOURCE = "de5b176c-8754-4553-a9e3-c22719cd6e10";

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto(`${BASE}/fr/login`, { waitUntil: "domcontentloaded" });
await page.locator('input[name="email"]').fill(email);
await page.locator('input[name="password"]').fill(password);
await page.locator('button[type="submit"]').click();
await page.waitForURL(/\/fr\/app$/, { timeout: 30000 });
await page.goto(`${BASE}/fr/app/sources/${SOURCE}/channels?view=guide`, { waitUntil: "domcontentloaded" });
await page.waitForSelector('div[role="table"]', { timeout: 20000 });
await page.waitForTimeout(800);

const layout = await page.evaluate(() => {
  const sc = document.querySelector('div[role="table"]').closest(".overflow-x-auto");
  const cs = getComputedStyle(sc);
  const parent = sc.parentElement;
  const pcs = getComputedStyle(parent);
  const gp = getComputedStyle(parent.parentElement);
  return {
    docScrollWidth: document.documentElement.scrollWidth,
    docClientWidth: document.documentElement.clientWidth,
    bodyScrollWidth: document.body.scrollWidth,
    innerWidth: window.innerWidth,
    scroller: {
      clientWidth: sc.clientWidth,
      scrollWidth: sc.scrollWidth,
      overflowX: cs.overflowX,
      display: cs.display,
      minWidth: cs.minWidth,
      tag: sc.tagName,
      className: sc.className,
    },
    scrollerParent: {
      tag: parent.tagName,
      className: parent.className,
      display: pcs.display,
      minWidth: pcs.minWidth,
      overflow: pcs.overflow,
      width: Math.round(parent.getBoundingClientRect().width),
    },
    scrollerGrandparent: {
      tag: parent.parentElement.tagName,
      className: parent.parentElement.className,
      display: gp.display,
      minWidth: gp.minWidth,
    },
    scrollerIndexInParent: [...parent.children].indexOf(sc),
    parentChildCount: parent.children.length,
  };
});

// Document-level scroll test.
const beforeDoc = await page.evaluate(() => {
  const now = [...document.querySelectorAll("a")].find((a) => /Maintenant/.test(a.textContent));
  const nav = document.querySelector("nav");
  const grid = document.querySelector('div[role="table"]');
  return { now: now.getBoundingClientRect().left, nav: nav.getBoundingClientRect().left, grid: grid.getBoundingClientRect().left };
});
await page.evaluate(() => window.scrollTo(400, 0));
await page.waitForTimeout(300);
const afterDoc = await page.evaluate(() => {
  const now = [...document.querySelectorAll("a")].find((a) => /Maintenant/.test(a.textContent));
  const nav = document.querySelector("nav");
  const grid = document.querySelector('div[role="table"]');
  return { scrollX: window.scrollX, now: now.getBoundingClientRect().left, nav: nav.getBoundingClientRect().left, grid: grid.getBoundingClientRect().left };
});
await page.screenshot({ path: `${out}/g214-03-doc-scrolled.png` });

// In-grid scroll test: does mutating scrollLeft survive?
const gridScroll = await page.evaluate(async () => {
  const sc = document.querySelector('div[role="table"]').closest(".overflow-x-auto");
  sc.scrollLeft = 300;
  await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
  return { after: sc.scrollLeft, scrollWidth: sc.scrollWidth, clientWidth: sc.clientWidth };
});

console.log(JSON.stringify({ layout, beforeDoc, afterDoc, gridScroll,
  navFixedOnDocScroll: Math.abs(afterDoc.nav - beforeDoc.nav) <= 1,
  nowFixedOnDocScroll: Math.abs(afterDoc.now - beforeDoc.now) <= 1,
}, null, 2));
await browser.close();
