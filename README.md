# RouteGuard

RouteGuard is a Spring Boot backend for delivery companies and their operations teams. It receives delivery events, checks the supplied evidence, flags suspicious deliveries, and records human reviews with recommended recovery actions.

The problem: a delivery marked as completed may have missing evidence, an invalid OTP, a location mismatch, or a reused proof photo. RouteGuard provides a company-scoped workflow for investigating those claims.

## Features

- Company registration and platform administrator activation.
- Company administrator, operations officer, and risk reviewer accounts.
- JWT authentication and role-based authorization.
- Company API credential creation and revocation.
- Delivery webhooks authenticated with API keys.
- Idempotency keys to prevent duplicate events when webhooks are retried.
- GPS distance, OTP, and proof-photo reuse checks for delivered events.
- Evidence checks for failed deliveries.
- Stored evaluation decisions, reason codes, risk scores, and recovery recommendations.
- Manual risk reviews and review history.
- Company-scoped event queries with status filtering and pagination.
- Flyway database migrations and Hibernate schema validation.

## Technology

| Component | Technology |
| --- | --- |
| Language | Java, targeting Java 17 |
| Framework | Spring Boot 4.1.1 |
| Security | Spring Security, JWT |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Build | Maven Wrapper |
| Testing | JUnit, Mockito, Spring testing support, PostgreSQL integration tests |
| Local infrastructure | Docker Compose, Adminer |

## Roles

| Role | Responsibilities |
| --- | --- |
| PLATFORM_ADMIN | Bootstrap the initial platform administrator and activate delivery companies. Bootstrap is a public initialization endpoint; subsequent company activation requires the administrator role. |
| COMPANY_ADMIN | Create company staff, manage API credentials, and read company events, evaluations, and review history. |
| OPERATIONS_OFFICER | Evaluate company delivery events and read company events, evaluations, and review history. |
| RISK_REVIEWER | Submit risk reviews and read company events, evaluations, and review history. |

Delivery webhooks use company API keys. Other protected endpoints use JWT bearer tokens. Company-scoped endpoints derive the company from the authenticated user.

## Run locally

### Prerequisites

- JDK 17 or later compatible with the project.
- Docker Desktop with Docker Compose.
- Git.
- Postman or another HTTP client.

The Maven Wrapper is included, so a separate Maven installation is not required.

### 1. Start PostgreSQL and Adminer

From the repository root:

```powershell
docker compose up -d
docker compose ps
```

| Service | Address |
| --- | --- |
| RouteGuard API | http://localhost:8083 |
| PostgreSQL from the host | localhost:5438 |
| Adminer | http://localhost:8087 |

Local database settings:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5438/routeguard_db
spring.datasource.username=routeguard_user
spring.datasource.password=routeguard_password
```

For Adminer, choose PostgreSQL and use server `postgres`, database `routeguard_db`, username `routeguard_user`, and password `routeguard_password`. Adminer connects through the Docker network, so its server address differs from the host address.

These database credentials are for local development.

### 2. Check application configuration

The application uses port 8083. Database schema changes are managed by Flyway:

```properties
server.port=8083
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false
routeguard.evaluation.maximum-gps-distance-metres=200
```

The initial migration is `src/main/resources/db/migration/V1__initial_schema.sql`.

On a fresh database, Flyway applies the initial migration. The existing development database was baselined at version 1 during migration adoption. Do not enable automatic baselining for an arbitrary existing database; first verify that its schema matches the intended baseline.

Create new versioned migrations for future schema changes instead of relying on Hibernate `update`.

### 3. Start RouteGuard

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux or macOS:

```bash
./mvnw spring-boot:run
```

Check application health:

```text
GET http://localhost:8083/actuator/health
```

### JWT configuration

The application accepts `JWT_SECRET` and `JWT_EXPIRATION_MINUTES`. The configured token lifetime defaults to 60 minutes. Set a strong secret through the environment before deploying; the development fallback is intended for local use.

Expired tokens require another login. An API key is a separate credential; a credential with `expiresAt: null` has no configured expiration and remains subject to revocation and company/credential status checks.

## API endpoints

All paths below are relative to `http://localhost:8083`.

