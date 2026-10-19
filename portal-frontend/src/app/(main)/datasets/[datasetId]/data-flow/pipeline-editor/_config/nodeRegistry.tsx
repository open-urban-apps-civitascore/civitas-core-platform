'use client'

/**
 * Pipeline Node Registry — the single source of truth for pipeline node types.
 *
 * Adding (or changing) a node type happens in THIS file only. Each entry describes
 * everything the editor needs about a node:
 *  - palette appearance (category, icon, i18n keys, single-use)
 *  - canvas appearance (handles, category color via BasePipelineNode)
 *  - default data + type-guard
 *  - inspector panel component
 *
 * Everything else (React Flow nodeTypes map, palette grouping, inspector routing,
 * default-data factory) is DERIVED from this array. Pipeline-wide concerns that are
 * not per-node (RedPandaConnect model building, structural validation) live in their
 * dedicated services (`modelBuilderService`, `validationService`).
 *
 * A node type's DATA-FLOW declaration (role, emitted/accepted payload forms, schedule
 * port) lives in the React-free sibling `nodeFlow.ts` — the validation service consumes
 * it, and this file's inspector panels import validation helpers, so keeping it here
 * would create an import cycle. Its `Record<PipelineNodeType, …>` fails to compile when
 * a type added here is missing there.
 */

import type { LucideIcon } from 'lucide-react'
import { Circle, Clock, Database, Radio, Workflow } from 'lucide-react'
import { type ComponentType, createElement, forwardRef } from 'react'

import { ControlPanel } from '../_components/inspector/panels/ControlPanel'
import { CronPanel } from '../_components/inspector/panels/CronPanel'
import { DataSourcePanel } from '../_components/inspector/panels/DataSourcePanel'
import { FrostPanel } from '../_components/inspector/panels/FrostPanel'
import { GeoPersistencePanel } from '../_components/inspector/panels/GeoPersistencePanel'
import { MappingPanel } from '../_components/inspector/panels/MappingPanel'
import { emptyMappingConfig } from '../_components/mapping-editor/_types'
import { DataSourceSublabel } from '../_components/nodes/DataSourceSublabel'
import { FrostSublabel } from '../_components/nodes/FrostSublabel'
import { MappingSublabel } from '../_components/nodes/MappingSublabel'
import { NODE_CATEGORIES, type NodeCategory } from '../_constants/nodeCategories'
import {
  type ControlNodeData,
  type CronNodeData,
  type DataSourceNodeData,
  ENTITY_TYPES,
  type FrostNodeData,
  type GeoPersistenceNodeData,
  type MappingNodeData,
  type PipelineNodeData,
} from '../_types/nodes'
import { PIPELINE_NODE_TYPES, type PipelineNodeType } from '../_types/pipeline'

/** Filled circle icon — renders Circle with a solid fill to appear as a full dot. */
// eslint-disable-next-line react/display-name
const FilledCircle = forwardRef<SVGSVGElement, React.ComponentPropsWithoutRef<LucideIcon>>((props, ref) =>
  createElement(Circle, { ...props, ref, fill: 'currentColor' }),
) as unknown as LucideIcon
FilledCircle.displayName = 'FilledCircle'

// ============================================================================
// Inspector panel prop contract
// ============================================================================

export interface NodeInspectorPanelProps<D extends PipelineNodeData = PipelineNodeData> {
  data: D
  onUpdate: (updates: Partial<D>) => void
}

// ============================================================================
// Node definition
// ============================================================================

