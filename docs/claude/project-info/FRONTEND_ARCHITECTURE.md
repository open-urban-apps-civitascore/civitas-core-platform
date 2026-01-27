# Frontend Architecture & Development Patterns

## Stack

Next.js 15 (App Router), React 19, TypeScript, TailwindCSS 4, NextAuth 5, next-intl

## Key Directories

- `src/app/(main)/` - Main application routes (datasets, dataspaces, groups, roles, users, permissions, uml-modeler)
- `src/app/api/auth/` - NextAuth API routes
- `src/app/login/` - Login page
- `src/components/` - Reusable UI components (Radix UI based)
- `src/components/ui/` - Base UI primitives (shadcn/ui pattern)
- `src/lib/` - Shared utilities and configurations
- `src/i18n/` - Internationalization setup
- `src/messages/` - Translation files
- `src/hooks/` - Custom React hooks
- `src/utils/` - Utility functions

## Authentication (BFF Pattern)

Backend-For-Frontend pattern with NextAuth 5 (beta) and Keycloak OAuth2/OIDC provider:

- JWT tokens embedded in HTTP-only secure cookies (never exposed to client-side)
- Session managed via `SessionProvider` and `SessionManager`
- Token refresh: Event-based, proactive (4-minute intervals), and activity-based (2-minute cooldown)
- Session maximum age: 30 days with 60-second expiration buffer
- Dual logout mechanism (NextAuth + Keycloak)

## Component Structure

Follow the established pattern in `src/components/` - each component in its own directory with index file.

## Form Handling

Use `react-hook-form` with Zod validation and `@hookform/resolvers`.

## Tables

Use `@tanstack/react-table` with custom table components from `src/components/table/`.

## API Calls

API routes in `src/app/api/` proxy to backend at `http://localhost:8089/v2`.

## Internationalization

Use next-intl with message files in `src/messages/`. Access via `useTranslations()` hook.

## Routing

App Router with route groups. Main app routes in `(main)` group, auth routes separate.

## Styling

TailwindCSS 4 with IBM Plex Sans/Mono fonts. Design tokens fetched from Figma.

## Production Builds

Build against `pnpm-lock.yaml`, not `package.json`, for consistency and reproducibility.
