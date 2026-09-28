<!-- VENDORED from agent-context@4c3d61f9 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Architecture/Architecture_General/Authorization_Data_Model.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/Authorization_Data_Model -->

# Authorization Data Model

The **Authorization Data Model** provides a high-level overview of the core building blocks of the CIVITAS/CORE's authorization system.

**Table of Contents**

- [Authorization Data Model](#)
  - [Core Concepts](#core-concepts)
    - [A `Tenant` (Isolated Environment)](#a-tenant-isolated-environment)
    - [The `User` and `Groups` (Who?)](#the-user-and-groups-who)
    - [`Permission` and `Role` (With What?)](#permission-and-role-with-what)
    - [The Data (`DataSet` and `DataPool`) (For What?)](#the-data-dataset-and-datapool-for-what)
    - [Role-`Assignment` (The Link Between Them)](#role-assignment-the-link-between-them)
  - [Concepts in Detail](#concepts-in-detail)
    - [Permissions](#permissions)
    - [Roles](#roles)
    - [Users](#users)
    - [Groups of Users](#groups-of-users)
    - [Binary Assignment: Permissions to Roles](#binary-assignment-permissions-to-roles)
      - [Overview](#overview)
      - [Standard Role Definitions](#standard-role-definitions)
    - [Binary Assignment: System Roles and Groups](#binary-assignment-system-roles-and-groups)
    - [Ternary Assignment: Data/Governance Roles and Groups Across Levels](#ternary-assignment-datagovernance-roles-and-groups-across-levels)
      - [Dataset level](#dataset-level)
      - [DataPool level](#datapool-level)
      - [(Data) Platform level](#data-platform-level)

## Core Concepts

![Authorization_Data_Model](https://docs.core.civitasconnect.digital/docs_v2/Architecture/Architecture_General/img/AuthDataModel.drawio.svg)

| Concept           | German       | Description                                                                | Example                                                          |
| ----------------- | ------------ | -------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| **Tenant**        | Mandant      | Isolated organizational unit                                               | City of Berlin                                                   |
| **User**          | Benutzer     | Individual within a tenant                                                 | Alice Smith                                                      |
| **Group**         | Gruppe       | Groups of Users within a tenant                                            | Data Analytics Unit                                              |
| **Permission**    | Berechtigung | Smallest atomic authorization                                              | Read Data                                                        |
| **Role**          | Rolle        | Group of permissions                                                       | Data Analyst                                                     |
| **DataSet**       | Datensatz    | Protected data product                                                     | Traffic Statistics 2024                                          |
| **DataSource**    | Datenquelle  | Source of Data related to a specific connector.                            | Traffic Statistics 2024 (CSV)                                    |
| **DataPool (will be implemented in v2.1)**      | Datenpool    | Logical container for datasets                                             | Traffic Data Domain                                              |
| **DataCatalogue** | Datenkatalog | organizational container for dataset for their (semi-)public documentation | Open Data of the City of Münster                                 |
| **Tag**           | Tag          | Descriptive feature of a dataset/datasource/datastructure                  | KRITIS-relevant                                                  |
| **Assignment**    | Zuordnung    | Mapping defining who gets what where                                       | Alice has Role "Data Analyst" in DataPool "Traffic Data Domain" |

### A `Tenant` (Isolated Environment)

A **Tenant** represents a fully isolated environment for a specific organization.
All users, roles, and access rules exist securely within their tenant. What happens in one tenant does **not** affect any other.

Each tenant owns its elements — users, roles, datasets, etc. — as integral components (technically modeled as _composition_).
In the diagram, this relationship is reflected by the tenant being defined as a property within related entities.

### The `User` and `Groups` (Who?)

A `User` represents an individual who accesses the system.
Each user strictly belongs to one tenant.
In order to assign a permission/role to a user, he/she must be assigned to a `Group`.

### `Permission` and `Role` (With What?)

- A **`Permission`** is the smallest atomic unit of authorization — like a single key, e.g., "read data" or "delete entry."
- A **`Role`** is a collection of permissions — comparable to a keyring. It groups all permissions required for a particular job or task.

Managing roles rather than individual permissions makes administration much more efficient.

### The Data (`DataSet` and `DataPool`) (For What?)

- A **`DataSet`** represents a concrete data product that needs protection, such as "Traffic Statistics 2024."
- A **`DataPool`** is a broader logical domain grouping multiple related datasets, such as "All Traffic Data."

### Role-`Assignment` (The Link Between Them)

At the heart of the model lies the **`Assignment`**, which acts as a central mapping or rulebook defining access control.
Each assignment explicitly answers:

> **Who** (`Group`) gets **which Role** (`Role`) for **which Scope** (`Scope`)?

The **Scope** defines the context in which the role applies — this could be:

- the entire tenant (system-level permissions),
- a `DataPool`, or
- a specific `DataSet`.

This flexible scoping mechanism allows very fine-grained access control.

## Concepts in Detail

### Permissions

Permissions have an attribute `category` that is used to group them. This is especially useful for system-level permissions.
There are **two types of permissions**:

1. **System Permissions**

   - Apply to system-administrative operations.
   - Cannot be modified by users.
   - Some are instance-level (e.g., creating a new tenant), others tenant-level.
   - Also apply on client level.

2. **Data Permissions**

   - Enable access to data sets, data structures, and data sources.
   - Access can be defined to the definition of these enitities (metadata level) or the content of these entities (payload level)
   - Cannot be modified by users.
   - Include standard data permissions.
   - Also available on client level.

### Roles

Roles are grouped according to the type of permissions they contain:

- **System Roles** — contain system permissions
- **Data Roles** — contain data permissions
  - A role contains one or multiple permissions.
  - Can be created by users.
  - Are assigned at dataset, data pool, or platform level.

Each role consists of one or more permissions and can also be created by users.

**Default Roles**

The system comes with **default roles** based on predefined permissions:

- Purpose: ensure the system is _ready to start_ and reduce effort in role creation.
- Users can use them but are not required to.
- Default roles are **not modifiable**.
- Default roles have the attribute `readonly` set to `true`.

### Users

- A user can have one or multiple system roles.
- Each new user automatically receives the default role **"Standard User"** after registration.

### Groups of Users

- All users in a group implicitly inherit the group’s system roles.
- A group can have one or multiple system roles.
- **Groups are the only mean to assign a role (and thus permission) to a user**.

### Binary Assignment: Permissions to Roles

#### Overview

Permissions are assigned to **roles** to enable platform access.
The CIVITAS/CORE platform defines its **own permission model** that is mapped to the respective permissions of each application within the platform (e.g. Superset, Grafana). This will be transparent to the user.

The following figure shows the assignment of system permissions to roles as an example:

<img src={require("./img/AuthDataModel_system_permissions.png").default} style={{"max-width": "800px"}} width="800px"/>

The following figure shows the assignment of data permissions to roles as an example. Usually, data permissions are predefined in the respective applications, e.g. FROST comes with a predefined set of data permissions: . The CIVITAS/CORE platform will transparently map its own permissions model to these predefined application permissions.

<img src={require("./img/AuthDataModel_data_permissions.png").default} style={{"max-width": "700px"}} width="700px"/>

#### Standard Role Definitions

| Name            | Scope             | Main Objectives                                                                                                                                                                                                                                                                                                          | Rights                                                                                                                                                                                                                | Typical Role in Administration                                                                                 |
| --------------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Platform Admin  | System            | Configuration of tenants. System monitoring with the aim to identify logical, stability-related or resource-related problems on the platform. Working closely with the operations staff of the K8 cluster to ensure that the platform is stable and has enough resources.                                                | Management rights for the configuration of tenants and basic system parameters. Monitoring permissions of all platform system information.                                                                            | IT personnel                                                                                                   |
| Tenant Admin    | System            | User, role and permissions management. Configuration of tenant-wide system parameters.                                                                                                                                                                                                                                   | Tenant-wide permissions to manage users, roles and permissions as well as tenant parameters.                                                                                                                          | IT personnel or technical specialist in cross-domain department (e.g. geoinformation or smart city department) |
| Data Architect  | Data              | Definition of guidance on the structuring and administration of data on the tenant. Definition of commonly used data models and metadata. Administration of data pools. Administration of data sources, data structures, transformation pipelines, metadata and APIs. Works closely with all other roles on the tenant. | Management rights for all aspects of data ingestion, data processing, data storage and data output.                                                                                                                   | Technical or data specialist in cross-domain department (e.g. geoinformation or smart city department)         |
| Data Consumer   | Data              | Consumption of data. Use of metadata catalog.                                                                                                                                                                                                                                                                            | Authenticated or non-authenticated user on the platform. If authenticated, user can access all data he/she has permissions to in his/her roles. If not authenticated, user can access open data and public functions. | Everybody                                                                                                      |
| Data Steward    | Data              | Management of domain specific data concepts within a data pool. Application of data and metadata standards models created by the data architect. Creation, update and deletion of data sets.                                                                                                                            | Responsibility for the life-cycle of domain specific data in a data pool.                                                                                                                                            | Department employee, data / domain specialist                                                                  |
| Data Owner      | Data + Governance | Definition of data guidelines and cooperation in data governance definition. Release authorization of new data sets.                                                                                                                                                                                                     | Business responsibility for one or several domains.                                                                                                                                                                   | Chief Officer or Department Head                                                                               |
| Data Gatekeeper | Data + Governance | Definition of data governance models and processes on the tenant. Administration of data governance models. Release authorization of data sets that are affected by privacy concerns.                                                                                                                                    | Responsibility for data protection and data governance                                                                                                                                                                | Chief Data Officer (CDO), Data protection officer                                                              |

#### Standard Role Permissions (Data Scope)

- 🟢 Data Architect
- 🟣 Data Consumer
- 🟠 Data Steward
- 🔵 Data Owner
- 🟤 Data Gatekeeper

| Permissions / Objects | EXISTS     | READ       | CREATE | UPDATE | DELETE | RELEASE | USE    |
| --------------------- | ---------- | ---------- | ------ | ------ | ------ | ------- | ------ |
| **DataSet**           | 🔵🟠🟢🟣🟤 | 🔵🟠🟢🟣🟤 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟤    | 🔵🟠🟢 |
| ↳ Payload             | -          | 🔵🟠🟣🟤   | 🔵🟠   | 🔵🟠   | 🔵🟠   | -       | -      |
| **DataSource**        | 🔵🟠🟢🟤   | 🔵🟠🟢🟤   | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟤    | 🔵🟠🟢 |
| **DataStructure**     | 🔵🟠🟢🟤   | 🔵🟠🟢🟤   | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟠🟢 | 🔵🟤    | 🔵🟠🟢 |
| **DataPool**         | 🔵🟠🟢🟣🟤 | 🔵🟠🟢🟣🟤 | 🟢     | 🔵🟠🟢 | 🟢     | -       | -      |
| **DataCatalogue**     | 🔵🟠🟢🟣🟤 | 🔵🟠🟢🟣🟤 | 🟢     | 🔵🟠🟢 | 🟢     | -       | -      |
| **Tag**               | 🔵🟠🟢🟣🟤 | 🔵🟠🟢🟣🟤 | 🟢🟤   | 🟢🟤   | 🟢🟤   | -       | -      |

#### Available Scopes for Objects

| Permissions / Objects | EXISTS                     | READ                       | CREATE            | UPDATE                     | DELETE                     | RELEASE                    | USE                        |
| --------------------- | -------------------------- | -------------------------- | ----------------- | -------------------------- | -------------------------- | -------------------------- | -------------------------- |
| **DataSet**           | tenant, dataPool, dataSet | tenant, dataPool, dataSet | tenant, dataPool | tenant, dataPool, dataSet | tenant, dataPool, dataSet | tenant, dataPool, dataSet | tenant, dataPool, dataSet |
| **DataSource**        | tenant, dataSource         | tenant, dataSource         | tenant            | tenant, dataSource         | tenant, dataSource         | tenant, dataSource         | tenant, dataSource         |
| **DataStructure**     | tenant, dataStructure      | tenant, dataStructure      | tenant            | tenant, dataStructure      | tenant, dataStructure      | tenant, dataStructure      | tenant, dataStructure      |
| **DataPool**          | tenant, dataPool          | tenant, dataPool          | tenant            | tenant, dataPool          | tenant, dataPool          | tenant, dataPool          | tenant, dataPool          |
| **DataCatalogue**     | tenant, dataCatalogue      | tenant, dataCatalogue      | tenant            | tenant, dataCatalogue      | tenant, dataCatalogue      | tenant, dataCatalogue      | tenant, dataCatalogue      |
| **Tag**               | tenant                     | tenant                     | tenant            | tenant                     | tenant                     | tenant                     | tenant                     |

### Binary Assignment: System Roles and Groups

System roles are assigned to **groups** to enable platform access.
This is a binary assignment, i.e. a group is directly assigned a system role.

<img src={require("./img/AuthDataModel_user_group_system_role.png").default} style={{"max-width": "700px"}} width="700px"/>

- A system role consists of multiple permissions.
- It can be created by users.
- Some are provided by the system by default.
- Control administrative and platform-level functions.

### Ternary Assignment: Data Roles and Groups Across Levels

Data and governance roles can be assigned to **groups** on multiple levels.
Important: This is a **ternary assignment**, i.e. a group is assigned a data or governance role **in respect to** a specific dataset, data pool, or platform level.

#### Dataset level

- Each newly created dataset receives a **default role** (e.g., "Dataset Owner").
- Role sets or templates can be loaded at the dataset level and assigned to groups.
- Role sets from a data pool can also be applied to datasets for efficiency and finer-grained control.

<img src={require("./img/AuthDataModel_ternary_dataset.png").default} style={{"max-width": "900px"}} width="900px"/>

#### DataPool level

- Each newly created data pool has a **default role**.
- Role sets or templates can be loaded and assigned at the data pool level.
- Role assignments at the data pool level are **inherited** by all datasets within the pool.
- Data pools can overlap but do not have to.

<img src={require("./img/AuthDataModel_ternary_datapool.png").default} style={{"max-width": "900px"}} width="900px"/>

#### (Data) Platform level

- Roles or templates can be loaded and assigned at the platform level.
- Enables efficient and global management of roles across the entire platform.
- Assignments at this level **inherit** all data and governance roles to all datasets across all data pools.

<img src={require("./img/AuthDataModel_ternary_platform.png").default} style={{"max-width": "900px"}} width="900px"/>

This allows differentiated role assignment depending on topics, departments, and data responsibilities.
