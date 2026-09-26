const API="http://localhost:8080/v1";
const email="qa.mobile.cold.20260926@test.example", password=process.env.QA_PASS??"lumo-qa-cold-2026!";
const j=r=>r.text().then(t=>({status:r.status,body:t?JSON.parse(t):null}));
const l=await j(await fetch(`${API}/auth/login`,{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({email,password,device:{platform:"ANDROID_MOBILE",name:"QA"}})}));
const auth={authorization:`Bearer ${l.body.access_token??l.body.accessToken}`};
const src=(await j(await fetch(`${API}/sources`,{headers:auth}))).body.items.find(s=>s.label==="Banc à froid mobile");
console.log("A:", src.id, src.status);
let ok=false;
for(let a=0;a<9 && !ok;a++){
  const r=await fetch(`${API}/sources/${src.id}/sync`,{method:"POST",headers:auth});
  console.log(new Date().toISOString(),"sync ->",r.status);
  if(r.status<300){ok=true;break;}
  if(r.status===429){console.log("429, wait 60s");await new Promise(x=>setTimeout(x,60000));}
  else break;
}
if(!ok){console.log("SYNC_NOT_ACCEPTED");process.exit(2);}
for(let i=0;i<90;i++){const g=await j(await fetch(`${API}/sources/${src.id}`,{headers:auth}));if(g.body.status==="READY"||g.body.status==="ERROR"){console.log("status",g.body.status);break;}await new Promise(x=>setTimeout(x,1000));}
const ch=await j(await fetch(`${API}/sources/${src.id}/channels?pageSize=100`,{headers:auth}));
const items=ch.body.items??[];
console.log("CHANNELS_A="+items.length);
console.log(items.map(c=>`${c.name} tvg=${c.tvgId??c.epgId??"?"} id=${c.id.slice(0,8)}`).join("\n"));
const bench1=items.find(c=>/01/.test(c.name));
const from=new Date(Date.UTC(2026,8,26,0,0,0)).toISOString();
const to=new Date(Date.UTC(2026,8,27,0,0,0)).toISOString();
const epg=await j(await fetch(`${API}/sources/${src.id}/epg?channelIds=${bench1.id}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,{headers:auth}));
console.log("epg",epg.status);
const grid=epg.body?.channels??[];
for(const c of grid){const ps=c.programmes??[];console.log("channel",c.channelId?.slice(0,8),"progs",ps.length);for(const p of ps.filter(p=>/Nuit/.test(p.title??"")))console.log("  CROSS", p.title, p.startsAt, "->", p.endsAt);}
