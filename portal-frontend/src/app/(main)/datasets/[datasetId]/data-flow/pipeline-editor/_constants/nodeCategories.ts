/**
 * Node category definitions for the pipeline editor palette.
 *
 */

// ============================================================================
// Node Categories
// ============================================================================

/**
 * Pipeline node category identifiers.
 * Used to group nodes in the palette sidebar.
 */
export const NODE_CATEGORIES = {
  GENERAL: 'general',
  SOURCES: 'sources',
  TRIGGER: 'trigger',
  STORAGE: 'storage',
  TRANSFORMATION: 'transformation',
} as const

export type NodeCategory = (typeof NODE_CATEGORIES)[keyof typeof NODE_CATEGORIES]

// ============================================================================
// Category Display Names
// ============================================================================

/**
 * Display names for each category.
 *
 */
export const NODE_CATEGORY_LABELS: Record<NodeCategory, string> = {
  [NODE_CATEGORIES.GENERAL]: 'General',
  [NODE_CATEGORIES.SOURCES]: 'Sources',
  [NODE_CATEGORIES.TRIGGER]: 'Trigger',
  [NODE_CATEGORIES.STORAGE]: 'Storage',
  [NODE_CATEGORIES.TRANSFORMATION]: 'Transformation',
}

// ============================================================================
// Category Order
// ============================================================================

/**
 * Defines the order in which categories appear in the palette.
 *
 */
export const NODE_CATEGORY_ORDER: NodeCategory[] = [
  NODE_CATEGORIES.GENERAL,
  NODE_CATEGORIES.SOURCES,
  NODE_CATEGORIES.TRIGGER,
  NODE_CATEGORIES.STORAGE,
  NODE_CATEGORIES.TRANSFORMATION,
]

// ============================================================================
// Node Type to Category Mapping
// ============================================================================

import { PIPELINE_NODE_TYPES, type PipelineNodeType } from '../_types/pipeline'

/**
 * Maps each node type to its category.
 * Used for filtering and organization.
 *
 */
export const NODE_TYPE_CATEGORY_MAP: Record<PipelineNodeType, NodeCategory> = {
  // General / Control nodes
  [PIPELINE_NODE_TYPES.Start]: NODE_CATEGORIES.GENERAL,
  [PIPELINE_NODE_TYPES.End]: NODE_CATEGORIES.GENERAL,
  // Source nodes
  [PIPELINE_NODE_TYPES.DataSource]: NODE_CATEGORIES.SOURCES,
  // Trigger nodes
  [PIPELINE_NODE_TYPES.ApiRequest]: NODE_CATEGORIES.TRIGGER,
  [PIPELINE_NODE_TYPES.ApiResponse]: NODE_CATEGORIES.TRIGGER,
  [PIPELINE_NODE_TYPES.Cron]: NODE_CATEGORIES.TRIGGER,
  // Storage nodes
  [PIPELINE_NODE_TYPES.Frost]: NODE_CATEGORIES.STORAGE,
  // Transformation nodes
  [PIPELINE_NODE_TYPES.Mapping]: NODE_CATEGORIES.TRANSFORMATION,
}

/**
 * Gets the category for a given node type.
 */
export const getNodeCategory = (nodeType: PipelineNodeType): NodeCategory => {
  return NODE_TYPE_CATEGORY_MAP[nodeType]
}

/**
 * Gets all node types belonging to a specific category.
 */
export const getNodeTypesForCategory = (category: NodeCategory): PipelineNodeType[] => {
  return Object.entries(NODE_TYPE_CATEGORY_MAP)
    .filter(([, cat]) => cat === category)
    .map(([type]) => type as PipelineNodeType)
}
