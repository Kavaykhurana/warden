# Warden — implementation plan

Source of truth: the project brief (contract §4, schema §5). This plan maps each phase to concrete files.

## Repository layout decision

The existing frontend lived at the repo root (`warden-frontend/`). It was moved, unchanged, into `frontend/` so the
root matches the brief's layout (`frontend/`, `backend/`, `docs/`, `docker-compose.yml`, `README.md`).

## Pinned versions (verified on Maven Central, October 2026)

| Artifact | Version | Why |
|---|---|---|
| Spring Boot | 4.1.1 | Latest stable; Spring AI 2.0.1 is built against exactly this version |
| Spring AI BOM | 2.0.1 | Latest stable 2.0.x |
| springdoc-openapi | 3.1.1 | Built against Boot 4.1.0 |
| Testcontainers | 2.0.5 | Managed by the Boot BOM |
| Java | 21 (release target) | Compiled with the local JDK 25 using `--release 21` |

## Phases and files

Package root: `backend/src/main/java/edu/ashbrook/warden/`.

| # | Phase | Files |
|---|---|---|
| 0 | Recon | `docs/PLAN.md`, `docs/CONTRACT_CHECK.md` |
| 1 | Scaffold + schema + seed | `pom.xml`, `mvnw`, `.mvn/`, `docker-compose.yml`, `application.yml`, `db/migration/V1__init.sql`, `WardenApplication`, `config/ClockConfig`, `user/{User,Role,Department,UserRepository,DepartmentRepository}`, `academics/{Course,Enrollment,GradeChange,Grade,GradeConverter,*Repository}`, `dutyleave/{DutyLeaveRequest,LeaveStatus,DutyLeaveRepository}`, `approval/{ActionRequest,ActionStatus,ActionRequestRepository}`, `conversation/{Conversation,Message,*Repository}`, `ai/{PromptGuard,GuardResult,ToolEvent}`, `audit/{AuditService,HashChain,AuditType}`, `seed/DemoDataSeeder` |
| 2 | Auth + security | `config/{SecurityConfig,JwtConfig}`, `common/{ApiError,ErrorCode,GlobalExceptionHandler,BusinessRuleException,ConflictException,NotFoundException,FieldValidationException,RateLimiter,JsonAuthEntryPoint,JsonAccessDeniedHandler,RequestIdFilter}`, `auth/{AuthController,AuthService,TokenService,RefreshToken,RefreshTokenRepository,LoginRateLimit,ActiveUserFilter,Hashing,InvalidRefreshTokenException}`, `user/CurrentUser`; test `AuthIT` |
| 3 | Domain + REST | `academics/{AttendanceMath,AcademicsService,CourseService,CourseSecurity,AcademicsController,CourseController}`, `dutyleave/{WorkingDays,DutyLeaveService,DutyLeaveSecurity,DutyLeaveController}`, `approval/{ApprovalService,ApprovalEligibility,ActionExecutor,ApprovalExpiryJob,ApprovalController}`, `conversation/{ConversationService,ConversationController}`, `user/{UserService,AdminUserController,MeController}`, DTO records next to their services; tests `DutyLeaveIT`, `ApprovalIT`, `ContractIT`, unit tests for the pure helpers |
| 4 | Audit | `audit/{AuditController,AuditQueryRepository}`, `verify()`, `page()`, `summary()`; tests `AuditIT`, `HashChainTest` |
| 5 | AI layer (⏸ asks for the Gemini key first) | `config/AiConfig`, `ai/{ChatService,SystemPrompt,WardenTools,ToolGuard,ToolOutcome,ToolEventCollector,ToolKeys,Args}`; tests `PromptGuardTest`, `ToolGuardTest`, `ChatServiceIT`, `AuthorizationMatrixIT`, `InjectionRedTeamLiveIT` |
| 6 | Frontend integration | fixes in `frontend/src/api/*` only if needed, `frontend/.env.local`, `docs/DEMO_RUN.md` |
| 7 | Packaging + docs | `backend/Dockerfile`, `application-prod.yml`, `README.md`, `docs/THREAT_MODEL.md`, `docs/DECISIONS.md`, `frontend/vercel.json` |
| 8 | Deploy (⏸ asks first) | Neon + Render + Vercel, live URLs in README |

## Testing approach

- Pure helpers (`WorkingDays`, `AttendanceMath`, `Grade`, SGPA, `HashChain`, `PromptGuard`, `ToolGuard`) get plain JUnit tests.
- Everything touching the database runs as `*IT` against a Testcontainers PostgreSQL 16, sharing one container and one
  seeded database per JVM. Tests that mutate state create their own rows or restore what they change.
- Gemini is never called in the default build. `ChatServiceIT` stubs `ChatModel`; `AuthorizationMatrixIT` calls
  `WardenTools` directly with a real `ToolContext`. The live red-team test runs only under `-Plive`.
