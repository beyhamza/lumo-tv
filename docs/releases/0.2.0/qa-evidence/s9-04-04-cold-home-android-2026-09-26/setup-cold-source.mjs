/**
 * QA — prépare un compte « à froid » pour la recette S9-04-04 (Android mobile).
 *
 * Un compte neuf, une seule source prête, aucune lecture, aucun favori.
 * Le banc de dev sert playlist.m3u + guide.xml (injecté à la main).
 * État de recette, pas un test.
 */
const API = "http://localhost:8080/v1";
const email = process.env.QA_EMAIL ?? "qa.mobile.cold.20260926@test.example";
const password = process.env.QA_PASS;

const j = (r) => r.text().then((t) => ({ status: r.status, body: t ? JSON.parse(t) : null }));

let res = await j(
  await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      email,
      password,
      device: { platform: "ANDROID_MOBILE", name: "QA mobile cold" },
    }),
  }),
);
if (res.status >= 300) {
  console.error("login failed", res.status, JSON.stringify(res.body));
  process.exit(1);
}
const token = res.body.access_token ?? res.body.accessToken;
const auth = { authorization: `Bearer ${token}`, "content-type": "application/json" };
console.log("login:", res.status);

const list = await j(await fetch(`${API}/sources`, { headers: auth }));
console.log("sources before:", (list.body.sources ?? []).length);

let source = (list.body.sources ?? []).find((s) => s.label === "Banc à froid mobile");
if (!source) {
  const created = await j(
    await fetch(`${API}/sources`, {
      method: "POST",
      headers: auth,
      body: JSON.stringify({
        label: "Banc à froid mobile",
        kind: "M3U_URL",
        m3u_url: "http://bench/playlist.m3u",
        epg_url: "http://bench/guide.xml",
      }),
    }),
  );
  if (created.status >= 300) {
    console.error("create source failed", created.status, JSON.stringify(created.body));
    process.exit(1);
  }
  source = created.body;
  console.log("source created:", created.status);
}

for (let i = 0; i < 90; i++) {
  const got = await j(await fetch(`${API}/sources/${source.id}`, { headers: auth }));
  source = got.body;
  if (source.status === "READY" || source.status === "ERROR") break;
  await new Promise((r) => setTimeout(r, 1000));
}
console.log("source:", source.status, source.id);

// Preuve d'absence d'historique / favoris : c'est ce qui rend l'accueil « à froid ».
const hist = await j(await fetch(`${API}/history`, { headers: auth }).catch(() => ({ text: async () => "" })));
const favs = await j(await fetch(`${API}/favorites`, { headers: auth }).catch(() => ({ text: async () => "" })));
console.log("history probe:", hist.status);
console.log("favorites probe:", favs.status);

console.log("SOURCE_ID=" + source.id);
console.log("EMAIL=" + email);
