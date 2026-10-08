# Decisions and deviations

Each entry: what was decided, why, and where the brief said something different.

## Versions (verified on Maven Central, October 2026)

- **Spring Boot 4.1.1, Spring AI 2.0.1, springdoc 3.1.1, Testcontainers 2.0.5.** Spring AI 2.0.1's own
  autoconfigure module is built against Boot 4.1.1, and springdoc 3.1.x against Boot 4.1, so these move together.
- **Java 21 target, built locally on JDK 25.** `java.version=21` makes Maven compile with `--release 21`; the Docker
  image runs on `eclipse-temurin:21-jre` as the brief asks.

## Framework facts that differ from older Spring

- **Gemini options moved.** In Spring AI 2.0 `spring.ai.google.genai.chat.options.*` is deprecated; model,
  temperature and output limit are set directly under `spring.ai.google.genai.chat.*`. The brief's YAML used the old
  path. `spring.ai.model.chat: google-genai` selects the provider; tests set it to `none` and supply a stub.
- **Tools run in the ChatClient, not the model.** Spring AI 2.0 executes tool calls in a `ToolCallingAdvisor` inside
  `ChatClient`. That is what lets `ChatServiceIT` and `ContractIT` exercise the real tool path with a stubbed
  `ChatModel` that returns tool calls. `ChatClient.tools(Object...)` replaces the deprecated `toolCallbacks(...)`.
- **Jackson 3.** Boot 4 uses `tools.jackson.*` (`JsonMapper`, `JsonNode.asString()`); annotations stay in
  `com.fasterxml.jackson.annotation`.
- **Testcontainers 2.** Artifacts are `testcontainers-postgresql` / `testcontainers-junit-jupiter`; the container
  class is `org.testcontainers.postgresql.PostgreSQLContainer`.

## Model

- **Default `gemini-3.8-flash`, not `gemini-2.5-flash`.** The key could call `gemini-2.5-flash`, but with thinking
  disabled it occasionally passed wrong ids to tools (leave "101" for leave 2). `gemini-3.8-flash` is the newest Flash
  model the key lists and called tools with correct arguments in the demo. Override with `GEMINI_MODEL`.
- **`thinking-budget: 0`.** Keeps replies fast and keeps thinking tokens from eating the 800-token output limit.
- **Retries fail fast.** Spring AI's default retry (10 attempts, 2 s × 5 backoff) and the Google SDK's internal
  retries could both outlast the 45 s turn budget on a quota `429`. `spring.ai.retry` is capped at 3 attempts with
  short backoff, and `AiConfig` builds the Gemini `Client` with SDK retries off. A quota error is now a 503 in about a
  second.

## Security

- **`forward-headers-strategy: native`, not `framework`.** `framework` uses Spring's `ForwardedHeaderFilter`, which
  takes the left-most `X-Forwarded-For` value — a value the client controls — so anyone could dodge the per-IP login
  limit by sending a fake header. `native` uses Tomcat's `RemoteIpValve`: it only honours the header when the
  connection comes from an internal proxy (Render's load balancer) and reads it right-to-left.
- **Refresh-token reuse is audited only for rotated tokens.** A token revoked by logout or by an earlier family
  revocation is rejected silently; only a token that has a successor (`replaced_by`) is evidence of copying and gets
  `TOKEN_REUSE_DETECTED`.
- **AI leave decisions must name the student.** `decideDutyLeave` takes the student's roll number as well as the
  leave id, and the service rejects a mismatch. A model that copies the wrong id would otherwise decide a different
  request that the same reviewer happens to be eligible for.
- **Tool descriptions say "the server decides".** Descriptions phrased as "Only faculty may call it" made Gemini refuse
  in prose instead of calling the tool, so no DENIED card appeared and nothing was audited. They now say who is
  allowed *and* that the model must call the tool anyway.
- **Audit writes for decisions happen after commit.** `append` is `REQUIRES_NEW` as specified (so a DENIED survives a
  rollback). Outcomes such as `ACTION_EXECUTED` and `DUTY_LEAVE_DECIDED` use `appendAfterCommit`, so the log never
  claims an execution that was rolled back.
- **`ActiveUserFilter` eviction happens twice.** Immediately, and again after the deactivation commits, so a request
  racing the transaction cannot re-cache the old "active" value for 30 seconds.
- **Last-admin check locks the admin rows.** Two cross-approved deactivations of the last two admins would otherwise
  both pass a plain count.

## Structure

- **`frontend/` was moved, not copied.** The session started inside the frontend folder; it became the repository
  root with the frontend moved unchanged into `frontend/`.
- **`AuditService.appendAt` is public, not package-private.** The seeder lives in `seed/`, a different package. It is
  `@Transactional(MANDATORY)` so it joins the seeder's transaction; a `REQUIRES_NEW` append could not see the
  freshly inserted, uncommitted users its rows reference.
- **One `AcademicsController`** serves `/api/academics/me` and the `/api/courses/**` endpoints instead of two
  three-method controllers.
- **Compose port is configurable** (`WARDEN_DB_PORT`, default 5432). The development machine already runs a local
  PostgreSQL on 5432, so a git-ignored root `.env` sets 5433 there.
- **Each integration test class gets a fresh, freshly seeded database** (`@DirtiesContext`), at roughly 3–4 seconds
  per class, so classes can mutate seeded data freely.

## Known costs of the single-instance design

- `REQUIRES_NEW` audit writes hold a second pool connection while the business transaction holds the first; with
  Hikari's default 10 connections, heavy concurrency could exhaust the pool. Fine for this scale.
- The rate limiter, the active-user cache and the in-flight chat set are in memory: one instance only.
