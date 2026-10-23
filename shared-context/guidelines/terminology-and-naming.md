<!-- VENDORED from agent-context@b924326c — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@6847d4f0 · version next · Development/development-guidelines/terminology-and-naming.md
     Raw:    https://gitlab.com/civitas-connect/civitas-core/documentation/-/raw/6847d4f0ad9bda2133fcff49b3048e4f571966f6/docs_v2/Development/development-guidelines/terminology-and-naming.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/next/Development/development-guidelines/terminology-and-naming -->

---
sidebar_position: 1
---

# Terminology & Conventions

## Purpose

This guide defines the official user-facing terminology and writing conventions for CIVITAS/CORE.

It serves as the **single source of truth** to ensure consistent, clear, and understandable language across the Platform.

## Scope

The conventions apply to all user-facing language, including:

- User Interface (UI)
- User and technical documentation
- Issues and acceptance criteria
- Release notes
- Community discussions
- AI-generated user-facing content

> This guide is intended for Product Owners, Requirements Engineers, Developers, UX/UI Designers, Contributors, and AI Assistants.

**It covers terminology, action labels, and UI writing conventions.**

**Out of scope:**
Source code identifiers, database schemas, and internal architecture.


## 1. Core principles

### 1.1 Use domain terminology

Use the official domain terms consistently throughout the Platform.

Domain terms represent established concepts within CIVITAS/CORE and must retain their defined spelling and capitalization.

**Examples**

- Dataset
- Data source
- Data structure
- Data structure Version
- Data pool
- Pipeline
- User
- Group
- Role
- Permission
- Scope

> Use the same terminology in UI labels, descriptions, documentation, and issues.


### 1.2 Use clear, direct, and context-appropriate language

Describe functionality from the User's perspective using clear, concise, and direct language in both English and German.

- Explain what a feature does, what information is required, or what happens when an action is performed
- Use short sentences with simple structures
- Express one idea per sentence
- Use active voice where appropriate
- Focus on relevant information and actions

**Adapt the tone of voice to the context:**

**User Interface (UI)**

Use neutral language without directly addressing the User. Focus on actions, requirements, and system feedback.

**User Guide (Documentation)**

The User Guide in the public documentation is written in English only. Use direct language and address the reader as **you**. Provide clear, action-oriented instructions that guide Users through tasks and workflows.

> All content should be easy to understand and quick to scan.

**Examples**

| Context | English | German |
|---|---|---|
| User Interface | Select a Data source. | Datenquelle auswählen. |
| User Interface | The Dataset cannot be deleted because it is in use. | Der Datensatz kann nicht gelöscht werden, da er verwendet wird. |
| User Guide | Select the Data source you want to use. | – |
| User Guide | Enter a Name for your Dataset. | – |


### 1.3 Keep UI terminology independent of architecture

User-facing terminology must describe the functional purpose of a feature rather than its technical implementation.

Technical components may be replaced or changed without affecting the terminology used throughout the Platform.

Use established domain terms such as:

| User-facing term | Functional meaning |
|---|---|
| Sensor Data storage | Persists sensor and time-series data |
| Geospatial Data storage | Persists geospatial data |
| Data source | Defines how data is accessed and brought into the Platform |
| Data storage | Persists processed data within a Pipeline |
| Pipeline | Defines how data is ingested, transformed, and stored |

For example, a Sensor Data storage may be implemented using FROST Server, while a Geospatial Data storage may use GeoServer or PostGIS.

The UI describes the functional concept independently of these components.

> Technical product names may be used when Users explicitly interact with or configure a specific technology.

### 1.4 Use consistent action terminology

Use the same verb for the same type of operation throughout the Platform.

Action labels must clearly communicate what an action does and what happens to the affected element.

