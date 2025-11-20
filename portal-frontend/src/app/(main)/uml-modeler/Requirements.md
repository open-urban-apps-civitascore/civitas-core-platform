## ✅ ReactFlow Native Features (Out-of-Box)

### Canvas & Navigation

- **Zoom/Pan** - Mouse wheel zoom, drag to pan
- **Fit-to-View** - Auto-center diagram content
- **Mini-map** - Overview navigation component
- **Grid Background** - Built-in Background component
- **Viewport management** - Built-in viewport state

### Node Management

- **Node positioning** - Drag nodes to move
- **Node selection** - Click to select (single/multi)
- **Delete nodes** - Delete key removes selected
- **Basic node structure** - id, position, data, type
- **Custom node types** - Register custom components

### Edge Management

- **Create connections** - Click & drag between handles
- **Edge selection** - Click to select edges
- **Delete edges** - Delete key removes selected
- **Custom edge types** - Register custom components
- **Connection validation** - isValidConnection prop

### State Management

- **Node/Edge state** - useState with nodes/edges
- **Change handlers** - onNodesChange, onEdgesChange
- **Helper functions** - applyNodeChanges, applyEdgeChanges, addEdge

### UI Components

- **Controls** - Zoom buttons, fit view button
- **Connection line** - Visual feedback during connection
- **Handles** - Connection points on nodes

### Hooks & Utils

- **useReactFlow** - Access instance methods
- **useNodes/useEdges** - Access current state
- **Import/Export** - toObject() for serialization

## 🔧 Custom Built Features (UML-Specific)

### UML Element Creation

- **Element Types** - Class, AbstractClass, Interface, Enumeration
- **Drag & Drop from Palette** - Custom palette implementation
- **Element Templates** - Predefined node data structures
- **Auto-naming** - "NeueKlasse", "NeuesInterface", etc.

### UML-Specific Properties

- **Stereotypes** - <>, <>, <>
- **Attributes** - Custom attribute management with visibility symbols
- **Methods/Operations** - Parameter parsing, return types
- **Visibility Symbols** - +, -, #, ~ notation
- **Enum Literals** - Special handling for enumeration values

### Advanced Type System

- **Primitive Types** - String, Integer, Boolean, etc.
- **Type References** - Cross-element type linking
- **External References** - Cross-diagram references
- **Type Resolution** - Smart type name to reference mapping
- **Custom Type Override** - Manual type entry

### UML Relationships

- **Association Types** - Bidirectional, Aggregation, Composition
- **Inheritance/Realization** - Special edge types with markers
- **Diamond Markers** - Hollow/filled for aggregation/composition
- **Multiplicity Labels** - Cardinality notation (1, 0.._, _)
- **Role Names** - Source/target role labels
- **Floating Edge Points** - Dynamic connection point calculation

### Multi-Session Management

- **Session Tabs** - Multiple diagrams open simultaneously
- **Session State** - Independent diagram instances
- **Dirty State Tracking** - Unsaved changes indicators
- **Smart Closing** - Save prompts before closing

### Inspector Panel

- **Node Inspector** - Detailed property editing UI
- **Edge Inspector** - Relationship property editing
- **Attribute Forms** - Dynamic form generation
- **Type Dropdowns** - Categorized type selection

### XMI Integration

- **XMI Import/Export** - Full UML 2.5 XMI support
- **External References** - href resolution in XMI
- **Type Mapping** - UML primitive type URIs
- **Custom Serializer** - ReactFlow → XMI conversion
- **Custom Deserializer** - XMI → ReactFlow conversion

### API Integration

- **Remote Save/Load** - HTTP API operations
- **Configurable Endpoints** - Settings-based URLs
- **ID Parameter Handling** - :id, {id} substitution
- **Error Handling** - API-specific error messages

### Visual Enhancements

- **Abstract Italics** - Style differentiation
- **Connection Mode** - Visual feedback for tool selection
- **Attribute/Method Lists** - Formatted display in nodes
- **UML Notation** - Standard-compliant visual representation

### Business Logic

- **Element Ownership** - Parent-child relationships
- **Immutable Updates** - Reducer-pattern state management
- **Cross-Reference Validation** - Type consistency checks
- **Smart Default Values** - Context-aware defaults

## Summary

ReactFlow provides excellent foundation features for building flow-based applications (about 30-40% of the total functionality), but the UML Modeler required extensive customization (60-70%) to achieve:

1. **Domain-specific elements** - UML classes, interfaces, enums
2. **Complex property management** - Attributes, methods, visibility
3. **UML-compliant relationships** - Various association types
4. **Standards compliance** - XMI import/export
5. **Enterprise features** - Multi-session, API integration
6. **Type system** - Cross-references, external types
7. **UML visual notation** - Stereotypes, markers, symbols

The custom implementation leverages ReactFlow's extensibility through custom nodes, edges, and state management while building a complete UML modeling solution on top.

Folder:

portal-frontend/src/app/(main)/uml-modeler/
├── page.tsx # Main UML Modeler page
├── Requirements.md # (existing)
├── components/ # UML-specific components
│ ├── canvas/
│ │ ├── UMLCanvas.tsx # Main ReactFlow wrapper
│ │ ├── CanvasToolbar.tsx # Zoom, fit view, etc.
│ │ └── CanvasBackground.tsx # Grid/background config
│ ├── nodes/ # Custom UML node types
│ │ ├── BaseUMLNode.tsx # Common node behavior
│ │ ├── ClassNode.tsx # UML Class node
│ │ ├── InterfaceNode.tsx # UML Interface node
│ │ ├── AbstractClassNode.tsx # UML Abstract Class node
│ │ ├── EnumNode.tsx # UML Enumeration node
│ │ └── nodeTypes.ts # Node type registry
│ ├── edges/ # Custom UML edge types
│ │ ├── AssociationEdge.tsx # Basic association
│ │ ├── AggregationEdge.tsx # Aggregation (hollow diamond)
│ │ ├── CompositionEdge.tsx # Composition (filled diamond)
│ │ ├── InheritanceEdge.tsx # Inheritance arrow
│ │ ├── RealizationEdge.tsx # Interface realization
│ │ └── edgeTypes.ts # Edge type registry
│ ├── palette/ # Element creation palette
│ │ ├── ElementPalette.tsx # Drag & drop palette
│ │ ├── PaletteItem.tsx # Draggable palette items
│ │ └── elementTemplates.ts # Default element structures
│ ├── inspector/ # Property inspector panels
│ │ ├── InspectorPanel.tsx # Main inspector container
│ │ ├── NodeInspector.tsx # Node property editor
│ │ ├── EdgeInspector.tsx # Edge property editor
│ │ ├── AttributeEditor.tsx # Attribute management
│ │ ├── MethodEditor.tsx # Method management
│ │ └── TypeSelector.tsx # Type dropdown with categories
│ ├── sessions/ # Multi-session management
│ │ ├── SessionTabs.tsx # Tab interface
│ │ ├── SessionManager.tsx # Session state management
│ │ └── UnsavedChangesDialog.tsx # Save prompts
│ ├── toolbar/ # Main application toolbar
│ │ ├── MainToolbar.tsx # File operations, etc.
│ │ ├── ConnectionModeToggle.tsx # Connection tool toggle
│ │ └── ViewControls.tsx # Zoom, minimap controls
│ └── dialogs/ # Modal dialogs
│ ├── ImportXMIDialog.tsx # XMI import interface
│ ├── ExportXMIDialog.tsx # XMI export interface
│ └── SettingsDialog.tsx # API endpoint configuration
├── hooks/ # Custom React hooks
│ ├── useUMLDiagram.tsx # Main diagram state management
│ ├── useSessionManager.tsx # Multi-session logic
│ ├── useElementCreation.tsx # Drag & drop logic
│ ├── useTypeSystem.tsx # Type resolution & validation
│ ├── useXMIImportExport.tsx # XMI serialization
│ └── useAPIIntegration.tsx # Remote save/load
├── types/ # TypeScript type definitions
│ ├── uml.ts # UML element types
│ ├── diagram.ts # Diagram state types
│ ├── session.ts # Session management types
│ ├── xmi.ts # XMI-related types
│ └── api.ts # API response types
├── services/ # Business logic services
│ ├── diagramService.ts # Core diagram operations
│ ├── typeSystemService.ts # Type resolution logic
│ ├── xmiService.ts # XMI import/export logic
│ ├── apiService.ts # HTTP API client
│ └── validationService.ts # Cross-reference validation
├── utils/ # Utility functions
│ ├── nodeUtils.ts # Node manipulation helpers
│ ├── edgeUtils.ts # Edge manipulation helpers
│ ├── layoutUtils.ts # Positioning algorithms
│ ├── serializationUtils.ts # State serialization
│ └── umlNotationUtils.ts # UML notation formatting
├── constants/ # Application constants
│ ├── umlTypes.ts # UML primitive types
│ ├── defaultStyles.ts # Default node/edge styles
│ ├── stereotypes.ts # UML stereotype definitions
│ └── elementTemplates.ts # Default element data
└── styles/ # Component-specific styles
├── nodes.css # Node styling
├── edges.css # Edge styling
└── uml-modeler.css # Global UML styles
