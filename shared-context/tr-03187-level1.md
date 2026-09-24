<!-- VENDORED from agent-context@4c3d61f9 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- GENERATED from requirements project 74337888 · requirement-source::tr-03187,tr-level::1 minus req-status::rejected · 2026-09-17 · ./generate-tr-03187.sh -->

# TR-03187 — applicable requirements (level 1)

Rejected requirements are excluded; everything listed applies.
Titles and descriptions are verbatim German from the BSI guideline.

## AR

- **AR-1** Geringstmögliche Privilegien
  Interne und externe Zugriffe auf Plattformkomponenten MÜSSEN mit den geringstmöglichen Privilegien erfolgen.
- **AR-2** Legacy-Clienttechnologien vermeiden
  Clientseitige Technologien mit bekannten Sicherheitsschwächen (bspw. NSAPI, Flash, ActiveX oder Java-Applets) DÜRFEN NICHT verwendet werden. Die Sicherheitseigenschaften clientseitiger Technologien SOLLEN dem Stand der Technik entsprechen.
- **AR-3** Nicht verwendete Abhängigkeiten entfernen
  Nicht verwendete direkte Abhängigkeiten, nicht mehr benötigte Softwarekomponenten, Dateien und Dokumentationen MÜSSEN vom Produktivsystem entfernt werden.
- **AR-4** Version Pinning
  Die Version aller Softwarekomponenten SOLL in den jeweiligen Build-Prozessen, sowie in Paketverwaltungen fixiert sein (Version Pinning).
- **AR-5** Sitzungs-IDs zufällig und eindeutig
  Alle durch die UDP erstellten Sitzungs-IDs (Session Identifier) MÜSSEN einzigartig und nicht erratbar sein (z. B. durch Verwendung von Level 4 UUIDs).
- **AR-6** Sitzungsablauf nach Inaktivität
  Sitzungen (Sessions) MÜSSEN nach einem vorher definierten Zeitraum und nach Inaktivität ablaufen.
- **AR-7** Rollenbasierte Benutzerverwaltung
  Die Benutzerverwaltung SOLL gruppen- oder rollenbasiert erfolgen. Jeder Benutzer SOLL mindestens einer definierten Gruppe oder Rolle zugewiesen sein.
- **AR-8** Replay-Schutz
  Sicherheitsrelevante Informationen (insbesondere Information zu Gruppen- und Rollenzugehörigkeiten) MÜSSEN vor Manipulation und Replay-Angriffen (bspw. durch Verwendung von Zeitstempeln) geschützt sein.
- **AR-9** Geheimnisse müssen änderbar sein
  Alle verwendeten Schlüssel, PINs und Passwörter MÜSSEN (mit entsprechender Autorisierung) änderbar sein. Sofern verschlüsselte Daten gespeichert werden, MUSS es einen Prozess zum Schlüsselwechsel geben.
- **AR-10** Geheimnisse von Benutzern dürfen nicht zur Sicherstellung von Vertraulichkeit verwendet werden
  Durch Benutzer oder Clients bereitgestellte Geheimnisse (bspw. Passwörter, PINs, Schlüssel oder API-Tokens) MÜSSEN als unsicher betrachtet werden. Insbesondere DÜRFEN sie NICHT dafür verwendet werden, die Vertraulichkeit von Daten gegenüber Dritten sicherzustellen.
- **AR-11** Kryptografische Verfahren sollen TR-02102-1 entsprechen
  Alle verwendeten kryptographischen Verfahren SOLLEN gemäß TR-02102-1 ausgewählt und verwendet werden.
- **AR-12** Standardframeworks für sicherheitskritische Funktionen
  Für sicherheitskritische Funktionen (bspw. Authentifizierung, Autorisierung, Eingabevalidierung und Signaturprüfungen) SOLLEN etablierte und bewährte Standardfunktionen des verwendeten Frameworks, Betriebssystems oder der Programmiersprache genutzt werden.
- **AR-13** Ganze Zertifikatkette validieren
  Bei der Validierung von Zertifikaten (z. B. für TLS-Verbindungen) MUSS stets die ganze Zertifikatskette sowie alle relevanten Attribute (bspw. Gültigkeitszeitraum und „Common Name“) geprüft werden. Bekannte „Revocation Lists“ MÜSSEN und OCSP-Responder SOLLEN berücksichtigt werden.
