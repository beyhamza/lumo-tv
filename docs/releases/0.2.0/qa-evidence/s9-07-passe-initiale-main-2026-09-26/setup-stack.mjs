/**
 * QA first manual pass — stack setup through the real API.
 *
 * Creates a throwaway account, registers the bench playlist WITH an epg_url
 * pointing at the guide.xml I injected, waits for ingestion, and prints what
 * the API stored. This is state preparation for the manual pass, not a test.
 */
const API = "http://localhost:8080/v1";
const email = process.env.QA_EMAIL ?? "qa.manual.20260926@test.example";
const password = process.env.QA_PASS;

const j = (r) => r.text().then((t) => ({ status: r.status, body: t ? JSON.parse(t) : null }));

const register = async () => {
  const r = await fetch(`${API}/auth/register`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password, device: { platform: "WEB", name: "QA manual pass" } }),
  });
  return j(r);
};

let res = await register();
if (res.status === 409) {
  const login = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password, device: { platform: "WEB", name: "QA manual pass" } }),
  });
  res = await j(login);
}
if (res.status >= 300) {
  console.error("auth failed", res.status, res.body);
  process.exit(1);
}
const token = res.body.access_token ?? res.body.accessToken;
console.log("account:", email, "session:", res.status);

const auth = { authorization: `Bearer ${token}`, "content-type": "application/json" };

// Reuse an existing source if the script is run twice.
const list = await j(await fetch(`${API}/sources`, { headers: auth }));
let source = list.body.sources?.find((s) => s.label === "Banc QA");
if (!source) {
  const created = await j(
    await fetch(`${API}/sources`, {
      method: "POST",
      headers: auth,
      body: JSON.stringify({
        label: "Banc QA",
        kind: "M3U_URL",
        m3u_url: "http://bench/playlist.m3u",
        epg_url: "http://bench/guide.xml",
      }),
    }),
  );
  console.log("create source:", created.status, created.body?.status);
  source = created.body;
  if (created.status >= 300) {
    console.error(created.status, created.body);
    process.exit(1);
  }
}

for (let i = 0; i < 60; i++) {
  const got = await j(await fetch(`${API}/sources/${source.id}`, { headers: auth }));
  source = got.body;
  if (source.status === "READY" || source.status === "ERROR") break;
  await new Promise((r) => setTimeout(r, 1000));
}
console.log("source:", JSON.stringify(source));

const from = new Date(Date.now() - 2 * 3600_000).toISOString();
const to = new Date(Date.now() + 8 * 3600_000).toISOString();
const epg = await j(
  await fetch(`${API}/sources/${source.id}/epg?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { headers: auth }),
);
console.log("epg read:", epg.status);
const entries = epg.body?.entries ?? epg.body?.channels ?? [];
console.log("entries:", JSON.stringify(entries).slice(0, 300));
console.log("epg status field:", JSON.stringify(epg.body?.epg ?? epg.body).slice(0, 200));
console.log("SOURCE_ID=" + source.id);
console.log("EMAIL=" + email);
