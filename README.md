# SMS API

Spring Boot backend for the SMS employee and salary management application. The
Angular frontend is maintained separately in `SMS-UI/sms-ui`.

## Features and stack

- BCrypt password verification and JWT authentication.
- Departments with employee counts and paginated employee lists.
- Create, update, and delete employees together with their salary structures.
- Salary breakdowns embedded in employee responses.
- Average salary reports grouped by department or country, with headcounts and
  counts of missing salaries.
- Database-backed health check at `/health`.

The backend uses Java 21, Spring Boot 4.1.2, Spring Security, Spring Data JPA,
PostgreSQL, Bean Validation, and Lombok. Controllers return DTOs. Services use
constructor dependency injection and transactions for database operations.

## Local setup

Requirements: JDK 21, PostgreSQL, and a running Docker daemon for integration
tests. Set `JAVA_HOME` to your JDK 21 installation and check the runtime Maven
actually uses:

```powershell
.\mvnw.cmd -v
```

Run commands from this directory. The Maven wrapper supplies Maven. Create a
PostgreSQL database named `SMS`, or configure a different database through
`DB_URL`. Hibernate updates development tables at startup but does not create
the database itself.

Set connection overrides and a signing key in the same terminal before starting
the API. For example, in PowerShell 7:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/SMS'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'your-local-database-password'
$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
.\mvnw.cmd spring-boot:run
```

The API listens at `http://localhost:8080`. Start the frontend separately at
`http://localhost:4200`. Keep the signing key stable when tokens should survive
restarts. Development currently has a public fallback key; the production
profile requires an explicit private `JWT_SECRET`.

| Variable | Development default / purpose |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/SMS` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200`; comma-separated exact origins |
| `JWT_SECRET` | Base64-encoded signing key containing at least 32 random bytes |

These settings are read from the process environment. Spring Boot does not
automatically load the repository's `.env` files.

### Accounts and sample data

Login uses the application's `users` table with BCrypt password hashes. There is
no signup endpoint or automatically created account. Supabase Auth accounts do
not create SMS application users. See the first-login instructions in
[the deployment guide](docs/deployment.md#create-the-first-login); local users
belong in the local `users` table rather than the production `sms.users` table.

After development tables exist, optional synthetic employee data can be loaded:

```powershell
psql -h localhost -U postgres -d SMS -v ON_ERROR_STOP=1 -f scripts/seed.sql
```

The script requires empty `department`, `employee`, and `salary_structure`
tables and refuses to run if they contain data. It does not create login users.

## API

Except for login and health checks, requests require
`Authorization: Bearer <token>`.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| `POST` | `/auth/login` | Authenticate with `{ "name": "...", "password": "..." }` |
| `GET` | `/health` | Query the database and return `{ "status": "UP" }` on success |
| `GET` | `/departments` | Return `id`, `name`, and `totalEmployees` for each department |
| `GET` | `/employees?dep_id=1&page=0&size=20` | Return department employees in ID order |
| `POST` | `/employees` | Create an employee and salary structure; return HTTP 201 |
| `PUT` | `/employees/{id}` | Update the employee and salary structure atomically |
| `DELETE` | `/employees/{id}` | Delete the salary structure and employee atomically |
| `GET` | `/reports/salary-distribution?groupBy=department` | Return overall and grouped salary averages |

Pagination is zero-based. `page` defaults to `0`, `size` defaults to `20`, and
allowed sizes are `1` through `100`. Employee responses are arrays; department
employee counts provide the UI's page totals.

### Employee writes

Create and update requests use this shape:

```json
{
  "name": "Alice",
  "departmentId": 1,
  "country": "India",
  "salaryStructureDto": {
    "fixedPay": "10000.10",
    "variablePay": "2000.25",
    "bonuses": "500.30",
    "deduction": "750.45"
  }
}
```

The department must exist. Omit `id` and `salaryStructureDto.empId` from write
requests; the backend assigns or retains them. The backend calculates
`salary = fixedPay + variablePay + bonuses - deduction`. An optional supplied
`salary` must match that calculation.

Monetary inputs must be nonnegative, have at most two decimal places and 36
integer digits, and produce a nonnegative total within the supported range.
Decimal strings are accepted and used by the UI to preserve precision on writes.
Employee response DTOs include `id`, `name`, `departmentId`, `salary`, `country`,
and `salaryStructureDto`, whose `empId` identifies its employee. There is no
separate `/salary-structures` endpoint or `/employees/filter` endpoint.

### Authentication and reports

Successful login returns `authenticated`, `name`, `loginTime`, and `token`.
Invalid credentials return HTTP 401. Tokens expire after 30 minutes by default.
The API is stateless; frontend logout clears its in-memory session. See
[JWT authentication](docs/jwt-authentication.md) for validation details.

Reports accept `groupBy=department` (the default) or `groupBy=country`, returning
`averageSalary`, `employeeCount`, `missingSalaryCount`, and `groups`. Missing
salaries are excluded from averages but included in headcounts. Recorded zero
salaries are included. An average is `null` when no salaries are recorded.
Amounts are used as stored, without currency or pay-period conversion.

## Tests and builds

Use JDK 21. Integration tests start isolated PostgreSQL 17 containers through
Testcontainers and require Docker.

```powershell
# Full suite; clean removes compiled tests left after source changes.
.\mvnw.cmd clean test

# Focused service unit tests
.\mvnw.cmd '-Dtest=org.project.sms.service.*Tests' test

# Production-profile integration test
.\mvnw.cmd -Dtest=ProductionDeploymentIntegrationTests test

# Build the executable JAR and run the tests
.\mvnw.cmd package
```

Reports are written to `target/surefire-reports`. Run the built application with
`java -jar target/SMS-0.0.1-SNAPSHOT.jar`, using the same environment settings.
The latest verified full suite passed 73 tests with no failures, errors, or
skips on November 2, 2026.

## Deployment

The deployment layout uses separate Vercel projects for the Spring Boot container
and static Angular frontend, with Supabase PostgreSQL. See
[the deployment guide](docs/deployment.md) for schema setup, accounts, imports,
runtime settings, and checks.

The container activates `prod`, which uses `SUPABASE_DB_URL`,
`SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET`, and
`CORS_ALLOWED_ORIGINS`. It selects the `sms` schema and validates rather than
updates it. Apply `db/migrations/V001__initial_schema.sql` explicitly to a new
Supabase database before deployment; migrations are not run automatically.
`.env.example` lists runtime settings, including the optional per-instance
`DB_POOL_MAX_SIZE`.

## Project layout

```text
src/main/java/org/project/sms/
  config/       Security, JWT, and password encoder configuration
  controller/   HTTP endpoints
  dto/          Request/response data and validation
  entity/       JPA entities
  repository/   Database access and reporting queries
  security/     JWT request authentication
  service/      Business logic and transactions
src/main/resources/  Development and production configuration
src/test/            Unit and PostgreSQL integration tests
db/migrations/       Explicit production schema scripts
scripts/             Optional local sample-data script
docs/                Deployment and authentication guides
```
