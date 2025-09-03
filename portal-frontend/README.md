## Getting Started

1. Start keycloak and import the realm-export by running the following command in `dev-environment/keycloak`:
   ```bash
   docker compose up
   ```

2. Copy file `portal-frontend/.env.local.template` and call it `portal-frontend/.env.local`

3. Replace `KEYCLOAK_CLIENT_SECRET` with the client secret. To get the secret follow the link or steps below (available under http://localhost:8080/admin/master/console/#/civitas-core/clients/5e8301de-5869-4a80-91ef-637813579d3f/credentials):
   - 3.1 Log into Keycloak admin panel (User: admin, Password: admin)
   - 3.2 Select in keycloak the correct realm: civitas-core
   - 3.3 Navigate to clients
   - 3.4 Select portal-frontend client
   - 3.5 Navigate to credentials tab
   - 3.6 Copy client secret
   - 3.7 Select "Users" to add new test users

4. Run the development server:
   ```bash
   pnpm dev
   ```
