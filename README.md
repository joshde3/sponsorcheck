# Sponsor Check

A Chrome extension (Manifest V3) that shows whether the employer on the job post you are viewing is on a public visa sponsor register (UK, Netherlands, Ireland).

> Data comes from public government registers and may be out of date. This is not legal advice. Always confirm sponsorship with the employer.

## Layout

| Folder | What |
|---|---|
| `backend/` | Spring Boot API (`GET /v1/check`) and the register import job. Java 21, Gradle, Postgres |
| `extension/` | Chrome extension (weekend 3) |
| `DECISIONS.md` | Every design decision, why, and what it costs |

## Run the backend locally

```bash
docker compose up -d          # local Postgres 17
cp .env.example .env          # local-only settings, never committed
set -a; . ./.env; set +a      # load .env into this shell
cd backend && ./gradlew bootRun
curl localhost:8080/actuator/health
```

Tests start their own Postgres in Docker: `cd backend && ./gradlew test`.
