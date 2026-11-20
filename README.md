<p align="center">
  <img src="forge-logo.png" alt="Forge" width="140" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=white" />
  <img src="https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white" />
  <img src="https://img.shields.io/badge/Tailwind-3-06B6D4?logo=tailwindcss&logoColor=white" />
  <img src="https://img.shields.io/badge/Gemini-3.8_Flash-4285F4?logo=google&logoColor=white" />
  <img src="https://img.shields.io/badge/PWA-Ready-FF6F00?logo=pwa&logoColor=white" />
</p>

 Forge

> A personal AI-powered productivity system built to enforce discipline, track habits, and eliminate time leaks.

---

## ✦ Features

<img width="780" height="1688" alt="Forge · Today" src="https://github.com/user-attachments/assets/419d6004-ca83-4a1c-a589-6fd30a07ba03" />
<img width="780" height="1688" alt="Forge · Focus" src="https://github.com/user-attachments/assets/ae789143-ff51-4c61-89d1-33677a559328" />
<img width="780" height="1688" alt="Forge · Rank ladder" src="https://github.com/user-attachments/assets/5307ce83-7a47-48f6-9765-a9737c254f18" />
<img width="780" height="1688" alt="Forge · Coach" src="https://github.com/user-attachments/assets/a23a9791-a01a-425b-8f73-67130f07bf84" />
<img width="780" height="1688" alt="Forge · Home screen widgets" src="https://github.com/user-attachments/assets/6a3e370a-0ef3-45bd-964e-abe508b46356" />






| Tab | What it does |
|-----|-------------|
| **Today** | Daily action checklist with XP rewards + streak tracking |
| **Audit** | Log hours by category — expose where your time actually goes |
| **Progress** | Alignment score, XP, level, streak, weekly overview |
| **Coach** | Brutally honest AI coaching powered by Gemini 3.8 Flash |
| **Reflect** | End-of-day journaling with AI insights + weekly pattern summaries |

🧠 Starts with an **8-step onboarding quiz** that personalizes the entire experience — goals, time leaks, focus hours, accountability style.

---

## ⚡ Quick Start

```bash
git clone https://github.com/taranidhran1206-a11y/Forge-.git
cd Forge-
npm install
npm run dev
```

To build for production:

```bash
npm run build
```

---

## 🔑 API Key

The AI Coach and Reflect tabs need a Gemini API key:

- **Option 1** — In-app: tap the 🔑 icon in the Coach tab → paste key → Save
- **Option 2** — Create a `.env` file in the project root:
  ```
  VITE_GEMINI_API_KEY=your-key-here
  ```

Get your free key at [Google AI Studio](https://aistudio.google.com/apikey).

---

## 🚀 Deploy

Connected to **Netlify** for auto-deploy on push.

| Setting | Value |
|---------|-------|
| Build command | `npm run build` |
| Publish directory | `dist` |
| Environment variable | `VITE_GEMINI_API_KEY` |

**Live:** [idyllic-buttercream-e7f30a.netlify.app](https://idyllic-buttercream-e7f30a.netlify.app)

---

## 📱 Install as App

| Platform | How |
|----------|-----|
| **Android** | Download APK from [Releases](https://github.com/taranidhran1206-a11y/Forge-/releases) |
| **iOS** | Safari → Share → Add to Home Screen |
| **Desktop** | Chrome → ⋮ → Install Forge |

---

## 🛠 Tech Stack

```
React 18 + Vite 5 + Tailwind CSS 3
Gemini 3.8 Flash API (AI Coach + Reflect)
localStorage for persistence
```

---

##  Project Structure

```
Forge-/
├── index.html
├── package.json
├── vite.config.js
├── tailwind.config.js
├── postcss.config.js
└── src/
    ├── App.jsx        ← main app (all tabs)
    ├── main.jsx       ← entry point
    └── index.css      ← Tailwind imports
```

---

Built by **TK**.
