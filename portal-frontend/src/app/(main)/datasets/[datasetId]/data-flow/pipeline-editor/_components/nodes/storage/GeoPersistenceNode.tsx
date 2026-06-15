'use client'

/**
 * GeoPersistenceNode Component
 *
 * Pipeline node for geo data persistence storage.
 * Configured with a table name and data structure version.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Database } from 'lucide-react'

import type { GeoPersistenceNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * Geo Persistence node component - activity style with Database icon.
 * Configured when both table name and data structure version are set.
 *
 */
export const GeoPersistenceNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as GeoPersistenceNodeData

  return (
    <BasePipelineNode
      category="storage"
      isConfigured={nodeData.configured}
      isSelected={isSelected}
      label={nodeData.label || 'Geo Persistence'}
      sublabel={nodeData.tableName || undefined}
      icon={Database}
    />
  )
}
