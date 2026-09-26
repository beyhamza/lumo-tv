/**
 * QA — petit utilitaire de banc pour la recette S9-06-01 : créer/lister/supprimer
 * des sources de test sur le compte QA, et forcer une synchro quand le cooldown
 * de 5 min le permet. Rien n'est écrit dans le dépôt.
 *
 * Usage :
 *   node s90601-source.mjs list
 *   node s90601-source.mjs create <label>
 *   node s90601-source.mjs delete <id>
 */
const API = "http://localhost:8080/v1";
const email = process.env.QA_EMAIL ?? "qa.mobile.cold.20260926@test.example";
const password = process.env.QA_PASS;
if (!password) { console.error("QA_PASS requis dans l'environnement"); process.exit(1); }
const j = async (r) => {
  const t = await r.text();
  return { status: r.status, body: t ? JSON.parse(t) : null };
};

const login = await fetch(`${API}/auth/login`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ email, password, device: { platform: "ANDROID_MOBILE", name: "QA s90601 source" } }),
});
if (login.status >= 300) { console.error("login", login.status, await login.text()); process.exit(1); }
const lb = await login.json().catch(() => null);
const token = (lb?.access_token ?? lb?.accessToken);
const auth = { authorization: `Bearer ${token}`, "content-type": "application/json" };

const [cmd, arg] = process.argv.slice(2);
if (cmd === "list") {
  const r = await fetch(`${API}/sources`, { headers: auth });
  console.log(JSON.stringify((await r.json()).items, null, 2));
} else if (cmd === "create") {
  const created = await j(await fetch(`${API}/sources`, {
    method: "POST",
    headers: auth,
    body: JSON.stringify({ label: arg, kind: "M3U_URL", m3u_url: "http://bench/playlist.m3u", epg_url: "http://bench/guide.xml" }),
  }));
  if (created.status >= 300) { console.error("create", created.status, JSON.stringify(created.body)); process.exit(1); }
  const id = created.body.id;
  console.log("created", id);
  for (let i = 0; i < 90; i++) {
    const got = await j(await fetch(`${API}/sources/${id}`, { headers: auth }));
    if (got.body.status === "READY" || got.body.status === "ERROR") {
      console.log("source:", got.body.status, id, "channels:", got.body.channel_count, "last_synced:", got.body.last_synced_at);
      process.exit(got.body.status === "READY" ? 0 : 1);
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  console.error("timeout"); process.exit(1);
} else if (cmd === "delete") {
  const r = await fetch(`${API}/sources/${arg}`, { method: "DELETE", headers: auth });
  console.log("delete", arg, r.status);
} else {
  console.error("usage: list | create <label> | delete <id>");
  process.exit(1);
}
