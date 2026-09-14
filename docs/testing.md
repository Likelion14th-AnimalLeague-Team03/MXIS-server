# Backend tests

Use JDK 17. The Gradle wrapper is included in the repository.

```sh
./gradlew clean test
```

The default suite runs unit tests and MVC tests without MariaDB or external AI services.

## MariaDB integration tests

Create a dedicated, empty MariaDB database and an account that can create and alter tables in it.
Integration tests apply the actual Flyway migrations and validate the entire JPA schema before
running repository round trips. Do not point this command at a shared or production database.

```sh
TEST_DB_URL='jdbc:mariadb://127.0.0.1:3306/mxis_test' \
TEST_DB_USERNAME='mxis_test' \
TEST_DB_PASSWORD='mxis_test' \
./gradlew integrationTest
```

`integrationTest` requires `TEST_DB_URL` explicitly and never falls back to the application
database. Its tests run on every invocation. Repository tests roll back their data; concurrency
and worker tests commit isolated fixtures and explicitly delete them after each test.
The migrated schema remains available for inspection.

The GitHub Actions workflow runs both suites against an isolated MariaDB 11.4 service.
Reports are generated in `build/reports/tests/test/` and `build/reports/tests/integrationTest/`.
