<!-- VENDORED from agent-context@42d8321b — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@9ba48deb · version next · Architecture/platform-concepts/authorization-data-model.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/next/Architecture/platform-concepts/authorization-data-model -->

---
sidebar_position: 1
---
# Authorization Data Model

The **Authorization Data Model** provides a high-level overview of the core building blocks of the CIVITAS/CORE's authorization system.

## Core Concepts

![Authorization_Data_Model](https://docs.core.civitasconnect.digital/docs_v2/next/Architecture/platform-concepts/img/AuthDataModel_update.drawio.svg)

| Concept           | German       | Description                                                                | Example                                                          |
| ----------------- | ------------ | -------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| **Platform**        | Plattform      | Represents the entire Tenant as an authorization scope                       | City of Berlin                                                   |
| **User**          | User     | Individual within a Tenant                                                 | Alice Smith                                                      |
| **Group**         | Gruppe       | Groups of Users within a Tenant                                            | Data Analytics Unit                                              |
| **Permission**    | Berechtigung | Smallest atomic authorization                                              | Read Data                                                        |
| **Role**          | Rolle        | Group of permissions                                                       | Data Analyst                                                     |
| **Scope**    | Geltungsbereich    | Specific area or element for which access can be controlled                   | DataPool "Traffic Data Domain" |
| **Assignment**    | Zuordnung    | Mapping defining who gets what where                                       | Alice has Role "Data Architect" in DataPool "Traffic Data Domain" |
| **DataSet**       | Datensatz    | Protected data product                                                     | Traffic Statistics 2026                                          |
| **DataSource**    | Datenquelle  | Source of Data related to a specific connector.                            | Traffic Statistics 2026 (CSV)                                    |
| **DataStructure**    | Datenstruktur  | Schema or data model describing the structure of data.                            | Traffic Measurement (CSV)                                    |
| **DataPool**      | Datenpool    | Logical container for datasets                                             | Traffic Data Domain                                              |

### A `Tenant` (Isolated Environment)

A **Tenant** represents a fully isolated environment for a specific organization.
All Users, Roles, and access rules exist securely within their Tenant. What happens in one Tenant does **not** affect any other.

Each Tenant owns its elements — Users, Roles, Datasets, etc. — as integral components (technically modeled as _composition_).
In the diagram, this relationship is reflected by the Tenant being defined as a property within related entities.

> In the authorization model, access that applies to the entire Tenant is represented by the **Platform Scope**.

### The `User` and `Groups` (Who?)

A `User` represents an individual who accesses the system.
Each User strictly belongs to one Tenant.
In order to assign a Role to a User, he/she must be assigned to a `Group`.

### `Permission` and `Role` (With What?)

A **`Permission`** is the smallest atomic unit of authorization, like a single key, e.g., "read Dataset" or "delete Data source".
A **`Role`** is a collection of Permissions, comparable to a keyring. It groups all Permissions required for a particular job or task.

Managing Roles rather than individual Permissions makes administration much more efficient.

### `Scope` (For What?)

A **Scope** defines an area or element within the Platform for which access can be controlled.

CIVITAS/CORE supports the following Scopes:

- **Platform**: representing the entire Tenant
- **Data pool**: a logical grouping of related Datasets, such as "All Traffic Data"
- **Dataset**: a specific data product that needs protection, such as "Traffic Statistics 2026"
- **Data source**: a specific source of data
- **Data structure**: a specific schema or data model

Scopes allow access to be defined at different levels, from the entire Platform down to individual data-related elements.

### `Assignment` (The Link Between Them)

At the heart of the model lies the **`Assignment`**, which acts as a central mapping or rulebook defining access control.
Each assignment explicitly answers:

>  **Who** (`Group`) gets **which Role** (`Role`) **for what** (`Scope`)?


**Examples of different Scope levels**
> The Group `Data Analytics Unit` is assigned the Role `Data Architect` for the Dataset `Traffic Statistics 2026`. The resulting Permissions apply **only to this Dataset**.

> The Group `Data Analytics Unit` is assigned the Role `Data Architect` for the `Platform`.  The resulting Permissions apply to all data-related elements **across the Platform**.


This flexible scoping mechanism allows very fine-grained or Platform-wide access control.

## Concepts in Detail

### Permissions

Permissions have an attribute `category` that is used to group them. This is especially useful for system-level permissions.
There are **two types of permissions**:

1. **System Permissions**

   - Apply to system-administrative operations.
   - Cannot be modified by Users.
   - Some are instance-level (e.g., creating a new tenant), others tenant-level.
   - Also apply on client level.

2. **Data Permissions**

   - Enable access to Datasets, Data structures, and Data sources.
   - Access can be defined to the definition of these enitities (metadata level) or the content of these entities (payload level)
   - Cannot be modified by Users.
   - Include standard data permissions.
   - Also available on client level.

### Roles

Roles are grouped according to the type of permissions they contain:

- **System Roles**: contain system permissions
- **Data Roles**: contain data permissions
  - A Role contains one or multiple permissions.
  - Can be created by Users.
  - Are assigned at Data structures, Data sources, Dataset, Data pool, or platform level.

**Default Roles**

The system comes with **default Roles** based on predefined permissions:

- Purpose: ensure the system is _ready to start_ and reduce effort in Role creation.
- Users can use them but are not required to.
- Default Roles are **not modifiable**.
- Default Roles have the attribute `readonly` set to `true`.

### Users

- A User can receive multiple System and Data Roles through their Group memberships.

### Groups

- All Users in a Group implicitly inherit the Group’s System or Data Roles.
- A Group can have one or multiple System and Data Roles.
- **Groups are the only mean to assign a Role (and thus Permission) to a User**.

### Binary Assignment: Permissions to Roles

#### Overview

Permissions are assigned to **Roles** to enable platform access.
The CIVITAS/CORE platform defines its **own permission model** that is mapped to the respective permissions of each application within the platform (e.g. Superset, Grafana).

Usually, data permissions are predefined in the respective applications, e.g. FROST comes with a predefined set of data permissions. The CIVITAS/CORE platform will transparently map its own permissions model to these predefined application permissions.

#### Standard Role Definitions

| Name            | Role type             | Main Objectives                                                                                                                                                                                                                                                                                                          | Rights                                                                                                                                                                                                                | Typical Role in Administration                                                                                 |
| --------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| **Platform Admin**  | System            | Configuration of tenants. System monitoring with the aim to identify logical, stability-related or resource-related problems on the platform. Working closely with the operations staff of the K8 cluster to ensure that the platform is stable and has enough resources.                                                | Management rights for the configuration of tenants and basic system parameters. Monitoring permissions of all platform system information.                                                                            | IT personnel                                                                                                   |
| **Tenant Admin**    | System            | User, Role and Permissions management. Configuration of tenant-wide system parameters.                                                                                                                                                                                                                                   | Tenant-wide permissions to manage Users, Roles and Permissions as well as tenant parameters.                                                                                                                          | IT personnel or technical specialist in cross-domain department (e.g. geoinformation or smart city department) |
| **Data Architect**  | Data              | Definition of guidance on the structuring and administration of data on the tenant. Definition of commonly used data models and metadata. Administration of Data pools. Administration of Data sources, Data structures, transformation pipelines, metadata and APIs. Works closely with all other Roles on the Tenant. | Management rights for all aspects of data ingestion, data processing, data storage and data output.                                                                                                                   | Technical or data specialist in cross-domain department (e.g. geoinformation or smart city department)         |
| **Data Consumer**   | Data              | Consumption of data.                                                                                                                                                                                                                       | Authenticated or non-authenticated User on the platform. If authenticated, User can access all data he/she has permissions to in his/her Roles. If not authenticated, User can access open data and public functions. | Everybody                                                                                                      |
| **Data Steward**    | Data              | Management of domain specific data concepts within a Data pool. Application of data and metadata standards models created by the Data Architect. Creation, update and deletion of Datasets.                                                                                                                            | Responsibility for the life-cycle of domain specific data in a Data pool.                                                                                                                                            | Department employee, data / domain specialist                                                                  |
| **Data Owner**      | Data | Definition of data guidelines and cooperation in data governance definition. Release authorization of new Datasets.                                                                                                                                                                                                     | Business responsibility for one or several domains.                                                                                                                                                                   | Chief Officer or Department Head                                                                               |
| **Data Gatekeeper** | Data | Definition of data governance models and processes on the tenant. Administration of data governance models. Release authorization of Datasets that are affected by privacy concerns.                                                                                                                                    | Responsibility for data protection and data governance                                                                                                                                                                | Chief Data Officer (CDO), Data protection officer    |

#### Standard System Role Permissions
System Permissions control administrative operations on the Platform. They are grouped by administrative area.

**Tenant Administration**
| Object | Operations |
| ------ | ---------- |
| Assignment | Create, Read, Delete |
| Group | Create, Read, Update, Delete |
| Permission | Read |
| Role | Create, Read, Update, Delete |
| User | Create, Read, Update, Delete |


#### Standard Data Role Permissions

- 🟢 Data Architect
- 🟣 Data Consumer
- 🟠 Data Steward
- 🔵 Data Owner
- 🟤 Data Gatekeeper

| Permissions / Objects |  READ       | CREATE | UPDATE | DELETE | RELEASE |
| --------------------- | ---------- | ---------- | ------ | ------ | ------ |
| **DataSet**           |   🔵🟠🟢🟣🟤 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟤    |
| ↳ Payload             |   🔵🟠🟣🟤   | 🔵🟠   | 🔵🟠   | 🔵🟠   | -       |
| **DataSource**        |   🔵🟠🟢🟤   | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟤    |
| **DataStructure**     |   🔵🟠🟢🟤   | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟤    |
| **DataPool**          |  🔵🟠🟢🟣🟤 | 🟢     | 🔵🟠🟢 | 🟢     | -       |

#### Available Scopes for Data-related elements

| Permissions / Objects | READ                       | CREATE            | UPDATE                     | DELETE                     | RELEASE                    |
| --------------------- | -------------------------- | -------------------------- | ----------------- | -------------------------- | -------------------------- |
| **DataSet**           | Platform, DataPool, DataSet | Platform, DataPool | Platform, DataPool, DataSet | Platform, DataPool, DataSet | Platform, DataPool, DataSet |
| **DataSource**        |  Platform, DataSource         | Platform            | Platform, DataSource         | Platform, DataSource         | Platform, DataSource         |
| **DataStructure**     |  Platform, DataStructure      | Platform            | Platform, DataStructure      | Platform, DataStructure      | Platform, DataStructure      |
| **DataPool**          |  Platform, DataPool          | Platform            | Platform, DataPool          | Platform, DataPool          | Platform, DataPool          |

### Binary Assignment: System Roles and Groups

System Roles are assigned to **Groups** to enable platform access.
This is a binary assignment, i.e. a Group is directly assigned a System Role. The Platform Scope is implicit, as System Roles always apply Platform-wide.

- A System Role consists of multiple permissions.
- It can be created by Users.
- Some are provided by the system by default.
- Control administrative and platform-level functions.

### Ternary Assignment: Data Roles and Groups Across Levels

Data Roles can be assigned to **Groups** on multiple levels.
Important: This is a **ternary assignment**, i.e. a Group is assigned a Data Role **in respect to** a specific Dataset, Data pool, Data structure, Data source, or platform level.

#### (Data) Platform level

- Roles can be assigned at the platform level.
- Enables efficient and global management of Roles across the entire platform.
- Role-Group Assignments defined at Platform level apply to all data-related elements across the Platform.

#### Data pool level

- Role-Group Assignments defined at the Data pool level can be inherited by the Datasets within the Data pool.
- This allows common access rules to be managed centrally while individual Datasets can have additional Assignments where needed.

#### Dataset level

- Role-Group Assignments can be defined directly for a Dataset.
- If the Dataset belongs to a Data pool, it can also inherit Role-Group Assignments from the Data pool.
- Inherited Assignments can be extended by additional Assignments defined specifically for the Dataset.
- A Dataset does not have to belong to a Data pool.

#### Data source level

- Role-Group Assignments can be defined directly for a Data source.
- Access to a Data source is required to use it in a Pipeline within a Dataset.

#### Data structure level

- Role-Group Assignments can be defined directly for a Data structure.
- Access to a Data structure is required to use it within other data-related elements, such as Data sources or Datasets.
- Access defined for a Data structure applies to all of its versions.

This allows differentiated role assignment depending on topics, departments, and data responsibilities.
