import type { PipelineNodeType } from './pipeline'
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
  Api: 'api',
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
  entityId?: number | string
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
  entityMetadata?: {
    connector?: string
    connection?: string
    status?: string
    tags?: string[]
  }
}

/**
 * Data specific to API Request/Response nodes.
 * Auto-configured with a dynamically generated API path.
 * No user configuration needed.
 *
 */
export interface ApiNodeData extends BasePipelineNodeData {
  nodeType: typeof PIPELINE_NODE_TYPES.ApiRequest | typeof PIPELINE_NODE_TYPES.ApiResponse
  entityType: typeof ENTITY_TYPES.Api
  /** Auto-generated API path (e.g., "api/123") */
  apiPath: string
}

/**
 * Data specific to FROST storage nodes.
 * Auto-configured with the platform's fixed FROST server.
 * No user configuration needed.
 *
 */
export interface FrostNodeData extends BasePipelineNodeData {
  entityType: typeof ENTITY_TYPES.Frost
  /** Fixed FROST server display name */
  serverName: string
  /** Fixed FROST server URL */
  serverUrl: string
  /** SensorThings API version */
  version: string
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
 * Contains Bloblang mapping code.
 *
 */
export interface MappingNodeData extends BasePipelineNodeData {
  /** Bloblang mapping code */
  mappingCode: string
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
  | ApiNodeData
  | FrostNodeData
  | CronNodeData
  | MappingNodeData

// ============================================================================
// Node Data Type Guards
// ============================================================================

/**
 * Type guard to check if node data is for a control node (Start/End).
 */
export const isControlNodeData = (data: PipelineNodeData): data is ControlNodeData => {
  return 'description' in data && !('entityType' in data) && !('cronExpression' in data) && !('mappingCode' in data)
}

/**
 * Type guard to check if node data is for an entity-referencing node.
 */
export const isEntityNodeData = (data: PipelineNodeData): data is DataSourceNodeData | ApiNodeData | FrostNodeData => {
  return 'entityType' in data
}

/**
 * Type guard to check if node data is for a DataSource node.
 */
export const isDataSourceNodeData = (data: PipelineNodeData): data is DataSourceNodeData => {
  return 'entityType' in data && (data as EntityNodeData).entityType === ENTITY_TYPES.Datasource
}

/**
 * Type guard to check if node data is for an API node.
 */
export const isApiNodeData = (data: PipelineNodeData): data is ApiNodeData => {
  return 'entityType' in data && (data as EntityNodeData).entityType === ENTITY_TYPES.Api
}

/**
 * Type guard to check if node data is for a FROST node.
 */
export const isFrostNodeData = (data: PipelineNodeData): data is FrostNodeData => {
  return 'entityType' in data && (data as EntityNodeData).entityType === ENTITY_TYPES.Frost
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
  return 'mappingCode' in data
}

// ============================================================================
// Node Data Factory Helpers
// ============================================================================

/**
 * Creates default node data based on node type.
 * Used when creating new nodes from the palette.
 *
 */
export const createDefaultNodeData = (nodeType: PipelineNodeType, datasetId?: string): PipelineNodeData => {
  switch (nodeType) {
    case PIPELINE_NODE_TYPES.Start:
      return {
        nodeType: PIPELINE_NODE_TYPES.Start,
        label: 'Start',
        configured: true, // Always configured
        description: 'Entry point of the pipeline. Execution begins here.',
      }
    case PIPELINE_NODE_TYPES.End:
      return {
        nodeType: PIPELINE_NODE_TYPES.End,
        label: 'End',
        configured: true, // Always configured
        description: 'Exit point of the pipeline. Execution completes here.',
      }
    case PIPELINE_NODE_TYPES.DataSource:
      return {
        label: 'DataSource',
        configured: false, // Needs entity selection
        entityType: ENTITY_TYPES.Datasource,
      }
    case PIPELINE_NODE_TYPES.ApiRequest:
      return {
        nodeType: PIPELINE_NODE_TYPES.ApiRequest,
        label: 'API Request',
        configured: true, // Auto-configured with generated path
        entityType: ENTITY_TYPES.Api,
        apiPath: `api/${datasetId ?? ''}`,
      }
    case PIPELINE_NODE_TYPES.ApiResponse:
      return {
        nodeType: PIPELINE_NODE_TYPES.ApiResponse,
        label: 'API Response',
        configured: true, // Auto-configured with generated path
        entityType: ENTITY_TYPES.Api,
        apiPath: `api/${datasetId ?? ''}`,
      }
    case PIPELINE_NODE_TYPES.Frost:
      return {
        label: 'Storage',
        configured: true, // Auto-configured with fixed server
        entityType: ENTITY_TYPES.Frost,
        serverName: 'Frost Server',
        serverUrl: 'https://frost.example.com/v1.1',
        version: '1.1',
      }
    case PIPELINE_NODE_TYPES.Cron:
      return {
        label: 'CRON',
        configured: false, // Needs cron expression
        cronExpression: '',
      }
    case PIPELINE_NODE_TYPES.Mapping:
      return {
        label: 'Mapping',
        configured: false, // Needs mapping code
        mappingCode: '',
      }
    default:
      throw new Error(`Unknown node type: ${nodeType}`)
  }
}