See [Actions and operations](#3-actions-and-operations).

### 1.5 Use consistent terminology across languages

English and German UI texts must express the same domain concepts and action semantics.

Use the official translations defined in this guide.

---


## 2. Terminology

Use the official English and German terminology consistently across the Platform. Domain terms retain their defined capitalization in all contexts.

For definitions and explanations of platform concepts, see the [Glossary](./glossary.md).

### 2.1 Platform and domain elements

| English | German |
|---|---|
| Platform | Plattform |
| Data pool | Datenpool |
| Dataset | Datensatz |
| Data source | Datenquelle |
| Data structure | Datenstruktur |
| Data structure Version | Datenstrukturversion |
| Pipeline | Pipeline |
| Mapping | Mapping |
| Data storage | Datenspeicher |
| Sensor Data storage | Sensordaten-Speicher |
| Geospatial Data storage | Geodaten-Speicher |
| API | API |

### 2.2 Data modeling

| English | German |
|---|---|
| Structure definition | Strukturdefinition |
| Class | Klasse |
| Attribute | Attribut |
| Relationship | Beziehung |
| Enumeration | Enumeration |
| Root Class | Wurzelklasse |

### 2.3 Users and authorization

| English | German |
|---|---|
| User | User |
| Group | Gruppe |
| Role | Rolle |
| Permission | Berechtigung |
| Scope | Geltungsbereich |
| Assignment | Zuordnung |

### 2.4 Basic information and metadata

| English | German |
|---|---|
| Basic information | Basisinformationen |
| Name | Name |
| Description | Beschreibung |
| Metadata | Metadaten |

### 2.5 Status and lifecycle

| English | German |
|---|---|
| Draft | Entwurf |
| Ready | Fertig |
| Available | Verfügbar |
| In use | In Verwendung |
| Usage | Verwendung |

For details about lifecycle states and transitions, see the [Status lifecycle](https://docs.core.civitasconnect.digital/docs_v2/next/User-and-admin-guide/Getting_Started/Status_Lifecycle).

### 2.6 Data access and publication

| English | German |
|---|---|
| Access | Zugriff |
| Protected | Geschützt |
| Open Data | Open Data |

---

## 3. Actions and operations

Action labels communicate what Users can do and what the result of an operation will be.
Use consistent verbs for equivalent actions across the Platform.

### 3.1 Standard action terminology
| English | German | Usage | Example |
|---|---|---|---|
| Create | Erstellen | Create a new, independent element | Create Dataset |
| Add | Hinzufügen | Associate an existing element with another element or context | Add Group |
| Edit | Bearbeiten | Modify an existing element | Edit Dataset |
| Remove | Entfernen | Remove an association while retaining the referenced element | Remove Group |
| Delete | Löschen | Delete the element itself from the Platform | Delete Dataset |
| Save | Speichern | Persist changes to a configuration | Save |
| Apply | Anwenden | Apply a configuration or change within the current workflow | Apply |
| Validate | Validieren | Check a configuration against defined rules and requirements | Validate Pipeline |
| Release | Freigeben | Change an element to Available | Release Dataset |

### 3.2 Create and Add

Use **Create** when a new, independent element is created.

**Examples**

- Create Dataset
- Create Data source
- Create Data structure
- Create Group

Use **Add** when an existing element is associated with another element or context.

**Examples**

- Add Group
- Add Dataset to Data pool
- Add Data source to Pipeline

The action should reflect whether a new element is created or an existing element is assigned.

### 3.3 Remove and Delete

Use **Remove** when an existing association is removed.

The referenced element continues to exist independently of the removed association.

**Examples**

- Remove Group from an access configuration
- Remove Dataset from a Data pool
- Remove a Role assignment

Use **Delete** when the element itself is deleted from the Platform.

This is a destructive operation and may affect references or dependencies.

**Examples**

- Delete Dataset
- Delete Data source
- Delete Group

> Deletion may be restricted when the element is in use.

### 3.4 Edit

Use **Edit** for actions that modify the properties or configuration of an existing element.

> The availability of editing actions may depend on the element's status and dependencies.

### 3.5 Validate

Use **Validate** when the Platform checks whether a configuration meets defined requirements.

> Validation may identify missing information, incompatible configurations, or other issues that need to be resolved.

Validation does not itself change the lifecycle status or release an element.

### 3.6 Save and Apply

Use **Save** when changes are persisted.

Use **Apply** when changes are applied within a configuration workflow.

**Examples**

- Save changes to a Dataset
- Apply changes in the Mapping Editor

The distinction should remain consistent across similar workflows.

### 3.7 Release

Use **Release** when an element is changed to the Available status.

**Examples**

- Release Data structure Version
- Release Data source
- Release Dataset

> Releasing an element may require all referenced dependencies to be Available.

---

## 4. UI text conventions

### 4.1 Capitalization

Use **sentence case** for UI text.

Capitalize the first word of a label, heading, or sentence, as well as established domain terms.

Domain terms retain their official capitalization in all contexts. See [Platform and domain elements](#21-platform-and-domain-elements)

### 4.2 Buttons and action labels

Use concise, action-oriented labels.

Prefer a verb followed by the affected element when the context requires clarification.

**Examples**

- Create Dataset
- Add Group
- Delete Data source
- Open Mapping Editor
- Show Data structure

Use short labels such as **Save**, **Apply**, and **Cancel** when the action is clear from the surrounding context.

**Button icons**

Use a **plus icon** ` + ` before the label of buttons that **create new elements**.

### 4.3 Section and tab labels

Use consistent names for equivalent sections across data-related elements.

| English | German |
|---|---|
| Overview | Übersicht |
| Basic information | Basisinformationen |
| Access | Zugriff |

### 4.4 Field labels

Use short, descriptive labels that identify the information required.

**Examples**

- Name
- Description
- Data source
- Data structure Version
- Port
- CQL filter

Field labels should remain consistent wherever the same information is requested.

### 4.5 Placeholders and helper text

Use placeholders to communicate an expected input or selection.

Use helper text to explain requirements, constraints, or consequences that are not evident from the label.

**Example**

**Label:** Port

**Placeholder:** Select a Port

**Helper text:** The Port determines whether incoming data is stored as Things, Observations, or ThingTrees.

This example uses terminology from the standardized SensorThings data model because Users explicitly configure technology-specific options.

### 4.6 Required fields

Indicate required fields with a **red asterisk** `*` next to the field label. Apply this convention consistently across all forms.

Validation messages should explain what information is required or why an entered value is invalid.

### 4.7 Dialogs

Dialog titles should clearly describe the task or action. For confirmation dialogs, explain the consequence of the operation.

Use the established action terminology for primary buttons.

Examples:

- Delete Pipeline
- Remove Group

> Destructive actions should clearly identify the affected element.

### 4.8 Feedback and validation messages

Use feedback messages to communicate:

- Whether an action was successful
- Why an action could not be completed
- Which requirements remain unmet
- What the User can do next

Messages should describe the issue using established domain terminology.

> When an action is blocked by a dependency, explain the dependency and the necessary next step.

---

## 5. Terminology governance

### 5.1 Introducing new terminology

When a new platform concept or user-facing term is introduced:

1. Check whether an established term already describes the concept.
2. Review the terminology and conventions in this guide.
3. Discuss new or ambiguous terminology with Product Ownership and Community.
4. Define the English and German terms.
5. Document the meaning and intended usage.
6. Add the approved terminology to this guide.
7. Apply the terminology consistently across the Platform and documentation.

### 5.2 Issues and acceptance criteria

Issues describing user-facing functionality should use the official terminology.

This includes:

- User Stories
- Goal and Context
- Expected Behavior
- Acceptance Criteria
- UI labels and descriptions
- Validation and error messages

Terminology should be reviewed as part of the implementation and UX/UI review process.

### 5.3 Reviewing UI changes

When reviewing changes to user-facing functionality, verify that:

- Official domain terminology is used
- Capitalization follows the defined conventions
- Action labels reflect the intended operation
- English and German terminology are consistent
- Technical implementation details are appropriately abstracted
- Validation and feedback messages use established terminology
- New terminology has been reviewed and documented