| Method | Path | Authentication |
| --- | --- | --- |
| POST | `/api/v1/auth/platform-admin/bootstrap` | Public; initial administrator setup |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/companies` | Public |
| PATCH | `/api/v1/companies/{companyId}/activate` | PLATFORM_ADMIN |
| POST | `/api/v1/auth/company-admin/register` | Public |
| POST | `/api/v1/company-users` | COMPANY_ADMIN |
| POST | `/api/v1/api-credentials` | COMPANY_ADMIN |
| DELETE | `/api/v1/api-credentials/{credentialId}` | COMPANY_ADMIN |
| POST | `/api/v1/webhooks/delivery-events` | X-API-Key |
| POST | `/api/v1/delivery-events/{eventId}/evaluate` | OPERATIONS_OFFICER |
| GET | `/api/v1/delivery-events/{eventId}/evaluation` | COMPANY_ADMIN, OPERATIONS_OFFICER, RISK_REVIEWER |
| GET | `/api/v1/delivery-events?status=REVIEW_REQUIRED&page=0&size=20` | COMPANY_ADMIN, OPERATIONS_OFFICER, RISK_REVIEWER |
| POST | `/api/v1/delivery-events/{eventId}/reviews` | RISK_REVIEWER |
| GET | `/api/v1/delivery-events/{eventId}/reviews` | COMPANY_ADMIN, OPERATIONS_OFFICER, RISK_REVIEWER |

## Example workflow

Use new email addresses and a new company code if these examples already exist in your database. Replace placeholder passwords with suitable local values that satisfy validation.

### 1. Bootstrap the platform administrator

`POST /api/v1/auth/platform-admin/bootstrap`

```json
{
  "fullName": "Platform Administrator",
  "email": "platform.admin@example.com",
  "password": "<password with 12 to 72 characters>"
}
```

The bootstrap creates the first platform administrator. An existing administrator prevents repeating initialization.

### 2. Log in

`POST /api/v1/auth/login`

```json
{
  "email": "platform.admin@example.com",
  "password": "<the registered password>"
}
```

Save the returned JWT as a Postman collection variable named `platformAdminToken`. Use `{{platformAdminToken}}` in the Bearer Token field for company activation.

### 3. Register and activate a company

`POST /api/v1/companies`

```json
{
  "name": "Example Logistics",
  "companyCode": "EXAMPLE_LOGISTICS",
  "countryCode": "NG"
}
```

Save the returned company ID. The initial company status is `PENDING`.

Send `PATCH /api/v1/companies/{companyId}/activate` using the platform administrator token and no request body. The company becomes `ACTIVE`.

### 4. Register the company administrator

`POST /api/v1/auth/company-admin/register`

```json
{
  "fullName": "Company Administrator",
  "email": "company.admin@example.com",
  "password": "<password with 8 to 72 characters>",
  "companyCode": "EXAMPLE_LOGISTICS"
}
```

Log in with this account and save its JWT as `companyAdminToken`.

### 5. Create company staff

`POST /api/v1/company-users`, using the company administrator token:

```json
{
  "fullName": "Operations Officer",
  "email": "operations@example.com",
  "password": "<password with 8 to 72 characters>",
  "role": "OPERATIONS_OFFICER"
}
```

Create another account with role `RISK_REVIEWER` and a different email. Log in with each account and save the JWTs as `operationsToken` and `reviewerToken`.

### 6. Create a company API credential

Send `POST /api/v1/api-credentials` with `companyAdminToken` and no body. Save the returned `apiKey` as `companyApiKey` and retain the `credentialId` for revocation.

The raw key is returned at creation; the stored credential contains its hash. Keep tokens and API keys out of source control.

### 7. Submit a delivery event

`POST /api/v1/webhooks/delivery-events`, with no bearer authentication:

```text
X-API-Key: {{companyApiKey}}
Idempotency-Key: example-delivery-001
Content-Type: application/json
```

```json
{
  "externalDeliveryId": "EXAMPLE-DELIVERY-001",
  "eventType": "DELIVERED",
  "eventTimestamp": "2026-10-01T03:00:00Z",
  "riderId": "RIDER-001",
  "customerId": "CUSTOMER-001",
  "deliveryLatitude": 6.5244,
  "deliveryLongitude": 3.3792,
  "expectedLatitude": 6.5244,
  "expectedLongitude": 3.3792,
  "otpVerified": true,
  "proofPhotoHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "failureReason": null,
  "customerContactAttempted": true
}
```

Use the actual event timestamp for your request. The photo hash above is synthetic test data.

A new event returns `201 Created`, status `RECEIVED`, and `idempotentReplay: false`. Repeating the same payload with the same idempotency key returns `200 OK`, the same event ID, and `idempotentReplay: true`. Use a new key for a new event; reusing a key with a changed payload is rejected.

### 8. Evaluate the event

Send `POST /api/v1/delivery-events/{eventId}/evaluate` using `operationsToken`, with no body.

For a fresh photo hash, matching coordinates, and verified OTP, the expected decision is `VERIFIED`, with reason `ALL_EVIDENCE_VERIFIED`, risk score `0`, and recovery action `NO_ACTION`.

Repeated evaluation returns the stored evaluation. Read it with `GET /api/v1/delivery-events/{eventId}/evaluation` using an authorized company account.

### 9. Review a suspicious delivery

Create a second delivered event with a new external delivery ID, idempotency key, and photo hash, but set `otpVerified` to `false`. Evaluation flags `OTP_INVALID` with decision `SUSPICIOUS` and risk score `40`.

Send `POST /api/v1/delivery-events/{eventId}/reviews` using `reviewerToken`:

```json
{
  "decision": "INCONCLUSIVE",
  "recoveryAction": "MANUAL_INVESTIGATION",
  "notes": "OTP verification failed. Further confirmation from the customer and rider is required."
}
```

The event enters `UNDER_REVIEW`. A subsequent final decision of `CONFIRMED_VALID` or `CONFIRMED_SUSPICIOUS` resolves the review. `RESOLVED` indicates that the review reached a final decision; it does not mean an external recovery action has been executed.

## Evaluation behavior

| Delivered-event evidence | Effect |
| --- | --- |
| GPS coordinates missing | GPS_EVIDENCE_MISSING; requires review unless another check makes the event suspicious |
| Distance exceeds configured maximum | GPS_DISTANCE_EXCEEDED; adds 30 risk points |
| OTP evidence missing | OTP_EVIDENCE_MISSING; requires review unless another check makes the event suspicious |
| OTP invalid | OTP_INVALID; adds 40 risk points |
| Photo hash missing | PHOTO_EVIDENCE_MISSING; requires review unless another check makes the event suspicious |
| Photo hash appears on another event in the same company | PHOTO_REUSED; adds 30 risk points |
| All implemented delivered-event checks pass | VERIFIED, ALL_EVIDENCE_VERIFIED, NO_ACTION |

Failed deliveries use failure-reason and customer-contact evidence to produce review decisions and recommendations. A failed delivery is not automatically considered verified merely because those fields are present.

## Tests

Run the complete suite on Windows:

```powershell
.\mvnw.cmd clean test
```

Run the Flyway migration test separately:

```powershell
.\mvnw.cmd "-Dtest=FlywayMigrationTest" test
```

The supplied Compose file starts the development database and Adminer. PostgreSQL integration tests also require a separate test database instance; the development setup used a container named `routeguard-test-db` on host port `5439`.

Test database configuration used during development:

| Database | Username | Password |
| --- | --- | --- |
| routeguard_test_db | routeguard_test_user | routeguard_test_password |
| routeguard_migration_test_db | routeguard_test_user | routeguard_test_password |

For a machine without that test container, create it once:

```powershell
docker run -d --name routeguard-test-db -e POSTGRES_DB=routeguard_test_db -e POSTGRES_USER=routeguard_test_user -e POSTGRES_PASSWORD=routeguard_test_password -p 5439:5432 postgres:17-alpine
```

Wait until `pg_isready` reports that PostgreSQL accepts connections:

```powershell
docker exec routeguard-test-db pg_isready -U routeguard_test_user -d routeguard_test_db
```

Then create the migration-test database once:

```powershell
docker exec routeguard-test-db createdb -U routeguard_test_user routeguard_migration_test_db
```

If the container or database already exists, reuse it. Keep integration tests pointed at their dedicated databases: some tests create and drop schema objects.

The suite covers evidence evaluation, webhook ingestion and concurrent retries, repeated evaluation, company isolation, review behavior, controller authorization and validation, credential revocation, and initial migration application. The full suite passed locally after Flyway adoption.

### Manual verification status

Verified through Postman:

- Platform administrator creation and login.
- Company registration and activation.
- Company administrator and staff creation and login.
- API credential creation.
- Delivery webhook submission and idempotent replay.
- Verified delivery evaluation, repeated evaluation, and evaluation retrieval.
- Invalid-OTP suspicious delivery evaluation.
- Inconclusive risk review transitioning an event to `UNDER_REVIEW`.

Remaining manual checks include final review resolution, review history, filtered event listing, credential revocation, failed-delivery workflows, and negative authorization/validation cases. Automated test coverage and manual verification are separate forms of evidence.

## Database

The initial schema includes seven application tables:

- delivery_companies
- platform_users
- company_api_credentials
- delivery_events
- delivery_evaluations
- evaluation_reason_codes
- risk_reviews

Flyway additionally maintains `flyway_schema_history`.

Database dumps can contain sensitive application data. Keep local backup dumps, temporary schema exports, and startup logs out of Git; keep versioned SQL migrations tracked.

## Known limitations and remaining work

- This is a backend API; a user interface and hosted deployment are not included in the current verified version.
- Decisions rely on evidence supplied by the delivery company. An OTP flag and photo hash do not independently prove that a customer received a parcel.
- Photo reuse checks compare supplied hashes within a company; there is no photo upload or image-content verification workflow.
- Event timestamp staleness and evidence-conflict rules require further implementation or verification before being advertised as supported.
- `DELIVERY_ATTEMPTED` exists as an event type, but evaluator support remains unfinished. The established evaluation flows cover delivered and failed events.
- Recovery actions are recommendations; integrations to execute retries, contact customers, or reassign riders are not implemented.
- Existing legacy events with null payload hashes need a migration strategy before changed-payload detection can be guaranteed for those historical records.
- Production preparation must include environment-specific secrets and database configuration, deliberate protection of public registration/bootstrap flows, and review of exposed health details.

## Author

Qosim Faruq Olamide — Backend Java Developer
