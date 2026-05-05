# :soccer: Football Value Betting Engine

![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Scikit-learn](https://img.shields.io/badge/Scikit--learn-F7931E?style=for-the-badge&logo=scikitlearn&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-010101?style=for-the-badge&logo=socketdotio&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

> Sports analytics platform that identifies mispriced football bets using Monte Carlo simulations, Poisson models, and real-time odds scraping. Three independent technology stacks working together to detect value opportunities with EV > 5%.

---

## :dart: Features

- **10,000 Monte Carlo Simulations** per match for robust probability estimation
- **Poisson Distribution Model** for expected goals prediction
- **Live Odds Scraping** from Kambi/BetPlay bookmakers every 60 seconds
- **Expected Value (EV) Calculator** that triggers alerts when EV exceeds 5%
- **Confidence Ranking System**: ELITE / ALTA / MEDIA / BAJA classification
- **Advanced Analytics Modules**: referee tendencies, corner predictions, fatigue/altitude impact, Pearson correlations
- **Real-Time Dashboard** with dark-themed bet cards and live filtering
- **726 Matches Database** with micro-event data for model training
- **WebSocket Push Notifications** via STOMP/SockJS

---

## :building_construction: Architecture

```
+--------------------------------------------------+
|                 PYTHON ENGINE                     |
|  Poisson Model + Monte Carlo + Value Calculator  |
|  Referee Analysis + Corners + Fatigue/Travel      |
|  SQLite (726 matches) + Flask Dashboard (:5000)  |
+---------------------------+----------------------+
                            |
                     Model Outputs
                            |
+---------------------------v----------------------+
|              JAVA BACKEND (Spring Boot)          |
|  OddsFetcherService — scrapes odds every 60s    |
|  ValueBetAgent — EV evaluation + alert gen       |
|  AlertController — REST API (:8080)              |
|  WebSocketConfig — STOMP/SockJS on /ws           |
|  H2 in-memory DB                                 |
+---------------------------+----------------------+
                            |
                   REST + WebSocket
                            |
+---------------------------v----------------------+
|            REACT FRONTEND (Vite + TS)            |
|  AlertsDashboard — main view + filters           |
|  LiveBetCard — ranked bet cards (dark theme)     |
|  useValueBetAlerts — polling hook (10s)          |
|  Tailwind CSS 3 (:5173)                          |
+--------------------------------------------------+
```

---

## :hammer_and_wrench: Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **ML Engine** | Python, Pandas, NumPy, SciPy | Statistical modeling & data processing |
| **ML Models** | Scikit-learn, Poisson, Monte Carlo | Probability estimation & EV calculation |
| **Analytics API** | Flask | Model testing dashboard |
| **Backend** | Java 21, Spring Boot 3.4.4, Maven | REST API + WebSocket server |
| **Real-Time** | STOMP, SockJS, WebSocket | Live alert push notifications |
| **Frontend** | React 18, TypeScript, Vite | Interactive dashboard UI |
| **Styling** | Tailwind CSS 3 | Dark-themed responsive design |
| **Databases** | SQLite (726 matches), H2 (runtime) | Historical data + live state |

---

## :bar_chart: Analytics Modules

| Module | File | Description |
|--------|------|-------------|
| **Poisson Model** | `src/models/poisson_model.py` | Predicts expected goals using historical averages |
| **Monte Carlo** | `src/models/monte_carlo.py` | Runs 10K simulations per match for win/draw/loss probabilities |
| **Value Calculator** | `src/models/value_calculator.py` | Compares model probabilities vs bookmaker odds to find +EV |
| **Market Scanner** | `src/models/market_scanner.py` | Scans live odds across markets |
| **Referee Analysis** | `src/analytics/referee_analysis.py` | Tracks referee card/foul tendencies per competition |
| **Corners Model** | `src/analytics/corners_model.py` | Predicts corner kick totals |
| **Correlations** | `src/analytics/correlations.py` | Pearson correlation analysis across match variables |
| **Fatigue & Travel** | `src/engine/fatigue_travel.py` | Adjusts predictions for team fatigue and altitude factors |

---

## :file_folder: Project Structure

```
football-value-betting/
├── src/                          # Python ML Engine
│   ├── models/
│   │   ├── poisson_model.py
│   │   ├── monte_carlo.py
│   │   ├── value_calculator.py
│   │   └── market_scanner.py
│   ├── analytics/
│   │   ├── referee_analysis.py
│   │   ├── corners_model.py
│   │   └── correlations.py
│   ├── engine/
│   │   └── fatigue_travel.py
│   ├── database.py
│   └── database_v2.py
├── web/
│   └── app.py                    # Flask dashboard
├── config/
│   └── leagues.json              # League configurations
├── backend-java/                 # Spring Boot Backend
│   ├── pom.xml
│   └── src/main/java/.../
│       ├── OddsFetcherService.java
│       ├── ValueBetAgent.java
│       ├── AlertController.java
│       └── WebSocketConfig.java
├── frontend-react/               # React + TypeScript Frontend
│   ├── src/
│   │   ├── components/
│   │   │   ├── LiveBetCard.tsx
│   │   │   └── AlertsDashboard.tsx
│   │   ├── hooks/
│   │   │   └── useValueBetAlerts.ts
│   │   └── main.tsx
│   ├── vite.config.ts
│   └── tailwind.config.js
└── requirements.txt
```

---

## :rocket: Getting Started

### Python Engine

```bash
# Prerequisites: Python 3.10+
pip install -r requirements.txt

# Run Flask dashboard
python web/app.py
# Open http://localhost:5000
```

### Java Backend

```bash
# Prerequisites: Java 21+, Maven 3.9+
cd backend-java
mvn spring-boot:run
# API available at http://localhost:8080
```

### React Frontend

```bash
# Prerequisites: Node.js 18+
cd frontend-react
npm install
npm run dev
# Open http://localhost:5173
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/alerts` | GET | Get all active value bet alerts |
| `/api/matches/live` | GET | Get live matches being monitored |
| `/api/demo/inject` | POST | Inject demo data for testing |
| `/api/status` | GET | System health check |
| `/ws` | WS | STOMP/SockJS WebSocket endpoint |

---

## :framed_picture: Screenshots

| View | Description |
|------|-------------|
| **Alerts Dashboard** | Dark-themed dashboard showing ranked value bet opportunities |
| **Live Bet Cards** | Individual cards with EV%, confidence level, and odds comparison |
| **Flask Analytics** | Python model testing and historical data exploration |

> *Screenshots coming soon — run the project to see the live UI*

---

## :brain: How It Works

1. **Data Collection**: The Python engine maintains a SQLite database of 726+ matches with micro-event data
2. **Model Training**: Poisson and Monte Carlo models generate probability distributions for match outcomes
3. **Odds Scraping**: The Java backend scrapes live bookmaker odds every 60 seconds
4. **Value Detection**: The ValueBetAgent compares model probabilities vs. bookmaker implied probabilities
5. **Alert Generation**: When `EV > 5%`, an alert is created and ranked (ELITE/ALTA/MEDIA/BAJA)
6. **Real-Time Push**: Alerts are pushed to the React dashboard via WebSocket and REST polling

---

## :page_facing_up: License

This project is licensed under the MIT License.

---

<p align="center">
  <em>Built with Python + Java + React to find value where bookmakers miss it.</em>
</p>
