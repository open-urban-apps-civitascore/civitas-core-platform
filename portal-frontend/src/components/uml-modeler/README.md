# UML Modeler Architecture

## Overview

The UML Modeler uses a **two-layer architecture** to separate session management from diagram operations.

## Architecture Layers

### Layer 1: Session Management

- **Hook**: `useMultiSessionManager()`
- **Purpose**: Manages multiple diagram tabs (create, switch, close sessions)
- **Pattern**: Redux-style actions (`createSession()`, `switchToSession()`, etc.)

### Layer 2: Diagram Operations

- **Hook**: `useActiveDiagram()`
- **Purpose**: Provides operations for the current active diagram
- **Pattern**: Context-based functions (`addNode()`, `updateNode()`, etc.)

## Key Components

```
MultiSessionLayout
├── useMultiSessionManager()           // Manages tabs/sessions
├── ActiveDiagramProviderComponent     // Bridges both layers
    └── useActiveDiagram()             // Operations for current diagram
```

## Execution Flow

1. **App starts** → `MultiSessionLayout` renders
2. **Session layer** → `useMultiSessionManager` creates tab management
3. **Bridge** → `ActiveDiagramProviderComponent` connects sessions to diagrams
4. **Diagram layer** → Components use `useActiveDiagram()` for operations
5. **UI interactions** → Work with "current diagram" without knowing about sessions
