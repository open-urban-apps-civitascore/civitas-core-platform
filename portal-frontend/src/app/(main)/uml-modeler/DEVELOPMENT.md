# UML Modeler Development Guide

## Phase Overview

### Phase 1: Foundation ✅ DONE

- **Goal**: Type-safe architecture and state management
- **Output**: Working canvas with complete infrastructure

### Phase 2: UML Elements (Next)

- **Goal**: Visual UML nodes (Class, Interface, AbstractClass, Enum)
- **Tasks**: Custom node components, node registry, element styling

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

---

## Phase 2 Implementation Guide

### 1. Create Node Components

```
components/nodes/
├── BaseUMLNode.tsx     ← Start here (common styling/behavior)
├── ClassNode.tsx       ← UML Class visual component
├── InterfaceNode.tsx   ← UML Interface visual component
├── AbstractClassNode.tsx
├── EnumNode.tsx
└── nodeTypes.ts        ← Register all node types
```

### 2. Wire Node Registry

```typescript
// In UMLCanvas.tsx, replace empty nodeTypes:
const nodeTypes = useMemo(
  () => ({
    class: ClassNode,
    interface: InterfaceNode,
    abstractClass: AbstractClassNode,
    enumeration: EnumNode,
  }),
  [],
)
```

### 3. Reuse Existing Infrastructure

#### Element Creation

```typescript
// Already implemented - just call this:
addNode({
  elementType: 'class',
  position: { x: 100, y: 100 },
  name: 'MyClass',
})
```

#### Styling

```typescript
// Use existing constants:
import { UML_COLORS, NODE_DIMENSIONS } from '../constants/umlTypes'

const nodeStyle = {
  background: UML_COLORS.class.background,
  border: `2px solid ${UML_COLORS.class.border}`,
  minWidth: NODE_DIMENSIONS.minWidth,
}
```

#### State Updates

```typescript
// Update element properties:
updateNode(nodeId, {
  name: 'NewClassName',
  attributes: [...newAttributes],
})
```

### 4. Node Component Pattern

```typescript
// Follow this pattern for all node types:
export const ClassNode: React.FC<NodeProps<UMLNodeData>> = ({ data, selected }) => {
  const element = data.element as UMLClass

  return (
    <div className="uml-node" style={getNodeStyle('class', selected)}>
      <div className="node-header">{element.name}</div>
      {/* Render attributes, operations, etc. */}
    </div>
  )
}
```

### 5. Key Integration Points

- **Canvas**: `UMLCanvas.tsx` - Update nodeTypes registry
- **Hooks**: Use `useUMLDiagramCore()` for state management
- **Types**: All UML types already defined in `types/uml.ts`
- **Styling**: Colors and dimensions in `constants/umlTypes.ts`
- **Creation**: Templates in `constants/elementTemplates.ts`

**DO NOT**: Create new state management, type definitions, or factory functions. Everything exists and is ready for Phase 2 visual components.
