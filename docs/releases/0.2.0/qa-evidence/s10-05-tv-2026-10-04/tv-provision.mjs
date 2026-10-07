import { randomUUID } from "node:crypto";

const BASE = process.env.LUMO_API ?? "http://localhost:8080/v1";
const BENCH = process.env.LUMO_BENCH_M3U ?? "http://host.docker.internal:18081/mixed.m3u";
const USER_CODE = process.argv[2];
if (!USER_CODE) throw new Error("usage: node tv-provision.mjs <USER_CODE>");

async function call(method, path, body, token) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { status: res.status, json };
}

const email = `qa-s10-05-${randomUUID()}@test.example`;
const password = randomUUID();
console.log("register", email);

let r = await call("POST", "/auth/register", {
  email,
  password,
  displayName: "QA S10-05 TV",
  device: { platform: "ANDROID_TV", name: "QA emulator", model: "Television_1080p", appVersion: "0.1.0" },
});
if (r.status >= 300) throw new Error(`register ${r.status}: ${JSON.stringify(r.json)}`);
const token = r.json.access_token ?? r.json.accessToken;
console.log("token ok, user", r.json.user?.id);

r = await call("POST", "/sources", { label: "Banc S10-05 TV", kind: "M3U_URL", m3u_url: BENCH, auto_sync: true }, token);
if (r.status >= 300) throw new Error(`source ${r.status}: ${JSON.stringify(r.json)}`);
const sourceId = r.json.id;
console.log("source", sourceId, r.json.status);

let status = r.json.status;
for (let i = 0; i < 90 && status !== "READY" && status !== "ERROR"; i++) {
  await new Promise((s) => setTimeout(s, 2000));
  const s = await call("GET", `/sources/${sourceId}`, undefined, token);
  status = s.json.status;
}
console.log("source final status", status);
if (status !== "READY") throw new Error(`source not READY: ${status}`);

r = await call("POST", "/auth/device/approve", { user_code: USER_CODE }, token);
console.log("approve", r.status, JSON.stringify(r.json));
