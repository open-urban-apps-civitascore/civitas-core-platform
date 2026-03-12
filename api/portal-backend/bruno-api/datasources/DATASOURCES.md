# DataSources

## TC-SRC-01 – Get All DataSources

**Request**
```
GET {{baseUrl}}/datasources?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `name`, `description`, `dataSourceStatus` (e.g. `DRAFT`), `connectorType` (e.g. `MQTT`), `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of data source objects

---

## TC-SRC-02 – Get DataSource by ID

**Precondition:** `dataSourceId` is set.

**Request**
```
GET {{baseUrl}}/datasources/:id
```

**Expected Response**
- Status: `200 OK`
- Body contains the data source with matching `id`

---

## TC-SRC-03 – Create DataSource (MQTT)

**Request**
```
POST {{baseUrl}}/datasources

Content-Type: application/json

{
  "name": "MQTT Sensor Source",
  "description": "Temperature sensor data source",
  "connectorType": "MQTT",
  "configuration": {
    "urls": ["tcp://broker:1883"],
    "topics": ["sensor/#"],
    "qos": 1,
    "keepalive": "30s",
    "user": "mqttuser",
    "password": "secret",
    "client_id": "civitas-client-1",
    "connect_timeout": "5s",
    "tls": {
      "enabled": false
    }
  },
  "dataStructureVersionId": null,
  "assignments": []
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `dataSourceId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSource record created in `portal_backend` DB with status `DRAFT` | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-SRC-04 – Update DataSource (Full)

**Precondition:** `dataSourceId` is set (MQTT source).

**Request**
```
PUT {{baseUrl}}/datasources/:id

Content-Type: application/json

{
  "name": "MQTT Sensor Source",
  "description": "Updated temperature sensor data source",
  "connectorType": "MQTT",
  "configuration": {
    "urls": ["tcp://broker:1883"],
    "topics": ["sensor/#"],
    "qos": 1,
    "keepalive": "30s",
    "user": "mqttuser",
    "password": "secret",
    "client_id": "civitas-client-1",
    "connect_timeout": "5s",
    "tls": {
      "enabled": false
    }
  },
  "dataStructureVersionId": null,
  "assignments": []
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSource record updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-SRC-05 – Update DataSource (Partial)

**Precondition:** `dataSourceId` is set.

**Request**
```
PATCH {{baseUrl}}/datasources/:id

Content-Type: application/json

{
  "description": "Patched description"
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `description`; other fields remain unchanged

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSource `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-SRC-06 – Delete DataSource

**Precondition:** `dataSourceId` is set and the source is not in published state.

**Request**
```
DELETE {{baseUrl}}/datasources/:id
```

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-SRC-02 for the same `dataSourceId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSource record removed from `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-SRC-07 – Publish DataSource

**Precondition:** `dataSourceId` is set and the source is in `DRAFT` state.

**Request**
```
POST {{baseUrl}}/datasources/:id/publish
```

**Expected Response**
- Status: `200 OK`
- Body reflects the data source in published state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSource status updated to published in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.api.backend.created` Kafka event; registers the backend with APISIX | ☐ |
| APISIX | New upstream/route registered for this DataSource (verify in APISIX Admin API at `http://localhost:9180`) | ☐ |

---

## TC-SRC-08 – Unpublish DataSource

**Precondition:** `dataSourceId` is set and the source is in published state.

**Request**
```
POST {{baseUrl}}/datasources/:id/unpublish
```

**Expected Response**
- Status: `200 OK`
- Body reflects the data source returned to draft state

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | DataSource status reverted to draft in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.api.backend.deleted` Kafka event; removes the backend from APISIX | ☐ |
| APISIX | Upstream/route for this DataSource removed | ☐ |

---

## TC-SRC-09 – Update Published DataSource Meta

**Precondition:** `dataSourceId` is set and the source is in published state.

**Request**
```
PUT {{baseUrl}}/datasources/:id/published/meta

Content-Type: application/json

{
  "name": "Updated Source Name",
  "description": "Updated description for published data source",
  "assignments": []
}
```

**Expected Response**
- Status: `200 OK`
- Published name and description are updated

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Published DataSource name/description updated in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.api.backend.updated` Kafka event; updates the route metadata in APISIX | ☐ |
| APISIX | Route metadata updated for this DataSource | ☐ |

---

## TC-SRC-10 – Get DataSource Assignments

**Precondition:** `dataSourceId` is set.

**Request**
```
GET {{baseUrl}}/datasources/:id/assignments
```

**Expected Response**
- Status: `200 OK`
- Body is a list of assignments for this data source
