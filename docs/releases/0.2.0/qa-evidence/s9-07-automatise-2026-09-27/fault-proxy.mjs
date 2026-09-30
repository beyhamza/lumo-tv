// QA fault proxy for S9-06-04 / GD-10 (QA-06-04-06).
// Forwards every request to lumo-api on 18080, EXCEPT the guide reads
// (`.../epg`), which answer 503. This lets the acceptance run provoke a
// failing guide read without patching the repository's compose file.
import http from "node:http";

const UPSTREAM_HOST = "127.0.0.1";
const UPSTREAM_PORT = Number(process.env.QA_UPSTREAM_PORT ?? 18080);
const PORT = Number(process.env.QA_PROXY_PORT ?? 18082);

const server = http.createServer((req, res) => {
  if (req.url && req.url.includes("/epg")) {
    const body = JSON.stringify({
      type: "about:blank",
      title: "Service Unavailable",
      status: 503,
      code: "EPG_UNAVAILABLE",
      detail: "QA fault injection (S9-06-04 / GD-10)",
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
    { host: UPSTREAM_HOST, port: UPSTREAM_PORT, method: req.method, path: req.url, headers },
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
  console.log(`QA fault proxy on http://127.0.0.1:${PORT} -> ${UPSTREAM_HOST}:${UPSTREAM_PORT} (503 on /epg)`);
});
