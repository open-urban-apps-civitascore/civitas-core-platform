# Users

## TC-USER-01 – Get All Users

**Request**
```
GET {{baseUrl}}/users?page=0&size=20&sort=createdAt,DESC
```

**Optional query parameters:** `firstName`, `lastName`, `email`, `active`, `externalId`, `q`

**Expected Response**
- Status: `200 OK`
- Body is a paginated list of user objects

---

## TC-USER-02 – Get User by ID

**Precondition:** `userId` is set (from TC-USER-04).

**Request**
```
GET {{baseUrl}}/users/:id
```

**Path parameters:** `id` = `{{userId}}`

**Expected Response**
- Status: `200 OK`
- Body contains the user with matching `id`

---

## TC-USER-03 – Get My Profile

**Request**
```
GET {{baseUrl}}/users/me
```

**Expected Response**
- Status: `200 OK`
- Body contains `email` (non-empty)

---

## TC-USER-04 – Create User

**Request**
```
POST {{baseUrl}}/users

Content-Type: application/json

{
  "title": "MR",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+1234567890",
  "externalId": "ext-123",
  "active": true,
  "groupIds": []
}
```

**Expected Response**
- Status: `201 Created`
- Body contains `id` (non-empty string)

**Post-condition:** Save `userId` from `body.id`.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | User record created in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.user.created` Kafka event; forwards user creation to Keycloak | ☐ |
| Keycloak | New user created in `civitas-core` realm (verify in Keycloak Admin Console → Users) | ☐ |

---

## TC-USER-05 – Update User (Full)

**Precondition:** `userId` is set.

**Request**
```
PUT {{baseUrl}}/users/:id

Content-Type: application/json

{
  "title": "MR",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+1234567890",
  "externalId": "ext-123",
  "active": true,
  "groupIds": []
}
```

**Path parameters:** `id` = `{{userId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the updated values

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | User record updated in `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.user.updated` Kafka event; forwards update to Keycloak | ☐ |
| Keycloak | User attributes updated in `civitas-core` realm | ☐ |

---

## TC-USER-06 – Update User (Partial)

**Precondition:** `userId` is set.

**Request**
```
PATCH {{baseUrl}}/users/:id

Content-Type: application/json

{
  "firstName": "Jane"
}
```

**Path parameters:** `id` = `{{userId}}`

**Expected Response**
- Status: `200 OK`
- Body reflects the updated `firstName`; other fields remain unchanged

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | User `firstName` updated in `portal_backend` DB; other fields unchanged | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.user.updated` Kafka event; forwards update to Keycloak | ☐ |
| Keycloak | User firstName updated in `civitas-core` realm | ☐ |

---

## TC-USER-07 – Delete User

**Precondition:** `userId` is set.

**Request**
```
DELETE {{baseUrl}}/users/:id
```

**Path parameters:** `id` = `{{userId}}`

**Expected Response**
- Status: `204 No Content`
- Body is empty

**Verification:** Issue TC-USER-02 for the same `userId` and confirm a `404` response.

**System Impact**

| System | Expected Change | Verified |
|---|---|---|
| Backend | User record removed from `portal_backend` DB | ☐ |
| Config Adapter | Consumes `de.civitascore.idm.user.deleted` Kafka event; forwards deletion to Keycloak | ☐ |
| Keycloak | User removed from `civitas-core` realm | ☐ |
