# Pipeline Editor

A visual pipeline editor for defining data processing pipelines in CIVITAS/CORE, using a UML activity diagram style.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Core Concepts](#core-concepts)
- [State Management](#state-management)
- [Key Components](#key-components)
- [Data Flow](#data-flow)
- [Extending the Editor](#extending-the-editor)
- [Validation](#validation)
- [Backend Integration](#backend-integration)
- [Pipeline Types](#pipeline-types)

---

## Overview

The Pipeline Editor provides a visual interface for creating and configuring data processing pipelines. Users can drag nodes from a palette onto a canvas, connect them to define data flow, and configure each node through an inspector panel.

### Features

- **Multi-tab support** – Edit multiple pipelines simultaneously
- **Drag & drop palette** – Categorized node types for easy access
- **React Flow canvas** – Interactive diagram editing with zoom, pan, and selection
- **Inspector panel** – Context-sensitive configuration for selected nodes
- **Validation** – Pipeline validation before saving
- **Entity binding** – Nodes can reference datasources, APIs, and FROST servers

### Technology Stack

- React 18 with TypeScript
- React Flow for canvas rendering
- Node-based visual mapping editor (Schema-as-MegaNode) for field mappings
- Zustand-like state management via React Context + useReducer

---

## Architecture

### Folder Structure

```
pipeline-editor/
├── page.tsx                        # Entry point
├── _components/
│   ├── layout/
│   │   ├── PipelineEditorLayout.tsx    # Main 3-panel layout
│   │   └── PipelineEditorWrapper.tsx   # ReactFlowProvider wrapper
│   ├── canvas/
│   │   └── PipelineCanvas.tsx          # React Flow canvas
│   ├── nodes/
│   │   ├── base/
│   │   │   └── BasePipelineNode.tsx    # Base node component
│   │   ├── control/
│   │   │   ├── StartNode.tsx
│   │   │   └── EndNode.tsx
│   │   ├── source/
│   │   │   └── DataSourceNode.tsx
│   │   ├── trigger/
│   │   │   ├── ApiRequestNode.tsx
│   │   │   ├── ApiResponseNode.tsx
│   │   │   └── CronNode.tsx
│   │   ├── storage/
│   │   │   └── FrostNode.tsx
│   │   ├── transform/
│   │   │   └── MappingNode.tsx
│   │   └── nodeTypes.ts                # Node type registry
│   ├── edges/
│   │   ├── FlowEdge.tsx                # Custom edge component
│   │   └── edgeTypes.ts                # Edge type registry
│   ├── palette/
│   │   ├── PipelinePalette.tsx         # Left sidebar
│   │   ├── PaletteCategory.tsx
│   │   └── PaletteItem.tsx
│   ├── inspector/
│   │   ├── PipelineInspector.tsx       # Right sidebar
│   │   ├── components/
│   │   │   ├── InspectorHeader.tsx
│   │   │   ├── EntitySelector.tsx
│   │   │   └── EntityMetadata.tsx
│   │   ├── panels/
│   │   │   ├── ControlPanel.tsx
│   │   │   ├── DataSourcePanel.tsx
│   │   │   ├── ApiPanel.tsx
│   │   │   ├── FrostPanel.tsx
│   │   │   ├── CronPanel.tsx
│   │   │   └── MappingPanel.tsx
│   │   └── validation/
│   │       ├── ValidationPanel.tsx
│   │       └── ValidationIssueItem.tsx
│   ├── tabs/
│   │   ├── PipelineTabBar.tsx
│   │   └── PipelineToolbar.tsx
│   └── providers/
│       └── PipelineEditorProvider.tsx  # Main context provider
├── _hooks/
│   ├── use-pipeline-session.ts         # Multi-session management
│   └── use-active-pipeline.ts          # Active pipeline context hook
├── _services/
│   ├── pipelineService.ts              # Pipeline state operations
│   ├── sessionService.ts               # Session management
│   ├── validationService.ts            # Pipeline validation
│   ├── entityService.ts                # Entity data fetching
│   ├── modelBuilderService.ts          # RedPandaConnect model generation
│   └── payloadBuilderService.ts        # Backend payload assembly
├── _types/
│   ├── pipeline.ts                     # Core types
│   ├── nodes.ts                        # Node data types
│   ├── session.ts                      # Session types
│   └── context.ts                      # Context value types
└── _constants/
    ├── nodeCategories.ts               # Category definitions
    ├── paletteItems.ts                 # Palette configuration
    ├── pipelineStyles.ts               # Visual constants
```

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│                          Tab Bar                                 │
├──────────┬────────────────────────────────────────┬─────────────┤
│          │               Toolbar                   │             │
│          ├────────────────────────────────────────┤             │
│          │                                        │             │
│ Palette  │                                        │  Inspector  │
│  (Left)  │              Canvas                    │   (Right)   │
│          │          (React Flow)                  │             │
│          │                                        │             │
│          │                                        │             │
└──────────┴────────────────────────────────────────┴─────────────┘
```

---

## Core Concepts

### Pipeline

A pipeline represents a complete data processing workflow. It contains:

```typescript
interface Pipeline {
  id: string
  name: string
  description: string
  nodes: PipelineNode[]
  edges: PipelineEdge[]
  createdAt: string
  updatedAt: string
}
```

### Nodes

Nodes are the building blocks of a pipeline. Each node has a type, position, and configuration data.

```typescript
interface PipelineNode {
  id: string
  type: PipelineNodeType
  position: { x: number; y: number }
  data: PipelineNodeData
  selected?: boolean
}
```

#### Node Types

| Category       | Node Type     | Description                    | Configurable     |
| -------------- | ------------- | ------------------------------ | ---------------- |
| Control        | `start`       | Pipeline entry point (●)       | No               |
| Control        | `end`         | Pipeline exit point (○)        | No               |
| Sources        | `dataSource`  | Input data source              | Yes (entity)     |
| Trigger        | `apiRequest`  | REST API trigger               | Yes (entity)     |
| Trigger        | `apiResponse` | REST API response              | Yes (entity)     |
| Trigger        | `cron`        | Scheduled trigger              | Yes (expression) |
| Storage        | `frost`       | FROST/SensorThings persistence | Yes (entity)     |
| Transformation | `mapping`     | Bloblang data transformation   | Yes (code)       |

### Edges

Edges connect nodes and define data flow direction. Flow direction is left-to-right.

```typescript
interface PipelineEdge {
  id: string
  source: string // Source node ID
  target: string // Target node ID
  sourceHandle?: string
  targetHandle?: string
}
```

### Sessions

A session represents an open pipeline in a tab. Multiple sessions can be active simultaneously.

```typescript
interface PipelineSession {
  id: string
  name: string
  pipeline: Pipeline
  isDirty: boolean // Has unsaved changes
  createdAt: Date
  updatedAt: Date
}
```

---

## State Management

### Context Provider

The `PipelineEditorProvider` wraps the editor and provides state to all child components.

```typescript
// Access state and actions in any component
const {
  pipeline, // Current pipeline
  isDirty, // Has unsaved changes
  selectedNode, // Currently selected node

  // Node operations
  addNode,
  updateNode,
  deleteNodes,

  // Edge operations
  addEdge,
  deleteEdges,

  // Validation
  runValidation,
  validationResult,
  canSave,

  // Save
  savePipeline, // POST to backend API
  isSaving, // True during save request
} = useActivePipeline()
```

### Key Hooks

#### `useActivePipeline()`

Primary hook for accessing the current pipeline and operations. Use this in canvas components, nodes, and the inspector.

#### `usePipelineSession()`

Hook for session management (tabs). Use this for creating, closing, and switching between pipelines.

```typescript
const { sessions, activeSessionId, createSession, closeSession, switchToSession, updateSessionName } =
  usePipelineSession()
```

### Services

| Service                 | Purpose                                      |
| ----------------------- | -------------------------------------------- |
| `pipelineService`       | Pipeline reducer, node/edge operations       |
| `sessionService`        | Session reducer, multi-tab management        |
| `validationService`     | Pipeline validation rules                    |
| `entityService`         | Entity data fetching (datasources, APIs)     |
| `modelBuilderService`   | RedPandaConnect config generation from graph |
| `payloadBuilderService` | Assembles full backend payload from pipeline |

---

## Key Components

### BasePipelineNode

All activity-style nodes extend from `BasePipelineNode`. It provides:

- Connection handles (input on left, output on right)
- Configured/unconfigured visual states
- Selection highlighting
- Validation severity indicator

```typescript
<BasePipelineNode
  nodeId="node-1"
  category="sources"
  label="DataSource"
  sublabel="sensor-data"
  icon={<Database size={18} />}
  configured={true}
  selected={false}
  validationSeverity="none"
/>
```

### Inspector Panels

Each node type has a corresponding inspector panel:

| Node Type   | Panel Component   | Features                                 |
| ----------- | ----------------- | ---------------------------------------- |
| Start/End   | `ControlPanel`    | Static description                       |
| DataSource  | `DataSourcePanel` | Entity dropdown + metadata               |
| ApiRequest  | `ApiPanel`        | Entity dropdown + metadata               |
| ApiResponse | `ApiPanel`        | Entity dropdown + metadata               |
| Cron        | `CronPanel`       | Expression input                         |
| Frost       | `FrostPanel`      | Entity dropdown + metadata               |
| Mapping     | `MappingPanel`    | Source/target selectors + mapping editor |

### EntitySelector

Reusable dropdown component for selecting entities (datasources, APIs, FROST servers).

```typescript
<EntitySelector
  label="Select DataSource"
  entities={entities}
  selectedId={nodeData.entityId}
  isLoading={isLoading}
  onChange={(entity) => onUpdate({ entityId: entity?.id })}
/>
```

---

## Data Flow

### Drag & Drop from Palette

1. User drags a `PaletteItem`
2. `onDragStart` sets `dataTransfer` with node type
3. User drops on `PipelineCanvas`
4. `onDrop` handler extracts node type and position
5. `addNode()` creates node with default data
6. React Flow renders the new node

### Node Configuration

1. User selects a node on canvas
2. `PipelineInspector` detects selection via `selectedNode`
3. Inspector renders appropriate panel based on node type
4. User changes configuration (e.g., selects entity)
5. Panel calls `updateNode(nodeId, { ...newData })`
6. Node re-renders with updated data

### Validation Flow

1. User clicks "Validate" in toolbar
2. `runValidation()` executes validation rules
3. Results stored in context (`validationResult`)
4. Inspector shows `ValidationPanel` with issues
5. Nodes display validation severity indicators
6. If valid, `canSave` becomes `true`

---

## Extending the Editor

### Adding a New Node Type

1. **Define the node type** in `_types/pipeline.ts`:

```typescript
export type PipelineNodeType =
  | 'start'
  | 'end'
  | 'dataSource'
  // ... existing types
  | 'newNodeType' // Add here
```

2. **Create node data interface** in `_types/nodes.ts`:

```typescript
export interface NewNodeData extends BasePipelineNodeData {
  nodeType: 'newNodeType'
  // Add node-specific fields
  customField?: string
}

// Add to union type
export type PipelineNodeData =
  | ControlNodeData
  | DataSourceNodeData
  // ...
  | NewNodeData

// Add type guard
export function isNewNodeData(data: PipelineNodeData): data is NewNodeData {
  return data.nodeType === 'newNodeType'
}

// Add to createDefaultNodeData factory
case 'newNodeType':
  return {
    nodeType: 'newNodeType',
    label: 'New Node',
    configured: false,
    customField: undefined,
  }
```

3. **Create the node component** in `_components/nodes/[category]/NewNode.tsx`:

```typescript
import { memo } from 'react'
import { NodeProps } from 'reactflow'
import { BasePipelineNode } from '../base/BasePipelineNode'
import { useActivePipeline } from '../../../_hooks'
import { SomeIcon } from 'lucide-react'

export const NewNode = memo(function NewNode({ id, data, selected }: NodeProps) {
  const { getNodeValidationSeverity } = useActivePipeline()

  return (
    <BasePipelineNode
      nodeId={id}
      category="yourCategory"
      label={data.label}
      sublabel={data.customField || 'Not configured'}
      icon={<SomeIcon size={18} />}
      configured={data.configured}
      selected={selected}
      validationSeverity={getNodeValidationSeverity(id)}
    />
  )
})
```

4. **Register the node type** in `_components/nodes/nodeTypes.ts`:

```typescript
import { NewNode } from './[category]/NewNode'

export const pipelineNodeTypes = {
  // ... existing types
  newNodeType: NewNode,
}
```

5. **Add to palette** in `_constants/paletteItems.ts`:

```typescript
{
  id: 'yourCategory',
  title: 'Your Category',
  items: [
    {
      type: 'newNodeType',
      label: 'New Node',
      icon: 'IconName',
      description: 'Description for tooltip',
    },
  ],
}
```

6. **Create inspector panel** in `_components/inspector/panels/NewNodePanel.tsx`:

```typescript
interface NewNodePanelProps {
  nodeId: string
  data: NewNodeData
  onUpdate: (data: Partial<NewNodeData>) => void
}

export function NewNodePanel({ nodeId, data, onUpdate }: NewNodePanelProps) {
  return (
    <div className="space-y-4">
      {/* Your panel UI */}
    </div>
  )
}
```

7. **Add panel routing** in `PipelineInspector.tsx`:

```typescript
import { isNewNodeData } from '../../_types'
import { NewNodePanel } from './panels/NewNodePanel'

// In the render logic:
if (isNewNodeData(selectedNode.data)) {
  return <NewNodePanel nodeId={selectedNode.id} data={selectedNode.data} onUpdate={handleUpdate} />
}
```

### Adding Validation Rules

Add rules to `_services/validationService.ts`:

```typescript
// In validatePipeline function:
const newNodes = nodes.filter(n => n.type === 'newNodeType')

// Example: Require at least one newNode
if (newNodes.length === 0) {
  errors.push({
    message: 'Pipeline must have at least one New Node',
    severity: 'error',
  })
}

// Example: newNode must be configured
for (const node of newNodes) {
  if (!node.data.configured) {
    errors.push({
      message: `New Node "${node.data.label}" is not configured`,
      severity: 'error',
      nodeId: node.id,
    })
  }
}
```

---

## Validation

### Rules

| Rule                         | Severity | Description                              |
| ---------------------------- | -------- | ---------------------------------------- |
| Missing Start node           | Error    | Pipeline must have at least one Start    |
| Missing End node             | Error    | Pipeline must have at least one End      |
| API Request without Response | Error    | ApiRequest requires matching ApiResponse |
| Unconfigured entity node     | Error    | Entity-referencing nodes need selection  |
| Disconnected nodes           | Error    | All nodes must be connected to the flow  |

### Validation & Save Behavior

- Save button is disabled until validation passes
- After any pipeline change, validation must be re-run before save
- Validation results appear in the inspector panel
- Nodes with errors show a red indicator

---

## Backend Integration

### API Endpoint

Pipelines belong to a dataset: `POST /datasets/{datasetId}/pipelines` creates one, `PUT /datasets/{datasetId}/pipelines/{pipelineId}` replaces one. Both requests are routed through the Next.js API proxy with the `x-api-request: true` header (same pattern as other backend APIs like users).

```typescript
// src/app/services/api/pipelines/clientRequests.ts
export const useCreatePipeline = (datasetId: string) => {
  const queryClient = useQueryClient()

  return useMutation<ApiServiceResponse<PipelineOutputDTO>, unknown, PipelinePayload>({
    mutationFn: (data: PipelinePayload) =>
      apiRequest<PipelineOutputDTO>({
        method: 'POST',
        endpoint: `/datasets/${datasetId}/pipelines`,
        headers: { [API_REQUEST_HEADER]: 'true' },
        data,
        errorMessage: 'An error occurred while creating the pipeline.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PIPELINES_QUERY_KEY, datasetId] })
    },
    onError: error => {
      console.error('Failed to create pipeline:', error)
    },
  })
}
```

### Save Payload

When saving a pipeline, the `payloadBuilderService` assembles the following payload:

```typescript
interface PipelinePayload {
  name: string // Pipeline name from session
  description: string // Always "-" for now
  styles: PipelineStylesPayload // Viewport + node positions (for reload)
  dataSources: number[] // Entity IDs from DataSource nodes
  apis: string[] // API paths from ApiRequest/ApiResponse nodes
  persistences: string[] // Hardcoded ["frost"] if FROST node exists
  model: string // RedPandaConnect config as JSON string (empty for provide pipelines)
}

interface PipelineStylesPayload {
  viewport?: Viewport // React Flow viewport (zoom, pan)
  nodePositions: Record<string, { x: number; y: number }> // Node ID → position
}
```

### Payload Assembly (`payloadBuilderService`)

The `buildPipelinePayload()` function extracts data from the pipeline graph:

1. **Styles** – Captures viewport and all node positions for frontend reload
2. **DataSources** – Numeric entity IDs from configured DataSource nodes
3. **APIs** – Unique `apiPath` strings from ApiRequest/ApiResponse nodes
4. **Persistences** – Hardcoded `["frost"]` when any FROST node is present
5. **Model** – RedPandaConnect config JSON string from `modelBuilderService`, or empty string for provide pipelines

### Model Generation (`modelBuilderService`)

The `buildRedPandaConnectModel()` function transforms the visual graph into a RedPandaConnect configuration. It is a pure-function service with no hooks or side effects.

**Process:**

1. Traverse graph from Start → End following edges to get ordered nodes
2. Detect pipeline type (`feedin` or `provide`) based on node composition
3. If `provide` → return `null` (no model needed)
4. If `feedin` → build input, processors, and output sections

**Node Handler Registry:**

Each node type that contributes to the model registers a handler:

| Node Type    | Section     | Output                                           |
| ------------ | ----------- | ------------------------------------------------ |
| `dataSource` | `input`     | Connector config with `label: "datasource_<id>"` |
| `mapping`    | `processor` | `{ bloblang: "<user's mapping code>" }`          |
| `frost`      | `output`    | Static FROST switch + branch template            |

The DataSource input uses the connector type from entity metadata (mqtt, sql, csv, etc.). The backend resolves actual connection credentials from the label.

The FROST section is a fixed template that generates thing/observation upsert logic with `${FROST_BASE}` placeholders, which are resolved by the config-adapter before deployment.

### Save Flow

`saveAllPipelines()` in `PipelineEditorProvider` saves every dirty session, one after the other. Per session:

1. **Collect the removed sinks** – `getRemovedDataSinkIds()` compares the pipeline with the snapshot of the last save and collects the removed sink entity ids.
2. **Save the data sinks** – create a sink for a node that has no `entityId`, update a changed one. The returned `configurationUrn` is stashed on the node and becomes the model's `sinkRef`.
3. **Save the mappings** – create or version the mapping artifacts. An unchanged mapping is skipped, because the mapping API creates a new version per request.
4. **Save the pipeline** – `POST` for a new pipeline, `PUT` for an existing one, with the ids and URNs from the steps above.
5. **Delete the removed sinks** – the pipeline is already stored, so a failed delete gives a warning toast but keeps the save successful.
6. **Delete the removed mappings** – `getRemovedMappingUrns()` collects the logical URNs of mapping nodes that were removed since the last save. A node that was never saved has no URN and is skipped. A failed delete gives an error toast (`mappingStillInUse` or `mappingCleanupFailed`) but keeps the save successful.
7. **Clean the session** – mark it clean, write the new data sink and mapping snapshots, show the success toast.

**Order rule:** both deletes must stay behind the pipeline save. When they come first, the backend refuses them with `409 RESOURCE_IN_USE`, because the stored pipeline still points at the sink through `sinkRef` and at the mapping through `mappingRef`. For a sink, the pipeline could then no longer be saved at all.

If a step throws, the partial progress is written back to the session — still dirty — so a retry updates the artifacts that were already created instead of creating duplicates. The error is classified and reported in a toast.

### Locked Data Sinks

`GET /datasets/{datasetId}/datasinks` reports two flags per sink, `provisioned` and `inUseByLayer`. `useDataSinkLocks()` reads the flags once and tells the canvas and the inspector which lock a node has. It finds the sink through the node's `entityId`, so a node without one is new and never locked.

| Flag           | Delete | Data structure       | Table name |
| -------------- | ------ | -------------------- | ---------- |
| `provisioned`  | locked | edit button hidden   | locked     |
| `inUseByLayer` | locked | edit button disabled | editable   |

`provisioned` wins when both apply.

`PipelineCanvas` sets `deletable: false` on a locked node. `onBeforeDelete` shows an error toast with the reason.

The `PipelineInspector` shows a notice with the lock reason. In read-only mode, only the `inUseByLayer` hint is shown.

### Save Button States

- `isSavingAll` is exposed via `useActivePipeline()` context
- The Save button is disabled while no session is dirty, while a save runs, and in read-only mode
- It shows a loading spinner and "Saving..." text while the save runs

---

## Pipeline Types

Pipeline type is detected automatically by `modelBuilderService` based on node composition.

### Feed-in Pipeline

**Flow:** `Start → DataSource → Mapping → FROST → End`

Ingests data from an external source, transforms it, and writes to a FROST server. Generates a full RedPandaConnect config with:

- `input` – DataSource connector config (type from entity metadata, label with datasource ID)
- `pipeline.processors[]` – User's Bloblang mapping + FROST switch/branch logic
- `output` – FROST HTTP client (POST on match, drop on no match)

### Provide Pipeline

**Flow:** `Start → API Request → Data from FROST → API Response → End`

Exposes data from a FROST server via a REST API. Does **not** generate a RedPandaConnect config (`model: ""`). The backend uses the `apis` array to configure APISIX routes and the `persistences` array to know which FROST server to read from.

---

## External References

- [React Flow Documentation](https://reactflow.dev/)
- [Bloblang Documentation](https://docs.redpanda.com/redpanda-connect/guides/bloblang/about/)
- [RedPandaConnect Configuration](https://docs.redpanda.com/redpanda-connect/configuration/about/)
