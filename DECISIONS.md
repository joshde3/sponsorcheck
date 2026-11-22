# DECISIONS.md

One entry per decision: what we chose, why, and what we gave up.
Status: **Accepted**. Plan approved by Joshua on 22 Nov 2026.

---

## D1. Resolve the UK CSV URL through the GOV.UK Content API
- **Decision:** The import job asks `https://www.gov.uk/api/content/government/publications/register-of-licensed-sponsors-workers` for the current CSV attachment instead of hardcoding a file URL.
- **Why:** The file path contains a random media id and the date (`.../media/<id>/SP_-_Worker_and_Temporary_Worker_Web_Register_-_2026-11-19.csv`), so it changes with every update.
- **Trade-off:** One extra HTTP call, and we depend on the Content API's JSON shape (stable and documented).

## D2. Each country's result has its own meaning
- **Decision:** UK and NL say "licensed / recognised sponsor". IE says "issued N employment permits in YEAR (to MONTH)". DE says "no official public list" with an EU Blue Card link.
- **Why:** The Irish data is permit statistics, not a licence register. Calling it "licensed" would be wrong.
- **Trade-off:** The API response and badge need per-country wording instead of one yes/no.

## D3. API and import job share one codebase but run separately
- **Decision:** One Spring Boot project with two entry points (web API, batch import).
- **Why:** The normaliser and schema are shared, so matching at query time is guaranteed to use the same rules as at import time. A crashed import can't take the API down.
- **Trade-off:** Two Railway services (API + cron import) instead of one.

## D4. Staging table plus atomic swap, with validation
- **Decision:** Load into staging, check header, row count (within ~20% of last good import) and a few known sponsors, then swap in one transaction.
- **Why:** A changed source format must never wipe live data. Old data plus an alert beats empty results.
- **Trade-off:** Data can be a day or a month stale when a source breaks, so every answer carries `dataAsOf`.

