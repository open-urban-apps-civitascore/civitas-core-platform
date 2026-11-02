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
│   │   │   ├── DataSourceSelectModal.tsx
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
│   ├── use-active-pipeline.ts          # Active pipeline context hook
│   └── use-node-validation-severity.ts # Validation severity of a node
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
- Validation border: red for errors, orange for warnings

```typescript
<BasePipelineNode
  category="sources"
  label="DataSource"
  sublabel="sensor-data"
  icon={Database}
  isConfigured={true}
  isSelected={false}
  validationSeverity="none"
/>
```

`ActivityNode` reads the severity with `useNodeValidationSeverity(id)`. This hook reads a small separate context, so changes elsewhere in the editor context do not re-render the node.

### Inspector Panels

Each node type has a corresponding inspector panel:

| Node Type   | Panel Component   | Features                                 |
| ----------- | ----------------- | ---------------------------------------- |
| Start/End   | `ControlPanel`    | Static description                       |
| DataSource  | `DataSourcePanel` | Selection dialog + metadata              |
| ApiRequest  | `ApiPanel`        | Entity dropdown + metadata               |
| ApiResponse | `ApiPanel`        | Entity dropdown + metadata               |
| Cron        | `CronPanel`       | Expression input                         |
| Frost       | `FrostPanel`      | Entity dropdown + metadata               |
| Mapping     | `MappingPanel`    | Source/target selectors + mapping editor |

### DataSourceSelectModal

Selection dialog for the Data source of a Data source node: search, sortable table, single selection.
Unchecking the selected Data source and confirming clears the selection.

