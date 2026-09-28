<!-- VENDORED from agent-context@4c3d61f9 — DO NOT EDIT HERE.
     Change it in civitas-connect/civitas-core/civitas-core-v2/agent-context, then run ./sync-to-targets.sh -->

<!-- COPY — DO NOT EDIT HERE. Refresh with ./sync-guidelines.sh
     Source: documentation@e2f8a6fc · version 2.0-rc2 · Development/Code_Style_Guides/Backend-Style-Guide.md
     Web:    https://docs.core.civitasconnect.digital/docs_v2/Development/Code_Style_Guides/Backend-Style-Guide -->

---
sidebar_position: 2
---

# Backend Style Guide

**Version 0.9**

This section contains the code style guide for the CIVITAS/CORE V2 backend code. This guide should be followed at all times and considered during code reviews to maintain a clean, consistent, and readable codebase.

This style guide is not immutable. If some rules do not make sense or can be improved, changes can be made in consultation **with the team**. This should happen rarely and ideally only at the start of the project (the later a rule is changed, the more code must be adjusted).

The **Google Java Style Guide** is used as the reference for all coding conventions:
[https://google.github.io/styleguide/javaguide.html](https://google.github.io/styleguide/javaguide.html)

The **Google Java Format** plugin is used to enforce all formatting rules, including indentation, braces, line wrapping, imports, and more. Developers should **not manually adjust formatting**—always use the plugin.

---

## Naming Conventions

- **Classes & Interfaces:** `UpperCamelCase`
- **Methods & Variables:** `lowerCamelCase`
- **Constants:** `SCREAMING_SNAKE_CASE`
- **Packages:** lowercase, separated by dots
- **Acronyms / Initialisms:** all uppercase (e.g., `URLParser`)

---

## Formatting & Indentation

- Use **Google Java Format** for all code formatting
- Opening braces `{` on the same line
- Indentation is handled automatically by the plugin
- Do not manually break lines or adjust spacing

---

## Documentation

- All **public classes** must have Javadoc with description
- Javadoc formatting should follow the Google Java Style

---

## Imports

- Imports are automatically sorted and grouped by the plugin
- Avoid wildcard imports (e.g., `import java.util.*;`)
- Import only packages that are actually used
- Import specific versions (that's ensured by design when using Maven)

---

## Constants & Magic Numbers

- Use named constants instead of magic numbers

```java
❌
double total = price + (price * 0.2);

✅
static final double TAX_RATE = 0.2;
double total = price + (price * TAX_RATE);
````

---

## Project Structure

The backend follows a **layered, exercise-based folder structure** to maintain clarity and separation of concerns:

* **Controller:** Handles HTTP requests/responses.
* **Service:** Contains business logic.
* **Repository:** Handles data access/persistence.
* **Model / Entity:** Represents domain objects, database entities, or DTOs.

**Example folder layout:**

```
src/main/java/com/opsource/
├── controller/
│   └── UserController.java
├── service/
│   └── UserService.java
├── repository/
│   └── UserRepository.java
├── model/
│   ├── User.java
│   └── UserDTO.java
```

**Do / Don’t Examples**

```java
❌
// Mixing controllers and services in the same folder
src/main/java/com/opsource/
├── UserController.java
├── UserService.java

✅
// Separate folders by responsibility
src/main/java/com/opsource/controller/UserController.java
src/main/java/com/opsource/service/UserService.java
```

**Notes:**

* Each layer should only interact with the layers directly above or below it. For example, controllers call services, services call repositories.

---

## OpenAPI / API Design

* All endpoints must have an **OpenAPI specification** defined **from the start**
* The OpenAPI spec is the **source of truth** for the API
* Any changes to the API must update the OpenAPI spec immediately
* Follow **RESTful best practices**:
    * Use plural nouns for resource names (e.g., `/users` instead of `/user`)
    * Use proper HTTP methods (`GET`, `POST`, `PUT`, `DELETE`)
    * Return standard HTTP status codes
* Include **request and response schemas** in the OpenAPI definition
* Documentation should be automatically generated from the OpenAPI spec

**Do / Don’t Examples**

```java
❌
// Implementing an endpoint without OpenAPI spec
@PostMapping("/user/create")
public User createUser(User user) { ... }

✅
// Define in OpenAPI first
@PostMapping("/users")
public User createUser(@RequestBody User user) { ... }
```


## Type Inference with `var`

* Use `var` **only** when the type is immediately and clearly evident from the initializer
* **Never** use `var` in public APIs, fields, method parameters, or return values
* With larger scopes, reassignments, or complex expressions: use **explicit types**
* Consider readability—code should be understandable without additional context

**Do / Don't Examples**

```java
❌
// Type not immediately recognizable
var user = repository.findById(id);
var result = processData(input);

// In public API
public var getUser(var id) { ... }

// In fields
private var userService;

✅
// Type clearly evident
var user = new User();
var count = 42;
var name = "John Doe";
var isActive = true;

// In local loops with clear context
var users = List.of(user1, user2);
for (var user : users) {
    process(user);
}

// Explicit type when context is unclear
Optional<User> user = repository.findById(id);
List<String> names = extractNames(users);
Stream<Data> dataStream = collection.stream();
```

## Error Handling

* Throw **specific exceptions**; avoid generic `Exception`
* Prefer `try-with-resources` for AutoCloseable resources


## Testing

* Test names should describe behavior: `method_condition_expectedResult`
* Put each test class in the same package as the class it tests, inside `src/test/java`, to match the main code structure

**Do / Don’t Examples**

```java
❌
@Test
public void test1() { ... }

✅
@Test
public void divide_whenDenominatorIsZero_shouldThrowArithmeticException() { ... }
```

### Testing integration with external components

All components of CIVITAS/CORE must be able to be tested independent from each other.
No actual network requests may be made by the tests, all requests are expected to be mocked. For this, external components, like databases or Keycloak, should be mocked by using [Testcontainers](https://testcontainers.com/), [Mockito](https://site.mockito.org/) or similar solutions. These mocks must be based on documented interfaces of the external components.

---

## Logging

* Use a standard logging framework (**SLF4J**)
* Avoid `System.out.println`
* Use Encode.forJava to escape control characters in data to be logged
* Use appropriate log levels (`DEBUG`, `INFO`, `WARN`, `ERROR`) and include context
* Log security-relevant events (authentication failures, permission denials, suspicious activities)

**Do / Don't Examples**

```java
❌
System.out.println("Failed to save user");

✅
logger.error("Failed to save user with id {}", userId, exception);

✅ Log security events
logger.warn("Access denied for user {} attempting to access resource {}", username, resourceId);
logger.warn("Failed login attempt for user {}", username);
```

---

## CI Integration

* The CI pipeline must enforce:
    * Google Java Format compliance
    * OpenAPI spec validation
    * The test suite executes successfully
* Developers must run the formatter locally before submitting pull requests

---

## Security

Follow these security guidelines during development and code review. Based on our [SSDLC](./ssdlc-distilled.md).

### Authentication & Authorization

- Every endpoint must have explicit authentication and authorization checks
- Use Spring Security annotations (`@PreAuthorize`, `@Secured`) at controller level
- Verify permissions for each resource access (not just endpoint access)
- Never trust client-provided data for authorization decisions
- Enforce authorization at service and controller level

```java
❌ No authorization
public User getUser(@RequestParam String userId) { ... }

✅ Explicit authorization at controller
@PreAuthorize("hasRole('ADMIN') or @userService.isOwner(#userId, authentication)")
public User getUser(@PathVariable String userId) { ... }

✅ Also enforce at service level
@Service
public class UserService {
  public User getUser(String userId, Authentication auth) {
    if (!isOwner(userId, auth) && !hasRole(auth, "ADMIN")) {
      throw new AccessDeniedException("Not authorized");
    }
    return userRepository.findById(userId);
  }
}
```

### Input Validation

- Validate all inputs at the API boundary using Bean Validation (`@Valid`, `@NotNull`, `@Size`)
- Validate file uploads (type, size, content)
- When deserializing data structures, do not automatically deserialize all fields passed in and do not use "eval" or similar to parse JSON. Only deserialize expected data.
- Always use parameterized queries or JPA/Hibernate (never string concatenation)

```java
❌ SQL Injection vulnerability
String query = "SELECT * FROM users WHERE name = '" + userName + "'";

✅ Parameterized query
@Query("SELECT u FROM User u WHERE u.name = :name")
User findByName(@Param("name") String name);
```

### API Security

- Implement rate limiting for public endpoints (will likely be handled by platform)
- Use proper HTTP methods and status codes
- Enable CORS only for trusted origins
- Validate Content-Type headers

### Dependencies and Base Images

- Minimal container base images will be used, preferably gcr.io/distroless/java21-debian13. Ensure your code can run in this environment or be prepared to deliver any additional components you might need with the binaries.


### Miscellaneous

- When generating identifiers for sessions, use UUID.randomUUID() in Java or, for other languages, a Level 4-UUID generating library function based on a strong Pseudo Random Number Generator (PRNG)
- When generating random numbers in Java, use SecureRandom.


### Testing

- Include security scenarios in tests for non-trivial changes (unauthorized access, invalid input, permission checks)
- Test both positive and negative authorization cases

```java
✅ Test security scenarios
@Test
public void getUser_whenUnauthorized_shouldThrow403() {
  assertThrows(AccessDeniedException.class,
    () -> userService.getUser(otherUserId, unauthorizedAuth));
}

@Test
public void createUser_withInvalidInput_shouldRejectRequest() {
  assertThrows(ValidationException.class,
    () -> userService.createUser(invalidUser));
}
```

---

See also: [SSDLC Guidelines](./ssdlc-distilled.md) for comprehensive security requirements.
