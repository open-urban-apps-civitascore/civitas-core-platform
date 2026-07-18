import { type MappingConfig } from '../_components/mapping-editor/_types'
import type { StaTargetVocabulary } from '../_constants/staTargetCatalog'
import { PIPELINE_NODE_TYPES } from './pipeline'

// ============================================================================
// Entity Types
// ============================================================================

/**
 * Types of entities that can be selected in entity-referencing nodes.
 *
 */
export const ENTITY_TYPES = {
  Datasource: 'datasource',
  Frost: 'frost',
  Persistence: 'persistence',
} as const

export type EntityType = (typeof ENTITY_TYPES)[keyof typeof ENTITY_TYPES]

// ============================================================================
// Base Node Data
// ============================================================================

/**
 * Base data structure for all pipeline nodes.
 * All node data types extend this interface.
 *
 */
export interface BasePipelineNodeData extends Record<string, unknown> {
  /** Display label for the node */
  label: string
  /** Whether the node is fully configured */
  configured: boolean
  /** Whether this node is currently selected */
  isSelected?: boolean
}

// ============================================================================
// Control Node Data (Start, End)
// ============================================================================

/**
 * Data for Start and End nodes.
 * These nodes are always considered "configured" as they have no user configuration.
 *
 */
export interface ControlNodeData extends BasePipelineNodeData {
  /** Type of control node (start or end) */
  nodeType: typeof PIPELINE_NODE_TYPES.Start | typeof PIPELINE_NODE_TYPES.End
  /** Static description text shown in inspector */
  description: string
}

// ============================================================================
// Entity-Referencing Node Data
// ============================================================================

/**
 * Data for nodes that reference external entities (DataSource).
 * These nodes have a "not configured" state until an entity is selected.
 *
 */
export interface EntityNodeData extends BasePipelineNodeData {
  /** Type of entity this node references */
  entityType: EntityType
  /** ID of the selected entity */
  entityId?: string
  /** Display name of the selected entity */
  entityName?: string
  /** Additional metadata from the selected entity (read-only display) */
  entityMetadata?: Record<string, unknown>
}

/**
 * Data specific to DataSource nodes.
 *
 */
export interface DataSourceNodeData extends BasePipelineNodeData {
  entityType: typeof ENTITY_TYPES.Datasource
  entityId?: string
  entityName?: string
  /**
   * Versioned CORE URN of the selected DataSource's configuration artifact (the entity's
   * `configurationUrn`), resolved from the picker's fetched list at selection time and emitted as the
   * CORE pipeline node's `sourceRef`. Round-trips via `styles`; absent until a source is selected.
   */
  configurationUrn?: string
  entityMetadata?: {
    connector?: string
    connection?: string
    status?: string
    tags?: string[]
  }
}

/**
 * Data specific to FROST storage nodes.
 * Auto-configured with the platform's fixed FROST server.
 * No user configuration needed.
 *
 */
export interface FrostNodeData extends BasePipelineNodeData {
  entityType: typeof ENTITY_TYPES.Frost
  entityId?: string
  /**
   * Versioned CORE URN of the created DataSink's configuration artifact, stashed after the sink is
   * saved and emitted as the CORE pipeline node's `sinkRef`. Round-trips via `styles`.
   */
  configurationUrn?: string
  /** Fixed FROST server display name */
  serverName: string
  /** Fixed FROST server URL */
  serverUrl: string
  /** SensorThings API version */
  version: string
}

/**
 * Data specific to Geo Persistence storage nodes.
 * Configured with a table name and a data structure version.
 *
 */
export interface GeoPersistenceNodeData extends BasePipelineNodeData {
  entityType: typeof ENTITY_TYPES.Persistence
  entityId?: string
  /**
   * Versioned CORE URN of the created DataSink's configuration artifact, stashed after the sink is
   * saved and emitted as the CORE pipeline node's `sinkRef`. Round-trips via `styles`.
   */
  configurationUrn?: string
  /** Table name for geo data storage */
  tableName: string
  /** ID of the selected data structure version */
  dataStructureVersionId?: string
  /** Display name of the selected data structure */
  dataStructureName?: string
  /** Version number of the selected data structure version */
  versionNumber?: string
}

// ============================================================================
// Trigger Node Data (CRON)
// ============================================================================

