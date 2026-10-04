// S10-05 TV — injecte une panne sur la recherche FILMS uniquement.
// Répond 503 sur tout chemin contenant `/vod`, relaie le reste à lumo-api.
// Lancé par QA sur un port libre ; l'appareil TV y est pointé par `adb reverse`.
import http from "node:http";

const UPSTREAM = process.env.UPSTREAM ?? "http://127.0.0.1:8080";
const PORT = Number(process.env.PORT ?? 18082);

const server = http.createServer(async (req, res) => {
  const url = req.url ?? "/";
  if (url.includes("/vod")) {
    console.log(`503 ${url}`);
    res.writeHead(503, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "injected" }));
    return;
  }
  try {
    const body = await new Promise((resolve, reject) => {
      const chunks = [];
      req.on("data", (c) => chunks.push(c));
      req.on("end", () => resolve(Buffer.concat(chunks)));
      req.on("error", reject);
    });
    const upstream = await fetch(UPSTREAM + url, {
      method: req.method,
      headers: { ...req.headers, host: new URL(UPSTREAM).host },
      body: ["GET", "HEAD"].includes(req.method) ? undefined : body,
    });
    const buf = Buffer.from(await upstream.arrayBuffer());
    const headers = {};
    upstream.headers.forEach((v, k) => {
      if (!["content-encoding", "content-length", "transfer-encoding"].includes(k)) headers[k] = v;
    });
    res.writeHead(upstream.status, headers);
    res.end(buf);
  } catch (e) {
    console.log(`502 ${url} ${e.message}`);
    res.writeHead(502, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "proxy" }));
  }
});

server.listen(PORT, () => console.log(`fault-proxy on ${PORT} -> ${UPSTREAM}, 503 on /vod`));
