# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

MXIS-server is the backend for MXIS, an AI-based luxury leather goods care service for MCM. A Bluetooth "Smart Charm" attached to a bag records ambient temperature/humidity and motion/shock data; the app syncs that data, a rule-based engine turns it into a care diagnosis and a proactive care suggestion, and the customer books a store visit ("케어 컨시어지") directly from the suggestion. This repo implements only the customer-facing MVP API (no store-staff/admin surface is in scope).

The domain model and full REST surface are driven by two source-of-truth documents produced earlier in the project (not stored in-repo as of this writing): a DBML ERD and an API spec table. `src/main/resources/db/migration/V1__init_schema.sql` is the authoritative, adapted-for-MariaDB implementation of that ERD — read it before changing any entity.

## Commands

This project has no `gradlew` wrapper checked in yet (the sandbox this was scaffolded in has no internet access to fetch the wrapper jar). Options to get a working build:

- Open the project in IntelliJ and let it generate the Gradle wrapper on import, or
- Run `gradle wrapper` once (requires a local Gradle 8.x install), which will create `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`.

Once a wrapper (or local `gradle`) is available:

```bash
./gradlew build                                   # full build + tests
./gradlew bootRun                                  # run locally (needs MariaDB on localhost:3306, see below)
./gradlew test                                     # all tests
./gradlew test --tests "com.mxis.server.*.SomeTest"  # single test class
./gradlew test --tests "*.SomeTest.someMethod"       # single test method
```

Local DB: the app expects a MariaDB instance reachable at `jdbc:mariadb://localhost:3306/mxis` (see `application.yml`), with credentials from `DB_USERNAME`/`DB_PASSWORD` env vars (defaults `mxis`/`mxis`). Flyway runs automatically on startup (`spring.flyway.enabled=true`) and applies everything under `src/main/resources/db/migration`. There is no Docker Compose file yet — spin up MariaDB yourself (e.g. `docker run -e MARIADB_DATABASE=mxis -e MARIADB_USER=mxis -e MARIADB_PASSWORD=mxis -e MARIADB_ROOT_PASSWORD=root -p 3306:3306 mariadb:11`).

`JWT_SECRET` should be overridden outside local dev; the default in `application.yml` is a placeholder.

## Architecture

**Package-by-domain, not package-by-layer.** Each business area is a top-level package under `com.mxis.server` (`auth`, `user`, `device`, `product`, `sensor`, `care`, `store`, `reservation`), and each of those contains its own `entity/repository/service/controller/dto` subpackages. `common/` holds cross-cutting concerns (base entities, enums, exceptions, the JWT/security pieces), and `config/` holds Spring `@Configuration` classes. When adding a feature, find the matching domain package first rather than a generic "controllers" folder.

**Entity mutability follows the ERD exactly — this matters for how you write code:**
- *Mutable* entities (`users`, `notification_settings`, `devices`, `products`, `product_devices`, `care_algorithms`, `care_suggestions`, `stores`, `reservations`) extend `common/entity/BaseTimeEntity`, which gives `createdAt`/`updatedAt`.
- *Immutable / event* entities (`consents`, `sensor_readings`, `care_reports`) extend `common/entity/BaseCreatedAtEntity` (`createdAt` only) and must never be updated after insert — model state changes as a new row, not a mutation. `consents` in particular is an append-only event log (AGREED/REVOKED rows); "current" consent status is derived by querying the latest row per `(user_id, consent_type)`, there is no separate status table.
- The original ERD (Postgres) auto-updates `updated_at` via a DB trigger. This has been deliberately replaced with Spring Data JPA Auditing (`@EnableJpaAuditing` in `config/JpaAuditingConfig`) for MariaDB/application-layer portability — do not add a DB trigger for this; do not manually set `updatedAt` in service code.

**Soft deletes** are used for `users`, `devices`, and `products` only (`deleted_at` column). Repository queries against these tables must filter `deleted_at IS NULL` explicitly (see `UserRepository.findActiveByEmail`/`findActiveById` for the pattern) — there is no global Hibernate filter doing this automatically.