/**
 * Data for CRON trigger nodes.
 * Contains a cron expression for scheduling.
 */
export interface CronNodeData extends BasePipelineNodeData {
  /** Cron expression (e.g., "0 0 * * *" for daily at midnight) */
  cronExpression: string
  /** Human-readable preview of the cron schedule (computed) */
  cronPreview?: string
}

// ============================================================================
// Transform Node Data (Mapping)
// ============================================================================

/**
 * Data for Mapping nodes.
 * Holds the source/target schema references and the compiled mapping config (spec §13).
 *
 */
export interface MappingNodeData extends BasePipelineNodeData {
  sourceDatastructureId?: string
  sourceVersionId?: string
  sourceName?: string
  targetDatastructureId?: string
  targetVersionId?: string
  targetName?: string
  /** The single saved artifact produced by the mapping editor. */
  mappingConfig: MappingConfig
  /**
   * Versioned CORE URN of the Mapping artifact created for this node (the POST/PUT `/v1/mappings`
   * response `versionedUrn`), emitted as the CORE pipeline node's `mappingRef`. Round-trips via
   * `styles`; absent until the mapping has been saved at least once.
   */
  mappingRef?: string
  /**
   * Logical (unversioned) URN of the Mapping artifact. Present after the first save; a subsequent
   * save PUT-versions the same logical mapping instead of creating a new one.
   */
  mappingLogicalUrn?: string
  /**
   * Snapshot of the required target-field paths (e.g. {@code $.name}) at mapping-save time. Lets the
   * synchronous, pure pipeline validation check that every required target field is assigned without
   * re-fetching the target schema. {@code undefined} on legacy nodes saved before this existed.
   */
  targetRequiredFields?: string[]
  /**
   * Snapshot of the target structure's effective FROST match keys at mapping-save time (from the
   * {@code x-core-primaryKey} marker, fallback {@code reference}). Only consumed when this mapping
   * feeds a FROST sink; {@code undefined} on legacy nodes saved before this existed.
   */
  staMatchKeys?: StaTargetVocabulary
}

// ============================================================================
// Discriminated Union of All Node Data Types
// ============================================================================

/**
 * Union type representing all possible node data configurations.
 * Used as the data type for PipelineNode.
 *
 */
export type PipelineNodeData =
  | ControlNodeData
  | DataSourceNodeData
  | FrostNodeData
  | GeoPersistenceNodeData
  | CronNodeData
  | MappingNodeData

// ============================================================================
// Node Data Type Guards
// ============================================================================

/**
 * Type guard to check if node data is for a control node (Start/End).
 */
export const isControlNodeData = (data: PipelineNodeData): data is ControlNodeData => {
  return 'description' in data && !('entityType' in data) && !('cronExpression' in data) && !('mappingConfig' in data)
}

/**
 * Type guard to check if node data is for an entity-referencing node.
 */
export const isEntityNodeData = (data: PipelineNodeData): data is DataSourceNodeData | FrostNodeData => {
  return 'entityType' in data
}

/**
 * Type guard to check if node data is for a DataSource node.
 */
export const isDataSourceNodeData = (data: PipelineNodeData): data is DataSourceNodeData => {
  return 'entityType' in data && (data as EntityNodeData).entityType === ENTITY_TYPES.Datasource
}

/**
 * Type guard to check if node data is for a FROST node.
 */
export const isFrostNodeData = (data: PipelineNodeData): data is FrostNodeData => {
  return 'entityType' in data && (data as EntityNodeData).entityType === ENTITY_TYPES.Frost
}

/**
 * Type guard to check if node data is for a Geo Persistence node.
 */
export const isGeoPersistenceNodeData = (data: PipelineNodeData): data is GeoPersistenceNodeData => {
  return 'entityType' in data && (data as EntityNodeData).entityType === ENTITY_TYPES.Persistence
}

/**
 * Type guard to check if node data is for a CRON node.
 */
export const isCronNodeData = (data: PipelineNodeData): data is CronNodeData => {
  return 'cronExpression' in data
}

/**
 * Type guard to check if node data is for a Mapping node.
 */
export const isMappingNodeData = (data: PipelineNodeData): data is MappingNodeData => {
  return 'mappingConfig' in data
}
