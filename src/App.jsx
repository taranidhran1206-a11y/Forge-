import React, { useState, useEffect, useRef, useCallback } from "react";
import {
  Flame, Check, ChevronRight,
  CalendarDays, BarChart3, MessageSquare, Clock,
  Sparkles, Zap, Trophy, Target, Brain,
  Send, Loader2, ArrowRight, ArrowLeft,
  BookOpen, Search, Play, Pause, Square, Bell, BellOff,
  Home, Shield, Users, Globe, Lock, Crown,
  Plus, Pencil, X,
} from "lucide-react";

const LOGO = "data:image/png;base64,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";

// ── Rank System ──
const RANKS = [
  { name: "Iron",     minXP: 0,       color: "#6b7280", glow: "#6b728040" },
  { name: "Bronze",   minXP: 500,     color: "#cd7f32", glow: "#cd7f3240" },
  { name: "Silver",   minXP: 1500,    color: "#c0c0c0", glow: "#c0c0c040" },
  { name: "Gold",     minXP: 3000,    color: "#ffd700", glow: "#ffd70040" },
  { name: "Platinum", minXP: 6000,    color: "#00e5ff", glow: "#00e5ff40" },
  { name: "Diamond",  minXP: 10000,   color: "#b9f2ff", glow: "#b9f2ff40" },
  { name: "Titan",    minXP: 15000,   color: "#3b82f6", glow: "#3b82f640" },
  { name: "Mythic",   minXP: 25000,   color: "#a855f7", glow: "#a855f740" },
  { name: "Apex",     minXP: 50000,   color: "#ef4444", glow: "#ef444440" },
];

function getRank(xp) {
  let rank = RANKS[0];
  for (let i = RANKS.length - 1; i >= 0; i--) {
    if (xp >= RANKS[i].minXP) { rank = RANKS[i]; break; }
  }
  const idx = RANKS.indexOf(rank);
  const next = idx < RANKS.length - 1 ? RANKS[idx + 1] : null;
  const xpInRank = xp - rank.minXP;
  const xpForNext = next ? next.minXP - rank.minXP : 0;
  const progress = next ? xpInRank / xpForNext : 1;
  return { ...rank, idx, next, xpInRank, xpForNext, progress };
}

function usePersist(key, initial) {
  const [val, setVal] = useState(() => {
    try { const raw = localStorage.getItem(key); return raw !== null ? JSON.parse(raw) : initial; } catch { return initial; }
  });
  const [loaded] = useState(true);
  useEffect(() => {
    try { localStorage.setItem(key, JSON.stringify(val)); } catch {}
  }, [key, val]);
  const set = (fn) => { setVal((p) => typeof fn === "function" ? fn(p) : fn); };
  return [val, set, loaded];
}

const DAY_NAMES = ["S","M","T","W","T","F","S"];
const now = new Date();
const todayDOW = now.getDay();
const todayStr = now.toLocaleDateString("en-US", { weekday: "long", month: "short", day: "numeric" });
const todayShort = now.toLocaleDateString("en-US", { month: "short", day: "numeric" });
const todayKey = now.toISOString().slice(0, 10);
const hour = now.getHours();

const DEFAULT_TASKS = [
  { id: "bed", text: "Make Your Bed", desc: "Complete a task first thing in the morning", xp: 50, icon: "target", done: false },
  { id: "workout", text: "Back & Bicep Workout", desc: "30 min body weight routine", xp: 50, icon: "target", done: false },
  { id: "meal", text: "High Protein Meal", desc: "Eggs, toast, avocado, protein shake", xp: 50, icon: "brain", done: false },
  { id: "deepwork", text: "Deep work for 1 hour", desc: "Set the focus timer and get your work done!", xp: 50, icon: "target", done: false },
];

const CATEGORIES = [
  { key: "deepwork", label: "Deep Work", color: "#3b82f6", emoji: "🎯" },
  { key: "admin", label: "Admin / Logistics", color: "#6b7280", emoji: "📋" },
  { key: "social", label: "Social Media", color: "#ef4444", emoji: "📱" },
  { key: "learning", label: "Learning", color: "#22c55e", emoji: "📚" },
  { key: "health", label: "Health / Exercise", color: "#06b6d4", emoji: "💪" },
  { key: "rest", label: "Rest / Downtime", color: "#a78bfa", emoji: "😴" },
];

const ICON_MAP = { brain: Brain, eye: Search, target: Target, notebook: BookOpen };

function requestNotifPermission() {
  if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission();
  }
}
function sendNotif(title, body) {
  if ("Notification" in window && Notification.permission === "granted") {
    try { new Notification(title, { body, icon: LOGO }); } catch {}
  }
}

function fmtTime(s) {
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}
function fmtMins(totalSec) {
  const m = Math.round(totalSec / 60);
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const rm = m % 60;
  return rm > 0 ? `${h}h ${rm}m` : `${h}h`;
}

// ── Global Styles ──
const GLOBAL_CSS = `
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap');
  .no-scroll::-webkit-scrollbar{display:none}
  @keyframes xpUp{0%{opacity:0;transform:translateY(8px) scale(.9)}15%{opacity:1;transform:translateY(0) scale(1)}75%{opacity:1}100%{opacity:0;transform:translateY(-24px)}}
  .xp-pop{animation:xpUp 1.4s ease-out forwards}
  @keyframes pulse-ring{0%{box-shadow:0 0 0 0 rgba(59,130,246,.5)}70%{box-shadow:0 0 0 12px rgba(59,130,246,0)}100%{box-shadow:0 0 0 0 rgba(59,130,246,0)}}
  .timer-pulse{animation:pulse-ring 2s infinite}
  @keyframes rankGlow{0%,100%{filter:drop-shadow(0 0 8px var(--glow))}50%{filter:drop-shadow(0 0 16px var(--glow))}}
  .rank-glow{animation:rankGlow 3s ease-in-out infinite}
`;

