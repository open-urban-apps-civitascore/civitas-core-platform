<!-- VENDORED from agent-context@4c3d61f9 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Development/Code_Style_Guides/Frontend-Style-Guide.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Development/Code_Style_Guides/Frontend-Style-Guide -->

---
sidebar_position: 2
---

# Frontend Style Guide

**Version 0.9**

This section contains the code style guide for the CIVITAS/CORE V2 frontend code. This style guide should be respected at any point and also be considered in code reviews to achieve a clean, consistent codebase with a good readability.

This style guide is not immutable. If we recognise that some rules don't make sense or can be
improved, we can change them in consultation **with the team**. This shouldn't happen too
often and at best only in the beginning (the later we change a rule, the more code has to be
adjusted…).

The ESLint configuration which implements these guidelines can be found here: https://gitlab.com/civitas-connect/civitas-core/civitas-core-v2/civitas-core-platform/-/blob/main/portal-frontend/eslint.config.mjs

## Naming conventions

- **Variables, functions, instances:** camelCase
- **Constants:** SCREAMING_SNAKE_CASE
- **React components:** PascalCase
- **Classes, constructors:** PascalCase
- **Props:** camelCase for prop names, or PascalCase if the prop value is a React component
- **Acronyms, initialisms:** all uppercased, or all lowercased
- **booleans:** start with a prefix (is, has, should...)
- **Folder names:** kebab-case
- **Files containing Classes or ReactComponents:** PascalCase
- **Files containing helper functions (utils):** camelCase

## Use let and const instead of var

## Quotes

Use double quotes (") for JSX attributes, but single quotes (') for all other JS

## Don't use semicolons

Don't end statements with a semicolon

## Max line length: 120

## Use template strings for string concatenation

```
const world = "world";

❌ "hello " + world
❌ "hello ".concat(world)
✅ `hello ${world}`
```

## No magic numbers

Numbers should be declared as constants to make their meaning explicit

```
❌
const DUTY_FREE_PRICE = 100;
const FINAL_PRICE = DUTY_FREE_PRICE + (DUTY_FREE_PRICE \* 0.2);

✅
const DUTY_FREE_PRICE = 100;
const TAX = 0.2;
const FINAL_PRICE = DUTY_FREE_PRICE + (DUTY_FREE_PRICE \* TAX);
```

## Imports / Exports

- sort imports and exports alphabetically
- Don't use the following file extensions in imports: .js, .jsx, .ts, .tsx
- When building for production, always build against pnpm-lock.yaml, not against package.json

## No async code in constructor

Never do async operations inside a constructor!

## Prefer arrow functions

- use arrow functions instead of function declarations, except when they are necessary

## No chaining of array methods

```
❌ ["a", "b", "c"].filter(...).map(...)

✅
const filteredArray = ["a", "b", "c"].filter(...);
const mappedArray = filteredArray.map(...)
```

## No promise chains

- Use Async / Await

## Prefer positive conditions

```
❌ if (!hasAccess) { ... }

✅ if (hasAccess) { ... }
```

- clearer, easier to read
- exceptions: when the negative case is the main concern `if (!items.length) return;`, for early returns to avoid nesting

## Ternary Operator

- Use it for simple operations only (One condition)
- No chaining or nesting
- Otherwise use if-else-variations
- The example below shows the obvious reason: readability / maintainability

```
❌
isDeletable
? 'Delete'
: usecase.deployment_status == DeploymentState.Failed
? 'Assignments can not be deleted when deployment has an error'
: usecase?.deployment_status == DeploymentState.InProgress ||
usecase?.deployment_status == DeploymentState.Initiated
? 'Assignments can not be deleted when deployment is in progress'
: 'Assignments can not be deleted while the assignment process is running.'

✅
if (isDeletable) {
return 'Delete';
}
switch (usecase?.deployment_status) {
case DeploymentState.Failed: {
return 'Assignments can not be deleted when deployment has an error';
}
case DeploymentState.InProgress: {
return 'Assignments can not be deleted when deployment is in progress';
}
case DeploymentState.Initiated: {
return 'Assignments can not be deleted while the assignment process is running.';
}
}
return undefined;
```

## Only one React component per file

- multiple Stateless, or Pure, Components are allowed

## Prefer interfaces for component props

- define an interface for component props to keep them clear, reusable, and extendable
- don't use inline typing

```
❌
const MyComponent = ({
  children, theme, lang
}: Readonly<{
interface MyComponentProps {
  children: React.ReactNode, theme: Theme, lang: string
}>) => {

✅
interface MyComponentProps {
  children: React.ReactNode;
  theme: Theme;
  lang: string
}

const MyComponent = (props: MyComponentProps) => {
```

## Destructure props in function body

- always destructure component props inside the function body for readability

```
❌
const MyComponent = ({ children, theme, lang }: MyComponentProps) => {...}

✅
const MyComponent = (props: MyComponentProps) => {
  const { children, lang, theme } = props
  ...
}
```

## Security

Follow these security guidelines during development and code review. Based on our [SSDLC](./ssdlc-distilled.md).

### Authentication & Authorization

- Never rely on frontend-only authorization checks; all authorization logic must be enforced on the backend
- Use HTTP-only cookies for tokens (never localStorage)
- Frontend can hide UI elements, but backend must enforce access control

### Input & Output

- Validate all user inputs (frontend for UX, backend for security)
- Validate API responses before using data
- For all forms that contain sensitive information, disable autocomplete (autocomplete="off" or new-password)

```tsx
❌ Don't expose errors
showError(`Database error: ${error.message}`)

✅ Generic user message, detailed logging
logger.error('API failed', { error })
showError('Something went wrong. Please try again.')
```

### Secrets & Configuration

- Sensitive operations must use server-side API routes

### Data & Privacy

- Don't store sensitive data in localStorage/sessionStorage

### Testing

- Test input validation and error handling behavior

---

See also: [SSDLC Guidelines](./ssdlc-distilled.md) for comprehensive security requirements.
