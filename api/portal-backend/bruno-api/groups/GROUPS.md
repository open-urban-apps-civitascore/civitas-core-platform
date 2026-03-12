# Groups

## TC-GROUP-01 – Get All Groups

**Request**
```
GET {{baseUrl}}/groups?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `name`, `description`, `contactUserId`, `parentGroupId`, `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of group objects

---

## TC-GROUP-02 – Get Group by ID

**Precondition:** `groupId` is set.

**Request**
```
GET {{baseUrl}}/groups/:id
```

**Expected Response**
- Status: `200 OK`
- Body contains the group with matching `id`

**Post-condition:** Save `groupAssignments` from `body.assignments` (mapped to `groupId`, `roleId`, `scopeType`, `scopeId`).

---

## TC-GROUP-03 – Create Group

**Request**
```
POST {{baseUrl}}/groups

Content-Type: application/json

{
  "name": "Engineering",
  "description": "Engineering team",
  "contactUserId": null,
  "parentGroupId": null,
  "memberIds": []
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `groupId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Group record created in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.group.created` Kafka event; forwards group creation to Keycloak | ☐ |
| Keycloak | New group created in `civitas-core` realm (verify in Keycloak Admin Console → Groups) | ☐ |

---

## TC-GROUP-04 – Update Group (Full)

**Precondition:** `groupId` is set.

**Request**
```
PUT {{baseUrl}}/groups/:id

Content-Type: application/json

{
  "name": "Engineering",
  "description": "Updated engineering team",
  "contactUserId": null,
  "parentGroupId": null,
  "memberIds": []
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**Post-condition:** Save `groupId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Group record updated in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.group.updated` Kafka event; forwards update to Keycloak | ☐ |
| Keycloak | Group attributes updated in `civitas-core` realm | ☐ |

---

## TC-GROUP-05 – Update Group (Partial)

**Precondition:** `groupId` is set.

**Request**
```
PATCH {{baseUrl}}/groups/:id

Content-Type: application/json

{
  "description": "Patched description"
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `description`; other fields remain unchanged

**Post-condition:** Save `groupId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Group `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.group.updated` Kafka event; forwards update to Keycloak | ☐ |
| Keycloak | Group description updated in `civitas-core` realm | ☐ |

---

## TC-GROUP-06 – Delete Group

**Precondition:** `groupId` is set.

**Request**
```
DELETE {{baseUrl}}/groups/:id
```

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-GROUP-02 for the same `groupId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Group record removed from `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.group.deleted` Kafka event; forwards deletion to Keycloak | ☐ |
| Keycloak | Group removed from `civitas-core` realm | ☐ |

---

## TC-GROUP-07 – Replace Group Assignments

**Precondition:** `groupId` is set.

**Request**
```
PUT {{baseUrl}}/groups/:id/assignments

Content-Type: application/json

[
  {
    "roleId": "{{roleId}}",
    "scopeType": "{{scopeType}}",
    "scopeId": "{{scopeId}}"
  }
]
```

**Expected Response**
- Status: `200 OK`
- Body reflects the replaced assignments for the group

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Group assignments replaced in `portal_backend` DB | ☐ |
| Config Adapter | Consumes assignment-related Kafka event; forwards changes to Keycloak | ☐ |
| Keycloak | Group role assignments updated in `civitas-core` realm | ☐ |
