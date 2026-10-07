// QA fault proxy — S10-05 web, erreur partielle de recherche (S10-04).
//
// Relaie tout à lumo-api (18080) SAUF les lectures de films (`/vod`), qui
// répondent 503 problem+json. Cela permet de prouver qu'une section en échec
// n'est jamais rendue comme un ensemble vide et que les autres restent.
//
// Usage : QA_PROXY_PORT=18082 node search-fault-proxy.mjs
// puis  E2E_API_PORT=18082 E2E_SEARCH_FAULT_ARMED=1 pnpm exec playwright test ...
import http from "node:http";

const UPSTREAM_HOST = "127.0.0.1";
const UPSTREAM_PORT = Number(process.env.QA_UPSTREAM_PORT ?? 18080);
const PORT = Number(process.env.QA_PROXY_PORT ?? 18082);

const server = http.createServer((req, res) => {
  const path = req.url ?? "";
  if (path.includes("/vod")) {
    const body = JSON.stringify({
      type: "about:blank",
      title: "Service Unavailable",
      status: 503,
      code: "CATALOGUE_UNAVAILABLE",
      detail: "QA fault injection (S10-04 partial search error)",
    });
    res.writeHead(503, {
      "content-type": "application/problem+json",
      "content-length": Buffer.byteLength(body),
    });
    res.end(body);
    return;
  }

  const headers = { ...req.headers, host: `${UPSTREAM_HOST}:${UPSTREAM_PORT}` };
  const upstream = http.request(
    { host: UPSTREAM_HOST, port: UPSTREAM_PORT, method: req.method, path, headers },
    (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers);
      upstreamRes.pipe(res);
    },
  );
  upstream.on("error", (error) => {
    res.writeHead(502, { "content-type": "text/plain" });
    res.end(`proxy upstream error: ${error.message}`);
  });
  req.pipe(upstream);
});

server.listen(PORT, () => {
  console.log(
    `QA search fault proxy on http://127.0.0.1:${PORT} -> ${UPSTREAM_HOST}:${UPSTREAM_PORT} (503 on /vod)`,
  );
});