export interface PipelineNodeDef<D extends PipelineNodeData = PipelineNodeData> {
  /** Unique node type id (matches PIPELINE_NODE_TYPES). */
  type: PipelineNodeType
  /** Palette group + category color. */
  category: NodeCategory
  /** Canvas / palette / inspector icon. */
  icon: LucideIcon
  /**
   * Canvas rendering shape. Defaults to the labelled activity card.
   * `'controlStart'` / `'controlEnd'` render the UML-style filled / outlined circle.
   */
  shape?: 'activity' | 'controlStart' | 'controlEnd'
  /**
   * i18n key suffix used to resolve labels/descriptions. The registry stores keys only;
   * components translate. Defaults are derived from existing locale namespaces:
   *  - palette label:        `paletteItems.{paletteKey}`
   *  - palette description:  `paletteItems.{paletteKey}Desc`
   *  - inspector type label: `nodeTypes.{inspectorKey}`
   */
  paletteKey: string
  inspectorKey: string
  /** When true, only one instance per pipeline is allowed. */
  singleUse?: boolean
  /** Activity-node connection handles. */
  handles: { left: boolean; right: boolean }
  /** Creates the default node data used when dropping from the palette. */
  createDefaultData: () => D
  /** Narrowing type-guard for this node's data. */
  isData: (data: PipelineNodeData) => data is D
  /**
   * Optional sublabel shown under the node title on the canvas for configured nodes.
   * Most nodes have none; nodes opt in by implementing this.
   */
  getSublabel?: (data: D) => string | undefined
  /**
   * Optional component rendering the canvas sublabel for configured nodes. Use instead of
   * {@link getSublabel} when the sublabel must be resolved asynchronously (e.g. fetched from
   * the backend). Takes precedence over {@link getSublabel}.
   */
  SublabelComponent?: ComponentType<{ data: PipelineNodeData }>

  /** Inspector panel rendered when a node of this type is selected. */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- panels are typed per-node; the union is widened here
  InspectorPanel: ComponentType<NodeInspectorPanelProps<any>>
  /** Whether the inspector panel needs the `onUpdate` callback (read-only panels don't). */
  isPanelReadonly?: boolean
}

// ============================================================================
// The registry
// ============================================================================

