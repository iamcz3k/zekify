import { useEffect, useMemo, useRef, useState } from "react";
import Hls from "hls.js";
import { ScreenOrientation } from "@capacitor/screen-orientation";

const API = (import.meta.env.VITE_STREAM_API_BASE || "https://laczekstream.qzz.io").replace(/\/$/, "");
type Team = { name?: string; badge?: string };
type Source = { id?: string; streamNo?: number; source?: string; language?: string; hd?: boolean; embedUrl?: string };
type Match = { id: string; title?: string; league?: string; date?: number; status: "live" | "upcoming"; available?: boolean; teams?: {home?:Team;away?:Team}; sources?: Source[] };
const localTime = (value?: number) => value ? new Intl.DateTimeFormat(undefined,{weekday:"short",day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"}).format(new Date(value)) : "Kickoff TBC";
function Ball({small=false}:{small?:boolean}) { return <span className={small?"ball-icon small":"ball-icon"} aria-hidden="true">⚽</span>; }
function TeamBadge({team,size="normal"}:{team?:Team,size?:"normal"|"large"}) {
 const [bad,setBad]=useState(false);
 const src=team?.badge?.startsWith("/")?API+team.badge:team?.badge;
 return <span className={"team-badge "+(size==="large"?"large":"")}>{src&&!bad?<img src={src} alt="" onError={()=>setBad(true)}/>:<Ball small/>}</span>;
}
export default function App() {
 const [matches,setMatches]=useState<Match[]>([]);
 const [tab,setTab]=useState<"live"|"upcoming">("live");
 const [leagueFilter,setLeagueFilter]=useState("All");
 const [searchQuery,setSearchQuery]=useState("");
 const [loading,setLoading]=useState(true),[error,setError]=useState("");
 const [selected,setSelected]=useState<Match|null>(null),[detail,setDetail]=useState<Match|null>(null);
 const [sourceIndex,setSourceIndex]=useState(0),[streamError,setStreamError]=useState("");
 const [retry,setRetry]=useState(0),[fillMode,setFillMode]=useState(false),[menuOpen,setMenuOpen]=useState(false);
 const videoWrap=useRef<HTMLDivElement>(null);
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
 const leagues=useMemo(()=>["All",...Array.from(new Set(matches.map(m=>(m.league||"Other competitions").trim()).filter(Boolean))).sort((a,b)=>a.localeCompare(b))],[matches]);
 const visible=useMemo(()=>{
  const query=searchQuery.trim().toLocaleLowerCase();
  return matches.filter(m=>{
   if(m.status!==tab)return false;
   const league=(m.league||"Other competitions").trim();
   if(leagueFilter!=="All"&&league!==leagueFilter)return false;
   if(query){const haystack=[m.title,m.league,m.teams?.home?.name,m.teams?.away?.name].filter(Boolean).join(" ").toLocaleLowerCase();if(!haystack.includes(query))return false;}
   return true;
  }).sort((a,b)=>(a.date??0)-(b.date??0));
 },[matches,tab,leagueFilter,searchQuery]);
 async function openMatch(match:Match){
  setSelected(match);setDetail(null);setSourceIndex(0);setStreamError("");setRetry(0);setFillMode(false);
  // Android Capacitor native orientation lock works on user tap; web browsers may reject it.
  try{await ScreenOrientation.lock({orientation:"landscape"});}catch{}
  try{
   const response=await fetch(API+"/api/public/football-streams?mode=detail&sport=football&id="+encodeURIComponent(match.id));
   if(!response.ok) throw new Error("Match details unavailable");
   const payload=await response.json();
   if(!payload.success||!payload.data) throw new Error("No stream details were returned");
   setDetail(payload.data as Match);
  }catch(e){setStreamError(e instanceof Error?e.message:"Could not load stream details");}
 }
 async function closePlayer(){
  setSelected(null);setDetail(null);setFillMode(false);
  try{await ScreenOrientation.lock({orientation:"portrait"});}catch{try{await ScreenOrientation.unlock();}catch{}}
 }
 async function fullscreen(){
  const el=videoWrap.current;
  if(!el)return;
  try{
   const v=el.querySelector("video") as (HTMLVideoElement & {webkitEnterFullscreen?:()=>void})|null;
   if(v?.webkitEnterFullscreen){v.webkitEnterFullscreen();return;}
   if(!document.fullscreenElement){await (el.requestFullscreen?.()??v?.requestFullscreen?.());}
   else await document.exitFullscreen();
   try{await ScreenOrientation.lock({orientation:"landscape"});}catch{}
  }catch{}
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
 const home=detail?.teams?.home??selected?.teams?.home;
 const away=detail?.teams?.away??selected?.teams?.away;
 return <main className="app-shell">
  <header className="topbar"><div className="brand"><Ball/><span>ZEKIFY</span></div><div className="header-actions"><span className="live-dot">FOOTBALL</span><button className="menu-btn" aria-label="Open menu" onClick={()=>setMenuOpen(true)}><span>☰</span> Menu</button></div></header>
  {menuOpen&&<div className="menu-overlay" onClick={()=>setMenuOpen(false)}><aside className="menu-drawer" onClick={e=>e.stopPropagation()}><div className="drawer-head"><div className="brand"><Ball/> ZEKIFY</div><button className="close-menu" onClick={()=>setMenuOpen(false)}>✕</button></div><div className="owner-card"><span className="eyebrow">THE CREATOR</span><h2>Developed by<br/><em>TITUS LACZEK</em></h2><p>Made for the game. Built with passion.</p></div><div className="social-links"><a href="https://x.com/iam_czek" target="_blank" rel="noreferrer"><span>𝕏</span> X / Twitter <b>↗</b></a><a href="https://www.tiktok.com/@iamczek" target="_blank" rel="noreferrer"><span>♪</span> TikTok <b>↗</b></a><a href="https://www.instagram.com/iamczek" target="_blank" rel="noreferrer"><span>◎</span> Instagram <b>↗</b></a><a href="mailto:laczekrsa@proton.me"><span>✉</span> Email <b>↗</b></a></div><p className="drawer-foot">ZEKIFY · FOOTBALL EVERYWHERE</p></aside></div>}
  {selected?<section className="player-page">
   <button className="back" onClick={()=>void closePlayer()}>‹ Back to matches</button>
   <div className="match-heading"><small>{detail?.league??selected.league??"FOOTBALL"}</small><h1><TeamBadge team={home}/>{home?.name??selected.title??"Home"} <span>vs</span> <TeamBadge team={away}/>{away?.name??"Away"}</h1><p>{localTime(detail?.date??selected.date)}</p></div>
   <div className="player-controls"><button onClick={()=>setFillMode(v=>!v)} className={fillMode?"control active":"control"}>{fillMode?"↔ Fit video":"⛶ Fill / Zoom"}</button><button className="control" onClick={()=>void fullscreen()}>⛶ Fullscreen</button><button className="control" onClick={()=>{try{void ScreenOrientation.lock({orientation:"landscape"});}catch{}}}>↻ Rotate</button></div>
   <div className={"video-wrap "+(fillMode?"fill-video":"")} ref={videoWrap}>{stream?<video id="zekify-player" controls playsInline autoPlay/>:<div className="player-placeholder"><Ball/><p>{streamError||"Loading stream sources…"}</p></div>}</div>
   {fillMode&&<p className="zoom-note">Fill mode crops the video edges to remove black bars. Tap Fit video to restore the original ratio.</p>}
   {streamError&&<p className="hint">{streamError}<button className="retry" onClick={()=>{setRetry(x=>x+1);setStreamError("");}}>Retry source</button></p>}
   {sources.length>0&&<div className="source-panel"><div className="source-title">AVAILABLE SOURCES</div><div className="source-list">{sources.map((s,i)=><button key={s.id??i} className={i===sourceIndex?"source active":"source"} onClick={()=>{setSourceIndex(i);setRetry(x=>x+1);setStreamError("");}}>{s.source||"Source "+(i+1)}{s.hd?" · HD":""}</button>)}</div></div>}
  </section>:<>
   <section className="hero"><div className="hero-copy"><span className="eyebrow">YOUR MATCHDAY STARTS HERE</span><h1>Football.<br/><em>Without limits.</em></h1><p>Live action and upcoming fixtures, all in one place.</p><div className="timezone">Times shown in your device timezone · {timezone}</div></div><div className="hero-ball"><Ball/></div></section>
   <section className="matches-section"><div className="section-head"><div><span className="eyebrow">THE ACTION</span><h2>Matches</h2></div><button className="refresh" onClick={()=>void load()}>↻ Refresh</button></div>
    <div className="tabs"><button className={tab==="live"?"active":""} onClick={()=>setTab("live")}>● Live</button><button className={tab==="upcoming"?"active":""} onClick={()=>setTab("upcoming")}>Upcoming</button></div>
    <label className="match-search"><span>⌕</span><input type="search" value={searchQuery} onChange={e=>setSearchQuery(e.target.value)} placeholder="Search teams, matches or leagues" aria-label="Search matches"/></label>
    <div className="league-tabs" aria-label="Filter matches by league">{leagues.map(league=><button key={league} className={leagueFilter===league?"active":""} onClick={()=>setLeagueFilter(league)}>{league}</button>)}</div>
    {loading?<div className="state">Loading football…</div>:error?<div className="state error">{error}<button onClick={()=>void load()}>Try again</button></div>:visible.length===0?<div className="state">No {tab} matches match your filters right now.</div>:<div className="match-list">{visible.map(m=><button className="match-card" key={m.id} onClick={()=>void openMatch(m)}>
     <div className="match-meta"><span>{m.league||"Football"}</span>{m.status==="live"?<b className="live-label">LIVE</b>:<time>{localTime(m.date)}</time>}</div>
     <div className="teams"><div className="team"><TeamBadge team={m.teams?.home}/><strong>{m.teams?.home?.name??m.title??"Home"}</strong></div><span className="versus">VS</span><div className="team away"><TeamBadge team={m.teams?.away}/><strong>{m.teams?.away?.name??"Away"}</strong></div></div>
     <div className="card-footer"><span>{m.status==="live"?"Watch live":"Match details"}</span><span>↗</span></div>
    </button>)}</div>}
   </section>
  </>}
  <footer><Ball small/> ZEKIFY <span>·</span> MADE FOR THE GAME</footer>
 </main>;
}
