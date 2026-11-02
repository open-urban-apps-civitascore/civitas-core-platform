# DataStructure Versions

> Versions are nested resources under a DataStructure.

## TC-DSTV-01 – Get All DataStructure Versions

**Precondition:** `dataStructureId` is set.

**Request**
```
GET {{baseUrl}}/datastructures/:dataStructureId/versions
```

Optional query parameters (disabled by default):
- `page` (e.g. `0`)
- `size` (e.g. `20`)
- `sort` (e.g. `createdAt,DESC`)

**Expected Response**
- Status: `200 OK`
- Body contains a paginated list of versions for the given DataStructure

---

## TC-DSTV-02 – Get DataStructure Version by ID

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set.

**Request**
```
GET {{baseUrl}}/datastructures/:dataStructureId/versions/:id
```

**Expected Response**
- Status: `200 OK`
- Body contains the version with matching `id`

---

## TC-DSTV-03 – Create DataStructure Version

**Precondition:** `dataStructureId` is set.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/versions

Content-Type: application/json

{
  "description": "Initial version",
  "modelName": "StudentDatabaseModel1",
  "model": {
    "$id": "http://civitas.org/model/StudentDatabaseModel1/1.0.4",
    "title": "StudentDatabaseModel1",
    "type": "object",
    "properties": {}
  },
  "styles": {}
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `dataStructureVersionId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version record created in `portal_backend` DB, linked to the DataStructure | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTV-04 – Update DataStructure Version (Full)

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set.

**Request**
```
PUT {{baseUrl}}/datastructures/:dataStructureId/versions/:id

Content-Type: application/json

{
  "description": "Updated version",
  "modelName": "StudentDatabaseModel1",
  "model": {
    "$id": "http://civitas.org/model/StudentDatabaseModel1/1.0.4",
    "title": "StudentDatabaseModel1",
    "type": "object",
    "properties": {}
  },
  "styles": {}
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version record updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTV-05 – Update DataStructure Version (Partial)

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set.

**Request**
```
PATCH {{baseUrl}}/datastructures/:dataStructureId/versions/:id

Content-Type: application/json

{
  "description": "Patched version description"
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `description`; other fields remain unchanged

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTV-07 – Release DataStructure Version

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set; version is in draft state.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/versions/:versionId/release
```

**Expected Response**
- Status: `200 OK`
- Body reflects the version in released state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version status updated to released in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — (the model/JSON Schema is persisted on the version in `portal_backend`) | ☐ |

---

## TC-DSTV-08 – Unrelease DataStructure Version

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set; version is in released state.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/versions/:versionId/unrelease
```

**Expected Response**
- Status: `200 OK`
- Body reflects the version returned to draft state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version status reverted to draft in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTV-09 – Update Released Version Meta

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set; version is in released state.

**Request**
```
PATCH {{baseUrl}}/datastructures/:dataStructureId/versions/:versionId/released/meta

Content-Type: application/json

{
  "description": "Updated version description"
}
```

**Expected Response**
- Status: `200 OK`
- Released version metadata is updated
- `model`, `styles` or `modelName` in the body: `400 Bad Request`

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Released version metadata updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTV-10 – Duplicate DataStructure Version

**Precondition:** `dataStructureId` and `dataStructureVersionId` (the source version) are set. The
source version is in draft or released state.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/versions/:versionId/duplicate
```

**Expected Response**
- Status: `201 Created`
- `Location` header pointing at the new version
- Body is a new version with a fresh `id`, `dataStructureVersionStatus: "DRAFT"`, and the
  `description`, `modelName` and `importedStructureUrns` of the source
- `model` and `styles` are those of the source version, also when a later version exists
- `version` is the next major after the highest existing version, for example `3.0.0` for a
  copy of `1.0.0` while `2.0.0` exists
- A source without a model gives a copy without a model and without a `version`
- A version of another data structure: `404 Not Found`

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | New version record in `portal_backend` DB, pinned to the registry's copy of the source model | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTV-11 – Duplicate DataStructure Version Twice

**Precondition:** TC-DSTV-10 ran once against the same `dataStructureVersionId`.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/versions/:versionId/duplicate
```

**Expected Response**
- Status: `201 Created`
- Body is a version distinct from the first duplicate, with the next major number again

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Second new version record in `portal_backend` DB, with its own registry pin | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |
