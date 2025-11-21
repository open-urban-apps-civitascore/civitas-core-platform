# UML Modeler Development Guide

## Phase Overview

### Phase 1: Foundation ✅ DONE

- **Goal**: Type-safe architecture and state management
- **Output**: Working canvas with complete infrastructure

### Phase 2: Visual UML Nodes ✅ DONE

- **Goal**: Visual UML nodes (Class, Interface, AbstractClass, Enum)
- **Output**: Professional UML components with shared state management

### Phase 3: Relationships ✅ DONE

- **Goal**: UML edges with proper markers (inheritance, aggregation, etc.)
- **Output**: 6 UML relationship types with professional visual markers

### Phase 4: Advanced UML Features ✅ DONE

- **Goal**: Element Palette & Property Inspector for professional UML modeling workflow
- **Output**: Drag-and-drop element creation, comprehensive property editing

### Phase 5-6: Future Features

- **Tasks**: Multi-session, XMI export/import, API integration, performance optimization

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

## Phase 3 State ✅ DONE

### UML Edge Components (Ready to Use)

#### Edge Types (`components/edges/`)

```typescript
// Import and use directly - fully implemented
import { InheritanceEdge } from '../components/edges/InheritanceEdge' // Hollow triangle
import { RealizationEdge } from '../components/edges/RealizationEdge' // Dashed hollow triangle
import { AssociationEdge } from '../components/edges/AssociationEdge' // Filled arrow
import { AggregationEdge } from '../components/edges/AggregationEdge' // Hollow diamond
import { CompositionEdge } from '../components/edges/CompositionEdge' // Filled diamond
import { DependencyEdge } from '../components/edges/DependencyEdge' // Dashed arrow

// ReactFlow registry - already wired in UMLCanvas.tsx
const edgeTypes = {
  inheritance: InheritanceEdge,
  realization: RealizationEdge,
  association: AssociationEdge,
  aggregation: AggregationEdge,
  composition: CompositionEdge,
  dependency: DependencyEdge,
}
```

#### Base Edge Pattern (`components/edges/BaseUMLEdge.tsx`)

```typescript
// Reuse this for new edge types
import { BaseUMLEdge } from './BaseUMLEdge'

export const YourEdge = (props) => (
  <BaseUMLEdge {...props} relationshipType="yourType" markerEnd="yourMarker" />
)
```

### Visual Integration (Ready to Use)

#### UML Markers (`components/edges/UMLMarkers.tsx`)

```typescript
// SVG marker definitions - already imported in UMLCanvas
<UMLMarkers /> // Renders all arrow/diamond markers
```

#### Relationship Styling (`constants/umlTypes.ts`)

```typescript
// Visual styles - use these for consistency
RELATIONSHIP_STYLES.inheritance // Solid line, hollow triangle
RELATIONSHIP_STYLES.realization // Dashed line, hollow triangle
RELATIONSHIP_STYLES.association // Solid line, filled arrow
RELATIONSHIP_STYLES.aggregation // Solid line, hollow diamond
RELATIONSHIP_STYLES.composition // Solid line, filled diamond
RELATIONSHIP_STYLES.dependency // Dashed line, filled arrow
```

### Connection System (Ready to Use)

#### Handle Configuration (`components/nodes/BaseUMLNode.tsx`)

```typescript
// Universal handles - already implemented in all nodes
<Handle type="target" position={Position.Top} id="target" style={{opacity: 0}} />
<Handle type="source" position={Position.Bottom} id="source" style={{opacity: 0}} />
```

#### Edge Creation (`services/diagramService.ts`)

```typescript
// Validation - use these for edge operations
validateConnection(diagram, connection) // Basic validation
validateRelationshipConnection(diagram, connection, type) // Type-specific
```

### Integration for Phase 4

#### Add New Edge Features

1. **Use shared state**: `const { diagram, addEdge, updateEdge } = useUMLDiagram()`
2. **Reuse base pattern**: Extend `BaseUMLEdge` for new edge types
3. **Add to registry**: Register in `components/edges/edgeTypes.ts`
4. **Follow UML standards**: Use existing `RELATIONSHIP_STYLES`

#### Key Files for Extension

- `components/edges/edgeTypes.ts` - Add new edge types
- `components/edges/UMLMarkers.tsx` - Add new SVG markers
- `constants/umlTypes.ts` - Add styling for new relationships
- `services/diagramService.ts` - Extend validation logic

**DO NOT**: Create separate edge state, bypass BaseUMLEdge pattern, or recreate connection system.

---

# **UML Modeler - Phase 3: UML Relationships**

## Project Context

Professional UML Class Diagram modeler in Next.js/React with ReactFlow. **Phases 1 & 2 are complete** - Phase 3 needs to implement visual UML relationships/edges between nodes.

## Current Status ✅ Phases 1 & 2 Complete

- **Phase 1**: Complete TypeScript architecture, state management with `useUMLDiagram()` shared context
- **Phase 2**: 4 UML node components (Class, Interface, AbstractClass, Enum) rendering on canvas
- **Phase 3**: 6 UML relationship types with professional markers (inheritance, realization, association, aggregation, composition, dependency)
- **Working Canvas**: Professional UML nodes with proper styling, drag & drop, selection, and relationship creation

## Phase 4 State ✅ DONE

### Advanced User Experience

#### Element Palette System (`components/palette/`)

```typescript
// Drag-and-drop element creation - fully implemented
import { ElementPalette } from '../components/palette/ElementPalette'
import { PaletteCategory } from '../components/palette/PaletteCategory'
import { PaletteItem } from '../components/palette/PaletteItem'
import { RelationshipTool } from '../components/palette/RelationshipTool'

// Usage in layout
<ElementPalette className="flex-shrink-0" />
```

