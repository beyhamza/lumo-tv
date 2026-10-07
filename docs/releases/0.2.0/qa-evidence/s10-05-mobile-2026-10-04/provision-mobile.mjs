/**
 * QA — prépare le compte et la source pour la recette S10-05 mobile.
 *
 * Compte jetable, une source M3U_URL pointant le banc e2e (mixed.m3u : chaînes +
 * films, aucune série). Le mot de passe est lu dans l'environnement et n'est
 * jamais écrit dans le dépôt (INDEX.md, règle « mots de passe retirés »).
 *
 *   QA_PASS=... node provision-mobile.mjs
 */
const API = process.env.LUMO_API ?? "http://localhost:8080/v1";
const EMAIL = process.env.QA_EMAIL ?? "qa.s1005.mobile@test.example";
const PASS = process.env.QA_PASS;
const BENCH = process.env.LUMO_BENCH_M3U ?? "http://host.docker.internal:18081/mixed.m3u";
if (!PASS) {
  console.error("QA_PASS absent");
  process.exit(1);
}

async function call(method, path, body, token) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json; try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { status: res.status, json };
}

let r = await call("POST", "/auth/login", { email: EMAIL, password: PASS,
  device: { platform: "ANDROID_MOBILE", name: "QA Pixel_10 mobile", model: "Pixel_10", appVersion: "0.1.0" } });
console.log("login:", r.status);
if (r.status >= 300) {
  r = await call("POST", "/auth/register", { email: EMAIL, password: PASS, displayName: "QA S10-05 mobile",
    device: { platform: "ANDROID_MOBILE", name: "QA Pixel_10 mobile", model: "Pixel_10", appVersion: "0.1.0" } });
  console.log("register:", r.status);
  if (r.status >= 300) process.exit(1);
}
const token = r.json.access_token ?? r.json.accessToken;

const list = await call("GET", "/sources", undefined, token);
const items = list.json.items ?? list.json.sources ?? [];
let source = items.find((s) => s.label === "Banc S10-05 mobile");
if (!source) {
  const created = await call("POST", "/sources", { label: "Banc S10-05 mobile", kind: "M3U_URL", m3u_url: BENCH, auto_sync: true }, token);
  if (created.status >= 300) process.exit(1);
  source = created.json;
}
for (let i = 0; i < 90 && source.status !== "READY" && source.status !== "ERROR"; i++) {
  await new Promise((s) => setTimeout(s, 2000));
  source = (await call("GET", `/sources/${source.id}`, undefined, token)).json;
}
console.log("source:", source.status, source.id);
if (source.status !== "READY") process.exit(2);
console.log("SOURCE_ID=" + source.id);
