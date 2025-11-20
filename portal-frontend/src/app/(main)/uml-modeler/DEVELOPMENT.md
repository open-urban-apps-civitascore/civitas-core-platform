# UML Modeler Development Guide

## Phase Overview

### Phase 1: Foundation ✅ DONE

- **Goal**: Type-safe architecture and state management
- **Output**: Working canvas with complete infrastructure

### Phase 2: Visual UML Nodes ✅ DONE

- **Goal**: Visual UML nodes (Class, Interface, AbstractClass, Enum)
- **Output**: Professional UML components with shared state management

### Phase 3: Relationships

- **Goal**: UML edges with proper markers (inheritance, aggregation, etc.)
- **Tasks**: Custom edge components, relationship validation

### Phase 4-6: Advanced Features

- **Tasks**: Palette, Inspector, Multi-session, XMI, API integration

---

## Phase 1 State ✅ DONE

### Core Interfaces (Ready to Use)

#### UML Types (`types/uml.ts`)

```typescript
// Main element types - use these for all UML elements
;(UMLClass, UMLInterface, UMLAbstractClass, UMLEnumeration)
;(UMLRelationship, UMLAttribute, UMLOperation)

// Helper functions - use these for type checking
;(isAbstractElement(element), hasAttributes(element), hasOperations(element))
```

#### Diagram Types (`types/diagram.ts`)

```typescript
// ReactFlow integration - use these for node/edge data
UMLNode extends Node<UMLNodeData, UMLElementType>
UMLEdge extends Edge<UMLEdgeData, UMLRelationshipType>

// Actions - use these for state updates
DiagramAction, NodeCreationContext, EdgeCreationContext
```

### State Management (Ready to Use)

#### Core Hook (`hooks/useUMLDiagramCore.ts`)

```typescript
// Use this for non-ReactFlow components
const { diagram, addNode, updateNode, deleteNodes, addEdge, selectNode, dispatch } = useUMLDiagramCore()
```

#### ReactFlow Hook (`hooks/useUMLDiagram.ts`)

```typescript
// Use this inside ReactFlow provider only
const { ...coreFeatures, autoLayout, fitView } = useUMLDiagram()
```

#### Service Functions (`services/diagramService.ts`)

```typescript
// State reducer - already wired to hooks
diagramReducer(state, action)

// Utilities - use these for operations
;(validateConnection(), findNodeById(), getSelectedNodes())
;(serializeDiagram(), deserializeDiagram())
```

### Element Creation (Ready to Use)

#### Templates (`constants/elementTemplates.ts`)

```typescript
// Factory functions - use these to create elements
createUMLNode(elementType, position, name?)
createElement(elementType, name?)

// Auto-naming - handles German naming
getNextElementName('class') // → "NeueKlasse", "NeueKlasse2"
```

#### Constants (`constants/umlTypes.ts`)

```typescript
// Styling - use these for visual consistency
;(UML_COLORS.class, UML_COLORS.interface, UML_COLORS.abstractClass)
;(NODE_DIMENSIONS.minWidth, NODE_DIMENSIONS.headerHeight)

// Types - use these for dropdowns
;(UML_PRIMITIVE_TYPES, PRIMITIVE_TYPE_CATEGORIES)
```

## Phase 2 State ✅ DONE

### Shared State Management (CRITICAL - Use This)

#### Main Hook (`hooks/UMLDiagramContext.tsx`)

```typescript
// ALL components must use this hook (not useUMLDiagramCore directly)
import { useUMLDiagram } from '../hooks/UMLDiagramContext'

const { diagram, addNode, updateNode, deleteNodes, selectNode } = useUMLDiagram()
```

#### Provider Setup (`components/UMLDiagramProvider.tsx`)

```typescript
// Wrap your UML components with this provider
<UMLDiagramProvider>
  <ReactFlowProvider>
    <UMLCanvas />
    <YourComponent />
  </ReactFlowProvider>
</UMLDiagramProvider>
```

**⚠️ IMPORTANT**: Components using `useUMLDiagramCore()` directly get separate state instances. Always use `useUMLDiagram()` for shared state.

### Visual Components (Ready to Use)

#### UML Node Types (`components/nodes/`)

