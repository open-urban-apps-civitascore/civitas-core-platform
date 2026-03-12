# Assignments

## TC-ASSIGN-01 – Get All Assignments

**Request**
```
GET {{baseUrl}}/assignments
```

**Optional query parameters:** `page`, `size`, `sort`, `roleId`, `userId`, `groupId`, `scopeId`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of assignment objects

---

## TC-ASSIGN-02 – Get Assignment by ID

**Precondition:** `assignmentId` is set.

**Request**
```
GET {{baseUrl}}/assignments/:id
```

| Path Parameter | Value |
|---|---|
| `id` | `{{assignmentId}}` |

**Expected Response**
- Status: `200 OK`
- Body contains the assignment with matching `id`

---

## TC-ASSIGN-03 – Create Assignment

**Precondition:** `groupId` and `roleId` are set. `scopeType` and `scopeId` are set to `null` for unscoped assignments.

**Request**
```
POST {{baseUrl}}/assignments

Content-Type: application/json

{
  "groupId": "{{groupId}}",
  "roleId": "{{roleId}}",
  "scopeType": {{scopeType}},
  "scopeId": {{scopeId}}
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `assignmentId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Assignment record created in `portal_backend` DB linking the group to the role | ☐ |
| Config Adapter | AuthZ Repository is notified to refresh its authorization data cache | ☐ |
| AuthZ Repository (OPA) | Role-group binding becomes visible in OPA policy data; verify at `http://localhost:8181/v1/data` | ☐ |

---

## TC-ASSIGN-04 – Create Scoped Assignment

**Precondition:** `groupId`, `roleId`, `scopeType`, and `scopeId` are set to valid string values.

**Request**
```
POST {{baseUrl}}/assignments

Content-Type: application/json

{
  "groupId": "{{groupId}}",
  "roleId": "{{roleId}}",
  "scopeType": "{{scopeType}}",
  "scopeId": "{{scopeId}}"
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `assignmentId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Scoped assignment record created in `portal_backend` DB linking the group to the role with the given scope | ☐ |
| Config Adapter | AuthZ Repository is notified to refresh its authorization data cache | ☐ |
| AuthZ Repository (OPA) | Scoped role-group binding becomes visible in OPA policy data; verify at `http://localhost:8181/v1/data` | ☐ |

---

## TC-ASSIGN-05 – Delete Assignment

**Precondition:** `assignmentId` is set.

**Request**
```
DELETE {{baseUrl}}/assignments/:id
```

| Path Parameter | Value |
|---|---|
| `id` | `{{assignmentId}}` |

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-ASSIGN-02 for the same `assignmentId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Assignment record removed from `portal_backend` DB | ☐ |
| Config Adapter | AuthZ Repository is notified to refresh its authorization data cache | ☐ |
| AuthZ Repository (OPA) | Role-group binding no longer present in OPA policy data | ☐ |