- **AR-14** Alle vorgesehenen Sicherheitsmechanismen defaultmäßig anschalten
  Alle in der UDP verfügbaren Sicherheitsmechanismen SOLLEN standardmäßig aktiviert sein.
- **AR-15** Verschlüsselte Kommunikation
  Kommunikation zwischen Plattformkomponenten MUSS verschlüsselt, integritätsgeschützt und mindestens einseitig authentifiziert erfolgen (z. B. TLS).
- **AR-16** Abschirmung unterschiedlicher Vertrauenszonen
  Plattformkomponenten in unterschiedlichen Vertrauenszonen MÜSSEN voneinander (z. B. durch Firewall- Regeln, API-Gateways, Reverse Proxies, Security-Gruppen oder ähnliche Mechanismen) isoliert sein.
- **AR-17** Integritätsgeschützter Kanal für Deployment
  Für das Deployment von Softwarekomponenten MUSS ein authentifizierter und integritätsgeschützter Kanal verwendet werden. Die Integrität der Softwareartefakte selbst SOLL durch Signaturen sichergestellt werden.

## AUT

- **AUT-1** Einheitliche Vertrauensniveaus in der Authentifizierung
  Die UDP MUSS einheitliche Vertrauensniveaus für alle Wege und Schnittstellen, über die Benutzer authentifiziert werden können, festlegen und anbieten. Insbesondere DARF es NICHT die Möglichkeit geben, auf ein schwächeres Authentisierungsverfahren zu wechseln.
- **AUT-2** Multi-Faktor-Authentisierung
  Die UDP MUSS für alle passwortbasierten Benutzer-Authentisierungen Multi-Faktor- Authentisierung anbieten.
- **AUT-3** Dedizierte Serviceaccounts
  Für die Ausführung aller Softwarekomponenten und Dienste SOLLEN dedizierte Service-Accounts verwendet werden.
- **AUT-4** Kryptografisch abgesicherter Zugriff auf Infrastruktur
  Für die Authentisierung, Authentifizierung und Autorisierung von Infrastrukturadministratoren MUSS ein Public-Key-basiertes Authentisierungsverfahren verwendet werden.
- **AUT-5** Zugriffskontrollmodell definieren
  Es MUSS ein Zugriffskontrollmodell definiert werden, das festlegt, welche Benutzer, Gruppen und Rollen Zugriff auf welche Daten haben. Das gilt auch für die Administration der Infrastruktur.
- **AUT-6** Sitzungs-IDs invalidieren
  Eine Anwendung MUSS es dem Nutzer ermöglichen, einen oder alle zuvor ausgestellten Sitzungs-IDs (Session Identifier) bzw. Authentisierungstokens zu invalidieren.
- **AUT-7** Passwortrichtlinien
  Bei einer Authentisierung mit einem Passwort MÜSSEN starke Passwortrichtlinien existieren. Dabei DÜRFEN 25 Zeichen mit geringer Komplexität (z. B. Passphrases) oder 8 Zeichen mit hoher Komplexität NICHT unterschritten werden. Informationen über die Stärke des gewählten Passworts DÜRFEN NICHT gespeichert werden.

## CT

- **CT-1** Container-Hardening
  Container-Images DÜRFEN NICHT mehr als den benötigten Anwendungscode und die benötigten Abhängigkeiten enthalten.
- **CT-2** Werkzeuggestützte Prüfung auf Container-Sicherheitslücken
  Es SOLLEN Security-Werkzeuge (z. B. Clair, Trivy, Docker Security Scanning, etc.) verwendet werden, um Container-Images auf Sicherheitslücken zu prüfen.
- **CT-3** Basisimages aus vertrauenswürdigen Quellen
  Es SOLLEN ausschließlich Basis-Images aus Quellen verwendet werden, die vom Hersteller bereitgestellt oder von ihm referenziert werden und für die regelmäßig und zeitnah Sicherheitsaktualisierungen zur Verfügung gestellt werden.
- **CT-4** Minimalistische Basisimages
  Es SOLLEN minimalistische Basis-Images (z. B. Alpine Linux und Windows Nano Server) verwendet werden, um Angriffsflächen zu reduzieren.