export default function App() {
  const [tab, setTab] = useState("home");
  const [profile, setProfile, lp] = usePersist("tk-profile-v2", null);
  const [streak, setStreak, l1] = usePersist("tk-streak", 0);
  const [weekDone, setWeekDone, l2] = usePersist("tk-week", Array(7).fill(false));
  const [tasks, setTasks, l3] = usePersist("tk-tasks", DEFAULT_TASKS);
  const [journal, setJournal, l4] = usePersist("tk-journal", []);
  const [auditLog, setAuditLog, l5] = usePersist("tk-audit", []);
  const [totalXP, setTotalXP, l6] = usePersist("tk-xp", 0);
  const [coachHistory, setCoachHistory, l7] = usePersist("tk-coach", []);
  const [timerSessions, setTimerSessions, l8] = usePersist("tk-timer-sessions", []);
  const [notifEnabled, setNotifEnabled, l9] = usePersist("tk-notif", false);
  const loaded = lp && l1 && l2 && l3 && l4 && l5 && l6 && l7 && l8 && l9;
  const rank = getRank(totalXP);
  const completedCount = tasks.filter(t => t.done).length;
  const [toasts, setToasts] = useState([]);
  const toast = (xp) => { const id = Date.now(); setToasts(p => [...p, { id, xp }]); setTimeout(() => setToasts(p => p.filter(t => t.id !== id)), 1400); };

  const toggleTask = (id) => {
    setTasks(prev => prev.map(t => {
      if (t.id !== id) return t;
      const d = !t.done;
      if (d) { setTotalXP(p => p + t.xp); toast(t.xp); }
      else { setTotalXP(p => Math.max(0, p - t.xp)); }
      return { ...t, done: d };
    }));
  };

  useEffect(() => {
    if (loaded && tasks.length > 0 && tasks.every(t => t.done) && !weekDone[todayDOW]) {
      setWeekDone(p => { const c = [...p]; c[todayDOW] = true; return c; });
      setStreak(s => s + 1);
    }
  }, [tasks, loaded]);

  useEffect(() => { if (loaded && notifEnabled) requestNotifPermission(); }, [loaded, notifEnabled]);

  const todaySessions = timerSessions.filter(s => s.date === todayKey);
  useEffect(() => {
    if (!loaded || !notifEnabled || hour < 10) return;
    const nudgeKey = `tk-nudge-${todayKey}`;
    if (todaySessions.length === 0 && !sessionStorage.getItem(nudgeKey)) {
      const t = setTimeout(() => {
        sendNotif("Forge — Time to focus", "You haven't tracked any focus time today. Start a session?");
        sessionStorage.setItem(nudgeKey, "1");
      }, 60000);
      return () => clearTimeout(t);
    }
  }, [loaded, notifEnabled, todaySessions.length]);

  if (!loaded) return (
    <div className="min-h-screen flex items-center justify-center" style={{ background: "#060c1a" }}>
      <img src={LOGO} className="h-28 animate-pulse" alt="" />
    </div>
  );

  if (!profile) return (<OnboardingQuiz onComplete={(p) => setProfile(p)} />);

  return (
    <div className="min-h-screen w-full flex justify-center" style={{ background: "#060c1a", fontFamily: "'Inter',system-ui,sans-serif" }}>
      <style>{GLOBAL_CSS}</style>
      <div className="relative w-full max-w-[480px] min-h-screen flex flex-col" style={{ background: "linear-gradient(180deg,#0d1a2e 0%,#060c1a 30%)" }}>
        <div className="flex-1 overflow-y-auto no-scroll pb-28 pt-6 px-5">
          {tab === "home" && <HomeTab {...{ streak, weekDone, tasks, toggleTask, completedCount, totalXP, rank, profile }} />}
          {tab === "timer" && <TimerTab {...{ timerSessions, setTimerSessions, setTotalXP, toast, setAuditLog, notifEnabled, setNotifEnabled, profile }} />}
          {tab === "coach" && <CoachTab {...{ coachHistory, setCoachHistory, tasks, auditLog, journal, streak, totalXP, rank, profile, timerSessions }} />}
          {tab === "rank" && <RankTab {...{ totalXP, rank, streak, weekDone, tasks, timerSessions }} />}
          {tab === "reflect" && <ReflectTab {...{ journal, setJournal, setTotalXP, toast, profile, tasks, auditLog, streak, timerSessions }} />}
        </div>
        {/* XP Toasts */}
        <div className="pointer-events-none absolute top-20 inset-x-0 flex flex-col items-center gap-2 z-50">
          {toasts.map(t => (
            <div key={t.id} className="xp-pop flex items-center gap-2 bg-emerald-500 text-white font-bold text-sm rounded-full px-5 py-2 shadow-lg shadow-emerald-500/30">
              <Check size={14} strokeWidth={3} />+{t.xp} XP
              <span className="font-normal text-emerald-100 text-xs">Task Completed</span>
            </div>
          ))}
        </div>
        {/* Bottom Nav */}
        <div className="absolute bottom-0 inset-x-0 border-t border-white/[.06] px-2" style={{ paddingBottom: "calc(0.75rem + env(safe-area-inset-bottom))", background: "linear-gradient(180deg,rgba(6,12,26,0) 0%,#060c1a 30%)" }}>
          <div className="flex justify-between pt-2">
            {[
              { id: "home", icon: Home, label: "Home" },
              { id: "timer", icon: Clock, label: "Timer" },
              { id: "coach", icon: Sparkles, label: "AI Coach" },
              { id: "rank", icon: Shield, label: "Rank" },
              { id: "reflect", icon: BookOpen, label: "Reflect" },
            ].map(n => (
              <button key={n.id} onClick={() => setTab(n.id)} className="flex flex-col items-center flex-1 gap-0.5 py-1">
                <div className={`w-10 h-10 rounded-2xl flex items-center justify-center transition ${tab === n.id ? "bg-blue-600/20" : ""}`}>
                  <n.icon size={20} className={tab === n.id ? "text-blue-400" : "text-gray-500"} />
                </div>
                <span className={`text-[10px] font-medium ${tab === n.id ? "text-blue-400" : "text-gray-500"}`}>{n.label}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── HOME TAB ──
function HomeTab({ streak, weekDone, tasks, toggleTask, completedCount, totalXP, rank, profile }) {
  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <img src={LOGO} className="h-16" alt="Forge" />
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 bg-orange-500/10 border border-orange-500/20 rounded-full px-3 py-1.5">
            <Flame size={14} className="text-orange-400" />
            <span className="text-orange-300 font-bold text-xs">{streak}</span>
          </div>
          <div className="flex items-center gap-1.5 rounded-full px-3 py-1.5 border" style={{ background: `${rank.color}10`, borderColor: `${rank.color}30` }}>
            <Shield size={14} style={{ color: rank.color }} />
            <span className="font-bold text-xs" style={{ color: rank.color }}>{rank.name}</span>
          </div>
        </div>
      </div>

      {/* Today's Plan Card */}
      <div className="bg-white/[.04] border border-white/[.06] rounded-2xl p-5 mb-5">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-gray-500 mb-0.5">Today's Plan</p>
        <p className="text-white text-lg font-bold">{todayStr}</p>
        <p className="text-gray-400 text-sm mt-1">
          {completedCount === tasks.length
            ? "All tasks done — you showed up today."
            : `${completedCount} of ${tasks.length} completed. Keep going.`}
        </p>
      </div>

      {/* Week Dots */}
      <div className="flex justify-between mb-5 px-2">
        {DAY_NAMES.map((d, i) => {
          const isToday = i === todayDOW;
          const isDone = weekDone[i];
          return (
            <div key={i} className="flex flex-col items-center gap-1.5">
              <span className={`text-[10px] font-semibold ${isToday ? "text-white" : "text-gray-500"}`}>{d}</span>
              <div className={`w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold transition-all ${
                isDone ? "bg-emerald-500 text-white" : isToday ? "bg-orange-500 text-white" : "bg-white/[.06] text-gray-600"
              }`}>
                {isDone ? <Check size={14} strokeWidth={3} /> : ""}
              </div>
            </div>
          );
        })}
      </div>

      {/* XP Progress */}
      <div className="flex items-center gap-3 bg-white/[.04] border border-white/[.06] rounded-xl px-4 py-3 mb-5">
        <div className="w-10 h-10 rounded-full flex items-center justify-center" style={{ background: `${rank.color}15` }}>
          <Shield size={18} style={{ color: rank.color }} />
        </div>
        <div className="flex-1">
          <div className="flex justify-between text-xs mb-1">
            <span className="text-gray-400">{rank.name}</span>
            <span className="font-semibold" style={{ color: rank.color }}>
              {totalXP.toLocaleString()} XP
            </span>
          </div>
          <div className="h-1.5 rounded-full bg-white/10 overflow-hidden">
            <div className="h-full rounded-full transition-all" style={{ width: `${rank.progress * 100}%`, background: rank.color }} />
          </div>
          {rank.next && (
            <p className="text-[10px] text-gray-500 mt-1">{(rank.xpForNext - rank.xpInRank).toLocaleString()} XP to {rank.next.name}</p>
          )}
        </div>
      </div>

      {/* Morning Tasks */}
      <div className="flex justify-between items-center mb-3">
        <div className="flex items-center gap-2">
          <span className="text-white text-xs">☀</span>
          <span className="text-white font-semibold text-sm uppercase tracking-wider">Morning</span>
        </div>
        <span className="text-xs text-gray-500">{completedCount}/{tasks.length}</span>
      </div>

      <div className="space-y-2.5">
        {tasks.map(t => {
          const Ic = ICON_MAP[t.icon] || Sparkles;
          return (
            <button key={t.id} onClick={() => toggleTask(t.id)}
              className="w-full flex items-center gap-3.5 bg-white/[.04] border border-white/[.06] rounded-2xl px-4 py-4 text-left transition active:scale-[.98]">
              <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-all ${
                t.done ? "bg-emerald-500" : "bg-white/[.06] border border-white/10"
              }`}>
                {t.done ? <Check size={18} className="text-white" strokeWidth={3} /> : <Ic size={18} className="text-gray-400" />}
              </div>
              <div className="flex-1 min-w-0">
                <p className={`text-sm font-semibold ${t.done ? "text-gray-500 line-through" : "text-white"}`}>{t.text}</p>
                <p className="text-xs text-gray-500 mt-0.5">{t.desc}</p>
              </div>
              <ChevronRight size={16} className="text-gray-600 shrink-0" />
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ── TIMER TAB ──
function TimerTab({ timerSessions, setTimerSessions, setTotalXP, toast, setAuditLog, notifEnabled, setNotifEnabled, profile }) {
  const [running, setRunning] = useState(false);
  const [paused, setPaused] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [category, setCategory] = useState("deepwork");
  const intervalRef = useRef(null);
  const startTimeRef = useRef(null);
  const pausedElapsedRef = useRef(0);
  const breakNotifSent = useRef(false);

  const todaySessions = timerSessions.filter(s => s.date === todayKey);
  const todayByCategory = CATEGORIES.map(c => {
    const total = todaySessions.filter(s => s.category === c.key).reduce((sum, s) => sum + s.duration, 0);
    return { ...c, total };
  }).filter(c => c.total > 0);
  const todayTotal = todaySessions.reduce((s, ss) => s + ss.duration, 0);

  useEffect(() => {
    if (running && !paused) {
      intervalRef.current = setInterval(() => {
        const now = Date.now();
        const el = Math.floor((now - startTimeRef.current) / 1000) + pausedElapsedRef.current;
        setElapsed(el);
        if (notifEnabled && el >= 3000 && !breakNotifSent.current) {
          sendNotif("Forge — Take a break", "You've been focused for 50 minutes. Time for a 5-minute break?");
          breakNotifSent.current = true;
        }
      }, 1000);
    }
    return () => clearInterval(intervalRef.current);
  }, [running, paused, notifEnabled]);

  const startTimer = () => {
    startTimeRef.current = Date.now();
    pausedElapsedRef.current = 0;
    breakNotifSent.current = false;
    setElapsed(0);
    setRunning(true);
    setPaused(false);
  };
  const pauseTimer = () => { pausedElapsedRef.current = elapsed; setPaused(true); };
  const resumeTimer = () => { startTimeRef.current = Date.now(); setPaused(false); };
  const stopTimer = () => {
    clearInterval(intervalRef.current);
    if (elapsed >= 10) {
      const session = { id: Date.now(), date: todayKey, category, duration: elapsed, endedAt: new Date().toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }) };
      setTimerSessions(p => [session, ...p]);
      const xp = Math.max(5, Math.round(elapsed / 60));
      setTotalXP(p => p + xp);
      toast(xp);
      const catLabel = CATEGORIES.find(c => c.key === category)?.label || category;
      setAuditLog(p => [{ date: todayShort, hours: { [category]: +(elapsed / 3600).toFixed(2) }, id: Date.now(), fromTimer: true, label: catLabel }, ...p]);
      if (notifEnabled) {
        sendNotif("Session saved", `${fmtMins(elapsed)} of ${catLabel} tracked. +${xp} XP`);
      }
    }
    setRunning(false);
    setPaused(false);
    setElapsed(0);
  };

  const toggleNotif = () => {
    if (!notifEnabled) { requestNotifPermission(); setNotifEnabled(true); }
    else { setNotifEnabled(false); }
  };

  const catObj = CATEGORIES.find(c => c.key === category);

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <div>
          <p className="text-blue-400 text-xs font-semibold uppercase tracking-wider mb-1">Focus Timer</p>
          <h2 className="text-white text-2xl font-bold">Track Your Time</h2>
        </div>
        <button onClick={toggleNotif} className={`w-9 h-9 rounded-full flex items-center justify-center transition ${notifEnabled ? "bg-blue-600/20 border border-blue-500/30" : "bg-white/5 border border-white/10"}`}>
          {notifEnabled ? <Bell size={16} className="text-blue-400" /> : <BellOff size={16} className="text-gray-500" />}
        </button>
      </div>

      <div className="bg-white/[.04] border border-white/[.06] rounded-2xl p-6 mb-5 flex flex-col items-center">
        <div className={`w-44 h-44 rounded-full flex items-center justify-center mb-4 border-4 transition-all ${running ? "border-blue-500" : "border-white/10"} ${running && !paused ? "timer-pulse" : ""}`} style={running ? { borderColor: catObj?.color } : {}}>
          <div className="text-center">
            <p className="text-white text-4xl font-black tracking-tight font-mono">{fmtTime(elapsed)}</p>
            {running && <p className="text-xs mt-1" style={{ color: catObj?.color }}>{catObj?.emoji} {catObj?.label}</p>}
          </div>
        </div>

        {!running && (
          <div className="w-full mb-4">
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">What are you working on?</p>
            <div className="grid grid-cols-3 gap-2">
              {CATEGORIES.map(c => (
                <button key={c.key} onClick={() => setCategory(c.key)}
                  className={`rounded-xl px-3 py-2.5 text-xs font-medium text-center transition border-2 ${category === c.key ? "text-white" : "text-gray-400 border-transparent bg-white/[.04]"}`}
                  style={category === c.key ? { borderColor: c.color, background: `${c.color}15` } : {}}>
                  <span className="block text-base mb-0.5">{c.emoji}</span>
                  {c.label}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="flex gap-3 w-full">
          {!running ? (
            <button onClick={startTimer} className="flex-1 py-3.5 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 bg-blue-600 text-white transition active:scale-[.97]">
              <Play size={18} fill="white" /> Start Focus
            </button>
          ) : (
            <>
              {paused ? (
                <button onClick={resumeTimer} className="flex-1 py-3.5 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 bg-blue-600 text-white">
                  <Play size={18} fill="white" /> Resume
                </button>
              ) : (
                <button onClick={pauseTimer} className="flex-1 py-3.5 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 bg-yellow-600/80 text-white">
                  <Pause size={18} /> Pause
                </button>
              )}
              <button onClick={stopTimer} className="py-3.5 px-5 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 bg-red-600/80 text-white">
                <Square size={16} fill="white" /> Stop
              </button>
            </>
          )}
        </div>
      </div>

      {todayByCategory.length > 0 && (
        <div className="bg-white/[.04] border border-white/[.06] rounded-xl p-4 mb-5">
          <div className="flex justify-between items-center mb-3">
            <p className="text-sm text-white font-semibold">Today's Breakdown</p>
            <span className="text-xs text-cyan-300 font-bold">{fmtMins(todayTotal)} total</span>
          </div>
          <div className="space-y-2.5">
            {todayByCategory.map(c => {
              const pct = todayTotal > 0 ? Math.round((c.total / todayTotal) * 100) : 0;
              return (
                <div key={c.key}>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-gray-300">{c.emoji} {c.label}</span>
                    <span className="text-gray-400">{fmtMins(c.total)} ({pct}%)</span>
                  </div>
                  <div className="h-1.5 rounded-full bg-white/10 overflow-hidden">
                    <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, background: c.color }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {todaySessions.length > 0 && (
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">Today's Sessions</p>
          <div className="space-y-2">
            {todaySessions.slice(0, 10).map(s => {
              const c = CATEGORIES.find(cc => cc.key === s.category);
              return (
                <div key={s.id} className="bg-white/[.03] border border-white/[.04] rounded-xl px-4 py-3 flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full flex items-center justify-center text-sm" style={{ background: `${c?.color || "#666"}20` }}>{c?.emoji || "⏱"}</div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-white font-medium">{c?.label || s.category}</p>
                    <p className="text-xs text-gray-500">Ended {s.endedAt}</p>
                  </div>
                  <span className="text-sm font-bold text-gray-300">{fmtMins(s.duration)}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {todaySessions.length === 0 && !running && (
        <div className="text-center py-8">
          <Clock size={32} className="text-gray-600 mx-auto mb-3" />
          <p className="text-gray-500 text-sm">No sessions tracked today yet.</p>
          <p className="text-gray-600 text-xs mt-1">Pick a category and start focusing.</p>
        </div>
      )}
    </div>
  );
}

// ── AI COACH TAB ──
function CoachTab({ coachHistory, setCoachHistory, tasks, auditLog, journal, streak, totalXP, rank, profile, timerSessions }) {
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const endRef = useRef(null);
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: "smooth" }); }, [coachHistory]);

  const todaySessions = (timerSessions || []).filter(s => s.date === todayKey);
  const todayFocusMin = Math.round(todaySessions.reduce((s, ss) => s + ss.duration, 0) / 60);
  const timerBreakdown = CATEGORIES.map(c => {
    const mins = Math.round(todaySessions.filter(s => s.category === c.key).reduce((sum, s) => sum + s.duration, 0) / 60);
    return mins > 0 ? `${c.label}: ${mins}m` : null;
  }).filter(Boolean).join(", ");

  const profileCtx = profile ? `\nUser profile:\n- Role: ${profile.role}\n- #1 Goal: ${profile.topGoal}\n- Second life: ${profile.secondLife}\n- Peak focus hours: ${profile.peakHours}\n- Biggest time leak: ${profile.biggestLeak}\n- Available hours/day: ${profile.dailyHours}\n- Pattern: ${profile.challenge}\n- Accountability style: ${profile.commitLevel}` : "";
  const coachTone = profile?.commitLevel === "drill" ? "Be extremely direct. Zero tolerance for excuses." : profile?.commitLevel === "brutal" ? "Be blunt and honest. No sugarcoating." : "Be firm but encouraging.";
  const sys = `You are Forge Coach — a personalized coach inside a productivity app called Forge. ${coachTone} Keep answers to 3-5 sentences. Reference the user's specific goals.${profileCtx}\n\nLive data:\n- Streak: ${streak} days, Rank: ${rank.name} (${totalXP} XP)\n- Tasks: ${JSON.stringify(tasks.map(t => ({ task: t.text, done: t.done })))}\n- Focus today: ${todayFocusMin}min${timerBreakdown ? ` (${timerBreakdown})` : ""}\n- Sessions today: ${todaySessions.length}\n- Recent audits: ${JSON.stringify(auditLog.slice(0, 3).map(a => ({ date: a.date, hours: a.hours })))}\n- Recent reflections: ${JSON.stringify(journal.slice(0, 3).map(j => ({ date: j.date, text: j.text })))}\n\nRules: Reference their goals and time leaks by name. If 0 focus minutes today, call it out. Celebrate streak milestones. Never say "great question".`;

  const send = async () => {
    if (!input.trim() || loading) return;
    const msg = { role: "user", content: input.trim() };
    const updated = [...coachHistory, msg];
    setCoachHistory(updated);
    setInput("");
    setLoading(true);
    try {
      const apiKey = import.meta.env.VITE_GEMINI_API_KEY || localStorage.getItem("tk-gemini-key") || "";
      if (!apiKey) { setCoachHistory(p => [...p, { role: "assistant", content: "No API key found. Tap the key icon to add your Gemini API key." }]); setLoading(false); return; }
      const geminiMessages = updated.slice(-20).map(m => ({ role: m.role === "assistant" ? "model" : "user", parts: [{ text: m.content }] }));
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${apiKey}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ system_instruction: { parts: [{ text: sys }] }, contents: geminiMessages, generationConfig: { maxOutputTokens: 1000, temperature: 0.8 } }) });
      const data = await res.json();
      const reply = data?.candidates?.[0]?.content?.parts?.[0]?.text || "I'm here. Ask me anything.";
      setCoachHistory(p => [...p, { role: "assistant", content: reply }]);
    } catch { setCoachHistory(p => [...p, { role: "assistant", content: "Connection issue — try again." }]); }
    setLoading(false);
  };

  const [showKey, setShowKey] = useState(false);
  const [keyVal, setKeyVal] = useState(localStorage.getItem("tk-gemini-key") || "");
  const saveKey = () => { localStorage.setItem("tk-gemini-key", keyVal); setShowKey(false); };

  const suggestions = ["What's my plan today?", "Where do I rank?", "How am I really doing?", "Create a sustainable routine to get in shape, develop a stronger mindset"];

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="text-lg font-bold text-white flex items-center gap-2">
            <Sparkles size={20} className="text-blue-400" />
            Coach
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowKey(s => !s)} className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center" title="API Key">
            <span className="text-xs">🔑</span>
          </button>
          <button onClick={() => setCoachHistory([])} className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center" title="Clear chat">
            <Pencil size={14} className="text-gray-400" />
          </button>
        </div>
      </div>

      {showKey && (
        <div className="bg-white/[.04] border border-white/[.06] rounded-xl p-4 mb-4">
          <p className="text-xs text-gray-400 mb-2">Gemini API Key</p>
          <div className="flex gap-2">
            <input type="password" value={keyVal} onChange={e => setKeyVal(e.target.value)} placeholder="AIza..." className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm text-white outline-none" />
            <button onClick={saveKey} className="bg-blue-600 text-white text-xs font-semibold px-3 rounded-lg">Save</button>
          </div>
        </div>
      )}

      {/* Welcome message when empty */}
      {coachHistory.length === 0 && (
        <div className="flex flex-col items-center text-center mb-4 pt-8">
          <div className="w-14 h-14 rounded-full bg-blue-600/20 flex items-center justify-center mb-4">
            <Sparkles size={24} className="text-blue-400" />
          </div>
          <p className="text-white text-base leading-relaxed max-w-[280px]">
            Hi there! I'm your personal coach. How can I help you today with your fitness, social skills, and appearance?
          </p>
          <p className="text-gray-500 text-xs mt-3">Tap a suggestion below or type your own.</p>
        </div>
      )}

      {/* Chat messages */}
      <div className="space-y-3 mb-4 flex-1">
        {coachHistory.map((m, i) => (
          <div key={i} className={`flex ${m.role === "user" ? "justify-end" : "justify-start"}`}>
            <div className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${m.role === "user" ? "bg-blue-600 text-white rounded-br-md" : "bg-white/[.06] text-gray-200 rounded-bl-md"}`}>
              {m.content}
            </div>
          </div>
        ))}
        {loading && (
          <div className="flex justify-start">
            <div className="bg-white/[.06] rounded-2xl rounded-bl-md px-4 py-3">
              <Loader2 size={16} className="text-blue-400 animate-spin" />
            </div>
          </div>
        )}
        <div ref={endRef} />
      </div>

      {/* Suggestion chips */}
      {coachHistory.length === 0 && (
        <div className="flex flex-wrap gap-2 mb-3">
          {suggestions.map((q, i) => (
            <button key={i} onClick={() => { setInput(q); }}
              className="bg-white/[.06] border border-white/[.08] rounded-full px-4 py-2 text-xs text-gray-300 hover:border-blue-500/40 transition whitespace-nowrap">
              {q}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      <div className="flex gap-2 sticky bottom-0">
        <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => e.key === "Enter" && send()}
          placeholder="Ask your coach..." className="flex-1 bg-white/[.06] border border-white/[.08] rounded-full px-4 py-3 text-sm text-white placeholder-gray-500 outline-none focus:border-blue-500/50" />
        <button onClick={send} disabled={loading || !input.trim()} className="bg-blue-600 rounded-full w-11 h-11 flex items-center justify-center disabled:opacity-30 shrink-0">
          <Send size={16} className="text-white" />
        </button>
      </div>
    </div>
  );
}

// ── RANK LADDER TAB ──
function RankTab({ totalXP, rank, streak, weekDone, tasks, timerSessions }) {
  const done = tasks.filter(t => t.done).length;
  const todaySessions = (timerSessions || []).filter(s => s.date === todayKey);
  const todayFocus = todaySessions.reduce((s, ss) => s + ss.duration, 0);

  return (
    <div>
      {/* Header */}
      <div className="flex items-center gap-2 mb-6">
        <ArrowLeft size={20} className="text-gray-400" />
        <h2 className="text-white text-lg font-bold uppercase tracking-wider">Rank Ladder</h2>
      </div>

      {/* Current Rank Display */}
      <div className="flex flex-col items-center text-center mb-6">
        <div className="w-32 h-32 rounded-full flex items-center justify-center mb-4 rank-glow" style={{ "--glow": rank.glow, background: `${rank.color}10`, border: `3px solid ${rank.color}40` }}>
          <Shield size={56} style={{ color: rank.color }} />
        </div>
        <p className="text-white text-2xl font-black">{rank.name}</p>

        {/* Stats */}
        <div className="flex gap-8 mt-4">
          <div className="text-center">
            <p className="text-[10px] text-gray-500 uppercase tracking-wider">Current XP</p>
            <p className="text-white text-2xl font-black">{totalXP.toLocaleString()}</p>
          </div>
          <div className="text-center">
            <p className="text-[10px] text-gray-500 uppercase tracking-wider">Global Rank</p>
            <p className="text-white text-2xl font-black">
              Top {Math.max(1, Math.round(100 - (totalXP / 500)))}%
            </p>
          </div>
        </div>

        {/* Progress to next rank */}
        {rank.next && (
          <div className="w-full mt-4 px-4">
            <div className="h-2 rounded-full bg-white/10 overflow-hidden">
              <div className="h-full rounded-full transition-all" style={{ width: `${rank.progress * 100}%`, background: rank.color }} />
            </div>
            <p className="text-xs text-gray-500 mt-1">{(rank.xpForNext - rank.xpInRank).toLocaleString()} XP to {rank.next.name}</p>
          </div>
        )}
      </div>

      {/* Quick Actions */}
      <div className="grid grid-cols-2 gap-3 mb-6">
        <div className="bg-white/[.04] border border-white/[.06] rounded-2xl p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-blue-600/20 flex items-center justify-center">
            <Globe size={18} className="text-blue-400" />
          </div>
          <div>
            <p className="text-white text-sm font-semibold">Global Leaderboard</p>
            <p className="text-gray-500 text-[10px]">See how you rank worldwide</p>
          </div>
        </div>
        <div className="bg-white/[.04] border border-white/[.06] rounded-2xl p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-purple-600/20 flex items-center justify-center">
            <Users size={18} className="text-purple-400" />
          </div>
          <div>
            <p className="text-white text-sm font-semibold">Friends</p>
            <p className="text-gray-500 text-[10px]">Connect and compete</p>
          </div>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 gap-3 mb-6">
        {[
          { label: "Total XP", value: totalXP.toLocaleString(), icon: Zap, color: "text-cyan-300", bg: "bg-cyan-600/10" },
          { label: "Streak", value: `${streak}d`, icon: Flame, color: "text-orange-400", bg: "bg-orange-600/10" },
          { label: "Tasks Today", value: `${done}/${tasks.length}`, icon: Check, color: "text-emerald-400", bg: "bg-emerald-600/10" },
          { label: "Focus Today", value: fmtMins(todayFocus), icon: Clock, color: "text-purple-400", bg: "bg-purple-600/10" },
        ].map((s, i) => (
          <div key={i} className="bg-white/[.04] border border-white/[.06] rounded-xl p-4">
            <div className={`w-8 h-8 rounded-lg ${s.bg} flex items-center justify-center mb-2`}>
              <s.icon size={16} className={s.color} />
            </div>
            <p className="text-white text-xl font-bold">{s.value}</p>
            <p className="text-gray-500 text-xs">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Rank Ladder */}
      <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">All Ranks</p>
      <div className="space-y-2">
        {[...RANKS].reverse().map((r, i) => {
          const isCurrentRank = r.name === rank.name;
          const isLocked = totalXP < r.minXP;
          return (
            <div key={r.name} className={`flex items-center gap-3 rounded-xl px-4 py-3 border transition ${
              isCurrentRank ? "bg-white/[.06] border-white/[.1]" : "bg-white/[.02] border-white/[.04]"
            }`}>
              <div className="w-8 h-8 rounded-full flex items-center justify-center" style={{ background: isLocked ? "#ffffff08" : `${r.color}20` }}>
                {isLocked ? <Lock size={14} className="text-gray-600" /> : <Shield size={14} style={{ color: r.color }} />}
              </div>
              <div className="flex-1">
                <p className={`text-sm font-semibold ${isLocked ? "text-gray-600" : "text-white"}`}>{r.name}</p>
                <p className="text-[10px] text-gray-500">{r.minXP.toLocaleString()} XP total</p>
              </div>
              {isCurrentRank && (
                <span className="text-[10px] font-bold text-blue-400 uppercase tracking-wider">Current</span>
              )}
              {isLocked && (
                <span className="text-[10px] text-gray-600">Locked</span>
              )}
            </div>
          );
        })}
      </div>

      {/* Week */}
      <div className="bg-white/[.04] border border-white/[.06] rounded-xl p-4 mt-6">
        <p className="text-sm text-white font-semibold mb-3">This Week</p>
        <div className="flex justify-between">
          {DAY_NAMES.map((d, i) => (
            <div key={i} className="flex flex-col items-center gap-1.5">
              <span className="text-[10px] text-gray-500">{d}</span>
              <div className={`w-7 h-7 rounded-full flex items-center justify-center ${weekDone[i] ? "bg-emerald-500" : "bg-white/5"}`}>
                {weekDone[i] && <Check size={12} className="text-white" strokeWidth={3} />}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── REFLECT TAB ──
function ReflectTab({ journal, setJournal, setTotalXP, toast, profile, tasks, auditLog, streak, timerSessions }) {
  const [text, setText] = useState("");
  const [saved, setSaved] = useState(false);
  const [aiInsight, setAiInsight] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);

  const todaySessions = (timerSessions || []).filter(s => s.date === todayKey);
  const todayFocusMin = Math.round(todaySessions.reduce((s, ss) => s + ss.duration, 0) / 60);

  const save = () => {
    if (!text.trim()) return;
    const entry = { id: Date.now(), date: todayShort, text: text.trim() };
    setJournal(p => [entry, ...p]);
    setTotalXP(p => p + 30);
    toast(30);
    setText("");
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
    getAiInsight(entry.text);
  };

  const getAiInsight = async (reflectionText) => {
    const apiKey = import.meta.env.VITE_GEMINI_API_KEY || localStorage.getItem("tk-gemini-key") || "";
    if (!apiKey) return;
    setAiLoading(true);
    try {
      const profileCtx = profile ? `User: ${profile.role}. Goal: ${profile.topGoal}. Time leak: ${profile.biggestLeak}. Pattern: ${profile.challenge}.` : "";
      const sys = `You are a reflection coach. Give a 2-3 sentence insight: spot patterns, call out blind spots, give ONE action for tomorrow. Be direct. ${profileCtx} Tasks: ${tasks.filter(t => t.done).length}/${tasks.length}. Streak: ${streak}d. Focus: ${todayFocusMin}min.`;
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${apiKey}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ system_instruction: { parts: [{ text: sys }] }, contents: [{ role: "user", parts: [{ text: reflectionText }] }], generationConfig: { maxOutputTokens: 300, temperature: 0.7 } }) });
      const data = await res.json();
      setAiInsight(data?.candidates?.[0]?.content?.parts?.[0]?.text || null);
    } catch {}
    setAiLoading(false);
  };

  const getWeeklySummary = async () => {
    const apiKey = import.meta.env.VITE_GEMINI_API_KEY || localStorage.getItem("tk-gemini-key") || "";
    if (!apiKey || journal.length < 2) return;
    setAiLoading(true);
    try {
      const recentEntries = journal.slice(0, 7).map(j => `${j.date}: ${j.text}`).join("\n");
      const sys = `Analyze these reflections: 3-4 sentence weekly pattern summary. Highlight recurring themes, progress, #1 thing to change. Be specific.`;
      const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${apiKey}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ system_instruction: { parts: [{ text: sys }] }, contents: [{ role: "user", parts: [{ text: recentEntries }] }], generationConfig: { maxOutputTokens: 400, temperature: 0.7 } }) });
      const data = await res.json();
      setAiInsight(data?.candidates?.[0]?.content?.parts?.[0]?.text || null);
    } catch {}
    setAiLoading(false);
  };

  const prompts = [
    "What was your single highest-leverage action today?",
    "Where did you lose time you didn't plan to spend?",
    "What pattern did you notice about your focus today?",
    "What's one thing you'll do differently tomorrow?",
  ];

  return (
    <div>
      <p className="text-blue-400 text-xs font-semibold uppercase tracking-wider mb-1">End-of-Day Reflection</p>
      <h2 className="text-white text-2xl font-bold mb-2">Review & Recalibrate</h2>
      <p className="text-gray-400 text-sm mb-6 leading-relaxed">Radical clarity comes from honest reflection, not perfection.</p>

      <p className="text-xs text-gray-500 uppercase tracking-wider mb-2">Prompts</p>
      <div className="space-y-2 mb-6">
        {prompts.map((p, i) => (
          <button key={i} onClick={() => setText(p + "\n\n")}
            className="w-full text-left bg-white/[.04] border border-white/[.06] rounded-xl px-4 py-3 text-sm text-gray-300 hover:border-blue-500/30 transition">
            {p}
          </button>
        ))}
      </div>

      <div className="bg-white/[.04] border border-white/[.06] rounded-xl p-4 mb-4">
        <textarea value={text} onChange={e => setText(e.target.value)} rows={4} placeholder="Write your reflection..."
          className="w-full bg-transparent text-white text-sm placeholder-gray-500 outline-none resize-none leading-relaxed" />
        <div className="flex justify-between items-center mt-2">
          <span className="text-xs text-gray-600">+30 XP</span>
          <button onClick={save} disabled={!text.trim()}
            className={`text-sm font-semibold px-4 py-2 rounded-lg transition ${text.trim() ? "bg-blue-600 text-white" : "bg-white/5 text-gray-600"}`}>
            {saved ? "Saved" : "Save reflection"}
          </button>
        </div>
      </div>

      {(aiInsight || aiLoading) && (
        <div className="bg-blue-600/10 border border-blue-500/20 rounded-xl p-4 mb-4">
          <div className="flex items-center gap-2 mb-2">
            <Sparkles size={14} className="text-blue-400" />
            <span className="text-xs text-blue-400 font-semibold uppercase tracking-wider">AI Insight</span>
          </div>
          {aiLoading
            ? <div className="flex items-center gap-2"><Loader2 size={14} className="text-blue-400 animate-spin" /><span className="text-xs text-gray-400">Analyzing...</span></div>
            : <p className="text-sm text-gray-200 leading-relaxed">{aiInsight}</p>}
        </div>
      )}

      {journal.length >= 2 && (
        <button onClick={getWeeklySummary} disabled={aiLoading}
          className="w-full mb-4 py-2.5 rounded-xl text-sm font-semibold bg-white/[.04] border border-white/[.06] text-gray-300 hover:border-blue-500/30 flex items-center justify-center gap-2 transition">
          <Brain size={14} className="text-blue-400" />Get AI Weekly Pattern Summary
        </button>
      )}

      {journal.length > 0 && (
        <div>
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">Past reflections</p>
          {journal.slice(0, 5).map(j => (
            <div key={j.id} className="bg-white/[.03] border border-white/[.04] rounded-xl px-4 py-3 mb-2">
              <p className="text-xs text-blue-400 font-semibold mb-1">{j.date}</p>
              <p className="text-sm text-gray-300 leading-relaxed">{j.text}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── ONBOARDING ──
function OnboardingQuiz({ onComplete }) {
  const [step, setStep] = useState(0);
  const [a, setA] = useState({ role: "", topGoal: "", secondLife: "", peakHours: "", biggestLeak: "", dailyHours: "", challenge: null, commitLevel: null });
  const upd = (k, v) => setA(p => ({ ...p, [k]: v }));

  const steps = [
    { key: "role", type: "text", title: "What do you do?", subtitle: "Your job title and what you work on day-to-day.", placeholder: "e.g. Supply Chain Manager at a medical device company" },
    { key: "topGoal", type: "text", title: "What is the #1 goal you are working toward right now?", subtitle: "The specific thing that if you made real progress on it in 90 days, you'd feel it was worth it.", placeholder: "e.g. Complete my DBA coursework while excelling at my new role" },
    { key: "secondLife", type: "text", title: "What's your 'second life' — the things outside of work you want to protect time for?", subtitle: "Fitness, side projects, family, studies, hobbies.", placeholder: "e.g. DBA studies, gym 4x/week, reading before bed" },
    { key: "peakHours", type: "text", title: "When are your peak focus hours?", subtitle: "The window where your brain is sharpest.", placeholder: "e.g. 6-9am before work, or 9-11pm after everything settles" },
    { key: "biggestLeak", type: "text", title: "Where does your time actually go that you wish it didn't?", subtitle: "Be brutally honest. This is what the coach will call you out on.", placeholder: "e.g. Doomscrolling TikTok after dinner, getting pulled into emails" },
    { key: "dailyHours", type: "text", title: "Realistically, how many hours per day can you dedicate to your goals outside of work?", subtitle: "Not the ideal — the honest number.", placeholder: "e.g. 2-3 hours on weekdays, 4-5 on weekends" },
    { key: "challenge", type: "select", title: "What pattern keeps holding you back?", subtitle: "Pick the one that hits hardest.", options: [
      { value: "start_strong", label: "I start strong then fade", desc: "Motivation drops after the first week" },
      { value: "overcommit", label: "I overcommit and then crash", desc: "Too many goals, not enough follow-through" },
      { value: "distracted", label: "I lose hours to distractions without realizing", desc: "Phone, scrolling, context-switching" },
      { value: "no_system", label: "I don't have a system — I just wing it", desc: "No structure, no accountability" },
      { value: "perfectionist", label: "I overthink and delay starting", desc: "Analysis paralysis, waiting for the perfect plan" },
    ] },
    { key: "commitLevel", type: "select", title: "What level of accountability do you want from your coach?", subtitle: "This sets the tone. You can change it later.", options: [
      { value: "supportive", label: "Firm but supportive", desc: "Push me, but keep it encouraging" },
      { value: "brutal", label: "Brutally honest", desc: "Don't sugarcoat anything — call me out" },
      { value: "drill", label: "Drill sergeant mode", desc: "Zero excuses. Maximum pressure. I need it." },
    ] },
  ];

  const cur = steps[step];
  const val = a[cur.key];
  const ok = cur.type === "text" ? (val || "").trim().length > 5 : val !== null;
  const total = steps.length;

  return (
    <div className="min-h-screen w-full flex justify-center" style={{ background: "#060c1a", fontFamily: "'Inter',system-ui,sans-serif" }}>
      <style>{GLOBAL_CSS}</style>
      <div className="w-full max-w-[480px] min-h-screen flex flex-col px-6 pt-10 pb-8" style={{ background: "linear-gradient(180deg,#0d1a2e 0%,#060c1a 30%)" }}>
        <img src={LOGO} className="h-20 w-auto self-center mb-6" alt="Forge" />
        <div className="flex gap-1 mb-6">
          {steps.map((_, i) => (<div key={i} className={`flex-1 h-1 rounded-full transition-all ${i <= step ? "bg-blue-500" : "bg-white/10"}`} />))}
        </div>
        <p className="text-blue-400 text-xs font-semibold mb-1">{step + 1} of {total}</p>
        <h2 className="text-white text-lg font-bold mb-2 leading-snug">{cur.title}</h2>
        <p className="text-gray-400 text-sm mb-6 leading-relaxed">{cur.subtitle}</p>

        <div className="flex-1">
          {cur.type === "text" && (
            <textarea value={val || ""} onChange={e => upd(cur.key, e.target.value)} placeholder={cur.placeholder} rows={3}
              className="w-full bg-white/[.04] border border-white/[.08] rounded-xl px-4 py-3.5 text-white text-sm placeholder-gray-500 outline-none focus:border-blue-500 resize-none leading-relaxed" />
          )}
          {cur.type === "select" && (
            <div className="space-y-2.5">
              {cur.options.map(opt => (
                <button key={opt.value} onClick={() => upd(cur.key, opt.value)}
                  className={`w-full text-left rounded-xl px-5 py-4 border-2 transition ${val === opt.value ? "border-blue-500 bg-blue-600/10" : "border-white/[.06] bg-white/[.03]"}`}>
                  <p className="text-white text-sm font-semibold">{opt.label}</p>
                  <p className="text-gray-400 text-xs mt-0.5">{opt.desc}</p>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex gap-3 mt-6">
          {step > 0 && (
            <button onClick={() => setStep(s => s - 1)} className="px-5 py-3.5 rounded-xl text-sm font-semibold text-gray-400 bg-white/5">Back</button>
          )}
          <button onClick={step < total - 1 ? () => setStep(s => s + 1) : () => onComplete(a)} disabled={!ok}
            className={`flex-1 py-3.5 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 transition ${ok ? "bg-blue-600 text-white" : "bg-white/5 text-gray-600"}`}>
            {step < total - 1 ? "Continue" : "Start My System"}
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}
