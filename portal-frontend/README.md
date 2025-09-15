## Getting Started

### Configuring Keycloak

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

### Running json server
For mocking data in the frontend the json-server can be used.

1. Adjust the port in the env variable JSON_SERVER_PORT if you need to use another port.
2. Start the json-server by running the comman `pnpm json-server`

## Testing

### Running e2e-tests

This project uses [Playwright](https://playwright.dev) for end-to-end tests.  
To run e2e tests locally, ensure that you have

1. installed Playwright and its dependencies locally (see [docs](https://playwright.dev/docs/intro#updating-playwright) for details)
2. setup and started Keycloak as mentioned above (see [Configuring Keycloak](#configuring-keycloak))
3. adjusted your local `.env.local` file with the variables matching the user you created in your local Keycloak instance

That's it!  
You can now run your e2e tests either in the CLI with  
```bash
pnpm test:e2e
```

or in the Playwright UI with  
```bash
pnpm test:e2e:ui
```