**MariaDB does not support Postgres-style partial unique indexes**, but the ERD relies on three of them. Each has been re-implemented as a virtual generated column (constant-when-condition-holds, `NULL` otherwise) plus a plain `UNIQUE` index on that column, since MariaDB unique indexes treat multiple `NULL`s as non-conflicting. See `V1__init_schema.sql` for all three:
- `product_devices.active_primary_product_id` — at most one `PRIMARY_SENSOR` (with `detached_at IS NULL`) per product.
- `care_algorithms.active_flag` — at most one `is_active = true` row globally.
- `reservations.active_slot_key` — at most one `CONFIRMED` reservation per `(store_id, reserved_date, reserved_time)`; cancelled reservations don't occupy the slot.

Any service-layer logic that flips these flags (promoting a new `PRIMARY_SENSOR`, activating a new `care_algorithms` version, confirming a reservation) must be written knowing the DB will reject a second "active" row — surface that as a `BusinessException` with the matching `ErrorCode`, don't let it bubble up as a raw constraint violation.

**Auth is stateless JWT**, no server-side session or refresh-token table. `JwtTokenProvider` issues access/refresh tokens (type carried as a claim, checked in `JwtAuthenticationFilter`, which only accepts `ACCESS` tokens for request auth). Because there's no token store, `/api/v1/auth/logout` cannot actually revoke a token server-side — it's advisory (client discards tokens). If real revocation is needed later, that requires adding a blacklist/refresh-token table, which does not exist in the current schema.

**Enums**: DB columns are `VARCHAR` + `CHECK` constraints (not native MariaDB `ENUM`), mapped via `@Enumerated(EnumType.STRING)` — Java enum constant names must match the `CHECK` constraint values in `V1__init_schema.sql` exactly. One exception: `auth_provider` stores lowercase values (`local`, `kakao`) per the original ERD while the Java enum (`AuthProvider`) uses uppercase constants; `common/enums/AuthProviderConverter` (`@Converter(autoApply = true)`) bridges the two. If you add a new enum-backed column, prefer matching Java constant name to DB value directly and skip writing a converter.

**Response/error convention**: all controllers should return `common/response/ApiResponse<T>` (`ApiResponse.ok(data)` / `ApiResponse.error(code, message)`). Business-rule failures should throw `common/exception/BusinessException` with an `ErrorCode` (add new failure cases to that enum rather than throwing raw exceptions) — `GlobalExceptionHandler` converts these to the right HTTP status and JSON shape automatically.

**Known intentional gap vs. the original one-pager/ERD** (flagged during design review, not yet reflected in the schema, keep in mind when implementing reservation/care logic): there's no field distinguishing free "정기 케어" (charm owners) from paid "AS" reservations, and no lifetime/cumulative outing-count aggregate on `products` (only per-`care_report`-period `outing_count` exists). Don't assume either exists when implementing reservation eligibility checks or care-suggestion messaging — check with the user before adding them, since schema changes need a new Flyway migration, not an edit to `V1`.

## Progress note

This backend is being built incrementally per domain (auth → user → device → product → sensor → security wiring → verification). Check for a task list / prior session notes before assuming a domain is unimplemented — `common/`, DB migrations, and project scaffolding are done; most domain packages currently contain only entities/DTOs or are empty pending implementation.

## Current implementation scope

The DB migration (`V1__init_schema.sql`) creates all 12 tables from the full ERD, but **the API layer being built right now only covers a subset**, per explicit user direction:

- `auth`: signup, login, refresh, logout
- `user`: `GET /users/me`, consents (`GET`/`POST /users/me/consents`), notification settings (`GET`/`PATCH /users/me/notification-settings`)
- `device`: register, list, detail, status `PATCH`, lookup-by-serial, soft delete, and `POST /devices/{deviceId}/sensor-readings/batch`
- `product`: DPP recognize, register, list, detail, soft delete, plus `product_devices` linking (`POST`/`GET /products/{productId}/devices`, `PATCH`/`DELETE /products/{productId}/devices/{deviceId}`)

**`care` (care_algorithms/care_reports/care_suggestions) and `store`/`reservation` are explicitly out of scope for now** — their tables exist in the schema (referenced by FKs from `sensor_readings`/`product_devices`), but no service/controller/repository code should be added for them unless the user asks. Don't let unrelated refactors drift into building those domains.
