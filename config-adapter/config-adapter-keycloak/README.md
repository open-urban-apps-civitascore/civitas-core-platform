# Keycloak Config Adapter

Production-ready adapter for managing [Keycloak](https://www.keycloak.org/) Identity and Access Management configuration through CloudEvents.

## Overview

The Keycloak adapter integrates with Keycloak's Admin REST API to manage identity resources. It consumes CloudEvents from Kafka and translates them into Keycloak Admin API calls, enabling automated configuration management for your IAM infrastructure.

**Key Features:**
- ✅ Full CRUD operations for Users, Realms, Clients, Roles, and Groups
- ✅ Role synchronization (realm roles and client roles)
- ✅ Composite roles support
- ✅ Group hierarchy (subgroups) support
- ✅ Asynchronous result publishing via CloudEvents
- ✅ Comprehensive error handling with HTTP status-based categorization
- ✅ PII masking in logs for security compliance
- ✅ Production-ready with integration tests

## Architecture

```
┌─────────────────────────┐
│    Kafka Topics         │
│  - user.*               │
│  - realm.*              │
│  - client.*             │
│  - role.*               │
│  - group.*              │
└───────────┬─────────────┘
            │ CloudEvents
            ↓
┌───────────────────────────┐
│    KeycloakAdapter        │
│  - Keycloak Admin Client  │
│  - Event Processing       │
│  - Role Synchronization   │
│  - Error Handling         │
└───────────┬───────────────┘
            │ REST API (8080)
            ↓
┌───────────────────────────┐
│   Keycloak Admin API      │
│  - Realm Management       │
│  - User Management        │
│  - Client Management      │
│  - Role Management        │
│  - Group Management       │
└───────────────────────────┘
```

## Supported Operations

### Resource Types

| Resource Type | CREATE | UPDATE | DELETE | Description |
|---------------|--------|--------|--------|-------------|
| `user` | ✅ | ✅ | ✅ | User accounts with roles |
| `realm` | ✅ | ✅ | ✅ | Keycloak realms |
| `client` | ✅ | ✅ | ✅ | OAuth2/OIDC clients |
| `role` | ✅ | ✅ | ✅ | Realm roles (with composite support) |
| `group` | ✅ | ✅ | ✅ | Groups with hierarchy support |

### Subscribed Topics (16 Topics)

The adapter subscribes to all IDM lifecycle events:

**User Events (7):**
- `core.civitas.idm.user.created`
- `core.civitas.idm.user.updated`
- `core.civitas.idm.user.deleted`
- `core.civitas.idm.user.locked`
- `core.civitas.idm.user.unlocked`
- `core.civitas.idm.user.password.changed`
- `core.civitas.idm.user.password.reset`

**Realm Events (3):**
- `core.civitas.idm.realm.created`
- `core.civitas.idm.realm.updated`
- `core.civitas.idm.realm.deleted`

**Client Events (3):**
- `core.civitas.idm.client.created`
- `core.civitas.idm.client.updated`
- `core.civitas.idm.client.deleted`

**Role Events (3):**
- `core.civitas.idm.role.created`
- `core.civitas.idm.role.updated`
- `core.civitas.idm.role.deleted`

**Group Events (3):**
- `core.civitas.idm.group.created`
- `core.civitas.idm.group.updated`
- `core.civitas.idm.group.deleted`

## Configuration

### Required Properties

```properties
# Keycloak server URL
keycloak.url=http://localhost:8080

# Admin realm (for authentication)
keycloak.realm=master

# Admin credentials
keycloak.username=admin
keycloak.password=admin

# Admin client ID
keycloak.client.id=admin-cli

# Topics to subscribe to (comma-separated)
keycloak.topics=core.civitas.idm.user.created,core.civitas.idm.user.updated,core.civitas.idm.user.deleted,core.civitas.idm.realm.created,core.civitas.idm.realm.updated,core.civitas.idm.realm.deleted,core.civitas.idm.client.created,core.civitas.idm.client.updated,core.civitas.idm.client.deleted,core.civitas.idm.role.created,core.civitas.idm.role.updated,core.civitas.idm.role.deleted,core.civitas.idm.group.created,core.civitas.idm.group.updated,core.civitas.idm.group.deleted
```

### Environment Variables

All properties can be overridden with environment variables:

```bash
KEYCLOAK_URL=http://keycloak:8080
KEYCLOAK_REALM=master
KEYCLOAK_USERNAME=admin
KEYCLOAK_PASSWORD=secure-password
KEYCLOAK_CLIENT_ID=admin-cli
KEYCLOAK_TOPICS=core.civitas.idm.user.created,core.civitas.idm.user.updated
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  config-adapter:
    image: config-adapter:latest
    environment:
      ADAPTERS: keycloak
      EVENTHANDLER_NAME: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      KEYCLOAK_URL: http://keycloak:8080
      KEYCLOAK_REALM: master
      KEYCLOAK_USERNAME: admin
      KEYCLOAK_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
      KEYCLOAK_CLIENT_ID: admin-cli
      KEYCLOAK_TOPICS: core.civitas.idm.user.created,core.civitas.idm.user.updated,core.civitas.idm.user.deleted
    depends_on:
      - kafka
      - keycloak
```

## Event Format

### Input Event (CloudEvent)

#### User Create Example

```json
{
  "specversion": "1.0",
  "type": "core.civitas.idm.user.created",
  "source": "civitas.idm.provisioning",
  "id": "event-123",
  "datacontenttype": "application/json",
  "data": {
    "metadata": {
      "messageId": "msg-456",
      "timestamp": "2026-01-15T10:00:00Z",
      "source": "idm.service",
      "correlationId": "corr-789",
      "configVersion": "1.0",
      "resultTopic": "core.civitas.idm.processing.result"
    },
    "payload": {
      "targetComponent": "user",
      "targetResource": "civitas-core",
      "operation": "CREATE",
      "config": {
        "path": "realms/civitas-core/users/user-123",
        "value": {
          "resourceType": "user",
          "username": "john.doe",
          "email": "john.doe@example.com",
          "firstName": "John",
          "lastName": "Doe",
          "enabled": true,
          "realmRoles": ["user", "viewer"],
          "clientRoles": {
            "my-client": ["read", "write"]
          }
        }
      }
    }
  }
}
```

#### Realm Create Example

```json
{
  "specversion": "1.0",
  "type": "core.civitas.idm.realm.created",
  "source": "civitas.idm.provisioning",
  "id": "event-456",
  "data": {
    "metadata": {
      "messageId": "msg-789",
      "correlationId": "corr-012",
      "resultTopic": "core.civitas.idm.processing.result"
    },
    "payload": {
      "targetComponent": "realm",
      "targetResource": "new-realm",
      "operation": "CREATE",
      "config": {
        "path": "realms/new-realm",
        "value": {
          "resourceType": "realm",
          "realm": "new-realm",
          "displayName": "New Realm",
          "enabled": true,
          "registrationAllowed": false
        }
      }
    }
  }
}
```

#### Role with Composites Example

```json
{
  "specversion": "1.0",
  "type": "core.civitas.idm.role.created",
  "source": "civitas.idm.provisioning",
  "id": "event-789",
  "data": {
    "metadata": {
      "messageId": "msg-012",
      "correlationId": "corr-345",
      "resultTopic": "core.civitas.idm.processing.result"
    },
    "payload": {
      "targetComponent": "role",
      "targetResource": "civitas-core",
      "operation": "CREATE",
      "config": {
        "path": "realms/civitas-core/roles/admin",
        "value": {
          "resourceType": "role",
          "name": "admin",
          "description": "Administrator role",
          "composite": true,
          "compositeRoles": ["user", "manager", "viewer"]
        }
      }
    }
  }
}
```

#### Group with Hierarchy Example

```json
{
  "specversion": "1.0",
  "type": "core.civitas.idm.group.created",
  "source": "civitas.idm.provisioning",
  "id": "event-012",
  "data": {
    "metadata": {
      "messageId": "msg-345",
      "correlationId": "corr-678",
      "resultTopic": "core.civitas.idm.processing.result"
    },
    "payload": {
      "targetComponent": "group",
      "targetResource": "civitas-core",
      "operation": "CREATE",
      "config": {
        "path": "realms/civitas-core/groups/department-a",
        "value": {
          "resourceType": "group",
          "name": "Department A",
          "parentId": "parent-group-uuid",
          "realmRoles": ["employee"],
          "clientRoles": {
            "my-app": ["read"]
          }
        }
      }
    }
  }
}
```

### Output Event (Result)

```json
{
  "specversion": "1.0",
  "type": "core.civitas.idm.processing.result",
  "source": "civitas.config-adapter.keycloak",
  "id": "result-123",
  "datacontenttype": "application/json",
  "correlationid": "corr-789",
  "originalmessageid": "msg-456",
  "status": "SUCCESS",
  "operation": "CREATE",
  "targetresource": "civitas-core",
  "resourceid": "user-uuid-123",
  "data": {
    "correlationId": "corr-789",
    "originalMessageId": "msg-456",
    "status": "SUCCESS",
    "message": "USER_CREATE_SUCCESS",
    "resourceId": "user-uuid-123",
    "operation": "CREATE",
    "targetResource": "civitas-core",
    "timestamp": "2026-01-15T10:00:01Z",
    "source": "civitas.config-adapter.keycloak"
  }
}
```

## Error Handling

### HTTP Status Code Mapping

The adapter categorizes errors based on HTTP response status:

| HTTP Status | Exception Type | Behavior |
|-------------|----------------|----------|
| 5xx (Server Error) | `RetryableAdapterException` | Blocking retry with exponential backoff |
| 409 (Conflict) | `FatalAdapterException` | Send to DLQ (resource already exists) |
| 404 (Not Found) | `FatalAdapterException` | Send to DLQ (resource not found) |
| 4xx (Client Error) | `FatalAdapterException` | Send to DLQ (validation/config error) |
| Network Error | `RetryableAdapterException` | Blocking retry with exponential backoff |

### Keycloak-Specific Error Codes

| Code | Name | Description |
|------|------|-------------|
| 3001 | `KEYCLOAK_ERROR` | General Keycloak error |
| 3002 | `KEYCLOAK_CONFLICT` | Resource already exists (HTTP 409) |
| 3003 | `KEYCLOAK_REALM_ERROR` | Realm operation failed |
| 3004 | `KEYCLOAK_USER_ERROR` | User operation failed |
| 3005 | `KEYCLOAK_CLIENT_ERROR` | Client operation failed |
| 3006 | `KEYCLOAK_ROLE_ERROR` | Role operation failed |
| 3007 | `KEYCLOAK_GROUP_ERROR` | Group operation failed |

### Error Response Example

```json
{
  "correlationId": "corr-789",
  "originalMessageId": "msg-456",
  "status": "FAILURE",
  "errorCode": "KEYCLOAK_CONFLICT(3002)",
  "message": "Resource already exists",
  "operation": "CREATE",
  "targetResource": "civitas-core",
  "timestamp": "2026-01-15T10:00:01Z"
}
```

## Advanced Features

### Role Synchronization

When creating or updating users/groups, the adapter performs **declarative role synchronization**:

1. **Desired State:** Roles specified in the event payload
2. **Current State:** Roles currently assigned in Keycloak
3. **Synchronization:**
   - Roles in desired but not current → **Added**
   - Roles in current but not desired → **Removed**
   - Roles in both → **Unchanged**

This ensures the user/group has exactly the roles specified in the event.

**Example:**
```json
{
  "realmRoles": ["user", "admin"],
  "clientRoles": {
    "my-client": ["read", "write"]
  }
}
```

### Composite Roles

Composite roles (roles containing other roles) are supported:

```json
{
  "name": "admin",
  "description": "Administrator role",
  "composite": true,
  "compositeRoles": ["user", "manager", "viewer"]
}
```

The adapter converts the simple `Set<String>` format to Keycloak's complex `Composites` structure automatically.

### Group Hierarchy (Subgroups)

Groups can be created as children of existing groups using `parentId`:

```json
{
  "name": "Team A",
  "parentId": "department-group-uuid"
}
```

If `parentId` is null or empty, the group is created at the top level.

### PII Masking

For security compliance, the adapter masks Personally Identifiable Information in logs:

- **Email addresses:** `john.doe@example.com` → `joh***@***.***`
- **UUIDs:** `06702f15-1439-4958-b069-2ac5716c7a5c` → `06702f15***`
- **File paths:** `/home/user/data` → `/***`

## Testing

### Unit Tests

```bash
mvn test -pl config-adapter-keycloak
```

### Integration Tests

Integration tests use Testcontainers with real Keycloak instances:

```bash
mvn verify -pl config-adapter-keycloak
```

Test scenarios:
- ✅ Create/update/delete users with role synchronization
- ✅ Create/update/delete realms
- ✅ Create/update/delete clients
- ✅ Create/update/delete roles (including composites)
- ✅ Create/update/delete groups (including subgroups)
- ✅ HTTP error handling (4xx, 5xx)
- ✅ Network error handling

## Troubleshooting

### Common Issues

#### 1. Connection Refused

```
Network error to keycloak: Connection refused
```

**Solution:** Verify Keycloak is running and `keycloak.url` is correct.

#### 2. Unauthorized (401)

```
Keycloak client error during user creation: HTTP 401
```

**Solution:** Check `keycloak.username`, `keycloak.password`, and `keycloak.client.id`.

#### 3. Forbidden (403)

```
Keycloak client error during realm creation: HTTP 403
```

**Solution:** Ensure the admin user has sufficient permissions in the `keycloak.realm`.

#### 4. Conflict (409)

```
Keycloak conflict: Resource already exists
```

**Solution:** The resource (user, client, realm) already exists. Use UPDATE operation instead.

#### 5. Role Not Found

```
Realm Role 'admin' not found, skipping assignment.
```

**Solution:** Create the role before assigning it to users. Roles must exist in Keycloak first.

### Enable Debug Logging

```properties
logging.level.com.civitas.configadapter.keycloak=DEBUG
```

Or via environment:
```bash
LOGGING_LEVEL_COM_CIVITAS_CONFIGADAPTER_KEYCLOAK=DEBUG
```

## Resources

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/)
- [Keycloak Admin Client](https://www.keycloak.org/docs/latest/server_development/#admin-rest-api)
- [CloudEvents Specification](https://cloudevents.io/)

## License

European Union Public License License (EU-PL) 1.2
