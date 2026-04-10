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
  "dataStructureVersionSource": "OWN",
  "version": "1.0.4",
  "description": "Initial version",
  "modelAtlasUri": "http://civitas.org/model/StudentDatabaseModel1/1.0.4",
  "modelName": "StudentDatabaseModel1",
  "model": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>...",
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
  "dataStructureVersionSource": "OWN",
  "version": "1.0.4",
  "description": "Updated version",
  "modelAtlasUri": "http://civitas.org/model/StudentDatabaseModel1/1.0.4",
  "modelName": "StudentDatabaseModel1",
  "model": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>...",
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

## TC-DSTV-06 – Delete DataStructure Version

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set; version is not in published state.

**Request**
```
DELETE {{baseUrl}}/datastructures/:dataStructureId/versions/:id
```

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-DSTV-02 for the same `dataStructureVersionId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version record removed from `portal_backend` DB | ☐ |
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
- Body reflects the version in published state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Version status updated to published in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| Model Atlas / Apicurio Registry | Version schema published at `modelAtlasUri`; verify in Apicurio Registry UI (`http://localhost:8888`) | ☐ |

---

## TC-DSTV-08 – Unrelease DataStructure Version

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set; version is in published state.

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
| Model Atlas / Apicurio Registry | Version schema unpublished/removed from registry | ☐ |

---

## TC-DSTV-09 – Update Released Version Meta

**Precondition:** `dataStructureId` and `dataStructureVersionId` are set; version is in published state.

**Request**
```
PUT {{baseUrl}}/datastructures/:dataStructureId/versions/:versionId/released/meta

Content-Type: application/json

{
  "dataStructureVersionSource": "OWN",
  "version": "1.0.1",
  "modelName": "UpdatedModelName",
  "styles": {}
}
```

**Expected Response**
- Status: `200 OK`
- Published version metadata is updated

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Published version metadata updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| Model Atlas / Apicurio Registry | Version metadata updated in registry | ☐ |
