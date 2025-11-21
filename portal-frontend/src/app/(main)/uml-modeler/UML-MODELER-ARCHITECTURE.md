# UML Modeler Architecture

## Overview

The UML Modeler uses a **single-provider architecture** with centralized state management. All diagram state is managed at the session level and accessed through a global context.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    MultiSessionLayout                        │
│  ┌─────────────────────────────────────────────────────┐    │
│  │          ActiveDiagramProviderComponent              │    │
│  │  (Provides diagram context for entire app)          │    │
│  └──────────────────────┬──────────────────────────────┘    │
│                         │                                    │
│  ┌──────────────┐  ┌───┴────┐  ┌─────────────────────┐    │
│  │ElementPalette│  │TabBar   │  │PropertyInspector     │    │
│  │              │  │         │  │                     │    │
│  │ - Drag items │  │- Switch │  │ - Edit properties   │    │
│  │ - Set tools  │  │  tabs   │  │ - Uses context      │    │
│  └──────────────┘  └─────────┘  └─────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    TabContent                        │    │
│  │  ┌──────────────────────────────────────────────┐   │    │
│  │  │              ReactFlowProvider                │   │    │
│  │  │  ┌────────────────────────────────────────┐  │   │    │
│  │  │  │            UMLCanvas                    │  │   │    │
│  │  │  │  - Renders diagram nodes/edges         │  │   │    │
│  │  │  │  - Handles user interactions           │  │   │    │
│  │  │  │  - Uses context for all operations     │  │   │    │
│  │  │  └────────────────────────────────────────┘  │   │    │
│  │  └──────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## State Flow

### 1. Session Manager (Source of Truth)

```typescript
// All diagram state is stored in sessions
interface DiagramSession {
  id: string
  name: string
  diagram: UMLDiagram // ← Single source of truth
  isDirty: boolean
  lastModified: Date
}
```

### 2. ActiveDiagramProvider (Global Access)

- Wraps the entire MultiSessionLayout
- Provides access to the active session's diagram
- Routes all updates back to the session manager

### 3. Component Updates

```
User Action → Component → useActiveDiagram → ActiveDiagramProvider → SessionManager
     ↑                                                                      ↓
     └──────────────────── Re-render with new state ←─────────────────────┘
```

## Context Usage

### Hook: `useActiveDiagram()`

All components use this single hook to access diagram state and operations:

```typescript
const {
  // State
  diagram, // Current diagram or null
  selectedNode, // Currently selected node
  selectedEdge, // Currently selected edge

  // Operations
  addNode, // Add new nodes
  updateNode, // Update node properties
  deleteNodes, // Delete nodes
  selectNode, // Select/deselect nodes
  addEdge, // Add relationships
  updateEdge, // Update edge properties
  // ... more operations
} = useActiveDiagram()
```

### Components Using Context

| Component              | Purpose                           | Context Usage                                         |
| ---------------------- | --------------------------------- | ----------------------------------------------------- |
| **UMLCanvas**          | Main diagram editor               | All operations (add, update, delete, select)          |
| **PropertyInspector**  | Shows selected element properties | `selectedNode`, `selectedEdge`                        |
| **NodePropertyEditor** | Edit node properties              | `updateNode`                                          |
| **EdgePropertyEditor** | Edit edge properties              | `updateEdge`                                          |
| **AttributeManager**   | Manage class attributes           | `updateNode`                                          |
| **OperationManager**   | Manage class operations           | `updateNode`                                          |
| **RelationshipTool**   | Set active relationship type      | `activeRelationshipType`, `setActiveRelationshipType` |

## Key Benefits

1. **Single Source of Truth**: No state synchronization issues
2. **Preserved State**: Tab switching maintains all diagram data
3. **Immediate Updates**: Property changes instantly reflect in canvas
4. **Simple Architecture**: One provider, one context, one hook
5. **Type Safety**: Full TypeScript support throughout

## Migration Notes

## Implementation Details

- **No Local State**: All diagram state lives in the session manager
- **No Provider Nesting**: Single provider at the top level
- **Reactive Updates**: Changes propagate automatically through React
- **Performance**: Memoized selectors prevent unnecessary re-renders