#### Property Inspector System (`components/inspector/`)

```typescript
// Comprehensive property editing - fully implemented
import { PropertyInspector } from '../components/inspector/PropertyInspector'
import { NodePropertyEditor } from '../components/inspector/NodePropertyEditor'
import { EdgePropertyEditor } from '../components/inspector/EdgePropertyEditor'
import { AttributeManager } from '../components/inspector/AttributeManager'
import { OperationManager } from '../components/inspector/OperationManager'

// Usage in layout
<PropertyInspector className="flex-shrink-0" />
```

#### Complete Layout System (`components/layout/`)

```typescript
// Three-panel professional layout
import { UMLModelerLayout } from '../components/layout/UMLModelerLayout'

// Complete UML modeler
<UMLModelerLayout />
// Renders: Palette | Canvas | Inspector
```

### Relationship Creation Workflow

#### Active Relationship Type System

```typescript
// Simplified relationship type management
const { activeRelationshipType, setActiveRelationshipType } = useUMLDiagram()

// Usage in relationship tools
const isActive = activeRelationshipType === relationshipType
setActiveRelationshipType(relationshipType) // Sets type for next connection
```

#### Edge Creation Process

1. **Select Relationship Type**: Click relationship tool in palette (inheritance, composition, etc.)
2. **Create Connection**: Drag from source node to target node
3. **Auto-Apply Type**: New edge uses selected relationship type
4. **Edit Properties**: Select edge and edit in property inspector
5. **Visual Updates**: Changes immediately reflected in diagram

### Property Editing Features

#### Node Property Editor

```typescript
// Tabbed interface for different property types
- Basic: Name, stereotype
- Attributes: Add/edit/remove class attributes with visibility, types, defaults
- Operations: Add/edit/remove methods with parameters, return types, visibility
- Literals: Manage enumeration values (for enum types)

// Usage pattern
const { updateNode } = useUMLDiagram()
updateNode(nodeId, { name: newName, stereotype: newStereotype })
```

#### Edge Property Editor

```typescript
// Comprehensive relationship property editing
- Relationship Type: Change between association, aggregation, composition, etc.
- Multiplicity: Source/target multiplicity (1, 0..1, 1..*, *)
- Roles: Source/target role names
- Navigation: Navigable, bidirectional properties
- Name: Optional relationship name

// Usage pattern
const { updateEdge } = useUMLDiagram()
updateEdge(edgeId, { type: newType, sourceMultiplicity: '1...*' })
```

#### Attribute & Operation Management

```typescript
// Dynamic CRUD operations for class members
// Attributes: name, type, visibility, default value, static flag
// Operations: name, return type, visibility, static/abstract flags, parameters

// Usage patterns
updateNode(nodeId, {
  attributes: [...existingAttributes, newAttribute],
  operations: [...existingOperations, newOperation],
})
```

### Integration Architecture

#### Shared State Updates

```typescript
// All property changes flow through shared context
const { updateNode, updateEdge } = useUMLDiagram()

// Real-time updates across all components
updateNode(nodeId, changes) // Updates node data
// → BaseUMLNode re-renders automatically
// → PropertyInspector shows updated values
// → Canvas reflects changes immediately
```

#### Type-Safe Property Management

```typescript
// Leveraging existing Phase 1-3 type system
import { UMLAttribute, UMLOperation, UMLRelationship } from '../types/uml'
import { UML_PRIMITIVE_TYPES, MULTIPLICITY_VALUES } from '../constants/umlTypes'

// Form validation and dropdowns use established constants
// No type system changes - extends existing architecture
```

### Key Implementation Patterns

#### Component Architecture

```typescript
// Follows established Phase 1-3 patterns
1. **Shared State**: All components use useUMLDiagram() hook
2. **Type Safety**: Strict TypeScript with established UML types
3. **Styling**: Consistent with UML_COLORS and design system
4. **ReactFlow Integration**: Proper node/edge data flow

// New components extend existing patterns without breaking changes
```

#### Event Flow

```typescript
// Element Palette → Canvas
1. Select element type in palette
2. Drag to canvas position
3. addNode() creates UML element
4. Canvas re-renders with new node

// Property Inspector → Canvas
1. Select element on canvas
2. Edit properties in inspector
3. updateNode() or updateEdge() applies changes
4. Canvas re-renders with updated element
```

#### Bug Fixes Applied

```typescript
// Issues resolved in Phase 4 implementation:
1. **Canvas Controls**: Fixed z-index issues, controls now visible
2. **Relationship Types**: Fixed hardcoded 'association', now uses selected type
3. **Visual Updates**: Edge type changes now update visuals immediately
4. **Exclusive Selection**: Only one relationship type active at a time
5. **React Keys**: Fixed array index key warnings with proper unique keys
```

---

## What's Still Missing

### 🚧 Phase 5: Advanced UML Features

#### **Advanced UML Elements**

- **Notes & Comments**: Text annotations with leader lines

### 💾 Import/Export & Integration

#### **File Format Support**

- **XMI Export/Import**: Industry standard UML exchange format

#### ** Multi Session**

- Show tabs to handle multiple session (use react flow component for that)

---

## Summary: Current Capabilities

### ✅ **Fully Implemented (Phases 1-4)**

- **Complete Type System**: TypeScript UML element definitions
- **State Management**: Shared context with useUMLDiagram() hook
- **4 UML Node Types**: Class, Interface, AbstractClass, Enumeration
- **6 UML Relationships**: All major relationship types with professional markers
- **Element Palette**: Drag-and-drop element creation
- **Property Inspector**: Comprehensive property editing for all elements
- **Professional Layout**: Three-panel interface (Palette | Canvas | Inspector)
- **Relationship Workflow**: Select type, create connection, edit properties
- **Real-time Updates**: All changes immediately reflected across components
