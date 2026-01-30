# Aufgabe: Extraktion von portal-model als eigenständiges Modul

**Priorität**: Mittel
**Aufwand**: ~2 Stunden
**Risiko**: Niedrig (rein strukturelle Änderung)

## Hintergrund

Das AuthZ-Repository (`authz/repository`) benötigt Zugriff auf die JPA-Entities (User, Group, Role, Permission, Assignment). Aktuell sind diese in `portal-backend` definiert. Um Code-Duplizierung zu vermeiden, schlagen wir vor, die gemeinsamen Model-Klassen in ein separates Modul zu extrahieren.

## Vorteile

- **Single Source of Truth**: Entities werden nur einmal definiert
- **Keine Duplizierung**: AuthZ-Repository nutzt dieselben Klassen
- **Automatische Synchronisation**: Schema-Änderungen gelten für beide Services
- **Saubere Architektur**: Klare Trennung von Model und Business-Logik

## Scope

### Zu verschiebende Dateien (21 Stück)

```
portal-model/src/main/java/de/civitascore/portal/model/
├── embedded/                          # 5 Dateien
│   ├── AssignmentType.java
│   ├── PermissionType.java
│   ├── RoleType.java
│   ├── ScopeType.java
│   └── UserTitleType.java
└── entity/                            # 16 Dateien
    ├── base/
    │   ├── BaseEntity.java
    │   ├── NamedEntity.java
    │   └── ScopedEntity.java
    ├── Activity.java
    ├── Agent.java
    ├── Assignment.java
    ├── Catalog.java
    ├── DataSet.java
    ├── DataSetSeries.java
    ├── DataSpace.java
    ├── Distribution.java
    ├── Group.java
    ├── Permission.java
    ├── Resource.java
    ├── Role.java
    └── User.java
```

**Wichtig**: Die Package-Namen bleiben unverändert (`de.civitascore.portal.model.*`), daher sind **keine Import-Änderungen** im bestehenden Code erforderlich.

## Durchführung

### Schritt 1: Neues Modul erstellen

Erstelle `portal-model/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>de.civitascore</groupId>
    <artifactId>portal-model</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Portal Model</name>
    <description>Gemeinsame JPA-Entities für CIVITAS CORE Platform</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- JPA API -->
        <dependency>
            <groupId>jakarta.persistence</groupId>
            <artifactId>jakarta.persistence-api</artifactId>
        </dependency>

        <!-- Validation API -->
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>

        <!-- Spring Data JPA (für Audit-Annotations) -->
        <dependency>
            <groupId>org.springframework.data</groupId>
            <artifactId>spring-data-jpa</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

### Schritt 2: Dateien verschieben

```bash
# Verzeichnisstruktur erstellen
mkdir -p portal-model/src/main/java/de/civitascore/portal/model

# Dateien verschieben (keine Kopie!)
mv portal-backend/src/main/java/de/civitascore/portal/model/embedded \
   portal-model/src/main/java/de/civitascore/portal/model/

mv portal-backend/src/main/java/de/civitascore/portal/model/entity \
   portal-model/src/main/java/de/civitascore/portal/model/
```

### Schritt 3: portal-backend pom.xml aktualisieren

Dependency hinzufügen:

```xml
<dependency>
    <groupId>de.civitascore</groupId>
    <artifactId>portal-model</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Schritt 4: Parent pom.xml aktualisieren (falls vorhanden)

Modul hinzufügen:

```xml
<modules>
    <module>portal-model</module>
    <module>portal-backend</module>
    <!-- andere Module -->
</modules>
```

### Schritt 5: Testen

```bash
cd portal-backend
mvn clean test
```

Alle bestehenden Tests sollten ohne Änderungen durchlaufen.

## Was NICHT verschoben wird

- `model/input/` (DTOs für API-Eingaben) → bleiben in portal-backend
- `model/output/` (DTOs für API-Ausgaben) → bleiben in portal-backend

Diese sind spezifisch für die REST-API und nicht relevant für andere Services.

## Nach Abschluss

Bitte Bescheid geben, dann können wir im AuthZ-Repository die Dependency hinzufügen:

```xml
<dependency>
    <groupId>de.civitascore</groupId>
    <artifactId>portal-model</artifactId>
    <scope>provided</scope>
</dependency>
```

Damit können wir unsere duplizierten Entity-Klassen löschen (~200 LoC weniger).

## Ansprechpartner

Bei Fragen: AuthZ-Team (Infosec)

---

*Erstellt: 2026-01-30*
