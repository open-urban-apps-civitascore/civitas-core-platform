<!-- VENDORED from agent-context@4c3d61f9 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Architecture/Architecture_General/Security_Architecture_Principles.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/Security_Architecture_Principles -->

# Security Architecture Principles

## As derived from TR-03187

Technische Richtlinie TR-03187 by the Bundesamt für Sicherheits in der Informationstechnik (BSI) gives a number of specific requirements that are relevant for the (component) architectural level.

The following table spells out their implications. Quotes from the TR are intentionally left in their German original, as there is no authoritative translation available from the BSI.

In particular:



### AR-1

**Description:** Interne und externe Zugriffe auf Plattformkomponenten MÜSSEN mit den geringstmöglichen Privilegien erfolgen.

**Level:** 1

**MUST/SHOULD:** MUST

Whenever access to data happens in an authorized (i.e. non-public) manner, design the interface in such a way that it's possible to assign authorizations that are sufficiently granular for the presumed use cases. Also, always document which authorizations are required for particular functions of an application, in order to avoid convenience-driven overpermissioning. When accessing other components/systems, do so with the minimum authorizations necessary for your use case.

### AR-2

**Description:** Clientseitige Technologien mit bekannten Sicherheitsschwächen (bspw. NSAPI, Flash, ActiveX oder Java-Applets) DÜRFEN NICHT verwendet werden. Die Sicherheitseigenschaften clientseitiger Technologien SOLLEN dem Stand der Technik entsprechen.

**Level:** 1

**MUST/SHOULD:** SHOULD

Do not use legacy client technologies, either for CIVITAS/CORE components or 3rd party components. Checks for this are implemented through the pipeline for CIVITAS/CORE custom front end code ([Implementation Ticket](https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/issues/517))

### AR-10

**Description:** Durch Benutzer oder Clients bereitgestellte Geheimnisse (bspw. Passwörter, PINs, Schlüssel oder API-Tokens) MÜSSEN als unsicher betrachtet werden. Insbesondere DÜRFEN sie NICHT dafür verwendet werden, die Vertraulichkeit von Daten gegenüber Dritten sicherzustellen.

**Level:** 1

**MUST/SHOULD:** MUST

Experience shows that users sometimes do not follow password hygiene recommendations. Also, user controlled input is generally to be considered untrusted.

An example of an applicable scenario would be using a user-supplied password to encrypt an email.

Treat this requirement as a warning to carefully evaluate the security properties of any user supplied secret, especially in cases where other users' data is involved.

### AR-12

**Description:** Für sicherheitskritische Funktionen (bspw. Authentifizierung, Authentisierung, Autorisierung, Eingabevalidierung und Signaturprüfungen) SOLLEN etablierte und bewährte Standardfunktionen des verwendeten Frameworks, Betriebssystems oder der Programmiersprache genutzt werden.

**Level:** 1

**MUST/SHOULD:** SHOULD

When implementing new functionality, check whether the component touches any of the areas mentioned in the requirement. For any of these, use battle hardened components. In particular, never implement any cryptography yourself, as doing that requires much deeper and specialized knowledge than even experienced developers have.


### AR-14

**Description:** Alle in der UDP verfügbaren Sicherheitsmechanismen SOLLEN standardmäßig aktiviert sein.

**Level:** 1

**MUST/SHOULD:** SHOULD

Use secure-by-default when implementing components, i.e. whenever there is any kind of configurable security measure (authorizations, gateways, allowlists etc.) configure them to the securest default that is still usable in a real-world scenario.

An example: even when a component can technically be deployed without any permissions assigned to it, a default admin authorization should be applied so that initial setup is possible. Use common sense while erring on the side of security.

### AR-16

**Description:** Plattformkomponenten in unterschiedlichen Vertrauenszonen MÜSSEN voneinander (z. B. durch Firewall-Regeln, API-Gateways, Reverse Proxies, Security-Gruppen oder ähnliche Mechanismen) isoliert sein.

**Level:** 1

**MUST/SHOULD:** MUST

Define security domains and ensure they are adequately shielded from each other. A good rule of thumb for a security domain is whether data with different protection requirements, strongly different attack surface or different administrators is/are present.

### ORG-7

**Description:** Tests und Weiterentwicklungen der UDP DÜRFEN NICHT direkt auf Produktivumgebungen durchgeführt werden. Stattdessen MUSS es eine dedizierte Staging-Umgebung geben.

**Level:** 1

**MUST/SHOULD:** MUST

In the context of this project, this means that no production data may be used in dev/staging/testing environments.
