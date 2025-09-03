# Keycloak Development Setup

This directory contains the Keycloak configuration for local development of the Civitas Core platform.

## Quick Start

1. **Start Keycloak:**
   ```bash
   cd dev-environment/keycloak
   docker compose up
   ```

2. **Access Keycloak:**
   - Admin Console: http://localhost:8080
   - Admin credentials: `admin` / `admin`

3. **The realm `civitas-core` will be automatically imported with:**
   - Pre-configured client: `portal-frontend`
   - All necessary scopes and roles
   - Ready-to-use configuration for the frontend