# MXIS-server

Backend server for the Smart Charm luxury care service.

- [API specification](api-spec.md)
- [Diagnosis and query refactoring](docs/backend-refactoring.md)
- [Sensor ingestion and durable diagnosis jobs](docs/sensor-diagnosis-pipeline.md)
- [Unit and MariaDB integration tests](docs/testing.md)

Java 17 / Spring Boot 3.3 / MariaDB / Flyway.

```sh
./gradlew clean test bootJar
```
