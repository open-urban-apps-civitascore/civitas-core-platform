'use client'

/**
 * CronNode Component
 *
 * Pipeline node for scheduled/timer-based triggers.
 * Shows configured cron expression or "Not configured" state.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Clock } from 'lucide-react'

import type { CronNodeData } from '../../../_types/nodes'
import { isCronNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * CRON node component - activity style with Clock icon.
 * Displays configured cron expression or "Not configured" state.
 *
 */
export const CronNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  // Type guard to ensure we have the correct data shape
  const nodeData = data as CronNodeData

  // Determine if node is configured (has a cron expression)
  const isConfigured = isCronNodeData(nodeData) && nodeData.cronExpression !== ''

  // Use cron preview if available, otherwise show the expression
  const sublabel = isConfigured ? (nodeData.cronPreview ?? nodeData.cronExpression) : undefined

  return (
    <BasePipelineNode
      category="trigger"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'CRON'}
      sublabel={sublabel}
      icon={Clock}
    />
  )
}
