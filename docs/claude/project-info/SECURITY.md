# Security & Access Control (TR-03187 Compliant)

## Security Architecture Principles

1. **Least Privilege Access (AR-1)**: Design interfaces with granular authorization options. Document required permissions to prevent unnecessary access elevation. Internal and external access must use minimal privileges.

2. **Legacy Technology Avoidance (AR-2)**: Prohibit outdated client-side technologies (Flash, ActiveX, Java applets). Maintain current security standards through automated pipeline checks.

3. **User Secret Distrust (AR-10)**: Never rely on user-supplied passwords or secrets to protect other users' data confidentiality. Treat all user-provided secrets as potentially compromised.

4. **Framework Standards (AR-12)**: Leverage established, proven security functions from frameworks, operating systems, and programming languages for critical features (authentication, authorization, input validation). **NEVER implement custom cryptography.**

5. **Secure Defaults (AR-14)**: All security mechanisms should be enabled by default. Configure security controls (permissions, gateways, allowlists) to the most secure usable state.

6. **Trust Zone Isolation (AR-16)**: Components in different trust zones must be isolated via firewalls, API gateways, and reverse proxies to separate security domains.

7. **Staging Environments (ORG-7)**: Prohibit production data in development, staging, or testing environments. Maintain dedicated non-production infrastructure.

## Authentication Flow (BFF Pattern)

- All components use Keycloak as the single source of truth for authentication
- OAuth2/OIDC with authorization code flow + PKCE
- Sensitive tokens never reach client-side (HTTP-only secure cookies)
- Server-side token exchange prevents client exposure
- State parameter validation protects against CSRF

## Authorization Implementation

- Implement RBAC at tenant/data space/dataset levels with inheritance
- Binary assignment: System permissions → Roles → Groups
- Ternary assignment: Data permissions → Roles → Groups → Scopes
- Default roles provided for immediate readiness (`readonly` attribute)
- Map Keycloak groups/roles to application-level permissions via configuration

## Authorization Data Model

The platform organizes authorization around five interconnected elements:

1. **Tenant**: Isolated organizational unit (e.g., City of Berlin)
2. **Users & Groups**: Individual actors and their collections within a tenant
3. **Permissions & Roles**: Atomic authorizations grouped into job-function packages
4. **Data Entities**: DataSets, DataSpaces, and DataCatalogues requiring protection
5. **Assignments**: Mappings linking groups to roles within defined scopes

### Permission Categories
- **System Permissions**: Administrative operations at instance or tenant levels (cannot be user-modified)
- **Data Permissions**: Control access to datasets, data structures, and sources (metadata and payload levels)

### Assignment Mechanisms
- **Binary Assignment**: Permissions directly map to roles; system roles assign to groups
- **Ternary Assignment**: Data roles assign to groups across multiple scopes (dataset, dataspace, or platform level)

### Scope-Based Access Control
Operates across three hierarchical levels with inheritance:
- **Dataset Level**: Default roles applied; can inherit from dataspace templates
- **DataSpace Level**: Role inheritance cascades to all contained datasets
- **Platform Level**: Global assignments propagate to all datasets across all data spaces

## Security Best Practices

- Use `UUID.randomUUID()` for session identifiers
- Use `SecureRandom` for random number generation
- Cryptographic algorithms must comply with BSI TR-02102-*
- Use prepared statements or ORMs (never string concatenation for queries)
- Validate inputs using Jakarta Bean Validation annotations (@NotNull, @Min, @Max)
- Do not automatically deserialize all fields; avoid eval-like parsing
