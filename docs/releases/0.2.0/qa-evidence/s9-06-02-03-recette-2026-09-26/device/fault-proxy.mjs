import http from "node:http";
const TARGET_HOST = "127.0.0.1", TARGET_PORT = 8080, PORT = Number(process.env.PROXY_PORT ?? 18082);
const server = http.createServer((req, res) => {
  if (req.url.includes("/epg")) {
    res.writeHead(503, { "content-type": "application/json" });
    res.end(JSON.stringify({ title: "EPG_UNAVAILABLE", detail: "QA fault proxy" }));
    console.log("503", req.method, req.url);
    return;
  }
  const opts = { host: TARGET_HOST, port: TARGET_PORT, method: req.method, path: req.url, headers: { ...req.headers, host: `${TARGET_HOST}:${TARGET_PORT}` } };
  const up = http.request(opts, (ur) => { res.writeHead(ur.statusCode ?? 502, ur.headers); ur.pipe(res); });
  up.on("error", (e) => { res.writeHead(502, {"content-type":"application/json"}); res.end(JSON.stringify({title:"PROXY",detail:String(e)})); });
  req.pipe(up);
});
server.listen(PORT, "127.0.0.1", () => console.log(`fault-proxy on ${PORT} -> ${TARGET_HOST}:${TARGET_PORT} (503 on /epg)`));