```typescript
<DataSourceSelectModal
  isOpen={isOpen}
  onOpenChange={setIsOpen}
  datasources={entities}
  isLoading={isLoading}
  selectedId={nodeData.entityId}
  onSelect={datasource => onUpdate({ entityId: datasource?.id })}
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

1. The editor validates every saved pipeline once after load (not in read-only mode). It waits until the Data sources are loaded.
2. The user clicks "Validate" in the toolbar to validate the active pipeline again (`runValidation()`).
3. Validation and save use the current connector type from the Data source list, on a copy of the pipeline.
4. Each pipeline keeps its own result. `validationResult` in the context is the result of the active pipeline.
5. The inspector shows `ValidationPanel` after "Validate" for the active pipeline, after load for every saved pipeline and after save for every changed pipeline, with or without errors.
6. Nodes show a red border for errors and an orange border for warnings. Tabs of pipelines with errors show a red dot.
7. When the active pipeline becomes dirty, or a node or edge is added or removed, its result is cleared. A tab switch keeps the result.
8. Each pipeline keeps its own panel state. Selecting a node or an edge, or a change, closes the panel only in this pipeline.

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
import { useNodeValidationSeverity } from '../../../_hooks/use-node-validation-severity'
import { SomeIcon } from 'lucide-react'

export const NewNode = memo(function NewNode({ id, data, selected }: NodeProps) {
  const validationSeverity = useNodeValidationSeverity(id)

  return (
    <BasePipelineNode
      nodeId={id}
      category="yourCategory"
      label={data.label}
      sublabel={data.customField || 'Not configured'}
      icon={<SomeIcon size={18} />}
      configured={data.configured}
      selected={selected}
      validationSeverity={validationSeverity}
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

| Rule                         | Severity | Description                                  |
| ---------------------------- | -------- | -------------------------------------------- |
| Missing Start node           | Error    | Pipeline must have at least one Start        |
| Missing End node             | Error    | Pipeline must have at least one End          |
| API Request without Response | Error    | ApiRequest requires matching ApiResponse     |
| Unconfigured entity node     | Error    | Entity-referencing nodes need selection      |
| Disconnected nodes           | Error    | All nodes must be connected to the flow      |
| Source requires CRON         | Error    | An HTTP source needs a CRON node wired to it |
| API call missing             | Error    | An HTTP source needs an API call             |
| API call not found           | Error    | The API call must exist in the Data source   |

### Validation & Save Behavior

- Save All validates every changed pipeline. Unchanged pipelines are not validated.
- An error in a changed pipeline blocks the save. Nothing is saved.
- Results of the save validation appear as node borders, as a red dot on the tab and in the inspector.

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

`saveAllPipelines()` in `PipelineEditorProvider` saves every dirty session, one after the other, in three phases.

Phase 1, per session:

1. **Collect the removed sinks** – `getRemovedDataSinkIds()` compares the pipeline with the snapshot of the last save and collects the removed sink entity ids.
2. **Save the data sinks** – create a sink for a node that has no `entityId`, update a changed one. The returned `configurationUrn` is stashed on the node and becomes the model's `sinkRef`.
3. **Save the mappings** – create or version the mapping artifacts. An unchanged mapping is skipped, because the mapping API creates a new version per request.

Phase 2, once for all sessions:

4. **Confirm the removal** – `getDatasetResources()` reads the data sinks, Layers and the Dataset's APIs, and `getDataSinkRemoval()` finds what goes with the removed sinks: their Layers, each API whose last data sink of the backing type goes (`BACKING_DATASINK_TYPE`), and whether a removed sink is provisioned. When anything of that goes, the `RemovalConfirmModal` lists it. Cancel stops the save before any pipeline is stored.

Phase 3, per session:

5. **Save the pipeline** – `POST` for a new pipeline, `PUT` for an existing one, with the ids and URNs from the steps above, and `confirmDataLoss=true` when the user confirmed a removal that deletes stored data, a Layer or an API. The backend deletes or deactivates the removed sinks, their Layers and the APIs without a sink in the same request. It refuses to remove a provisioned sink, a Layer or an API without `confirmDataLoss` (`409 RESOURCE_IN_USE`), which the save reports as `unconfirmedDataLossError`.
6. **Delete the removed mappings** – `getRemovedMappingUrns()` collects the logical URNs of mapping nodes that were removed since the last save. A node that was never saved has no URN and is skipped. A failed delete gives an error toast (`mappingStillInUse` or `mappingCleanupFailed`) but keeps the save successful.
7. **Clean the session** – mark it clean, write the new data sink and mapping snapshots, show the success toast.

**Order rules:** the removal is checked after phase 1, so a sink node replaced in one save keeps its API. The mapping delete stays behind the pipeline save. When it comes first, the backend refuses it with `409 RESOURCE_IN_USE`, because the stored pipeline still points at the mapping through `mappingRef`.

If a step throws, the partial progress is written back to the session — still dirty — so a retry updates the artifacts that were already created instead of creating duplicates. An error is classified and reported in a toast.

If the removal is cancelled in phase 2, every session whose pipeline was not saved is written back the same way, except for the data sinks and mappings that phase 1 created: no pipeline references them, so they are deleted again (`DELETE /datasets/{datasetId}/datasinks/{id}`, where a sink without storage or Layers needs no confirmation, and `DELETE /datasets/{datasetId}/mappings?urn={logicalUrn}`). Their nodes lose `entityId` and `configurationUrn`, or `mappingRef` and `mappingLogicalUrn`. An artifact whose delete fails keeps its ref, so the next save links or versions it. A mapping that existed before the save keeps the version phase 1 added. A pipeline save that fails in phase 3 keeps the data sinks that phase 1 created, with their refs: the removal check counted them as staying, and deleting them could remove an API without a confirmation.

### Pipeline Delete

`deletePipeline()` finds the data sinks of the pipeline in `getDatasetResources()` and shows the `RemovalConfirmModal` with what goes with them, or only the delete question when nothing goes. The `DELETE` carries `confirmDataLoss=true` when the dialog lists stored data, a Layer or an API. The Dataset overview (`PipelineCard`) works the same way.

### Locked Data Sinks

`GET /datasets/{datasetId}/datasinks` reports two flags per sink, `provisioned` and `inUseByLayer`. `useDataSinkLocks()` reads the flags once and tells the inspector which lock a node has. It finds the sink through the node's `entityId`, so a node without one is new and never has these two locks. Every sink node can be deleted; the removal confirmation above covers what goes with it.

| Flag           | Data structure       | Table name |
| -------------- | -------------------- | ---------- |
| `provisioned`  | edit button hidden   | locked     |
| `inUseByLayer` | edit button disabled | editable   |

`provisioned` wins when both apply. The `PipelineInspector` shows a notice with the lock reason. In read-only mode, only the `inUseByLayer` hint is shown.

### Read-only Mode

`PipelineEditorWrapper` sets the editor to read-only (`ReadOnlyProvider`) in two cases:

- The user has neither edit nor create permission for pipelines.
- The first load of the pipelines, the data sinks or the dataset fails (`useEditorLoadError`). Without this data, the locks above are incomplete. The editor then shows one error toast (`header.loadFailed`).

In read-only mode:

- Nodes cannot be added, moved, connected or deleted. Changes in the inspector are ignored.
- "Save all", the "New pipeline" tab button and the pipeline menu (rename, delete) are hidden. `deletePipeline` also returns early.
- "Validate" stays available.
- The inspector panels disable their edit fields (`isLocked`). "Show Data structure" and "Open Mapping Editor" stay available. The Mapping Editor opens without "Apply".

A failed refetch after a successful first load does not change the mode. The editor keeps the data of the first load.

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