- **CT-5** Geheimnisse zur Laufzeit dynamisch bereitstellen
  Geheimnisse MÜSSEN außerhalb von Container-Images gespeichert und zur Laufzeit dynamisch bereitgestellt werden.
- **CT-8** Geringstmögliche Privilegien für Container-Images
  Container-Images MÜSSEN so konfiguriert sein, dass sie mit den geringstmöglichen Privilegien betrieben werden können. Insbesondere DARF die Ausführung als „root“-Benutzer standardmäßig NICHT aktiviert sein.
- **CT-9** Transportverschlüsselung & Integrität zwischen Pods
  Für die Kommunikation zwischen Pods MUSS eine verschlüsselte und integritätsgeschützte Kommunikation (z. B. TLS) verwendet werden (vgl. auch AR-15).

## DH

- **DH-2** Schutz geheimer Schlüssel
  Jeder geheime Schlüssel in der UDP MUSS vor unbefugtem Zugriff geschützt werden.
- **DH-3** IPC-Schnittstellen müssen geschützt sein
  Schnittstellen zu Interprozesskommunikations-(IPC)- Mechanismen MÜSSEN durch Zugriffskontrollmechanismen vor unbefugtem Zugriff geschützt werden.
- **DH-4** Data Governance-Vorgaben etablieren
  Es MUSS Vorgaben zur „Data Governance“ geben, durch die insbesondere der Lebenszyklus von in der UDP gespeicherten Daten vorgegeben wird.

## L

- **L-1** Logging von Sicherheitsereignissen
  Alle Komponenten und Dienste der UDP MÜSSEN Log-Informationen zu Sicherheitsereignissen erstellen.
- **L-2** Logging von fehlgeschlagenen Authentisierungsvorgängen und allen Authorisierungvorgängen
  Logs von Sicherheitsereignissen MÜSSEN mindestens alle fehlgeschlagene Authentisierungsversuche und Autorisierungsvorgänge enthalten.
- **L-3** Schutz von Logs vor unbefugtem Zugriff
  Alle Log-Informationen MÜSSEN vor Manipulationen und vor unbefugtem Zugriff geschützt werden.
- **L-4** Sensible Informationen nicht loggen
  Sensible Daten (bspw. Passwörter oder Kreditkarteninformationen) DÜRFEN NICHT geloggt werden.

## ORG

- **ORG-6** Schwachstellenmanagement
  Es MUSS ein Verfahren zur Verwaltung von Schwachstellen implementiert werden. Dazu MUSS insbesondere festgelegt werden, wie Informationen über bekannte Schwachstellen in Komponenten, die von der UDP eingesetzt werden, erfasst werden und wie auf diese Informationen in angemessener Zeit reagiert werden kann.
- **ORG-8** Penetrationstests
  Die UDP SOLL regelmäßig Penetrationstests unterzogen werden.

## W

- **W-1** Deny All als Default
  Der Zugriff auf alle Ressourcen der UDP SOLL standardmäßig verweigert werden, außer für diejenigen, die als öffentliche Ressourcen vorgesehen sind.
- **W-2** Zugriffskontrollmodell definieren
  Es MUSS ein Zugriffskontrollmodell definiert werden, das festlegt, welche Benutzer, Gruppen und Rollen Zugriff auf welche Daten haben dürfen (vgl. AUT-5).
- **W-3** Einheitlicher Zugriffskontrollmechanismus
  Das Zugriffskontrollmodell MUSS für alle Daten durch einen einheitlichen Zugriffskontrollmechanismus durchgesetzt werden.
- **W-4** Restriktive CORS-Policy
  Die Nutzung von Cross-Origin Ressource Sharing (CORS) SOLL vermieden werden. Die Applikation SOLL eine dem Anwendungsfall entsprechend restriktive CORS-Policy implementieren.
- **W-5** Web-Server-Verzeichnisauflistung deaktivieren
  Die Web-Server-Verzeichnisauflistung SOLL deaktiviert werden.
- **W-6** Nicht benötigte Datei-Metadaten und Sicherungskopien vermeiden
  Es MUSS sichergestellt werden, dass nicht benötigte Datei-Metadaten sowie Sicherungskopien aus dem Verzeichnis, das durch den Web-Server bereitgestellt wird, bereinigt werden.
