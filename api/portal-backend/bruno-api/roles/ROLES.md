# Roles

## TC-ROLE-01 – Get All Roles

**Request**
```
GET {{baseUrl}}/roles?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `name`, `description`, `roleType`, `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of role objects

---

## TC-ROLE-02 – Get Role by ID

**Precondition:** `roleId` is set.

**Request**
```
GET {{baseUrl}}/roles/:id
```

**Expected Response**
- Status: `200 OK`
- Body contains the role with matching `id`

---

## TC-ROLE-03 – Create Role

**Request**
```
POST {{baseUrl}}/roles

Content-Type: application/json

{
  "name": "Data Manager",
  "description": "Can manage data resources",
  "roleType": "DATA",
  "permissionIds": [],
  "readonly": false
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id`

**Post-condition:** Save `roleId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Role record created in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-ROLE-04 – Update Role (Full)

**Precondition:** `roleId` is set.

**Request**
```
PUT {{baseUrl}}/roles/:id

Content-Type: application/json

{
  "name": "Data Manager",
  "description": "Updated description",
  "roleType": "DATA",
  "permissionIds": [],
  "readonly": false
}
```

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Role record updated in `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-ROLE-05 – Update Role (Partial)

**Precondition:** `roleId` is set.

**Request**
```
PATCH {{baseUrl}}/roles/:id

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
| Backend | Role `description` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |

---

## TC-ROLE-06 – Delete Role

**Precondition:** `roleId` is set.

**Request**
```
DELETE {{baseUrl}}/roles/:id
```

**Expected Response**
- Status: `204 No Content`

**Verification:** Issue TC-ROLE-02 for the same `roleId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | Role record removed from `portal_backend` DB | ☐ |
| Config Adapter | — | ☐ |
| External System | — | ☐ |