## D5. Hosting and scheduling: Railway Hobby plan ($5/month)
- **Decision:** One Railway project with three parts: an always-on Spring Boot API service, an import service on a Railway Cron Schedule (UTC, runs and exits), and Railway Postgres with `pg_trgm`. (Joshua's choice, 22 Nov 2026, replacing the GCP proposal.)
- **Why:** One dashboard, deploys on git push, no scale-to-zero cold starts. Estimated usage (about $3 API at `-Xmx256m`, about $1–1.50 Postgres, cents for the job) fits the $5 that the subscription includes. Railway cron doesn't auto-disable. GitHub Actions cron was rejected because GitHub disables scheduled workflows after 60 days without repository activity.
- **Trade-off:** A fixed $5 a month even while paused (about $60 a year), versus roughly $0 on GCP's free tier. Serverless sleep stays off: after sleeping, the first request can 502 and then waits for the JVM to start, and the database pool would keep it awake anyway. Railway documents no failure notifications for cron, so D6 still applies. Risk during a pause: an expired card stops the services.
- **Guardrails:** Set a Railway usage limit. Back up the subscription and alias tables (registers can be re-downloaded, paying users' records can't). Fallback if RAM costs grow: move the database to Neon's free tier.

## D6. Dead-man's-switch alerting (healthchecks.io)
- **Decision:** The job pings healthchecks.io only after a successful, validated import. No ping in the expected window means an email.
- **Why:** This catches every kind of failure (crash, format change, scheduler stopped) with one mechanism and no code that has to run correctly when everything else is broken.

## D7. Matching: normalise, then exact, then alias, then trigram
- **Decision:** Normalise (NFKD, lower-case, `&`→`and`, punctuation, per-country legal suffixes, "t/a" split). Look up exact match, then a curated alias table, then Postgres `pg_trgm` similarity (GIN index) re-scored with token-set similarity in Java.
- **Why:** Explainable, indexable, handles typos and suffix noise, and runs inside the database we already have.
- **Rejected:** Plain Levenshtein (no index, bad with reordered words). Embeddings (opaque, extra hosting, overkill).
- **Guardrail:** If the top two candidates score within 0.05, answer "several possible matches".

## D8. Extension permissions: fixed job-site hosts plus `activeTab`
- **Decision:** Host permissions only for LinkedIn, Indeed, Glassdoor, Seek and our API. Careers pages use `activeTab` via the popup's "Check this company".
- **Why:** Chrome Web Store minimum-permission policy, and avoids the "all websites" install warning.
- **Trade-off:** On careers pages the user has to click once. No automatic badge there.

## D9. Company detection: JSON-LD first, then per-site selectors, then manual
- **Why:** schema.org `JobPosting.hiringOrganization.name` is on many job pages and careers platforms, so one parser covers many sites. Site selectors break when sites redesign, so they are the second choice.

## D10. Payments: Paddle (still to confirm when applying)
- **Decision (proposed):** Paddle as merchant of record.
- **Why:** Accepts sellers based in India and handles global VAT/GST. Lemon Squeezy has stopped normal signups (it points to Stripe Managed Payments). Stripe Managed Payments is reported as not available to India-based businesses (to be confirmed).
- **Trade-off:** Paddle reviews the product and website before going live, and has no built-in licence keys, so we make our own.

## D11. Licensing is checked on the server only
- **Decision:** Paddle webhook (signature verified) updates subscription status. Our API issues a licence key, shown on the checkout success page. The extension sends the key and the API checks it.
- **Why:** Anything inside the extension can be edited by the user, so it can't be trusted.

## D12. Free quota per random install id
- **Decision:** 10 UK checks/day per install id, counted in Postgres. Cached results don't count.
- **Why:** Simple and collects no personal data.
- **Trade-off:** Reinstalling resets it. Accepted: it's a nudge, not DRM.

## D13. Monorepo: `/backend` and `/extension` in one repository
- **Decision:** One repo, two top-level folders.
- **Why:** The API contract and the extension that calls it change together, so one PR can update both. One place for DECISIONS.md and CI.
- **Trade-off:** CI must only build what changed (path filters), or every PR builds both.

## D14. Build tool: Gradle with the Kotlin DSL, plus the Gradle wrapper
- **Decision:** `backend/` uses Gradle (`build.gradle.kts`) and commits the wrapper (`gradlew`), so anyone builds with the same Gradle version without installing it.
- **Why:** Shorter build files than Maven, fast incremental builds, and type-checked build scripts with IDE completion. Railway detects Gradle projects automatically.
- **Trade-off:** Maven is more common in enterprise jobs and more declarative. Gradle builds can become "clever" code, so the rule is: plain dependency lists, no custom tasks unless DECISIONS.md explains them.

## D15. Stack versions: Java 21 LTS, Spring Boot 4.2, Postgres 17
- **Why:** Java 21 is the current long-term-support release that Railway and Docker images support well. Spring Boot 4.2 is the latest stable line (Nov 2026), so it gets the longest support while the project is paused.
- **Trade-off:** Spring Boot 4 split the auto-configuration into smaller modules, so some older tutorials (Boot 3) show different starter names.

## D16. Database schema is owned by Flyway migrations
- **Decision:** Every schema change is a numbered SQL file in `backend/src/main/resources/db/migration`. The application never creates or changes tables itself.
- **Why:** Plain SQL you can read in review, the same schema in tests, locally and on Railway, and a history of every change.

## D17. Integration tests use Testcontainers with a real Postgres
- **Decision:** Tests start a throwaway Postgres 17 in Docker instead of using H2.
- **Why:** The matching depends on Postgres-only features (`pg_trgm`). H2 would pass tests that fail in production.
- **Trade-off:** Tests need Docker and take a few seconds longer to start.

## D18. Data access: Spring `JdbcClient` and plain SQL, not JPA/Hibernate
- **Decision:** Repositories use `JdbcClient` with hand-written SQL.
- **Why:** The core queries are bulk loads, a table swap and `pg_trgm` similarity searches. Those are SQL features, and JPA would hide them behind native queries anyway. Plain SQL is easier to explain and to tune with `EXPLAIN`.
- **Trade-off:** No automatic entity mapping. We write small row mappers by hand (Java records keep them short).