```typescript
// Import and use directly - fully implemented
import { ClassNode } from '../components/nodes/ClassNode'
import { InterfaceNode } from '../components/nodes/InterfaceNode'
import { AbstractClassNode } from '../components/nodes/AbstractClassNode'
import { EnumNode } from '../components/nodes/EnumNode'

// ReactFlow registry - already wired in UMLCanvas.tsx
const nodeTypes = {
  class: ClassNode,
  interface: InterfaceNode,
  abstractClass: AbstractClassNode,
  enumeration: EnumNode,
}
```

#### Base Pattern (`components/nodes/BaseUMLNode.tsx`)

```typescript
// Reuse this for new node types
import { BaseUMLNode, NodeSection, NodeLine } from './BaseUMLNode'

export const YourNode = ({ data, selected }) => (
  <BaseUMLNode elementType="yourType" selected={selected} name={element.name}>
    <NodeSection>{/* your content */}</NodeSection>
  </BaseUMLNode>
)
```

### Integration for Phase 3

#### Add New UML Components

1. **Use shared state**: `const { diagram, addEdge } = useUMLDiagram()`
2. **Reuse styling**: `UML_COLORS.yourType`, `NODE_DIMENSIONS`
3. **Follow patterns**: See existing node components for structure
4. **Register in nodeTypes**: Add to `components/nodes/nodeTypes.ts`

#### Key Files to Extend

- `components/nodes/nodeTypes.ts` - Add new node/edge types
- `constants/umlTypes.ts` - Add colors for new elements
- `types/uml.ts` - Add new UML element interfaces (if needed)

**DO NOT**: Create new state management, recreate node patterns, or bypass the shared context.

---

# **UML Modeler - Phase 3: UML Relationships**

## Project Context

Professional UML Class Diagram modeler in Next.js/React with ReactFlow. **Phases 1 & 2 are complete** - Phase 3 needs to implement visual UML relationships/edges between nodes.

## Current Status ✅ Phases 1 & 2 Complete

- **Phase 1**: Complete TypeScript architecture, state management with `useUMLDiagram()` shared context
- **Phase 2**: 4 UML node components (Class, Interface, AbstractClass, Enum) rendering on canvas
- **Working Canvas**: Professional UML nodes with proper styling, drag & drop, selection

## Task: Phase 3 Implementation

Implement visual UML relationship edges that connect nodes on the canvas.

### Requirements:

1. **Create 6 UML Edge Components:**
   - `InheritanceEdge` - Solid line with hollow triangle (class → class/abstract)
   - `RealizationEdge` - Dashed line with hollow triangle (class → interface)
   - `AssociationEdge` - Solid line with arrow (basic relationship)
   - `AggregationEdge` - Solid line with hollow diamond (has-a, weak)
   - `CompositionEdge` - Solid line with filled diamond (part-of, strong)
   - `DependencyEdge` - Dashed line with arrow (uses, temporary)

2. **Follow UML Standards:**
   - Correct arrow markers and line styles per UML 2.5 specification
   - Proper relationship validation (e.g., inheritance only class→class)
   - Edge labels for multiplicity and role names

3. **Integrate with Existing Architecture:**
   - Use `useUMLDiagram()` hook for shared state (NOT `useUMLDiagramCore()` directly)
   - Register edge types in `UMLCanvas.tsx` edgeTypes
   - Reuse existing `RELATIONSHIP_STYLES` from `constants/umlTypes.ts`
   - Use existing `addEdge()` and `validateConnection()` functions

## Key Files to Reference:

- `DEVELOPMENT.md` - Complete Phase 2 documentation with integration patterns
- `constants/umlTypes.ts` - `RELATIONSHIP_STYLES` with predefined edge styling
- `types/uml.ts` - `UMLRelationshipType` and relationship interfaces
- `services/diagramService.ts` - `validateConnection()` function already implemented
- `hooks/UMLDiagramContext.tsx` - Shared state management (CRITICAL to use this)

## Success Criteria:

- Users can connect UML nodes with proper relationship types
- Relationships display correct UML arrows and line styles
- Connection validation prevents invalid relationships
- Edges integrate with existing state management and selection

**Working Directory:** `/Users/kaischwetz/Documents/codestryke/civitas/civitas-core-platform`

**Important:** Read `DEVELOPMENT.md` Phase 2 documentation first - it contains critical architecture patterns you MUST follow for state management and component integration
