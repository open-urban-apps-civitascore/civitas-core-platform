# Permissions

> Permissions are system-managed and read-only. No create/update/delete operations are available.

## TC-PERM-01 – Get All Permissions

**Request**
```
GET {{baseUrl}}/permissions
```

**Optional query parameters:** `name`, `description`, `q`, `permissionType`, `category`, `source`

**Expected Response**
- Status: `200 OK`
- Body is an array of permission objects (not paginated)

**Post-condition:** If the array is non-empty, save the first `id` as `permissionId`.

---

## TC-PERM-02 – Get Permission by ID

**Precondition:** `permissionId` is set (from TC-PERM-01).

**Request**
```
GET {{baseUrl}}/permissions/:id
```

| Path Parameter | Value |
|---|---|
| `:id` | `{{permissionId}}` |

**Expected Response**
- Status: `200 OK`
- Body contains the permission with matching `id`
