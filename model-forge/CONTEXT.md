# Model Forge

The registry domain: the schemas and configuration documents the platform is built from, the
identity each one carries, and what the registry knows about how they refer to each other.
Definitions only — when a term here conflicts with how code or conversation uses it, resolve the
conflict and update this file.

## Artifacts

**Artifact**:
The unit the registry stores and versions. Each has one identity and a history of versions, and its
kind decides which contract it must satisfy on write.
_Avoid_: entry, record, document

**Element**:
A single schema — the smallest thing the registry addresses on its own. Other artifacts refer to
Elements rather than containing them.
_Avoid_: type, class, model, definition

**Data Structure**:
An artifact that groups Elements as its members. A different concept from the Portal Backend's
**Data Structure**, despite the shared name: that one is a tenant-curated asset with a release
lifecycle, this one is the grouping in the registry it is translated into.
_Avoid_: schema set, model group, bundle

**Root**:
The one member of a Data Structure that is its entry point, named by the Data Structure's top-level
`$ref`. A Data Structure may be stored without one. An Element is its own root.
_Avoid_: main type, top-level class

**Import**:
Turning one submitted document into artifacts — separating the members it declares into Elements and
recording the grouping that holds them. Distinct from storing an artifact whose identity the caller
already owns.
_Avoid_: upload, ingest, load

## Identity

**URN**:
The identifier every artifact carries. The registry is its sole authority: a caller may propose one,
but what the registry assigns is what the artifact is. It holds the artifact's UUID and never its
name, so a rename leaves every reference intact. A member Element's UUID derives from its
grouping's UUID and the member's key.
_Avoid_: id, key, name

**Logical URN**:
The identifier without a version — the artifact across its whole history. What one artifact uses to
refer to another when it means whichever version is current.
_Avoid_: base urn, unversioned id

**Versioned URN**:
The identifier of one exact version. What a reference uses when it must not move.
_Avoid_: full urn, pinned id

**Pin**:
The versioned URN a write resolved to, returned by the write itself so a caller never has to read
back what it just stored.
_Avoid_: result id, assigned version

## References

**Reference**:
A recorded link from one artifact version to another artifact. References are what the registry
knows about the model beyond the content of each document.
_Avoid_: link, dependency, edge

**Unresolved reference**:
A reference whose target the registry no longer holds. Kept deliberately: the link still states what
the document said, and it resolves again if the target returns.
_Avoid_: dangling reference, broken link, orphan

**Data Set membership**:
The one kind of reference that does not by itself prevent its target from being deleted. It is
counted instead, so a member that belongs to a single Data Set can still be removed, while one
belonging to several cannot.
_Avoid_: dataset link, containment
