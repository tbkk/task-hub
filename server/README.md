# Task Hub Server

Java 17 backend shared by the mini program and admin frontend.

## Stack

- Java 17
- Spring Boot 3.5.6
- MyBatis Spring Boot Starter 3.0.5
- Maven 3.6.3 or newer
- MySQL Connector/J 9.4.0
- H2 is test-scoped only

Dependency versions are pinned in `pom.xml`. Spring Boot's dependency management pins the versions of its starter transitive dependencies.

## Configuration

| Environment variable | Default |
| --- | --- |
| `MYSQL_HOST` | `localhost` |
| `MYSQL_PORT` | `3306` |
| `MYSQL_DATABASE` | `task_hub` |
| `MYSQL_USER` | `taskhub` |
| `MYSQL_PASSWORD` | empty |
| `SERVER_PORT` | `8080` |

No real database credential is stored in the repository. Hikari is configured for lazy connection initialization, so the web service can start while MySQL is unavailable. Database state is reported by readiness instead.

## Run

```bash
mvn spring-boot:run
```

Or build and run the executable jar:

```bash
mvn clean package
java -jar target/task-hub-server-0.0.1-SNAPSHOT.jar
```

Example with an explicit database password:

```bash
MYSQL_PASSWORD=change-me java -jar target/task-hub-server-0.0.1-SNAPSHOT.jar
```

## System endpoints

`GET /api/health` does not access the database:

```json
{"code":0,"message":"ok","data":{"status":"UP"}}
```

`GET /api/ready` reads `application_metadata.meta_value` through MyBatis for `meta_key = 'schema_version'`. A successful response includes `status: READY` and `schemaVersion`. A missing row or database failure returns HTTP 503 without exception or connection details:

```json
{"code":503,"message":"service unavailable","data":null}
```

All other endpoints are denied by Spring Security and return HTTP 403:

```json
{"code":403,"message":"forbidden","data":null}
```

There is no generated user, fake login, or business endpoint. Boxed and primitive Java `long` values are globally serialized as JSON strings to preserve JavaScript integer precision.

## Verification

Run:

```bash
mvn test
mvn clean package
```

Initialization verification on 2026-09-20 used Java 17.0.18 and Maven 3.6.3. The contract suite ran 6 tests covering health, H2-backed MyBatis readiness, unavailable readiness, default GET/POST denial, database-free application startup, and boxed/primitive `long` serialization. Both commands completed with zero failures and zero errors.

Docker-based MySQL integration was not run because Docker is unavailable in the current environment. The readiness SQL itself is exercised against H2 in MySQL compatibility mode.
