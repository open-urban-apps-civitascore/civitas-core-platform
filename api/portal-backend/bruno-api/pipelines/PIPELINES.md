# Pipelines

> Pipelines are nested resources under a DataSet.

## TC-PIPE-01 – Get All Pipelines for DataSet

**Precondition:** `dataSetId` is set.

**Request**
```
GET {{baseUrl}}/datasets/:dataSetId/pipelines?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `name`, `description`, `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of pipeline objects belonging to the data set

---

## TC-PIPE-02 – Get Pipeline by ID

**Precondition:** `dataSetId` and `pipelineId` are set.

**Request**
```
GET {{baseUrl}}/datasets/:dataSetId/pipelines/:id
```

**Expected Response**
- Status: `200 OK`
- Body contains the pipeline with matching `id`

---

## TC-PIPE-03 – Create Pipeline

**Precondition:** `dataSetId` is set.

**Request**
```
POST {{baseUrl}}/datasets/:dataSetId/pipelines

Content-Type: application/json

{
  "name": "Traffic Data Pipeline",
  "description": "Processes traffic sensor data",
  "styles": {},
  "model": {},
  "dataSourceIds": [],
  "apis": [],
  "persistences": []
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `pipelineId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Pipeline record created in `portal_backend` DB, linked to the DataSet | ☐ |
| Config Adapter | — | ☐ |
| External System | — (FROST is affected when the parent DataSet is released, not on pipeline creation) | ☐ |

---

## TC-PIPE-04 – Update Pipeline (Full)

**Precondition:** `dataSetId` and `pipelineId` are set.

**Request**
```
PUT {{baseUrl}}/datasets/:dataSetId/pipelines/:id

Content-Type: application/json

{
  "name": "Traffic Data Pipeline",
  "description": "Updated pipeline description",
  "styles": {},
  "model": {},
  "dataSourceIds": [],
  "apis": [],
  "persistences": []
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Pipeline record updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — (FROST reflects pipeline changes only after the DataSet is re-released) | ☐ |

---

## TC-PIPE-05 – Update Pipeline (Partial)

**Precondition:** `dataSetId` and `pipelineId` are set.

**Request**
```
PATCH {{baseUrl}}/datasets/:dataSetId/pipelines/:id

Content-Type: application/json

{
  "description": "Patched pipeline description"
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `description`; other fields remain unchanged

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Pipeline `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-PIPE-06 – Delete Pipeline

**Precondition:** `dataSetId` and `pipelineId` are set.

**Request**
```
DELETE {{baseUrl}}/datasets/:dataSetId/pipelines/:id
```

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-PIPE-02 for the same `pipelineId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Pipeline record removed from `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |
