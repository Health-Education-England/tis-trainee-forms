# AGENTS.md

## Project Overview

Spring Boot microservice managing NHS trainee forms (Form R Part A/B, LTFT). Uses MongoDB for persistence, AWS S3 for file storage, SNS/SQS for event-driven communication, and Mongock for database migrations.

**Base package:** `uk.nhs.hee.tis.trainee.forms`  
**Context path:** `/forms` (port 8207)

## Architecture

Layered structure within a single package root:

- `api/` — REST controllers (`*Resource.java`), annotated with `@XRayEnabled` for AWS X-Ray tracing
- `service/` — Business logic; form services extend `AbstractAuditedFormService` for lifecycle/revision tracking
- `repository/` — Spring Data MongoDB repositories
- `model/` — MongoDB `@Document` entities; forms extend `AbstractAuditedForm` (status history, revisions, timestamps)
- `dto/` — Data transfer objects for API layer
- `mapper/` — MapStruct interfaces (use `componentModel = SPRING`, `injectionStrategy = CONSTRUCTOR`)
- `event/` — `@SqsListener` classes consuming AWS SQS messages (e.g., `ProfileMoveListener`, `FormEventListener`)
- `migration/` — Mongock `@ChangeUnit` classes for schema migrations
- `config/` — Spring configuration classes
- `api/validation/` — Custom validators per form type (e.g., `FormRPartAValidator`)

### Key Patterns

- **Admin vs Trainee controllers**: `Admin*Resource` endpoints for admin operations; regular `*Resource` for trainee-facing APIs
- **Event broadcasting**: Services publish to SNS topics (formr-updated, ltft-status-updated, etc.); listeners consume from SQS queues
- **Audited forms**: All form entities track status history and revisions via `AbstractAuditedForm`; mappers translate between `status.current.state` and flat DTO `lifecycleState`
- **Submission history**: Separate `*SubmissionHistory` documents and repositories for lightweight queries

## Build & Test Commands

```shell
# Unit tests (with JaCoCo coverage)
./gradlew test

# Integration tests (requires Docker for Testcontainers)
./gradlew integrationTest

# Full check (unit + integration + checkstyle)
./gradlew check

# Run locally
./gradlew bootRun
```

Integration tests use Testcontainers (`@Testcontainers`) with `@ServiceConnection` for MongoDB and LocalStack. The `spring.profiles.active=test` profile is set automatically. Mongock migrations are disabled in tests (`mongock.enabled: false`).

## Conventions

- **Java 17** with Adoptium toolchain
- **Lombok** for boilerplate (getters, setters, builders, `@Slf4j`)
- **Google Checkstyle** enforced via Gradle checkstyle plugin
- **MapStruct mappers** are interfaces extending `FormMapper<Entity, Dto>` and optionally `SubmissionHistoryMapper`
- **MIT License header** required in all source files
- **Lifecycle states** defined in `dto/enumeration/LifecycleState`
- Feature flags configured via `features.*` properties in `application.yml`

## Key Configuration

| Property path | Purpose |
|---|---|
| `application.aws.sns.*` | SNS topic ARNs for event publishing |
| `application.aws.sqs.*` | SQS queue URLs for event consumption |
| `application.file-store.bucket` | S3 bucket for form document storage |
| `application.signature.secret-key` | HMAC key for signed data validation |
| `application.review-workflows` | Per-DBC multi-stage review configuration |

## Adding a New Form Type

1. Create entity in `model/` extending `AbstractAuditedForm`
2. Create DTO in `dto/`
3. Create MapStruct mapper in `mapper/` extending `FormMapper`
4. Create repository in `repository/`
5. Create service in `service/` extending `AbstractAuditedFormService`
6. Create controller in `api/` with `@XRayEnabled`
7. Add validator in `api/validation/` if needed
8. Add SNS topic config in `application.yml` if publishing events

