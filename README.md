# Spring Boot 4.1 REST API with Unit and RestAssured Tests

A small Spring Boot REST API with one public and one secured endpoint, plus a **JSON-driven test
framework**: every test case lives in a JSON file per endpoint, and the same files drive

1. **Unit tests** - MockMvc, no server (`ControllerTest`)
2. **Embedded API tests** - RestAssured against the app started on a random port (`ApiIT`)
3. **Remote API tests** - RestAssured against any URL you pass, e.g. a passive deployment (`RemoteApiIT`)

Adding a test case or a new endpoint needs **no Java changes**, only a JSON file.

## Requirements
- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -v`)

## Project layout
```
src/main/java/com/example/demo
  DemoApplication.java
  config/SecurityConfig.java          in-memory users, HTTP Basic, @EnableMethodSecurity
  controller/HealthController.java    GET  /api/health   (public)
  controller/GreetingController.java  POST /api/greeting (@PreAuthorize("hasRole('USER')"))
  model/  service/
src/test/java/com/example/demo
  unit/GreetingServiceTest.java       plain JUnit
  unit/ControllerTest.java            MockMvc, runs every JSON case
  api/ApiIT.java                      RestAssured, embedded app + endpoint coverage check
  api/RemoteApiIT.java                RestAssured, external URL (-Dapi.baseUri)
  support/JsonFiles.java              discovers JSON files/cases (ApiCase)
  support/ApiCalls.java               shared RestAssured request/assert logic, ${...} resolution
src/test/resources
  json/health.json  json/greeting.json   one file per endpoint
  json/schema/                           JSON schemas used by "schema" checks
  junit-platform.properties              enables parallel execution
```

## Endpoints
| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/health` | none | Returns `{"status":"UP"}` |
| `POST` | `/api/greeting` | HTTP Basic + `@PreAuthorize("hasRole('USER')")` | Body `{"name":"World"}` → `{"message":"Hello, World!","requestedBy":"user"}` |

### Users (in-memory, `SecurityConfig.java`)
| Username | Password | Role | `/api/greeting` result |
|---|---|---|---|
| `user` | `password` | `USER` | 200 |
| `guest` | `guest` | `GUEST` | 403 |
| (none / wrong password) | | | 401 |

An empty `name` returns 400.

## Build and run the application
```
mvn clean package                 # compile, unit tests, build target/*.jar
mvn clean package -DskipTests     # build only
mvn spring-boot:run               # or: java -jar target/*.jar
```
The app listens on `http://localhost:8080` (change with
`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090`).

```
curl localhost:8080/api/health
curl -u user:password -H 'Content-Type: application/json' -d '{"name":"World"}' localhost:8080/api/greeting
curl -i -u guest:guest -H 'Content-Type: application/json' -d '{"name":"World"}' localhost:8080/api/greeting   # 403
```

## Running the tests
| Type | Classes | Plugin | Reports |
|---|---|---|---|
| Unit | `**/unit/*Test.java` | Surefire | `target/surefire-reports/` |
| Embedded API | `ApiIT` | Failsafe | `target/failsafe-reports/` |
| Remote API | `RemoteApiIT` (only when `api.baseUri` is set) | Failsafe | `target/failsafe-reports/` |

| Command | Runs |
|---|---|
| `mvn clean verify` | unit + embedded API tests |
| `mvn clean verify -Dapi.baseUri=http://localhost:8080` | **all 3 types** (start the app first) |
| `mvn test` | unit tests only |
| `mvn verify -Punit` | unit tests only (via `verify`) |
| `mvn verify -Papi` | embedded API tests (plus remote if `api.baseUri` is set) |
| `mvn verify -Premote -Dapi.baseUri=<url>` | remote API tests only |
| `mvn test -Dtest=ControllerTest` | one unit test class |
| `mvn verify -Papi -Dit.test=ApiIT` | one API test class |

Test names look like `POST /api/greeting [forbidden]`, so failures point straight at the JSON case.
HTML report: `mvn verify surefire-report:report-only` → `target/reports/surefire.html`.

In IntelliJ: right-click `src/test/java` → *Run 'All Tests'*, or the `unit` / `api` package.

## JSON test data
Each `src/test/resources/json/*.json` file describes one endpoint: an `endpoint` block with
defaults and a `cases` map. Files without both keys (e.g. schemas) are ignored.

Current files:
| File | Endpoint | Cases |
|---|---|---|
| `health.json` | `GET /api/health` | `public` |
| `greeting.json` | `POST /api/greeting` | `success`, `invalid`, `forbidden`, `badCredentials`, `unauthorized` |

