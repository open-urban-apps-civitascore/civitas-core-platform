# DataStructures

## TC-DSTR-01 – Get All DataStructures

**Request**
```
GET {{baseUrl}}/datastructures?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `name`, `description`, `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of data structure objects

---

## TC-DSTR-02 – Get DataStructure by ID

**Precondition:** `dataStructureId` is set.

**Request**
```
GET {{baseUrl}}/datastructures/:id
```

**Path parameters:** `id` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Body contains the data structure with matching `id`

---

## TC-DSTR-03 – Create DataStructure

**Request**
```
POST {{baseUrl}}/datastructures

Content-Type: application/json

{
  "name": "Sensor Data Structure",
  "description": "Data structure for sensor readings",
  "createdFromDataSource": false,
  "dataStructureVersionIds": [],
  "assignments": []
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `dataStructureId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataStructure record created in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTR-04 – Update DataStructure (Full)

**Precondition:** `dataStructureId` is set.

**Request**
```
PUT {{baseUrl}}/datastructures/:id

Content-Type: application/json

{
  "name": "Sensor Data Structure",
  "description": "Updated data structure for sensor readings",
  "createdFromDataSource": false,
  "dataStructureVersionIds": [],
  "assignments": []
}
```

**Path parameters:** `id` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataStructure record updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTR-05 – Update DataStructure (Partial)

**Precondition:** `dataStructureId` is set.

**Request**
```
PATCH {{baseUrl}}/datastructures/:id

Content-Type: application/json

{
  "description": "Patched description"
}
```

**Path parameters:** `id` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `description`; other fields remain unchanged

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataStructure `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTR-06 – Delete DataStructure

**Precondition:** `dataStructureId` is set and the structure is not in published state.

**Request**
```
DELETE {{baseUrl}}/datastructures/:id
```

**Path parameters:** `id` = `{{dataStructureId}}`

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-DSTR-02 for the same `dataStructureId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataStructure record removed from `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-DSTR-07 – Publish DataStructure

**Precondition:** `dataStructureId` is set and the structure is in draft state.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/publish
```

**Path parameters:** `dataStructureId` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the data structure in published state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataStructure status updated to published in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| Model Atlas | DataStructure schema becomes available in Model Atlas (`http://localhost:8086`) | ☐ |

---

## TC-DSTR-08 – Unpublish DataStructure

**Precondition:** `dataStructureId` is set and the structure is in published state.

**Request**
```
POST {{baseUrl}}/datastructures/:dataStructureId/unpublish
```

**Path parameters:** `dataStructureId` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the data structure returned to draft state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataStructure status reverted to draft in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| Model Atlas | DataStructure schema no longer publicly available in Model Atlas | ☐ |

---

## TC-DSTR-09 – Update Published DataStructure Meta

**Precondition:** `dataStructureId` is set and the structure is in published state.

**Request**
```
PUT {{baseUrl}}/datastructures/:dataStructureId/published/meta

Content-Type: application/json

{
  "name": "Updated Name",
  "description": "Updated description for published data structure"
}
```

**Path parameters:** `dataStructureId` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Published name and description are updated

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Published DataStructure name/description updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| Model Atlas | Metadata updated in Model Atlas | ☐ |

---

## TC-DSTR-10 – Get DataStructure Assignments

**Precondition:** `dataStructureId` is set.

**Request**
```
GET {{baseUrl}}/datastructures/:id/assignments
```

**Path parameters:** `id` = `{{dataStructureId}}`

**Expected Response**
- Status: `200 OK`
- Body contains the list of assignments for the data structure
