const API = "http://localhost:8080/v1";
const password = process.env.QA_PASS ?? "lumo-qa-cold-2026!";
const j = (r) => r.text().then((t) => (t ? JSON.parse(t) : null));
const login = await fetch(`${API}/auth/login`, { method:"POST", headers:{"content-type":"application/json"},
  body: JSON.stringify({ email:"qa.mobile.cold.20260926@test.example", password, device:{ platform:"ANDROID_MOBILE", name:"QA s90601fix probe" }})});
console.log("login", login.status);
const b = await j(login); const auth = { authorization:`Bearer ${b.access_token ?? b.accessToken}` };
const id = "c4e967b1-c427-4058-8007-3ea54464ec83";
const ch = await fetch(`${API}/sources/${id}/channels?pageSize=100`, { headers: auth });
console.log("channels", ch.status);
const chb = await ch.json();
const items = chb.items ?? [];
console.log("n channels", items.length, items.map(c=>c.name+"("+String(c.id).slice(0,8)+")").join(" | "));
const from = new Date(Date.now() - 2*3600_000).toISOString();
const to = new Date(Date.now() + 6*3600_000).toISOString();
const url = `${API}/sources/${id}/epg?${items.map(c=>`channelIds=${c.id}`).join("&")}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
const epg = await fetch(url, { headers: auth });
console.log("epg", epg.status);
const eb = await epg.json();
const grid = eb.channels ?? [];
for (const c of grid) {
  const nm = items.find(i=>i.id===c.channelId)?.name ?? String(c.channelId).slice(0,8);
  console.log("--", nm, "mapping", c.mappingStatus);
  for (const p of (c.programmes??[])) console.log("   ", p.startsAt, "->", p.endsAt, JSON.stringify(p.title));
}
