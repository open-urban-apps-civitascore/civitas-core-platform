# Approved License Exceptions

This document tracks packages that were manually approved despite GitLab's license scanner flagging them as "unknown" or unrecognized. This provides an audit trail for license compliance decisions.

## Approval Process

1. When GitLab flags a package with an unknown license, investigate the actual license
2. Verify the license is compatible with our project
3. Add the package to the appropriate section below with justification
4. Get approval from the team and merge the documentation update

---

## Frontend Dependencies

| Package                  | Version | Actual License | Reason for Approval                                                       | Reference                                                          |
| ------------------------ | ------- | -------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `@img/sharp-wasm32`      | 0.34.3  | Apache-2.0     | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE) |
| `@img/sharp-win32-arm64` | 0.34.3  | Apache-2.0     | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE) |
| `@img/sharp-win32-ia32`  | 0.34.3  | Apache-2.0     | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE) |
| `@img/sharp-win32-x64`   | 0.34.3  | Apache-2.0     | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE) |

---

## Backend Dependencies

| Package                                                | Version | Actual License | Reason for Approval                                                             | Reference                                                                                         |
| ------------------------------------------------------ | ------- | -------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `com.github.docker-java:docker-java-api`               | 3.4.2   | Apache-2.0     | Test dependency via testcontainers; scanner failed to detect license            | [docker-java LICENSE](https://github.com/docker-java/docker-java/blob/main/LICENSE)               |
| `com.github.docker-java:docker-java-transport`         | 3.4.2   | Apache-2.0     | Test dependency via testcontainers; scanner failed to detect license            | [docker-java LICENSE](https://github.com/docker-java/docker-java/blob/main/LICENSE)               |
| `com.github.docker-java:docker-java-transport-zerodep` | 3.4.2   | Apache-2.0     | Test dependency via testcontainers; scanner failed to detect license            | [docker-java LICENSE](https://github.com/docker-java/docker-java/blob/main/LICENSE)               |
| `org.testcontainers:testcontainers`                    | 1.21.3  | MIT            | Scanner failed to detect license                                                | [testcontainers LICENSE](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| `org.testcontainers:testcontainers-junit-jupiter`      | 1.21.3  | MIT            | Scanner failed to detect license                                                | [testcontainers LICENSE](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| `org.testcontainers:testcontainers-kafka`              | 1.21.3  | MIT            | Scanner failed to detect license                                                | [testcontainers LICENSE](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| `org.apache.commons:commons-lang3`                     | 3.17.0  | Apache-2.0     | Transitive dependency from specification-arg-resolver; scanner failed to detect | [commons-lang3 LICENSE](https://github.com/apache/commons-lang/blob/master/LICENSE.txt)           |
| `org.hamcrest:hamcrest`                                | \*      | BSD-3-Clause   | Test dependency; scanner failed to detect license                               | [hamcrest LICENSE](https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE)                  |

---

## Approval History

| Date       | Approved By  | Packages                  | Notes                              |
| ---------- | ------------ | ------------------------- | ---------------------------------- |
| 2025-12-03 | Patrick Kopp | All packages listed above | Initial license audit and approval |
