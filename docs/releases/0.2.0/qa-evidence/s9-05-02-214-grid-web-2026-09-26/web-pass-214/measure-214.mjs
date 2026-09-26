/**
 * QA #214 — visual acceptance of the web guide grid density, at 1440 px.
 *
 * Independent harness: drives the BRANCH build served on :3001 with real bench
 * data. Measures the rendered geometry (not the source): hour-label overlap,
 * width of a 30-minute programme block, horizontal scroll, and whether the day
 * tabs / Maintenant / Voir les chaînes sit outside the scrolling element.
 */
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

const guideUrl = `${BASE}/fr/app/sources/${SOURCE}/channels?view=guide`;
await page.goto(guideUrl, { waitUntil: "domcontentloaded" });
await page.waitForSelector('div[role="table"]', { timeout: 20000 });
await page.waitForTimeout(1200);

await page.screenshot({ path: `${out}/g214-01-guide-1440.png` });

const result = await page.evaluate(() => {
  const table = document.querySelector('div[role="table"]');
  const scroller = table?.closest(".overflow-x-auto");
  const rows = [...document.querySelectorAll('div[role="row"]')];
  const headerRow = rows[0];

  // Hour labels live in the header row's presentation cell (the 2nd child).
  const hourSpans = [...headerRow.querySelectorAll("span")].map((s) => {
    const r = s.getBoundingClientRect();
    return { text: s.textContent.trim(), left: r.left, right: r.right, width: r.width };
  });

  // Programme blocks (non-empty) carry a `title` attribute.
  const blocks = [...document.querySelectorAll('div[role="cell"] > div[title]')].map((b) => {
    const r = b.getBoundingClientRect();
    const titleSpan = b.querySelector("span");
    const timeSpan = b.querySelectorAll("span")[1];
    return {
      title: b.getAttribute("title"),
      width: Math.round(r.width * 10) / 10,
      titleClient: titleSpan?.clientWidth,
      titleScroll: titleSpan?.scrollWidth,
      timeText: timeSpan?.textContent?.trim() ?? null,
    };
  });

  const tabs = [...document.querySelectorAll('nav[aria-label] li a')].map((a) => ({
    text: a.textContent.trim(),
    href: a.getAttribute("href"),
  }));
  const nowLink = [...document.querySelectorAll("a")].find((a) => /Maintenant/.test(a.textContent));
  const seeLink = [...document.querySelectorAll("a")].find((a) => /Voir les cha/i.test(a.textContent));

  return {
    minWidthStyle: table?.style.minWidth ?? null,
    tableWidth: Math.round(table?.getBoundingClientRect().width ?? 0),
    scrollerClient: scroller?.clientWidth ?? null,
    scrollerScroll: scroller?.scrollWidth ?? null,
    scrollable: scroller ? scroller.scrollWidth > scroller.clientWidth : null,
    hourSpans,
    blocks,
    tabs,
    nowLink: nowLink ? { text: nowLink.textContent.trim(), href: nowLink.getAttribute("href"), inScroller: !!scroller?.contains(nowLink) } : null,
    seeLink: seeLink ? { text: seeLink.textContent.trim(), href: seeLink.getAttribute("href"), inScroller: !!scroller?.contains(seeLink) } : null,
    tabsInScroller: scroller ? scroller.contains(document.querySelector("nav")) : null,
  };
});

// Overlap test on hour labels.
let overlaps = 0;
const hs = [...result.hourSpans].sort((a, b) => a.left - b.left);
for (let i = 1; i < hs.length; i += 1) {
  if (hs[i].left < hs[i - 1].right - 0.5) overlaps += 1;
}
result.hourLabelOverlaps = overlaps;

// 30-minute blocks: ~0.5 h * 128 px = 64 px.
const halfHour = result.blocks.filter((b) => b.width >= 60 && b.width <= 68);
result.halfHourBlockCount = halfHour.length;
result.halfHourSample = halfHour.slice(0, 4);
result.smallestBlock = result.blocks.reduce((m, b) => (b.width < m ? b.width : m), Infinity);

// Scroll the grid and confirm the fixed controls do not move.
const before = await page.evaluate(() => {
  const sc = document.querySelector('div[role="table"]').closest(".overflow-x-auto");
  const now = [...document.querySelectorAll("a")].find((a) => /Maintenant/.test(a.textContent));
  const nav = document.querySelector("nav");
  sc.scrollLeft = 400;
  return { nav: nav.getBoundingClientRect().left, now: now.getBoundingClientRect().left };
});
const after = await page.evaluate(() => {
  const nav = document.querySelector("nav");
  const now = [...document.querySelectorAll("a")].find((a) => /Maintenant/.test(a.textContent));
  const sc = document.querySelector('div[role="table"]').closest(".overflow-x-auto");
  return { nav: nav.getBoundingClientRect().left, now: now.getBoundingClientRect().left, scrollLeft: sc.scrollLeft };
});
result.scrollMoved = after.scrollLeft;
result.navMovedWithScroll = Math.abs(after.nav - before.nav) > 1;
result.nowMovedWithScroll = Math.abs(after.now - before.now) > 1;

await page.screenshot({ path: `${out}/g214-02-guide-scrolled.png` });

console.log(JSON.stringify(result, null, 2));
await browser.close();
