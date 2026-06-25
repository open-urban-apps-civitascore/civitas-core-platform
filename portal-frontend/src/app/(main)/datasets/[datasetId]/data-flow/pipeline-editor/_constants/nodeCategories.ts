/**
 * Node category definitions for the pipeline editor palette.
 *
 * Categories themselves live on each node in `_config/nodeRegistry.tsx`
 * (`category: NODE_CATEGORIES.X`). This file only provides:
 *  - the typed category enum, so the registry's `category` field and
 *    `CATEGORY_COLORS` stay typo-safe and exhaustively typed, and
 *  - the deliberate palette ordering.
 *
 * Adding or changing a node type happens in `nodeRegistry.tsx` only.
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
// Category Order
// ============================================================================

/**
 * Defines the order in which categories appear in the palette.
 */
export const NODE_CATEGORY_ORDER: NodeCategory[] = [
  NODE_CATEGORIES.GENERAL,
  NODE_CATEGORIES.SOURCES,
  NODE_CATEGORIES.TRIGGER,
  NODE_CATEGORIES.STORAGE,
  NODE_CATEGORIES.TRANSFORMATION,
]
