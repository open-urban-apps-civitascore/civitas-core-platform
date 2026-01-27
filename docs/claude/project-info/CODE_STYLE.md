# Code Style Guides

## Frontend Code Style

### Naming Conventions
- Variables, functions, instances: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- React components & classes: `PascalCase`
- Props: `camelCase` (or `PascalCase` for component values)
- Booleans: prefix with `is`, `has`, or `should`
- Folders: `kebab-case`
- Class/component files: `PascalCase`
- Utility files: `camelCase`
- Acronyms: all uppercase or all lowercase

### Code Style
- Use `let` and `const` (never `var`)
- Double quotes (`"`) for JSX attributes
- Single quotes (`'`) for all other JavaScript
- Template strings for concatenation: `` `hello ${world}` ``
- Omit semicolons at statement endings
- Maximum line length: 120 characters
- Avoid magic numbers (use named constants)
- Sort imports/exports alphabetically
- Exclude file extensions in import statements (`.js`, `.jsx`, `.ts`, `.tsx`)

### Async Patterns
- Never execute async operations in constructors
- Use async/await instead of promise chains
- Avoid chaining array methods; assign intermediate results to variables

### Conditional Logic
- Prefer positive conditions for readability
- Use ternary operators only for simple, single conditions
- Use if-else statements or switch blocks for complex logic

### React Component Guidelines
- One component per file (multiple stateless components allowed)
- Define interfaces for component props (not inline types)
- Destructure props within function body, not in parameters
- Each component in its own directory with index file

## Backend Code Style

### Naming Conventions
- Classes & Interfaces: `UpperCamelCase`
- Methods & Variables: `lowerCamelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Packages: lowercase with dot separation
- Acronyms: all uppercase (e.g., `URLParser`)

### Code Quality Rules
- Use Google Java Format plugin (enforced via Spotless)
- Replace magic numbers with named constants
- Prohibit wildcard imports (`import java.util.*;`)
- All public classes must have Javadoc
- Use `var` sparingly (only when types are immediately obvious)
- Throw specific exceptions (not generic `Exception`)
- Use try-with-resources for AutoCloseable objects

### Logging
- Use SLF4J framework (avoid System.out.println)
- Default to not logging personal data (mask sensitive information with asterisks)
- Use appropriate log levels (DEBUG, INFO, WARN, ERROR)

## Formatting Enforcement

**Before committing**:
- Java: `mvn spotless:apply`
- Frontend: `pnpm format`
- Use pre-commit hooks for consistent code formatting
