# 🧠 SmartHire
### AI-Powered Recruitment Intelligence Platform

> **SmartHire is a full-stack enterprise SaaS application that lets recruiters post jobs, automatically rank candidates using a two-stage AI engine (TF-IDF + Claude), and manage the entire hiring pipeline from application to offer — with production-grade security, observability, and cloud deployment.**

[![CI/CD](https://github.com/yourgithub/smarthire/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/yourgithub/smarthire/actions)
[![Coverage](https://codecov.io/gh/yourgithub/smarthire/branch/main/graph/badge.svg)](https://codecov.io/gh/yourgithub/smarthire)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      SmartHire                          │
├──────────────┬─────────────────┬────────────────────────┤
│ React 18 +   │  Spring Boot 3  │   Background Workers   │
│ TypeScript   │  (REST API)     │  (Scoring + Email)     │
│ Nginx proxy  │  JWT + RBAC     │  @Async thread pools   │
└──────┬───────┴────────┬────────┴──────────┬─────────────┘
       │                │                   │
       ▼                ▼                   ▼
  ┌─────────┐   ┌──────────────┐   ┌──────────────────┐
  │  Redis  │   │  PostgreSQL  │   │   Claude API /   │
  │ Cache + │   │  (Flyway     │   │   TF-IDF Engine  │
  │ Rate    │   │  migrations) │   │   (pure Java)    │
  │ Limits  │   └──────────────┘   └──────────────────┘
  └─────────┘
       │
  ┌────▼──────────────────────┐
  │  Prometheus + Grafana     │
  │  Micrometer metrics       │
  └───────────────────────────┘
```

---

## 🤖 AI Scoring Pipeline

SmartHire uses a **two-stage scoring pipeline** for every application:

**Stage 1 — TF-IDF Cosine Similarity (pure Java, no external API)**
- Tokenises resume + job description (lowercase, stopword removal, suffix stemming)
- Builds TF-IDF vectors for each document
- Computes cosine similarity → 0–100 score
- Always runs — works offline, zero cost

**Stage 2 — Claude Narrative Analysis (optional, graceful fallback)**
- Sends resume + job requirements to Claude API
- Receives structured JSON: `overall_score`, `skill_match_pct`, `strengths`, `gaps`, `recommendation`, `summary`
- If Claude times out (5s) or is unavailable → falls back to TF-IDF only, sets `scoring_method: "tfidf_only"`

**Async processing:**
1. Candidate submits application → saved with `scoring_complete: false`
2. Spring `@Async` event triggers scoring pipeline (non-blocking)
3. Client polls `GET /applications/{id}` — score appears within ~30 seconds
4. Status email sent to candidate on any pipeline change

---

## ✨ Features

| Domain | Capability |
|---|---|
| **Auth** | JWT rotation, BCrypt 12, refresh token revocation, audit logs |
| **Jobs** | CRUD, full-text search, pagination, status workflow, view counter |
| **Applications** | Submit, AI score, pipeline status, ranked by AI score |
| **AI Engine** | TF-IDF + Claude, async processing, graceful fallback |
| **Dashboard** | Stats, pipeline breakdown, hiring funnel, top candidates |
| **Security** | Rate limiting, RBAC, HSTS, CSP, XSS/clickjacking protection |
| **Monitoring** | Prometheus, Grafana, Micrometer, structured JSON logging |
| **Email** | Status notifications (shortlisted, interview, offer, rejection) |

---

## 🚀 Quickstart (4 commands)

```bash
git clone https://github.com/yourgithub/smarthire.git && cd smarthire
cp .env.example .env          # fill in DB_PASS, REDIS_PASSWORD, JWT_SECRET
docker-compose up -d
open http://localhost          # React dashboard
```

| Service | URL |
|---|---|
| Frontend | http://localhost |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/docs |
| Grafana | http://localhost:3001 |
| Prometheus | http://localhost:9090 |

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Security |
| Database | PostgreSQL 16, Spring Data JPA, Hibernate, Flyway |
| Cache / Rate limit | Redis 7, Lettuce, Bucket4j |
| AI Engine | TF-IDF (pure Java) + Claude API (HTTP/RestTemplate) |
| API Docs | SpringDoc OpenAPI 3, Swagger UI |
| Frontend | React 18, TypeScript, Tailwind CSS, Vite |
| State | Zustand (in-memory JWT), React Query (stale-while-revalidate) |
| Charts | Recharts |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres + Redis), MockMvc |
| Build | Maven, multi-stage Docker, GitHub Actions |
| Monitoring | Micrometer, Prometheus, Grafana |

---

## 🔒 Security Model

- **Passwords**: BCrypt strength 12, never logged or returned
- **JWT**: Access token 15 min / Refresh token 7 days, rotated on every use
- **Refresh tokens**: Stored as SHA-256 hash — raw token never persisted
- **Token reuse detection**: Replay attack triggers revocation of all user tokens
- **Rate limits**: 5 login attempts / 15 min, 3 registrations / hour, 100 API calls / user / min
- **RBAC**: `@PreAuthorize` on every endpoint — ADMIN / RECRUITER / CANDIDATE separation
- **Audit trail**: Every auth event + application status change written to `audit_logs`
- **Input validation**: Jakarta Bean Validation + Zod (frontend) + HTML escaping
- **Headers**: HSTS, CSP, X-Frame-Options DENY, nosniff, Referrer-Policy

---

## 📡 API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | ❌ | Register account |
| POST | `/api/v1/auth/login` | ❌ | Login → tokens |
| POST | `/api/v1/auth/refresh` | ❌ | Rotate access token |
| POST | `/api/v1/auth/logout` | ✅ | Revoke tokens |
| GET  | `/api/v1/auth/me` | ✅ | Current user |
| GET  | `/api/v1/jobs` | ❌ | List jobs (public) |
| POST | `/api/v1/jobs` | RECRUITER | Create job |
| GET  | `/api/v1/jobs/{id}` | ✅ | Job detail |
| PUT  | `/api/v1/jobs/{id}` | RECRUITER | Update job |
| POST | `/api/v1/applications` | CANDIDATE | Submit application |
| GET  | `/api/v1/applications` | RECRUITER | List (filtered) |
| GET  | `/api/v1/applications/{id}` | ✅ | Detail + AI score |
| PUT  | `/api/v1/applications/{id}/status` | RECRUITER | Update status |
| GET  | `/api/v1/applications/my` | CANDIDATE | Own applications |
| GET  | `/api/v1/applications/job/{id}/ranked` | RECRUITER | AI-ranked list |
| GET  | `/api/v1/dashboard/stats` | RECRUITER | Hiring stats |
| GET  | `/api/v1/dashboard/pipeline` | RECRUITER | Status breakdown |
| GET  | `/api/v1/dashboard/top-candidates` | RECRUITER | Top 10 by AI score |
| GET  | `/api/v1/dashboard/hiring-funnel` | RECRUITER | Conversion rates |

---

## 🧪 Test Coverage

| Test Class | What it covers |
|---|---|
| `AuthServiceTest` | Register (duplicate email → 409), login (wrong password → 401 + audit), refresh (revoked token → lockout), logout (token revocation) |
| `AiScoringServiceTest` | TF-IDF 0.0 for unrelated texts, >0.8 for identical, Claude timeout → tfidf_only fallback, null inputs |
| `ApplicationControllerTest` | CANDIDATE submit/view, RECRUITER status update, cross-user 403, ranked endpoint, validation errors, security headers |
| `RateLimitTest` | 6th login → 429 + Retry-After, normal requests not blocked |

---

## 🗺️ Roadmap

**v1.0 (current)** — Full hiring pipeline, TF-IDF + Claude scoring, JWT auth, React dashboard

**v1.1** — pgvector semantic search (embeddings for resume/JD matching), saved search filters, bulk status updates

**v1.2** — Video interview AI analysis (transcript scoring), calendar integration (Google Calendar API), multi-tenant company accounts

---

## 📄 License

MIT — see [LICENSE](LICENSE) for details.

> *"Built to production engineering standards. Not a student project."*
