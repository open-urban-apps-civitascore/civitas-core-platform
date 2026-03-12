# Authorities

## TC-AUTH-01 – Get All Authorities

**Request**
```
GET {{baseUrl}}/authorities
```

| Optional Query Parameter | Example Value |
|---|---|
| `page` | `0` |
| `size` | `20` |

**Expected Response**
- Status: `200 OK`
- Body contains a list of authorities
