'use client'

/**
 * ApiRequestNode Component
 *
 * Pipeline node for REST API request triggers.
 * Auto-configured with a dynamically generated API path.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Globe } from 'lucide-react'

import type { ApiNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * API Request node component - activity style with Globe icon.
 * Always configured with the platform's generated API path.
 *
 */
export const ApiRequestNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const nodeData = data as ApiNodeData

  return (
    <BasePipelineNode
      category="trigger"
      isConfigured={true}
      isSelected={isSelected}
      label={nodeData.label || 'API Request'}
      sublabel={nodeData.apiPath}
      icon={Globe}
    />
  )
}
