/**
 * QA — fixture XMLTV de la recette S9-06-01 (fiche programme, GD-07/08).
 *
 * But : programmer, pour la chaîne du banc, un programme COURANT qui se termine
 * dans une fenêtre courte et un programme FUTUR qui devient courant pendant la
 * passe. Les dates sont relatives à l'instant de génération (T).
 *
 * Usage : node s90601-guide.mjs <pass-a|pass-b> [out.xml]
 *   pass-a : bench.1 courant de T-5min à T+CURRENT_END_S
 *   pass-b : bench.1 futur de T+FUTURE_START_S à T+FUTURE_END_S
 */
import { writeFileSync } from "node:fs";

const mode = process.argv[2] ?? "pass-a";
const OUT = process.argv[3] ?? "guide.xml";

const pad = (n) => String(n).padStart(2, "0");
const stamp = (d) =>
  `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}${pad(
    d.getUTCHours(),
  )}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())} +0000`;
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const T = Date.now();
const at = (s) => new Date(T + s * 1000);

const programmes = [];
const add = (channel, title, startS, endS, desc) =>
  programmes.push({ channel, title, start: at(startS), end: at(endS), desc });

// La fenêtre du Guide TV couvre environ l'heure courante ; on garde des
// programmes sur les 4 chaînes pour que la grille ne soit pas vide.
add("bench.1", "QA passé", -3600, -600, "Programme passé (aucune action).");
if (mode === "pass-b") {
  // Le FUTUR devient courant pendant la passe : startsAt = T+60 s.
  add("bench.1", "QA futur", 120, 1200, "Programme futur qui devient courant pendant la passe.");
} else {
  // Le COURANT finit pendant la passe : endsAt = T+CURRENT_END_S.
  add("bench.1", "QA courant", -300, Number(process.env.CURRENT_END_S ?? 420), "Programme courant qui se termine pendant la passe.");
}
add("bench.1", "QA suivant", 1200, 2400, "Programme suivant.");
add("bench.1", "QA plus tard", 2400, 3600, "Programme tardif.");

add("bench.2", "QA B courant", -600, 1800, "Autre chaîne, programme courant.");
add("bench.2", "QA B suivant", 1800, 3000, "Autre chaîne, programme suivant.");
add("bench.3", "QA C courant", -1200, 900, "Durée variable.");
add("bench.4", "QA D courant", -300, 1500, "Autre chaîne encore.");

const names = {
  "bench.1": "Chaîne 01 FHD",
  "bench.2": "Chaîne 02",
  "bench.3": "Chaîne 03 HD",
  "bench.4": "Chaîne 04 4K",
};
const parts = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="lumo-qa">'];
for (const [id, name] of Object.entries(names)) {
  parts.push(`  <channel id="${id}"><display-name lang="fr">${esc(name)}</display-name></channel>`);
}
for (const p of programmes) {
  parts.push(
    `  <programme start="${stamp(p.start)}" stop="${stamp(p.end)}" channel="${p.channel}">\n` +
      `    <title lang="fr">${esc(p.title)}</title>\n` +
      `    <desc lang="fr">${esc(p.desc)}</desc>\n` +
      `    <category lang="fr">Bench</category>\n` +
      `  </programme>`,
  );
}
parts.push("</tv>", "");
writeFileSync(OUT, parts.join("\n"), "utf8");
console.log(`wrote ${OUT} mode=${mode}: ${programmes.length} programmes, T=${new Date(T).toISOString()}`);
