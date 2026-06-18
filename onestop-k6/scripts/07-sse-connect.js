// Requires xk6-sse: xk6 build --with github.com/phymbert/xk6-sse
import sse from 'k6/x/sse';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { url, PATHS } from '../lib/config.js';
import { required } from '../lib/common.js';
const connected=new Counter('sse_connected'); const latency=new Trend('sse_connect_latency',true); const events=new Counter('sse_events');
export const options={scenarios:{connections:{executor:'per-vu-iterations',vus:Number(__ENV.VUS||20),iterations:1,maxDuration:__ENV.MAX_DURATION||'1m'}},thresholds:{sse_connect_latency:['p(95)<1000']}};
export default function(){const tokens=JSON.parse(required('ACCESS_TOKENS_JSON'));const start=Date.now();const r=sse.open(url(PATHS.sseSubscribe),{headers:{Authorization:`Bearer ${tokens[(__VU-1)%tokens.length]}`,Accept:'text/event-stream'},timeout:__ENV.SSE_TIMEOUT||'30s'},client=>{client.on('open',()=>{connected.add(1);latency.add(Date.now()-start)});client.on('event',()=>events.add(1));client.on('error',e=>console.error(`SSE VU=${__VU}: ${e.error()}`));});check(r,{'SSE 200':x=>x&&x.status===200});}
