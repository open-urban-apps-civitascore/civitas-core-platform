/**
 * Visual style constants for the pipeline editor.
 * Defines colors, dimensions, and styling for nodes and the canvas.
 */

import type { NodeCategory } from './nodeCategories'

// ============================================================================
// Color Palette
// ============================================================================

/**
 * Base colors for the pipeline editor.
 * Uses CSS custom properties for theme compatibility.
 */
export const PIPELINE_COLORS = {
  // Node states
  nodeConfiguredBorderNeutral: '#9ca3af', // Neutral grey border for configured but NOT selected (gray-400)
  nodeUnconfiguredBorder: 'hsl(var(--muted-foreground))',
  nodeSelectedBorder: 'hsl(var(--primary))',
  nodeHoverBorder: 'hsl(var(--primary) / 0.7)',

  // Node fill colors
  nodeConfiguredBg: '#f3f4f6', // Light grey (gray-100) for configured state
  nodeUnconfiguredBg: '#d5d7d9',

  // Node icon colors (for configured but NOT selected state)
  nodeConfiguredIconBg: '#e5e7eb', // Light grey background for icon (gray-200)
  nodeConfiguredIconColor: '#6b7280', // Grey icon color (gray-500)

  // Control nodes (Start/End) - solid black for visibility
  startNodeFill: '#1a1a1a',
  endNodeStroke: '#1a1a1a',

  // Edge colors
  edgeDefault: 'hsl(var(--muted-foreground))',
  edgeSelected: 'hsl(var(--primary))',
  edgeHover: 'hsl(var(--primary) / 0.7)',

  // Canvas
  canvasBg: 'hsl(var(--background))',
  canvasGrid: 'hsl(var(--border))',

  // Handle colors
  handleDefault: 'hsl(var(--muted-foreground))',
  handleValid: 'hsl(var(--primary))',
  handleInvalid: 'hsl(var(--destructive))',

  // Validation colors
  validationError: '#ef4444', // red-500
  validationWarning: '#f97316', // orange-500
} as const

// ============================================================================
// Category Colors
// ============================================================================

/**
 * Colors for each node category.
 * Used for visual distinction in palette and canvas.
 *
 */
export const CATEGORY_COLORS: Record<NodeCategory, { primary: string; secondary: string }> = {
  general: {
    primary: 'hsl(var(--foreground))',
    secondary: 'hsl(var(--muted))',
  },
  sources: {
    primary: 'hsl(142 76% 36%)', // Green
    secondary: 'hsl(142 76% 96%)',
  },
  trigger: {
    primary: 'hsl(217 91% 60%)', // Blue
    secondary: 'hsl(217 91% 96%)',
  },
  storage: {
    primary: 'hsl(199 89% 48%)', // Cyan / Frost blue
    secondary: 'hsl(199 89% 96%)',
  },
  transformation: {
    primary: 'hsl(262 83% 58%)', // Purple
    secondary: 'hsl(262 83% 96%)',
  },
}

// ============================================================================
// Node Dimensions
// ============================================================================

/**
 * Default dimensions for pipeline nodes.
 *
 */
export const NODE_DIMENSIONS = {
  // Activity-style nodes (rounded rectangle)
  activityNode: {
    minWidth: 160,
    minHeight: 50,
    maxWidth: 280,
    padding: 12,
    borderRadius: 8,
  },

  // Control nodes (circles)
  controlNode: {
    size: 32, // Diameter
    borderWidth: 2,
  },

  // Connection handles
  handle: {
    size: 10,
    offset: 0, // Distance from node edge
  },
} as const

// ============================================================================
// Layout Configuration
// ============================================================================

/**
 * Layout panel dimensions.
 *
 */
export const LAYOUT_DIMENSIONS = {
  paletteWidth: 240,
  paletteMinWidth: 200,
  paletteMaxWidth: 320,

  inspectorWidth: 320,
  inspectorMinWidth: 280,
  inspectorMaxWidth: 880,

  tabBarHeight: 40,
  toolbarHeight: 48,
} as const

// ============================================================================
// Canvas Configuration
// ============================================================================

/**
 * React Flow canvas settings.
 *
 */
export const CANVAS_CONFIG = {
  // Grid
  gridSize: 20,
  snapToGrid: true,

  // Viewport
  defaultZoom: 1,
  minZoom: 0.25,
  maxZoom: 2,
  zoomOnScroll: true,
  zoomOnDoubleClick: false,
  panOnDrag: true,
  panOnScroll: false,

  // Default viewport center
  defaultViewport: {
    x: 0,
    y: 0,
    zoom: 1,
  },

  // Fit view padding
  fitViewPadding: 0.2,
} as const

// ============================================================================
// Edge Configuration
// ============================================================================

/**
 * Edge styling and behavior settings.
 *
 */
export const EDGE_CONFIG = {
  // Edge type
  defaultEdgeType: 'smoothstep',

  // Styling
  strokeWidth: 2,
  strokeWidthSelected: 3,
} as const

// ============================================================================
// Node Visual States
// ============================================================================

/**
 * Visual styles for "not configured" state.
 *
 */
export const UNCONFIGURED_NODE_STYLE = {
  borderStyle: 'solid',
  borderWidth: 2,
  opacity: 0.8,
}
