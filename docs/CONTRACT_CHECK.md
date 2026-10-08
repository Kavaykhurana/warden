# Contract check — frontend vs brief §4

Checked files: `frontend/src/api/types.ts`, `api.ts`, `http.ts`, `index.ts`, `src/auth/AuthProvider.tsx`,
`vite.config.ts`, and every `api.*` call in `src/pages/App.tsx`.

## Types (`src/api/types.ts`)

| Type | Status |
|---|---|
| `Role`, `UserSummary`, `User`, `AuthResponse` | ✅ field-for-field |
| `ErrorCode`, `ApiError` | ✅ |
| `Decision`, `ToolEvent` | ✅ |
| `Conversation`, `Message`, `ChatTurn` | ✅ |
| `ActionStatus`, `ActionTool`, `ActionRequest` | ✅ |
| `LeaveStatus`, `DutyLeave` | ✅ |
| `Grade`, `AttendanceRow`, `GradeRow`, `AcademicSummary` | ✅ |
| `Course`, `RosterRow`, `AdminUser` | ✅ |
| `AuditEventType`, `AuditEvent`, `Page<T>`, `ChainVerification` | ✅ |

The file is the contract text with whitespace compressed; no field, optionality or union member differs.

## Endpoints (`src/api/http.ts`)

| Endpoint | Status |
|---|---|
| `POST /api/auth/login` `{email,password}` | ✅ |
| `POST /api/auth/refresh` | ✅ no body, cookie only |
| `POST /api/auth/logout` | ✅ 204 handled |
| `GET /api/me` | ✅ |
| `GET/POST /api/conversations` | ✅ (`create()` without a title sends `{}`) |
| `PATCH/DELETE /api/conversations/{id}` | ✅ |
| `GET/POST /api/conversations/{id}/messages` | ✅ 60 s client timeout > 45 s server AI timeout |
| `GET /api/academics/me` | ✅ |
| `GET /api/courses`, `GET /api/courses/{code}/roster` | ✅ |
| `POST /api/courses/{code}/grade-changes` `{enrollmentId,newGrade,reason}` | ✅ |
| `GET /api/duty-leave/mine`, `POST /api/duty-leave` | ✅ |
| `POST /api/duty-leave/{id}/cancel` | ✅ |
| `GET /api/duty-leave/review?status=PENDING\|ALL` | ✅ |
| `POST /api/duty-leave/{id}/decide` `{approve,note}` | ✅ |
| `GET /api/approvals?scope=&status=` | ✅ |
| `POST /api/approvals/{id}/approve` `{note?}` | ✅ (`undefined` note is dropped by `JSON.stringify`) |
| `POST /api/approvals/{id}/reject` `{note}` | ✅ |
| `GET /api/admin/users` | ✅ |
| `GET /api/admin/audit?eventType=&actorId=&from=&page=&size=` | ✅ undefined params are omitted |
| `GET /api/admin/audit/verify` | ✅ |

## HTTP client behaviour (`src/api/http.ts`)

| Requirement | Status |
|---|---|
| Access token in memory only | ✅ module-level variable, never written to storage |
| `credentials: "include"` on every request | ✅ |
| Single-flight refresh | ✅ shared `refreshPromise`, cleared in `finally` |
| Retry once after 401 | ✅ `retried` flag; `/auth/*` paths never trigger a refresh |
| Error bodies surfaced as `ApiError` | ✅ non-JSON bodies become `INTERNAL`; network failure → `status: 0` |

## Wiring

| Item | Status |
|---|---|
| `vite.config.ts` proxies `/api` → `http://localhost:8080` | ✅ |
| `VITE_USE_MOCK=false` selects `httpApi` | ✅ (`src/api/index.ts`); `frontend/.env.local` created in Phase 6 |
| `vercel.json` API rewrite | ⏳ added in Phase 8 once the Render host is known; SPA fallback already excludes `/api/` |

**Result: no mismatches.** No frontend code changes are required for the contract.
