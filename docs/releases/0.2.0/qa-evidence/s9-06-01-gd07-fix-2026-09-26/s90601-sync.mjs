/**
 * QA — force la resynchronisation d'une source Lumo après avoir remplacé
 * guide.xml dans le banc. Recette S9-06-01. Rien n'est écrit dans le dépôt.
 *
 * Usage : node s90601-sync.mjs <sourceId>
 */
const API = "http://localhost:8080/v1";
const id = process.argv[2];
if (!id) { console.error("usage: node s90601-sync.mjs <sourceId>"); process.exit(1); }
const email = process.env.QA_EMAIL ?? "qa.mobile.cold.20260926@test.example";
const password = process.env.QA_PASS;
if (!password) { console.error("QA_PASS requis dans l'environnement"); process.exit(1); }
const j = (r) => r.text().then((t) => (t ? JSON.parse(t) : null));

const login = await fetch(`${API}/auth/login`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ email, password, device: { platform: "ANDROID_MOBILE", name: "QA s90601 sync" } }),
});
if (login.status >= 300) { console.error("login", login.status, await login.text()); process.exit(1); }
const b = await j(login);
const token = b.access_token ?? b.accessToken;
const auth = { authorization: `Bearer ${token}` };

const before = await j(await fetch(`${API}/sources/${id}`, { headers: auth }));
console.log("before:", before.status, before.last_synced_at);
const sync = await fetch(`${API}/sources/${id}/sync`, { method: "POST", headers: auth });
console.log("sync:", sync.status, (await sync.text()).slice(0, 200));

for (let i = 0; i < 60; i++) {
  const got = await j(await fetch(`${API}/sources/${id}`, { headers: auth }));
  if (got.status === "READY" && got.last_synced_at !== before.last_synced_at) {
    console.log("synced:", got.status, got.last_synced_at, "channels:", got.channel_count);
    process.exit(0);
  }
  if (got.status === "ERROR") { console.error("ERROR", got.error_code); process.exit(1); }
  await new Promise((r) => setTimeout(r, 1000));
}
console.error("timeout");
process.exit(1);