- **W-7** Sitzungs-IDs nach Logout invalidieren
  Sitzungs-IDs (Session Identifier) MÜSSEN nach dem Abmelden eines Benutzers auf dem Backend-System als ungültig markiert werden.
- **W-8** Kurzlebige JWTs
  JSON-Web-Token (JWT) SOLLEN kurzlebig sein, um dieses Angriffsfenster zu minimieren.
- **W-9** Sichere DB-Zugriffe (ORM/Prepared)
  Die Web-Anwendung MUSS für Datenverarbeitung und den Datenzugriff sichere Schnittstellen und Verfahren (z. B. Prepared Statements oder ORM für SQL- Abfragen) verwenden.
- **W-10** Vom Benutzer übergebene Objekte defensiv deserialisieren
  Die Verwendung von Funktionen, die Benutzereingaben automatisch in Codevariablen, interne Objekte oder Objekteigenschaften einbinden (Mass Assignment) SOLL vermieden werden.
- **W-11** Clientseitige Eingabevalidierung
  Alle vom Client bereitgestellten Eingabewerte auf allen Feldern, Formularen und APIs MÜSSEN auf Seite des Clients validiert werden, um Systemmanipulationen und Fehler an der UDP zu verhindern.
- **W-12** Serverseitige Eingabevalidierung
  Eine serverseitige Eingabevalidierung MUSS zusätzlich zur clientseitigen Eingabevalidierung verwendet werden, um Systemmanipulationen und Fehler an der UDP (bspw. durch Injektionsangriffe) zu verhindern.
- **W-13** Limitierung Anzahl Ergebnisse Datenbankabfragen
  Die Anzahl der Ergebnisse von Datenbankabfragen SOLL limitiert werden (z. B. durch das Schlüsselwort LIMIT in SQL), um im Falle von Injektionen die massenhafte Offenlegung von Datensätzen zu verhindern. Ebenso SOLLEN nur Datenbankspalten abgefragt werden, die von der Applikation auch verarbeitet werden.
- **W-14** Standardzugangsdaten ändern
  Vorkonfigurierte Standardzugangsdaten MÜSSEN vor produktiver Nutzung einer Softwarekomponente geändert werden.
- **W-15** Whitelisting für Eingabedaten
  Bei der Deserialisierung von vom Client bereitgestellten Daten SOLLEN Whitelists verwendet werden, um die Objekte und Klassen zu beschränken, die aus den bereitgestellten Daten instanziiert werden können.
- **W-16** Whitelisting für URLs in Eingabedaten
  Das URL-Schema, die Adresse und der Port in den vom Client bereitgestellten URLs SOLL mithilfe einer Whitelist abgeglichen werden, um die Auswirkungen von Server- Side Requests Forgeries (SSRF) zu reduzieren.
- **W-18** Detaillierte Fehlermeldungen Produktivsystemen vermeiden
  Detaillierte Fehlermeldungen aus Produktivsystemen DÜRFEN Benutzern NICHT angezeigt werden.
- **W-19** Minimale Auskunft über Komponenten
  Detaillierte Informationen zu einem in der UDP eingesetzten Dienst, die für einen Angreifer hilfreich sein könnten (bspw. Versionsinformationen oder der Name des Dienstes) SOLLEN Benutzern NICHT angezeigt werden.
- **W-20** Nicht benötigte HTTP-Verben deaktivieren
  Die Verarbeitung aller nicht benötigten HTTP-Verben (z. B. PUT oder DELETE) MÜSSEN deaktiviert werden.
- **W-21** Keine sensiblen Daten in URLs
  Sensible Daten MÜSSEN entweder im HTTP-Body oder in den HTTP-Headern übertragen werden. Insbesondere DÜRFEN sensible Daten NICHT Teil der URL sein.
- **W-22** Clientseitiges Caching und Autocomplete abschalten
  Bei allen Formularen, die vertrauliche Informationen enthalten können, SOLL die clientseitige Zwischenspeicherung deaktiviert werden, einschließlich der Funktionen zur automatischen Vervollständigung (bspw. bei JavaScript oder HTML5 das „autocomplete” Attribute auf „off“ setzen).
