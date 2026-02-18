'use client'

/**
 * DataSourceNode Component
 *
 * Pipeline node for data input sources.
 * Shows configured datasource name or "Not configured" state.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Database } from 'lucide-react'

import type { DataSourceNodeData } from '../../../_types/nodes'
import { isDataSourceNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * DataSource node component - activity style with Database icon.
 * Displays selected datasource information or "Not configured" state.
 *
 */
export const DataSourceNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  // Type guard to ensure we have the correct data shape
  const nodeData = data as DataSourceNodeData

  // Determine if node is configured (has an entity selected)
  const isConfigured = isDataSourceNodeData(nodeData) && nodeData.entityId !== undefined

  return (
    <BasePipelineNode
      category="sources"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'DataSource'}
      sublabel={isConfigured ? nodeData.entityName : undefined}
      icon={Database}
    />
  )
}
