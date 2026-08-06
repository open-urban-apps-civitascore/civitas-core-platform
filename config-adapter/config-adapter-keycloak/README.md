# Keycloak Config Adapter

Translates Civitas configuration CloudEvents into [Keycloak](https://www.keycloak.org/) Admin REST API
calls, managing realms, clients, users, roles and groups. Identity provisioning is in scope. Runtime
token issuance and request authorization are Keycloak's and the gateway's concern, not this adapter's.

## Operations

The event's `targetComponent` selects the resource type and `targetResource` is the realm the operation
applies to.

| `targetComponent` | CREATE | UPDATE | DELETE | Resource |
|---|:--:|:--:|:--:|---|
| `realm` | yes | yes | yes | Keycloak realms |
| `client` | yes | yes | yes | OAuth2/OIDC clients |
| `user` | yes | yes | yes | User accounts, with role and group membership |
| `role` | yes | yes | yes | Realm roles, composites included |
| `group` | yes | yes | yes | Groups, subgroups included |

Rejected: an unrecognised `targetComponent` (`INVALID_RESOURCE_TYPE`), a `config.value` of an unknown
type (`INVALID_RESOURCE_TYPE`), and an operation other than `CREATE`, `UPDATE` or `DELETE`
(`UNSUPPORTED_OPERATION`).

`keycloak.topics` selects from `de.civitascore.idm.user.{created,updated,deleted,locked,unlocked}`,
`de.civitascore.idm.user.password.{changed,reset}` and
`de.civitascore.idm.{realm,client,role,group}.{created,updated,deleted}`. Every configured topic MUST be
one the framework knows; an unrecognised value fails startup. Dispatch is driven by the event's
`operation` and `targetComponent`, not by the topic it arrived on: the lock, unlock and password topics
are handled as user operations against the same handler.

`payload.config.value` carries a `resourceType` discriminator and is deserialised into the Keycloak
representation for the resource type; unknown fields are ignored. A result event goes to the
`resultTopic` named in the request metadata, with `source` = `de.civitascore.config-adapter.keycloak`.

## Behaviour

- **The Keycloak connection is validated at startup.** Initialization calls the server-info endpoint and
  fails with a configuration error if the URL is unreachable or the admin credentials are rejected.
- **Idempotency is a guarantee, not a fallback.** In every resource handler, HTTP 409 on `CREATE` is a
  success — the existing resource is looked up by name and reported, and for a user, roles and group
  memberships are synchronised onto the existing account so a replayed create converges rather than
  failing. A missing resource on `DELETE` is a success: the resource is already gone.
- **Membership is synchronised declaratively.** For users and groups, `realmRoles`, `clientRoles` and
  `groups` describe the desired end state. Entries present in the event but not in Keycloak are added,
  entries present in Keycloak but not in the event are removed, and the rest are left alone. A role that
  does not exist in the realm is skipped with a warning rather than failing the operation.
- **Composite roles are expanded.** A role with `composite: true` and a `compositeRoles` name list is
  converted into Keycloak's composites structure.
- **Group hierarchy.** A group with a `parentId` is created as that group's child. Without one it is
  created at the top level.
- **Required actions trigger an email.** When a created user carries `requiredActions`, Keycloak is asked
  to send an actions email so the user can verify the address and set a password. This needs SMTP
  configured on the realm. With both invitation properties set the link returns the user to the portal and
  a send failure is fatal — the user stays created and the event is dead-lettered as
  `KEYCLOAK_USER_ERROR`; with neither set a send failure is only logged as a warning.
- **Failure classification.** Connection failures and 5xx responses are retryable and are retried with
  exponential backoff. Every other 4xx response that is not an idempotent case is fatal and routes the
  event to the dead-letter queue.
- **Personally identifiable information is masked in logs.** Email addresses, UUIDs and filesystem paths
  are masked in log statements and in the messages carried by failure results, so neither logs nor result
  events expose them.

## Configuration

Keys carry the `keycloak.` prefix. Env-var names, production values, secret handling and container
configuration live in [../DEPLOYMENT.md](../DEPLOYMENT.md).

| Property | Coded default |
|---|---|
| `keycloak.topics` | — required; without it the adapter subscribes to nothing and is skipped |
| `keycloak.url` | `http://localhost:8080` |
| `keycloak.realm` | `master` |
| `keycloak.username` | `admin` |
| `keycloak.password` | `admin` |
| `keycloak.client.id` | `admin-cli` |
| `keycloak.invitation.client.id` | — |
| `keycloak.invitation.redirect.uri` | — |

The two invitation properties give the client and redirect target of the invitation email's link. They MUST
be set together or not at all; setting exactly one fails startup. When neither is set, the actions email
uses Keycloak's own default link target.

## Error codes

| Code | Name | Meaning | Retryable |
|---|---|---|---|
| 1002 | `RESOURCE_NOT_FOUND` | A user operation addressed an absent user | no |
| 1004 | `UNSUPPORTED_OPERATION` | The event's operation is not `CREATE`, `UPDATE` or `DELETE` | no |
| 1005 | `INVALID_RESOURCE_TYPE` | Unrecognised `targetComponent`, or a `config.value` of an unknown type | no |
| 2002 | `SERVICE_UNAVAILABLE` | Keycloak answered 5xx | yes |
| 2003 | `NETWORK_ERROR` | The request did not reach Keycloak | yes |
| 3002 | `KEYCLOAK_CONFLICT` | HTTP 409 outside a create path | no |
| 3003 | `KEYCLOAK_REALM_ERROR` | A realm operation failed with a 4xx | no |
| 3004 | `KEYCLOAK_USER_ERROR` | A user operation failed with a 4xx | no |
| 3005 | `KEYCLOAK_CLIENT_ERROR` | A client operation failed with a 4xx | no |
| 3006 | `KEYCLOAK_ROLE_ERROR` | A role operation failed with a 4xx | no |
| 3007 | `KEYCLOAK_GROUP_ERROR` | A group operation failed with a 4xx | no |

## Testing

```bash
mvn test -pl config-adapter-keycloak      # unit tests, no Docker
mvn verify -pl config-adapter-keycloak    # adds integration tests, requires Docker
```

Integration tests run against Keycloak and Mailpit containers started by Testcontainers, so the
required-actions email path is exercised end to end. Image tags are declared centrally in
`TestContainerImages`.
