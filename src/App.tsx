import { useEffect, useMemo, useRef, useState } from "react";
import Hls from "hls.js";
import { App as CapacitorApp } from "@capacitor/app";
import { ScreenOrientation } from "@capacitor/screen-orientation";
import { registerPlugin } from "@capacitor/core";

const ImmersiveMode = registerPlugin<{ enter(): Promise<void>; exit(): Promise<void> }>("ImmersiveMode");
const API = (import.meta.env.VITE_STREAM_API_BASE || "https://laczekstream.qzz.io").replace(/\/$/, "");
type Team = { name?: string; badge?: string };
type Source = { id?: string; streamNo?: number; source?: string; language?: string; hd?: boolean; embedUrl?: string };
type Match = { id: string; title?: string; league?: string; date?: number; status: "live" | "upcoming"; available?: boolean; teams?: {home?:Team;away?:Team}; sources?: Source[] };
const localTime = (value?: number) => value ? new Intl.DateTimeFormat(undefined,{weekday:"short",day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"}).format(new Date(value)) : "Kickoff TBC";

function Ball({small=false}:{small?:boolean}) {
 return <svg className={small?"ball-icon small":"ball-icon"} viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="21" fill="#f4f4f4"/><path d="m24 12 8 6-3 9H19l-3-9z" fill="#ff8a3d"/><path d="m24 12 0-8 11-3 6 8-9 9M32 18l9-9 6 9-4 11-11-2M29 27l14 2-1 12-11 5-2-19M19 27l-2 19-11-5-1-12 14-2M16 18 7 9l6-8 11 3v8" fill="none" stroke="#171717" strokeWidth="2" strokeLinejoin="round"/></svg>;
}
function BrandIcon({kind}:{kind:"x"|"tiktok"|"instagram"|"mail"}) {
 if(kind==="x") return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M18.9 2H22l-6.8 7.8L23.2 22h-6.3L12 14.6 5.6 22H2.4l7.3-8.4L1.8 2h6.5l4.5 6.8L18.9 2Zm-1.1 18h1.7L7.3 3.9H5.5L17.8 20Z"/></svg>;
 if(kind==="tiktok") return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M19.6 5.7a5.7 5.7 0 0 1-3.5-4.2V1h-4.2v14.3a2.8 2.8 0 1 1-2.8-2.8c.4 0 .8.1 1.1.2V8.4a7 7 0 1 0 5.9 6.9V8a9.8 9.8 0 0 0 5.2 1.5V5.3c-.6 0-1.2.1-1.7.4Z"/></svg>;
 if(kind==="instagram") return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5Zm0 2.2A2.8 2.8 0 0 0 4.2 7v10A2.8 2.8 0 0 0 7 19.8h10a2.8 2.8 0 0 0 2.8-2.8V7A2.8 2.8 0 0 0 17 4.2H7Zm5 2.3a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11Zm0 2.2a3.3 3.3 0 1 0 0 6.6 3.3 3.3 0 0 0 0-6.6Zm5.8-3.4a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6Z"/></svg>;
 return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M3 4h18a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm9 9.2L3.3 6.1v11.8h17.4V6.1L12 13.2Zm0-2.7L20.1 6H3.9l8.1 4.5Z"/></svg>;
}
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
 const [playing,setPlaying]=useState(false),[sourceIndex,setSourceIndex]=useState(0),[streamError,setStreamError]=useState("");
 const [retry,setRetry]=useState(0),[fillMode,setFillMode]=useState(true),[menuOpen,setMenuOpen]=useState(false),[controlsVisible,setControlsVisible]=useState(true),[isPaused,setIsPaused]=useState(false);
 const videoWrap=useRef<HTMLDivElement>(null),videoRef=useRef<HTMLVideoElement>(null);
 const timezone=Intl.DateTimeFormat().resolvedOptions().timeZone||"device local time";
 async function load(){
  setLoading(true);setError("");
  try{
   const response=await fetch(API+"/api/public/football-streams?mode=matches&sport=football",{headers:{Accept:"application/json"}});
   if(!response.ok) throw new Error("Football service returned "+response.status);
   const payload=await response.json(),list=payload.data;
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
  setSelected(match);setDetail(null);setPlaying(false);setSourceIndex(0);setStreamError("");setRetry(0);setFillMode(true);
  try{await ScreenOrientation.lock({orientation:"portrait"});}catch{}
  try{await ImmersiveMode.exit();}catch{}
  try{
   const response=await fetch(API+"/api/public/football-streams?mode=detail&sport=football&id="+encodeURIComponent(match.id));
   if(!response.ok) throw new Error("Match details unavailable");
   const payload=await response.json();
   if(!payload.success||!payload.data) throw new Error("No stream details were returned");
   setDetail(payload.data as Match);
  }catch(e){setStreamError(e instanceof Error?e.message:"Could not load match details");}
 }
 async function startPlayer(){
  if(!detail?.sources?.length){setStreamError("No stream sources are available for this match yet.");return;}
  setPlaying(true);setControlsVisible(true);setIsPaused(false);
  try{await ScreenOrientation.lock({orientation:"landscape"});}catch{}
  try{await ImmersiveMode.enter();}catch{}
 }
 async function exitPlayer(){
  setPlaying(false);setControlsVisible(true);
  try{await ImmersiveMode.exit();}catch{}
  try{await ScreenOrientation.lock({orientation:"portrait"});}catch{try{await ScreenOrientation.unlock();}catch{}}
 }
 function backToMatches(){
  if(playing){void exitPlayer();return;}
  setSelected(null);setDetail(null);setStreamError("");
 }
 useEffect(()=>{
  let listener:{remove:()=>Promise<void>}|undefined;
  void CapacitorApp.addListener("backButton",()=>{
   if(menuOpen){setMenuOpen(false);return;}
   if(playing){void exitPlayer();return;}
   if(selected){backToMatches();return;}
  }).then(handle=>{listener=handle;});
  return()=>{void listener?.remove();};
 },[playing,selected,menuOpen]);
 useEffect(()=>()=>{void ImmersiveMode.exit();void ScreenOrientation.lock({orientation:"portrait"});},[]);
 const sources=detail?.sources??[];
 const active=sources[sourceIndex];
 const stream=active?.embedUrl?.startsWith("/")?API+active.embedUrl:active?.embedUrl??"";
 useEffect(()=>{
  const video=videoRef.current;
  if(!video||!stream||!playing)return;
  let hls:Hls|undefined,cancelled=false;
  const fail=()=>{if(!cancelled)setStreamError("This source is not responding. Try another source.");};
  setStreamError("");
  if(Hls.isSupported()){
   hls=new Hls({enableWorker:true,maxBufferLength:20,manifestLoadingMaxRetry:4,levelLoadingMaxRetry:5,fragLoadingMaxRetry:5});
   hls.loadSource(stream);hls.attachMedia(video);
   hls.on(Hls.Events.ERROR,(_event,data)=>{if(!data.fatal)return;if(data.type===Hls.ErrorTypes.MEDIA_ERROR)hls?.recoverMediaError();else fail();});
  }else if(video.canPlayType("application/vnd.apple.mpegurl")){video.src=stream;video.onerror=fail;}
  else fail();
  return()=>{cancelled=true;hls?.destroy();video.pause();video.removeAttribute("src");video.load();};
 },[stream,retry,playing]);
 async function toggleFullscreen(){
  const el=videoWrap.current;if(!el)return;
  try{if(!document.fullscreenElement)await el.requestFullscreen?.();else await document.exitFullscreen();}catch{}
  try{await ImmersiveMode.enter();}catch{}
 }
 function togglePlayback(){
  const video=videoRef.current;if(!video)return;
  if(video.paused){void video.play().then(()=>setIsPaused(false)).catch(()=>setIsPaused(true));}
  else{video.pause();setIsPaused(true);}
 }
 const home=detail?.teams?.home??selected?.teams?.home,away=detail?.teams?.away??selected?.teams?.away;
 return <main className="app-shell">
  {!playing&&<header className="topbar"><div className="brand"><Ball/><span>ZEKIFY</span></div><div className="header-actions"><span className="live-dot">FOOTBALL</span><button className="menu-btn" aria-label="Open menu" onClick={()=>setMenuOpen(true)}><span>☰</span> Menu</button></div></header>}
  {menuOpen&&<div className="menu-overlay" onClick={()=>setMenuOpen(false)}><aside className="menu-drawer" onClick={e=>e.stopPropagation()}><div className="drawer-head"><div className="brand"><Ball/> ZEKIFY</div><button className="close-menu" aria-label="Close menu" onClick={()=>setMenuOpen(false)}>×</button></div><div className="owner-card"><span className="eyebrow">THE CREATOR</span><h2>Developed by<br/><em>TITUS LACZEK</em></h2><p>Made for the game. Built with passion.</p></div><div className="social-links"><a href="https://x.com/iam_czek" target="_blank" rel="noreferrer"><span><BrandIcon kind="x"/></span> X / Twitter <b>↗</b></a><a href="https://www.tiktok.com/@iamczek" target="_blank" rel="noreferrer"><span><BrandIcon kind="tiktok"/></span> TikTok <b>↗</b></a><a href="https://www.instagram.com/iamczek" target="_blank" rel="noreferrer"><span><BrandIcon kind="instagram"/></span> Instagram <b>↗</b></a><a href="mailto:laczekrsa@proton.me"><span><BrandIcon kind="mail"/></span> Email <b>↗</b></a></div><p className="drawer-foot">ZEKIFY · FOOTBALL EVERYWHERE</p></aside></div>}
  {selected&&!playing?<section className="detail-page">
   <button className="back" onClick={backToMatches}><span aria-hidden="true">←</span> Back to matches</button>
   <div className="detail-hero"><span className="eyebrow">{detail?.league??selected.league??"FOOTBALL"}</span><div className="detail-teams"><div><TeamBadge team={home} size="large"/><strong>{home?.name??selected.title??"Home"}</strong></div><span className="detail-vs">VS</span><div><TeamBadge team={away} size="large"/><strong>{away?.name??"Away"}</strong></div></div><p>{localTime(detail?.date??selected.date)}</p><span className={selected.status==="live"?"detail-status live":"detail-status"}>{selected.status==="live"?"LIVE NOW":"UPCOMING FIXTURE"}</span></div>
   <div className="detail-info"><h2>Match details</h2><p>{detail?.title??selected.title??"Football match"}</p><div className="detail-meta"><span>Competition</span><b>{detail?.league??selected.league??"Football"}</b></div><div className="detail-meta"><span>Stream sources</span><b>{sources.length||"Checking…"}</b></div>{streamError&&<p className="hint">{streamError}</p>}<button className="watch-button" disabled={!detail?.sources?.length} onClick={()=>void startPlayer()}><span>▶</span> Watch match</button></div>
  </section>:playing?<section className={"player-page immersive-player "+(fillMode?"fill-video":"fit-video")} onClick={()=>setControlsVisible(v=>!v)}>
   <div className="video-wrap" ref={videoWrap}><video ref={videoRef} id="zekify-player" playsInline autoPlay onPlay={()=>setIsPaused(false)} onPause={()=>setIsPaused(true)}/>{!isPaused&&<button className="video-tap-layer" aria-label="Show player controls" onClick={e=>{e.stopPropagation();setControlsVisible(v=>!v);}}/>}</div>
   {controlsVisible&&<div className="immersive-controls" onClick={e=>e.stopPropagation()}><button onClick={()=>void exitPlayer()} aria-label="Back to match details">← Back</button><div className="immersive-actions"><button onClick={togglePlayback} aria-label={isPaused?"Play":"Pause"}>{isPaused?"▶":"Ⅱ"}</button><button className={!fillMode?"selected":""} onClick={()=>setFillMode(v=>!v)}>{fillMode?"Fit":"Fill"}</button><button onClick={()=>{setSourceIndex((sourceIndex+1)%Math.max(1,sources.length));setRetry(v=>v+1);}} disabled={sources.length<2}>Source {sourceIndex+1}/{sources.length}</button><button onClick={()=>void toggleFullscreen()} aria-label="Toggle fullscreen">⛶</button></div></div>}
   {streamError&&<div className="immersive-error" onClick={e=>e.stopPropagation()}>{streamError}<button onClick={()=>{setRetry(v=>v+1);setStreamError("");}}>Retry</button></div>}
   {sources.length>1&&controlsVisible&&<div className="immersive-source-list" onClick={e=>e.stopPropagation()}>{sources.map((s,i)=><button key={s.id??i} className={i===sourceIndex?"active":""} onClick={()=>{setSourceIndex(i);setRetry(v=>v+1);setStreamError("");}}>{s.source||"Source "+(i+1)}{s.hd?" · HD":""}</button>)}</div>}
  </section>:<>
   <section className="hero"><div className="hero-copy"><span className="eyebrow">YOUR MATCHDAY STARTS HERE</span><h1>Football.<br/><em>Without limits.</em></h1><p>Live action and upcoming fixtures, all in one place.</p><div className="timezone">Times shown in your device timezone · {timezone}</div></div><div className="hero-ball"><Ball/></div></section>
   <section className="matches-section"><div className="section-head"><div><span className="eyebrow">THE ACTION</span><h2>Matches</h2></div><button className="refresh" onClick={()=>void load()}>↻ Refresh</button></div>
    <div className="tabs"><button className={tab==="live"?"active":""} onClick={()=>setTab("live")}>● Live</button><button className={tab==="upcoming"?"active":""} onClick={()=>setTab("upcoming")}>Upcoming</button></div>
    <label className="match-search"><span>⌕</span><input type="search" value={searchQuery} onChange={e=>setSearchQuery(e.target.value)} placeholder="Search teams, matches or leagues" aria-label="Search matches"/></label>
    <div className="league-tabs" aria-label="Filter matches by league">{leagues.map(league=><button key={league} className={leagueFilter===league?"active":""} onClick={()=>setLeagueFilter(league)}>{league}</button>)}</div>
    {loading?<div className="state">Loading football…</div>:error?<div className="state error">{error}<button onClick={()=>void load()}>Try again</button></div>:visible.length===0?<div className="state">No {tab} matches match your filters right now.</div>:<div className="match-list">{visible.map(m=><button className="match-card" key={m.id} onClick={()=>void openMatch(m)}>
     <div className="match-meta"><span>{m.league||"Football"}</span>{m.status==="live"?<b className="live-label">LIVE</b>:<time>{localTime(m.date)}</time>}</div>
     <div className="teams"><div className="team"><TeamBadge team={m.teams?.home}/><strong>{m.teams?.home?.name??m.title??"Home"}</strong></div><span className="versus">VS</span><div className="team away"><TeamBadge team={m.teams?.away}/><strong>{m.teams?.away?.name??"Away"}</strong></div></div>
     <div className="card-footer"><span>{m.status==="live"?"Match details":"Match details"}</span><span>→</span></div>
    </button>)}</div>}
   </section>
  </>}
  {!playing&&<footer><Ball small/> ZEKIFY <span>·</span> MADE FOR THE GAME</footer>}
 </main>;
}
