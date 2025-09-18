# Postgres Development Setup

This directory contains the Postgres configuration for local development of the Civitas Core platform - Backend.

## Quick Start

1. **Start Postgres:**
   ```bash
   cd dev-environment/postgres
   docker compose up
   ```

2. **Access Postgres:**
   - Host: localhost
   - Port: 5432
   - Database: `portal_backend`
   - User: `iot`
   - Password: `iot`

3. **Notes:**
   - The database is ready to use after startup.
   - You can connect using `psql` or a GUI tool like DBeaver.
