/**
 * Palette items configuration for the pipeline editor.
 * Defines all draggable node items organized by category.
 *
 */

import type { LucideIcon } from 'lucide-react'
import { Circle, CircleDot, Clock, Database, Globe, Radio, Reply, Workflow } from 'lucide-react'

import { PIPELINE_NODE_TYPES, type PipelineNodeType } from '../_types/pipeline'
import { NODE_CATEGORIES, type NodeCategory } from './nodeCategories'

// ============================================================================
// Palette Item Type
// ============================================================================

/**
 * Definition for a single palette item (draggable node).
 *
 */
export interface PaletteItem {
  /** Node type identifier (matches PipelineNodeType) */
  type: PipelineNodeType
  /** Display label in the palette */
  label: string
  /** Lucide icon component to display */
  icon: LucideIcon
  /** Short description shown in tooltip or help text */
  description: string
  /** Whether this node can only be used once per pipeline */
  singleUse?: boolean
}

/**
 * Definition for a palette category containing items.
 *
 */
export interface PaletteCategory {
  /** Category identifier */
  id: NodeCategory
  /** Display title for the category */
  title: string
  /** Items within this category */
  items: PaletteItem[]
  /** Whether the category is collapsed by default */
  defaultCollapsed?: boolean
}

// ============================================================================
// Palette Items by Node Type
// ============================================================================

/**
 * Individual node definitions.
 * Use this for looking up node metadata by type.
 *
 */
export const PALETTE_NODE_DEFINITIONS: Record<PipelineNodeType, PaletteItem> = {
  // Control nodes
  [PIPELINE_NODE_TYPES.Start]: {
    type: PIPELINE_NODE_TYPES.Start,
    label: 'Flow Start',
    icon: CircleDot,
    description: 'Pipeline entry point. Execution begins here.',
    singleUse: true,
  },
  [PIPELINE_NODE_TYPES.End]: {
    type: PIPELINE_NODE_TYPES.End,
    label: 'Flow End',
    icon: Circle,
    description: 'Pipeline exit point. Execution completes here.',
  },

  // Source nodes
  [PIPELINE_NODE_TYPES.DataSource]: {
    type: PIPELINE_NODE_TYPES.DataSource,
    label: 'DataSource',
    icon: Radio,
    description: 'Data input source. Select a configured datasource.',
  },

  // Trigger nodes
  [PIPELINE_NODE_TYPES.ApiRequest]: {
    type: PIPELINE_NODE_TYPES.ApiRequest,
    label: 'API Request',
    icon: Globe,
    description: 'REST API request trigger. Starts pipeline on HTTP request.',
  },
  [PIPELINE_NODE_TYPES.ApiResponse]: {
    type: PIPELINE_NODE_TYPES.ApiResponse,
    label: 'API Response',
    icon: Reply,
    description: 'REST API response. Returns data to HTTP client.',
  },
  [PIPELINE_NODE_TYPES.Cron]: {
    type: PIPELINE_NODE_TYPES.Cron,
    label: 'CRON',
    icon: Clock,
    description: 'Scheduled trigger. Runs pipeline on a time schedule.',
  },

  // Storage nodes
  [PIPELINE_NODE_TYPES.Frost]: {
    type: PIPELINE_NODE_TYPES.Frost,
    label: 'FROST Server',
    icon: Database,
    description: 'SensorThings API persistence. Store or retrieve data.',
  },
  [PIPELINE_NODE_TYPES.GeoPersistence]: {
    type: PIPELINE_NODE_TYPES.GeoPersistence,
    label: 'Geo Persistence',
    icon: Database,
    description: 'Geo data persistence. Store geo data with a data structure.',
  },

  // Transform nodes
  [PIPELINE_NODE_TYPES.Mapping]: {
    type: PIPELINE_NODE_TYPES.Mapping,
    label: 'Mapping',
    icon: Workflow,
    description: 'Bloblang data transformation. Map and transform data.',
  },
}

// ============================================================================
// Complete Palette Structure
// ============================================================================

/**
 * Full palette configuration with all categories and items.
 * This is the main export used by the PipelinePalette component.
 *
 */
export const PIPELINE_PALETTE_CATEGORIES: PaletteCategory[] = [
  {
    id: NODE_CATEGORIES.GENERAL,
    title: 'General',
    items: [PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.Start], PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.End]],
    defaultCollapsed: false,
  },
  {
    id: NODE_CATEGORIES.SOURCES,
    title: 'Sources',
    items: [PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.DataSource]],
    defaultCollapsed: false,
  },
  {
    id: NODE_CATEGORIES.TRIGGER,
    title: 'Trigger',
    items: [
      PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.ApiRequest],
      PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.ApiResponse],
      PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.Cron],
    ],
    defaultCollapsed: false,
  },
  {
    id: NODE_CATEGORIES.STORAGE,
    title: 'Storage',
    items: [
      PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.Frost],
      PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.GeoPersistence],
    ],
    defaultCollapsed: false,
  },
  {
    id: NODE_CATEGORIES.TRANSFORMATION,
    title: 'Transformation',
    items: [PALETTE_NODE_DEFINITIONS[PIPELINE_NODE_TYPES.Mapping]],
    defaultCollapsed: false,
  },
]

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Gets palette item definition for a node type.
 */
export const getPaletteItem = (nodeType: PipelineNodeType): PaletteItem => {
  return PALETTE_NODE_DEFINITIONS[nodeType]
}

/**
 * Gets all palette items as a flat array.
 */
export const getAllPaletteItems = (): PaletteItem[] => {
  return PIPELINE_PALETTE_CATEGORIES.flatMap(category => category.items)
}

/**
 * Finds a palette category by ID.
 */
export const getPaletteCategory = (categoryId: NodeCategory): PaletteCategory | undefined => {
  return PIPELINE_PALETTE_CATEGORIES.find(category => category.id === categoryId)
}
