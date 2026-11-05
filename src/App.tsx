import { useEffect, useMemo, useState } from "react";
import Hls from "hls.js";

const API = (import.meta.env.VITE_STREAM_API_BASE || "https://laczekstream.qzz.io").replace(/\/$/, "");
type Team = { name?: string; badge?: string };
type Source = { id?: string; streamNo?: number; source?: string; language?: string; hd?: boolean; embedUrl?: string };
type Match = { id: string; title?: string; league?: string; date?: number; status: "live" | "upcoming"; available?: boolean; teams?: {home?:Team;away?:Team}; sources?: Source[] };
const localTime = (value?: number) => value ? new Intl.DateTimeFormat(undefined,{weekday:"short",day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"}).format(new Date(value)) : "Kickoff TBC";
function Ball({small=false}:{small?:boolean}) { return <span className={small?"ball-icon small":"ball-icon"} aria-hidden="true">⚽</span>; }
export default function App() {
 const [matches,setMatches]=useState<Match[]>([]);
 const [tab,setTab]=useState<"live"|"upcoming">("live");
 const [loading,setLoading]=useState(true),[error,setError]=useState("");
 const [selected,setSelected]=useState<Match|null>(null),[detail,setDetail]=useState<Match|null>(null);
 const [sourceIndex,setSourceIndex]=useState(0),[streamError,setStreamError]=useState("");
 const [retry,setRetry]=useState(0);
 const timezone=Intl.DateTimeFormat().resolvedOptions().timeZone||"device local time";
 async function load(){
  setLoading(true);setError("");
  try{
   const response=await fetch(API+"/api/public/football-streams?mode=matches&sport=football",{headers:{Accept:"application/json"}});
   if(!response.ok) throw new Error("Football service returned "+response.status);
   const payload=await response.json();
   const list=payload.data;
   if(!payload.success||!Array.isArray(list)) throw new Error("Football service returned unexpected match data");
   setMatches(list as Match[]);
  }catch(e){setError(e instanceof Error?e.message:"Could not load matches");}
  finally{setLoading(false);}
 }
 useEffect(()=>{void load();},[]);
 const visible=useMemo(()=>matches.filter(m=>m.status===tab).sort((a,b)=>(a.date??0)-(b.date??0)),[matches,tab]);
 async function openMatch(match:Match){
  setSelected(match);setDetail(null);setSourceIndex(0);setStreamError("");setRetry(0);
  try{
   const response=await fetch(API+"/api/public/football-streams?mode=detail&sport=football&id="+encodeURIComponent(match.id));
   if(!response.ok) throw new Error("Match details unavailable");
   const payload=await response.json();
   if(!payload.success||!payload.data) throw new Error("No stream details were returned");
   setDetail(payload.data as Match);
  }catch(e){setStreamError(e instanceof Error?e.message:"Could not load stream details");}
 }
 const sources=detail?.sources??[];
 const active=sources[sourceIndex];
 const stream=active?.embedUrl?.startsWith("/")?API+active.embedUrl:active?.embedUrl??"";
 useEffect(()=>{
  const video=document.querySelector<HTMLVideoElement>("#zekify-player");
  if(!video||!stream)return;
  let hls:Hls|undefined, cancelled=false;
  const fail=()=>{if(!cancelled)setStreamError("This source is not responding. Try another source.");};
  setStreamError("");
  if(Hls.isSupported()){
   hls=new Hls({enableWorker:true,maxBufferLength:20,manifestLoadingMaxRetry:4,levelLoadingMaxRetry:5,fragLoadingMaxRetry:5});
   hls.loadSource(stream);hls.attachMedia(video);
   hls.on(Hls.Events.ERROR,(_event,data)=>{if(!data.fatal)return;if(data.type===Hls.ErrorTypes.MEDIA_ERROR)hls?.recoverMediaError();else fail();});
  }else if(video.canPlayType("application/vnd.apple.mpegurl")){video.src=stream;video.onerror=fail;}
  else fail();
  return()=>{cancelled=true;hls?.destroy();video.pause();video.removeAttribute("src");video.load();};
 },[stream,retry]);
 const home=detail?.teams?.home?.name??selected?.teams?.home?.name??"Home";
 const away=detail?.teams?.away?.name??selected?.teams?.away?.name??"Away";
 return <main className="app-shell">
  <header className="topbar"><div className="brand"><Ball/><span>ZEKIFY</span></div><span className="live-dot">FOOTBALL</span></header>
  {selected?<section className="player-page">
   <button className="back" onClick={()=>{setSelected(null);setDetail(null);}}>‹ Back to matches</button>
   <div className="match-heading"><small>{detail?.league??selected.league??"FOOTBALL"}</small><h1>{home} <span>vs</span> {away}</h1><p>{localTime(detail?.date??selected.date)}</p></div>
   <div className="video-wrap">{stream?<video id="zekify-player" controls playsInline autoPlay/>:<div className="player-placeholder"><Ball/><p>{streamError||"Loading stream sources…"}</p></div>}</div>
   {streamError&&<p className="hint">{streamError}<button className="retry" onClick={()=>{setRetry(x=>x+1);setStreamError("");}}>Retry source</button></p>}
   {sources.length>0&&<div className="source-panel"><div className="source-title">AVAILABLE SOURCES</div><div className="source-list">{sources.map((s,i)=><button key={s.id??i} className={i===sourceIndex?"source active":"source"} onClick={()=>{setSourceIndex(i);setRetry(x=>x+1);setStreamError("");}}>{s.source||"Source "+(i+1)}{s.hd?" · HD":""}</button>)}</div></div>}
  </section>:<>
   <section className="hero"><div className="hero-copy"><span className="eyebrow">YOUR MATCHDAY STARTS HERE</span><h1>Football.<br/><em>Without limits.</em></h1><p>Live action and upcoming fixtures, all in one place.</p><div className="timezone">Times shown in your device timezone · {timezone}</div></div><div className="hero-ball"><Ball/></div></section>
   <section className="matches-section"><div className="section-head"><div><span className="eyebrow">THE ACTION</span><h2>Matches</h2></div><button className="refresh" onClick={()=>void load()}>↻ Refresh</button></div>
    <div className="tabs"><button className={tab==="live"?"active":""} onClick={()=>setTab("live")}>● Live</button><button className={tab==="upcoming"?"active":""} onClick={()=>setTab("upcoming")}>Upcoming</button></div>
    {loading?<div className="state">Loading football…</div>:error?<div className="state error">{error}<button onClick={()=>void load()}>Try again</button></div>:visible.length===0?<div className="state">No {tab} matches found right now.</div>:<div className="match-list">{visible.map(m=><button className="match-card" key={m.id} onClick={()=>void openMatch(m)}>
     <div className="match-meta"><span>{m.league||"Football"}</span>{m.status==="live"?<b className="live-label">LIVE</b>:<time>{localTime(m.date)}</time>}</div>
     <div className="teams"><strong>{m.teams?.home?.name??m.title??"Home"}</strong><span className="versus">VS</span><strong>{m.teams?.away?.name??"Away"}</strong></div>
     <div className="card-footer"><span>{m.status==="live"?"Watch live":"Match details"}</span><span>↗</span></div>
    </button>)}</div>}
   </section>
  </>}
  <footer><Ball small/> ZEKIFY <span>·</span> MADE FOR THE GAME</footer>
 </main>;
}