### Full example
```json
{
  "endpoint": {
    "method": "PUT",
    "path": "/api/users/{id}",
    "headers": { "Accept": "application/json" }
  },
  "cases": {
    "update": {
      "method": "PATCH",
      "auth": { "type": "bearer", "token": "${API_TOKEN}" },
      "headers": { "X-Correlation-Id": "123" },
      "pathParams": { "id": "42" },
      "queryParams": { "dryRun": "true" },
      "cookies": { "session": "xyz" },
      "contentType": "application/json",
      "body": { "name": "Bob" },
      "response": {
        "status": 200,
        "headers": { "Content-Type": "application/json" },
        "contentType": "application/json",
        "jsonPath": { "id": 42, "name": "Bob" },
        "bodyContains": "Bob",
        "schema": "json/schema/user-schema.json",
        "body": { "id": 42, "name": "Bob" },
        "strict": false
      }
    }
  }
}
```

### Request fields
All optional. Values in `endpoint` are defaults; maps are merged with the case's (case wins),
other fields are replaced by the case.

| Field | Meaning |
|---|---|
| `method` | any HTTP method (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`); default `GET` |
| `path` | URL path, may contain `{placeholders}` filled from `pathParams` |
| `headers`, `queryParams`, `pathParams`, `cookies`, `formParams` | key/value maps |
| `auth` | `{"type":"basic","username","password"}` (default type), `{"type":"bearer","token"}`, `{"type":"apikey","header","key"}` (header defaults to `X-API-Key`) |
| `contentType` | defaults to `application/json` when a JSON body is present |
| `body` (or legacy `request`) | JSON object/array is sent as JSON; a string is sent raw (XML, text, ...) |

### Response fields
| Field | Check |
|---|---|
| `status` | **required**, exact status code |
| `headers` | each header contains the given value |
| `contentType` | content type contains the value |
| `jsonPath` | path → expected value |
| `bodyContains` | body contains the text |
| `schema` | body matches a classpath JSON schema |
| `body` | JSON compare (`strict` defaults to `true`); a string means exact text match |

`ControllerTest` (MockMvc) checks only `status` and `body`; the RestAssured tests check all fields.

### Adding tests
- **New case:** add an entry under `cases` in the endpoint's file.
- **New endpoint:** add a new `json/<name>.json` file. Nothing else to change.

### Endpoint coverage check
`ApiIT.everyEndpointHasJsonFile` lists every `@RequestMapping` endpoint in `com.example` code
(printed as `[json OK]` / `[NO JSON]`) and fails if any endpoint has no JSON case. Method and path
must match the mapping exactly, e.g. `GET /api/users/{id}`; an endpoint without an HTTP method is
covered by any case with its path.

## Testing an external server (passive → test → promote)
`RemoteApiIT` runs the **same JSON cases** against a URL you provide; no application code or
Spring context is started.
```
mvn verify -Premote -Dapi.baseUri=https://passive.example.com
mvn verify -Premote -Dapi.baseUri=https://10.0.0.5:8443 -Dapi.relaxedHttps=true   # self-signed certs
```
Pipeline: deploy to passive → run the command above → promote to active only if Maven exits with
0 → optionally rerun against the active URL as a smoke test.

### Secrets per environment
Auth values, headers, query/path params, cookies and form params may use `${NAME}` or
`${NAME:default}`, resolved from `-DNAME=...` or environment variable `NAME`:
```json
"auth": { "username": "${API_USER:user}", "password": "${API_PASSWORD:password}" }
```
```
API_USER=svc API_PASSWORD=*** mvn verify -Premote -Dapi.baseUri=https://passive.example.com
```
A placeholder without a default and without a value fails the case. The current JSON files use the
local users directly; replace them with placeholders before testing real environments.

## Parallel execution
`src/test/resources/junit-platform.properties` runs all cases of all endpoints in parallel.
`ApiCalls` builds a fresh RestAssured request spec per case (no static config), so it is thread-safe.

## Troubleshooting
- **`NullPointerException` in RestAssured tests:** Spring Boot 4 ships Groovy 5, which RestAssured
  5.x does not support. `pom.xml` pins `groovy.version` to 4.0.28 for this reason.
- **Deprecation warning in `ControllerTest`:** only a warning; see details with
  `-Dmaven.compiler.showDeprecation=true`.
- **Tests don't find JSON files:** discovery reads `src/test/resources/json` from disk (normal for
  Maven/IntelliJ); it does not work from a packaged jar.
- **`RemoteApiIT` shows as skipped:** `-Dapi.baseUri` was not set.
