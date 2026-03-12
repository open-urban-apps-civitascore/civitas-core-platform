# DataSets

## TC-DSET-01 – Get All DataSets

**Request**
```
GET {{baseUrl}}/datasets?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `name`, `description`, `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of data set objects

---

## TC-DSET-02 – Get DataSet by ID

**Precondition:** `dataSetId` is set.

**Request**
```
GET {{baseUrl}}/datasets/:id
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `200 OK`
- Body contains the data set with matching `id`

---

## TC-DSET-03 – Create DataSet

**Request**
```
POST {{baseUrl}}/datasets

Content-Type: application/json

{
  "name": "Sensor Readings Dataset",
  "description": "Dataset containing sensor readings",
  "openDataAccess": false,
  "assignments": []
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `dataSetId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSet record created in `portal_backend` DB with status `DRAFT` | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSET-04 – Update DataSet (Full)

**Precondition:** `dataSetId` is set.

**Request**
```
PUT {{baseUrl}}/datasets/:id

Content-Type: application/json

{
  "name": "Sensor Readings Dataset",
  "description": "Updated dataset description",
  "openDataAccess": false,
  "assignments": []
}
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSet record updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSET-05 – Update DataSet (Partial)

**Precondition:** `dataSetId` is set.

**Request**
```
PATCH {{baseUrl}}/datasets/:id

Content-Type: application/json

{
  "description": "Patched description"
}
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `description`; other fields remain unchanged

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSet `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSET-06 – Delete DataSet

**Precondition:** `dataSetId` is set and the data set is not in released state.

**Request**
```
DELETE {{baseUrl}}/datasets/:id
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-DSET-02 for the same `dataSetId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSet record removed from `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSET-07 – Publish DataSet

**Precondition:** `dataSetId` is set and the data set is in `DRAFT` state.

**Request**
```
POST {{baseUrl}}/datasets/:id/publish
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `200 OK`
- Body contains `dataSetStatus: "READY"`

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSet status updated to `READY` in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSET-08 – Update Published DataSet Meta

**Precondition:** `dataSetId` is set and the data set is in published state.

**Request**
```
PUT {{baseUrl}}/datasets/:id/published/meta

Content-Type: application/json

{
  "name": "Updated Dataset Name",
  "description": "Updated description for published dataset",
  "openDataAccess": false,
  "assignments": []
}
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `200 OK`
- Published metadata is updated

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Published DataSet name/description updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSET-09 – Get DataSet Assignments

**Precondition:** `dataSetId` is set.

**Request**
```
GET {{baseUrl}}/datasets/:id/assignments
```

**Path parameters:** `id` = `{{dataSetId}}`

**Expected Response**
- Status: `200 OK`
- Body contains the list of assignments for the data set
