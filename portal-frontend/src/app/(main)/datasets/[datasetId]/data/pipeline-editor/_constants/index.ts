/**
 * Pipeline Editor Constants
 *
 * This module exports all constants used throughout the pipeline editor.
 * Import from this index file for cleaner imports.
 *
 */

// Node categories
export {
  getNodeCategory,
  getNodeTypesForCategory,
  NODE_CATEGORIES,
  NODE_CATEGORY_LABELS,
  NODE_CATEGORY_ORDER,
  NODE_TYPE_CATEGORY_MAP,
  type NodeCategory,
} from './nodeCategories'

// Palette items
export {
  getAllPaletteItems,
  getPaletteCategory,
  getPaletteItem,
  PALETTE_NODE_DEFINITIONS,
  type PaletteCategory,
  type PaletteItem,
  PIPELINE_PALETTE_CATEGORIES,
} from './paletteItems'

// Visual styles
export {
  CANVAS_CONFIG,
  CATEGORY_COLORS,
  EDGE_CONFIG,
  LAYOUT_DIMENSIONS,
  NODE_DIMENSIONS,
  NODE_STATE_CLASSES,
  NODE_TYPE_DIMENSIONS,
  PIPELINE_COLORS,
  UNCONFIGURED_NODE_STYLE,
} from './pipelineStyles'
