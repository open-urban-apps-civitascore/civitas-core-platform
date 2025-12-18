# Approved License Exceptions

This document tracks packages that were manually approved despite GitLab's license scanner flagging them as "unknown" or unrecognized. This provides an audit trail for license compliance decisions.

## Approval Process

1. When GitLab flags a package with an unknown license, investigate the actual license
2. Verify the license is compatible by our (list of allowed licenses)[https://opencode.de/de/wissen/rechtssichere-nutzung/open-source-lizenzen#2.-Open-Source-Lizenzliste]
3. Add the package to the appropriate section below with justification
4. Get approval from the team and merge the documentation update

---

## Frontend Dependencies

| Package                            | Version | Actual License     | Reason for Approval                                                       | Reference                                                                           |
| ---------------------------------- | ------- | ------------------ | ------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `@img/sharp-wasm32`                | 0.34.3  | Apache-2.0         | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE)                  |
| `@img/sharp-win32-arm64`           | 0.34.3  | Apache-2.0         | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE)                  |
| `@img/sharp-win32-ia32`            | 0.34.3  | Apache-2.0         | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE)                  |
| `@img/sharp-win32-x64`             | 0.34.3  | Apache-2.0         | Platform-specific binary of sharp; license declared in main sharp package | [sharp LICENSE](https://github.com/lovell/sharp/blob/main/LICENSE)                  |
| `@swc/core-darwin-arm64`           | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-darwin-x64`             | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-linux-arm64-gnu`        | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-linux-arm64-musl`       | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-linux-x64-gnu`          | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-linux-x64-musl`         | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-win32-arm64-msvc`       | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-win32-ia32-msvc`        | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@swc/core-win32-x64-msvc`         | \*      | Apache-2.0 AND MIT | Platform-specific binary of SWC; scanner failed to detect license         | [swc LICENSE](https://github.com/swc-project/swc/blob/main/LICENSE)                 |
| `@typescript-eslint/eslint-plugin` | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/parser`        | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/project-serv.` | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/scope-manager` | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/tsconfig-ut..` | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/type-utils`    | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/types`         | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/typescript-e.` | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/utils`         | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@typescript-eslint/visitor-keys`  | \*      | MIT                | TypeScript ESLint tooling; scanner failed to detect license               | [typescript-eslint LICENSE](https://github.com/typescript-eslint/typescript-eslint) |
| `@xyflow/react`                    | \*      | MIT                | React Flow library; scanner failed to detect license                      | [xyflow LICENSE](https://github.com/xyflow/xyflow)                                  |
| `@xyflow/system`                   | \*      | MIT                | React Flow library; scanner failed to detect license                      | [xyflow LICENSE](https://github.com/xyflow/xyflow)                                  |
| `baseline-browser-mapping`         | \*      | Apache-2.0         | Browser compatibility tool; scanner failed to detect license              | [baseline LICENSE](https://github.com/web-platform-dx/baseline-browser-mapping)     |
| `browserslist`                     | \*      | MIT                | Browser compatibility tool; scanner failed to detect license              | [browserslist LICENSE](https://github.com/browserslist/browserslist)                |
| `caniuse-lite`                     | \*      | CC-BY-4.0          | Browser compatibility data; scanner failed to detect license              | [caniuse-lite LICENSE](https://github.com/browserslist/caniuse-lite)                |
| `electron-to-chromium`             | \*      | ISC                | Browser compatibility tool; scanner failed to detect license              | [electron-to-chromium LICENSE](https://github.com/kilian/electron-to-chromium)      |
| `update-browserslist-db`           | \*      | MIT                | Browser compatibility tool; scanner failed to detect license              | [update-browserslist-db LICENSE](https://github.com/browserslist/update-db)         |
| `jose`                             | \*      | MIT                | JWT/JWS/JWE library; scanner failed to detect license                     | [jose LICENSE](https://github.com/panva/jose)                                       |
| `libphonenumber-js`                | \*      | MIT                | Phone number parsing library; scanner failed to detect license            | [libphonenumber-js LICENSE](https://gitlab.com/catamphetamine/libphonenumber-js)    |
| `next-intl`                        | \*      | MIT                | Next.js internationalization; scanner failed to detect license            | [next-intl LICENSE](https://github.com/amannn/next-intl)                            |
| `next-intl-swc-plugin-extractor`   | \*      | MIT                | Next.js internationalization; scanner failed to detect license            | [next-intl LICENSE](https://github.com/amannn/next-intl)                            |
| `use-intl`                         | \*      | MIT                | React internationalization; scanner failed to detect license              | [next-intl LICENSE](https://github.com/amannn/next-intl)                            |

---

## Backend Dependencies

| Package                                                | Version | Actual License                              | Reason for Approval                                                             | Reference                                                                                         |
| ------------------------------------------------------ | ------- | ------------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `com.github.docker-java:docker-java-api`               | 3.4.2   | Apache-2.0                                  | Test dependency via testcontainers; scanner failed to detect license            | [docker-java LICENSE](https://github.com/docker-java/docker-java/blob/main/LICENSE)               |
| `com.github.docker-java:docker-java-transport`         | 3.4.2   | Apache-2.0                                  | Test dependency via testcontainers; scanner failed to detect license            | [docker-java LICENSE](https://github.com/docker-java/docker-java/blob/main/LICENSE)               |
| `com.github.docker-java:docker-java-transport-zerodep` | 3.4.2   | Apache-2.0                                  | Test dependency via testcontainers; scanner failed to detect license            | [docker-java LICENSE](https://github.com/docker-java/docker-java/blob/main/LICENSE)               |
| `org.testcontainers:testcontainers`                    | 1.21.3  | MIT                                         | Scanner failed to detect license                                                | [testcontainers LICENSE](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| `org.testcontainers:testcontainers-junit-jupiter`      | 1.21.3  | MIT                                         | Scanner failed to detect license                                                | [testcontainers LICENSE](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| `org.testcontainers:testcontainers-kafka`              | 1.21.3  | MIT                                         | Scanner failed to detect license                                                | [testcontainers LICENSE](https://github.com/testcontainers/testcontainers-java/blob/main/LICENSE) |
| `org.apache.commons:commons-lang3`                     | 3.17.0  | Apache-2.0                                  | Transitive dependency from specification-arg-resolver; scanner failed to detect | [commons-lang3 LICENSE](https://github.com/apache/commons-lang/blob/master/LICENSE.txt)           |
| `org.hamcrest:hamcrest`                                | \*      | BSD-3-Clause                                | Test dependency; scanner failed to detect license                               | [hamcrest LICENSE](https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE)                  |
| `org.glassfish.jersey.core:jersey-client`              | 3.1.5   | EPL-2.0 OR GPL-2.0-with-classpath-exception | JAX-RS client; scanner failed to detect license                                 | [Jersey LICENSE](https://github.com/eclipse-ee4j/jersey/blob/master/LICENSE.md)                   |
| `org.glassfish.jersey.core:jersey-common`              | 3.1.5   | EPL-2.0 OR GPL-2.0-with-classpath-exception | JAX-RS common; scanner failed to detect license                                 | [Jersey LICENSE](https://github.com/eclipse-ee4j/jersey/blob/master/LICENSE.md)                   |
| `org.glassfish.jersey.ext:jersey-entity-filtering`     | 3.1.5   | EPL-2.0 OR GPL-2.0-with-classpath-exception | JAX-RS entity filtering; scanner failed to detect license                       | [Jersey LICENSE](https://github.com/eclipse-ee4j/jersey/blob/master/LICENSE.md)                   |
| `org.glassfish.jersey.inject:jersey-hk2`               | 3.1.5   | EPL-2.0 OR GPL-2.0-with-classpath-exception | JAX-RS dependency injection; scanner failed to detect license                   | [Jersey LICENSE](https://github.com/eclipse-ee4j/jersey/blob/master/LICENSE.md)                   |
| `org.glassfish.jersey.media:jersey-media-json-jackson` | 3.1.5   | EPL-2.0 OR GPL-2.0-with-classpath-exception | JAX-RS JSON support; scanner failed to detect license                           | [Jersey LICENSE](https://github.com/eclipse-ee4j/jersey/blob/master/LICENSE.md)                   |

---

## Approval History

| Date       | Approved By  | Packages                                                       | Notes                                                                 |
| ---------- | ------------ | -------------------------------------------------------------- | --------------------------------------------------------------------- |
| 2025-12-03 | Patrick Kopp | sharp binaries, docker-java, testcontainers, commons, hamcrest | Initial license audit and approval                                    |
| 2025-12-11 | Patrick Kopp | SWC binaries, typescript-eslint, xyflow, browserslist, etc.    | Added 27 packages flagged by GitLab CI/CD run                         |
| 2025-12-16 | Patrick Kopp | Jersey JAX-RS libraries (jersey-client, jersey-common, etc.)   | Added 5 Jersey packages with EPL-2.0/GPL-2.0 with classpath exception |
