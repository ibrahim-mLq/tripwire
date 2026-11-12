# Tripwire

A fraud triage and refund case management system. Customers submit refund requests through a public form. Each request is risk scored and lands in a queue, where fraud analysts pick up cases, investigate them and approve or deny the refund.

Built by a team of five as the final project of the MThree software development programme, working in Agile sprints. I owned the backend: the database design, the data layer, the REST API and wiring it to the front end.

**Stack:** Java 21, Spring Boot 3.4 (Web, JDBC), Spring Security Crypto, H2, Maven, JUnit 5, Mockito. The front end is plain HTML, CSS and JavaScript served by the same app.

## How it works

```
refund form ──> POST /refund/submit ──> account + case created ──> risk score
                                                                       │
analyst dashboard <── queue of cases with their scores <───────────────┘
       │
       └─> assign to me ──> investigate ──> approve / deny
```

### Risk scoring

Every case gets a score from 0 to 100, taken as the higher of two sources:

- **External check.** The customer's email, phone, IP address and IBAN are sent to the Dymo verification API, which flags things like fraudulent email addresses and domains.
- **Local rules.** The app runs its own checks on the same details, such as a disposable email domain or a phone number from a different country to the bank account.

Taking the higher of the two means the system still scores cases when the external API is down or not configured, and a local red flag can't be hidden by a clean API result. Scoring lives in [`DymoService`](src/main/java/com/teamcrocodile/tripwire/service/DymoService.java).

### Case lifecycle

A case moves through a fixed set of states, enforced in [`TransactionServiceImpl`](src/main/java/com/teamcrocodile/tripwire/service/TransactionServiceImpl.java):

```
UNASSIGNED ──assign──> UNDER_REVIEW ──decide──> APPROVED or DENIED (final)
     ^                      │
     └──────unassign────────┘
```

- A case must be assigned before it can be decided.
- Only an unassigned case can be assigned, so two analysts can't take the same case.
- An approved or denied case is final. It can't be reassigned, reopened or changed.
- A decision can only change the status and the analyst's note. The refund amount can't be edited.

### Access control

There are two roles: analysts work cases, and admins also manage the team.

- Logging in creates a server-side session. Every API route under `/agent`, `/transaction` and `/account` needs one, and returns 401 without it.
- The server works out who is calling from the session. It never trusts an id sent in the request.
- Admin-only: viewing the team, creating and removing analysts, deleting cases.
- Analysts can only assign cases to themselves, only the assigned analyst can decide a case, and an agent can only change their own password and profile.
- Passwords set through the app are stored hashed with bcrypt, and the hash is never included in any API response.
- The refund form is public. It returns only a case reference, never the risk score.

## Running it

You need Java 21 and Maven.

```bash
mvn spring-boot:run
```

Then open http://localhost:8080.

| Page | URL |
|---|---|
| Analyst login | http://localhost:8080/login.html |
| Customer refund form | http://localhost:8080/refund.html |

The database is in-memory H2 and is rebuilt from `schema.sql` and `data.sql` on every start, with sample cases and one admin account:

```
email:    sarah.okafor@tripwire.internal
password: tripwire123
```

This login is demo seed data only, which is why its password sits in `data.sql` in plain form.

To use the external check, set a Dymo API token before starting. Without it the app uses local rules only.

```bash
export DYMO_API_TOKEN=your-token
```

## API

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/refund/submit` | public | Submit a refund request |
| POST | `/agent/login` | public | Log in, starts a session |
| POST | `/auth/logout` | anyone | End the session |
| GET | `/transaction/transactions` | agent | All cases |
| GET | `/transaction/{id}` | agent | One case |
| GET | `/transaction/agent/{agentId}` | agent | Cases assigned to an agent |
| GET | `/transaction/account/{accountId}` | agent | A customer's refund history |
| POST | `/transaction/{id}/score` | agent | Re-run risk scoring |
| PUT | `/transaction/{id}/assign/{agentId}` | agent (self) or admin | Take a case |
| PUT | `/transaction/{id}/unassign/{agentId}` | agent (self) or admin | Hand a case back |
| PUT | `/transaction/{id}` | assigned agent or admin | Record a decision or note |
| DELETE | `/transaction/{id}` | admin | Delete a case |
| GET | `/account/{id}` | agent | Customer account details |
| GET | `/agent/team` | admin | List the team |
| POST | `/agent/admin/create` | admin | Create an analyst |
| DELETE | `/agent/admin/{id}` | admin | Remove an analyst |
| PUT | `/agent/{id}/password` | that agent | Change password |
| PUT | `/agent/{id}/profile-picture` | that agent | Change profile picture |

Errors come back as JSON with the right status: 400 invalid input, 401 not logged in, 403 not allowed, 404 not found, 409 not valid for the case's current state.

## Tests

```bash
mvn test
```

37 tests:

- **DAO tests** run the real SQL against H2.
- **Service tests** use Mockito to check the case lifecycle rules and password handling.
- **API security tests** ([`ApiSecurityTest`](src/test/java/com/teamcrocodile/tripwire/controller/ApiSecurityTest.java)) go through the HTTP layer and check who can do what: no access without login, analysts blocked from admin routes, finalised cases can't be reopened, password hashes never leak.

## Project layout

```
src/main/java/com/teamcrocodile/tripwire/
  controller/   REST endpoints
  service/      business rules: scoring, case lifecycle, accounts
  dao/          SQL through Spring JDBC, with row mappers
  model/        Account, Agent, Transaction, Status, AgentRole
  client/       HTTP client for the Dymo API
  security/     session check for API routes
  exception/    maps exceptions to HTTP error responses
src/main/resources/
  schema.sql, data.sql   database schema and sample data
  static/                front-end pages
```

## Known limitations

This is a training project, not a production system.

- H2 in-memory database, so data resets on restart. Moving to PostgreSQL would mainly mean changing the datasource config.
- Sessions are held in memory on one server.
- No CSRF tokens. A real deployment would add Spring Security for this.
- The client IP is taken from the connection. Behind a reverse proxy, `server.forward-headers-strategy` needs setting so the real address comes through from a trusted proxy.
- No rate limiting on login or the refund form.
