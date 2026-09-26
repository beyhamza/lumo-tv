/**
 * QA first manual pass — XMLTV fixture with RELATIVE dates.
 *
 * The bench (apps/web/e2e/bench) serves no XMLTV yet (that is S9-07-03, dev's
 * harness). For the manual pass I generate a guide whose programmes are anchored
 * to "now" and drop it into the running bench container. Nothing in the repo is
 * touched; this file is the record of how the fixture was produced.
 *
 * Channel ids are the playlist's tvg-ids: bench.1 .. bench.5 (bench.5 has NO
 * tvg-id in playlist.m3u, so it must match nothing).
 *
 * Deliberate shapes, so the pass can actually exercise the criteria:
 *   bench.1 : regular 30-min grid, now-2h .. now+8h, and tomorrow
 *   bench.2 : 1-h programmes WITH a 1-h gap (GAP) around now+1h  -> lacune
 *   bench.3 : mixed 45-min / 2-h durations -> no drift on different durations
 *   bench.4 : 20-min / 40-min staggered -> "en cours" at a different minute
 *   bench.5 : present in the guide but absent from the playlist's tvg-id join
 */
import { writeFileSync } from "node:fs";

const OUT = process.argv[2] ?? "guide.xml";

const pad = (n) => String(n).padStart(2, "0");
const stamp = (d) =>
  `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}${pad(
    d.getUTCHours(),
  )}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())} +0000`;

const xmlEscape = (s) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

// Anchor: top of the current hour, so titles stay readable while the pass runs.
const now = new Date();
const anchor = new Date(now);
anchor.setUTCMinutes(0, 0, 0);

/** @type {{channel:string,title:string,desc:string,start:Date,end:Date,category:string}[]} */
const programmes = [];
const add = (channel, title, desc, startMin, endMin, category) =>
  programmes.push({
    channel,
    title,
    desc,
    start: new Date(anchor.getTime() + startMin * 60_000),
    end: new Date(anchor.getTime() + endMin * 60_000),
    category,
  });

// bench.1 — regular 30-min grid, now-2h .. now+8h.
for (let i = -4; i < 16; i++) {
  add(
    "bench.1",
    `Journal de midi ${i >= 0 ? `+${i}` : i}`,
    "Édition d'information en continu, titres et météo.",
    i * 30,
    (i + 1) * 30,
    "Information",
  );
}
// Tomorrow, to prove the day window.
for (let i = 0; i < 4; i++) {
  add("bench.1", `Matinale ${i}`, "Réveil et revue de presse.", 24 * 60 + i * 30, 24 * 60 + (i + 1) * 30, "Magazine");
}

// bench.2 — 1-h programmes with a deliberate gap (lacune) around now+1h.
add("bench.2", "Documentaire — océans", "Longue description pour vérifier la troncature et le repli. ".repeat(6), -60, 0, "Documentaire");
add("bench.2", "Débat — énergie", "Table ronde.", 0, 60, "Débat");
// GAP: nothing between +60 and +120
add("bench.2", "Série — atelier", "Fiction.", 120, 180, "Série");
add("bench.2", "Série — atelier (suite)", "Fiction.", 180, 240, "Série");
add("bench.2", "Film du soir", "Long métrage.", 240, 360, "Film");

// bench.3 — mixed durations (45 min and 2 h), no drift.
let cursor = -120;
const durations = [45, 45, 120, 45, 120, 45, 120];
let n = 0;
for (const d of durations) {
  add("bench.3", `Chrono ${++n} (${d} min)`, "Durée variable pour la grille TV.", cursor, cursor + d, "Sport");
  cursor += d;
}

// bench.4 — staggered start so "en cours" is mid-programme, not on the hour.
add("bench.4", "Direct — stade", "Rencontre en direct.", -20, 40, "Sport");
add("bench.4", "Analyse", "Après-match.", 40, 80, "Sport");
add("bench.4", "Autre direct", "Suite.", 80, 200, "Sport");

// bench.5 — in the guide, but the playlist gives it no tvg-id.
add("bench.5", "Programme orphelin", "Ne doit rien afficher : pas de tvg-id côté chaîne.", -30, 90, "Divers");

const parts = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="lumo-qa">'];
const names = { "bench.1": "Chaîne 01", "bench.2": "Chaîne 02", "bench.3": "Chaîne 03", "bench.4": "Chaîne 04", "bench.5": "Chaîne 05" };
for (const [id, name] of Object.entries(names)) {
  parts.push(`  <channel id="${id}"><display-name lang="fr">${xmlEscape(name)}</display-name></channel>`);
}
for (const p of programmes) {
  parts.push(
    `  <programme start="${stamp(p.start)}" stop="${stamp(p.end)}" channel="${p.channel}">\n` +
      `    <title lang="fr">${xmlEscape(p.title)}</title>\n` +
      `    <desc lang="fr">${xmlEscape(p.desc)}</desc>\n` +
      `    <category lang="fr">${xmlEscape(p.category)}</category>\n` +
      `  </programme>`,
  );
}
parts.push("</tv>", "");

writeFileSync(OUT, parts.join("\n"), "utf8");
console.log(`wrote ${OUT}: ${programmes.length} programmes, anchor ${anchor.toISOString()}`);