export const PIPELINE_NODE_DEFS: PipelineNodeDef[] = [
  // ---- Control: Start ----
  {
    type: PIPELINE_NODE_TYPES.Start,
    category: NODE_CATEGORIES.GENERAL,
    icon: FilledCircle,
    shape: 'controlStart',
    paletteKey: 'flowStart',
    inspectorKey: 'start',
    singleUse: true,
    handles: { left: false, right: true },
    createDefaultData: (): ControlNodeData => ({
      nodeType: PIPELINE_NODE_TYPES.Start,
      label: 'Start',
      configured: true,
      description: 'Entry point of the pipeline. Execution begins here.',
    }),
    isData: (data): data is ControlNodeData =>
      'nodeType' in data && (data as ControlNodeData).nodeType === PIPELINE_NODE_TYPES.Start,

    InspectorPanel: ControlPanel,
    isPanelReadonly: true,
  },

  // ---- Control: End ----
  {
    type: PIPELINE_NODE_TYPES.End,
    category: NODE_CATEGORIES.GENERAL,
    icon: Circle,
    shape: 'controlEnd',
    paletteKey: 'flowEnd',
    inspectorKey: 'end',
    handles: { left: true, right: false },
    createDefaultData: (): ControlNodeData => ({
      nodeType: PIPELINE_NODE_TYPES.End,
      label: 'End',
      configured: true,
      description: 'Exit point of the pipeline. Execution completes here.',
    }),
    isData: (data): data is ControlNodeData =>
      'nodeType' in data && (data as ControlNodeData).nodeType === PIPELINE_NODE_TYPES.End,

    InspectorPanel: ControlPanel,
    isPanelReadonly: true,
  },

  // ---- Source: DataSource ----
  {
    type: PIPELINE_NODE_TYPES.DataSource,
    category: NODE_CATEGORIES.SOURCES,
    icon: Radio,
    paletteKey: 'dataSource',
    inspectorKey: 'dataSource',
    handles: { left: true, right: true },
    createDefaultData: (): DataSourceNodeData => ({
      label: 'DataSource',
      configured: false,
      entityType: ENTITY_TYPES.Datasource,
    }),
    isData: (data): data is DataSourceNodeData =>
      'entityType' in data && (data as DataSourceNodeData).entityType === ENTITY_TYPES.Datasource,
    SublabelComponent: DataSourceSublabel,
    InspectorPanel: DataSourcePanel,
  },

  // ---- Trigger: Cron ----
  {
    type: PIPELINE_NODE_TYPES.Cron,
    category: NODE_CATEGORIES.TRIGGER,
    icon: Clock,
    paletteKey: 'cron',
    inspectorKey: 'cronTrigger',
    handles: { left: true, right: true },
    createDefaultData: (): CronNodeData => ({
      label: 'Scheduled Trigger',
      configured: false,
      cronExpression: '',
    }),
    isData: (data): data is CronNodeData => 'cronExpression' in data,
    InspectorPanel: CronPanel,
  },

  // ---- Storage: Frost ----
  {
    type: PIPELINE_NODE_TYPES.Frost,
    category: NODE_CATEGORIES.STORAGE,
    icon: Database,
    paletteKey: 'frostServer',
    inspectorKey: 'frostStorage',
    handles: { left: true, right: true },
    createDefaultData: (): FrostNodeData => ({
      label: 'Sensor Data Storage',
      // A new sink carries no port, so it is not configured. The platform must not decide what a
      // Pipeline writes, and an unconfigured node is what stops the Dataset from publishing.
      configured: false,
      entityType: ENTITY_TYPES.Frost,
      serverName: 'Sensor Data Storage',
      serverUrl: '',
      version: '1.1',
    }),
    isData: (data): data is FrostNodeData =>
      'entityType' in data && (data as FrostNodeData).entityType === ENTITY_TYPES.Frost,
    SublabelComponent: FrostSublabel,
    InspectorPanel: FrostPanel,
  },

  // ---- Storage: GeoPersistence ----
  {
    type: PIPELINE_NODE_TYPES.GeoPersistence,
    category: NODE_CATEGORIES.STORAGE,
    icon: Database,
    paletteKey: 'geoPersistence',
    inspectorKey: 'geoPersistence',
    handles: { left: true, right: true },
    createDefaultData: (): GeoPersistenceNodeData => ({
      label: 'Geospatial Data Storage',
      configured: false,
      entityType: ENTITY_TYPES.Persistence,
      tableName: '',
    }),
    isData: (data): data is GeoPersistenceNodeData =>
      'entityType' in data && (data as GeoPersistenceNodeData).entityType === ENTITY_TYPES.Persistence,
    getSublabel: data => (data as GeoPersistenceNodeData).tableName || undefined,
    InspectorPanel: GeoPersistencePanel,
  },

  // ---- Transform: Mapping ----
  {
    type: PIPELINE_NODE_TYPES.Mapping,
    category: NODE_CATEGORIES.TRANSFORMATION,
    icon: Workflow,
    paletteKey: 'mapping',
    inspectorKey: 'mapping',
    handles: { left: true, right: true },
    createDefaultData: (): MappingNodeData => ({
      label: 'Mapping',
      configured: false,
      mappingConfig: emptyMappingConfig(),
    }),
    isData: (data): data is MappingNodeData => 'mappingConfig' in data,
    SublabelComponent: MappingSublabel,
    InspectorPanel: MappingPanel,
  },
]

// ============================================================================
// Derived lookups
// ============================================================================

export const PIPELINE_NODE_DEF_BY_TYPE: Record<PipelineNodeType, PipelineNodeDef> = Object.fromEntries(
  PIPELINE_NODE_DEFS.map(def => [def.type, def]),
) as Record<PipelineNodeType, PipelineNodeDef>

export const getNodeDef = (type: PipelineNodeType): PipelineNodeDef | undefined => PIPELINE_NODE_DEF_BY_TYPE[type]

/** Returns the definition whose type-guard matches the given node data. */
export const getNodeDefForData = (data: PipelineNodeData): PipelineNodeDef | undefined =>
  PIPELINE_NODE_DEFS.find(def => def.isData(data))
