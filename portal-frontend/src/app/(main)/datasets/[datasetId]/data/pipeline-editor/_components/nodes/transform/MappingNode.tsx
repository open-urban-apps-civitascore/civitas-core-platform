'use client'

/**
 * MappingNode Component
 *
 * Pipeline node for Bloblang data transformation/mapping.
 * Shows configured status or "Not configured" state.
 * Uses the BasePipelineNode for consistent styling.
 *
 */

import type { NodeProps } from '@xyflow/react'
import { Workflow } from 'lucide-react'
import { useTranslations } from 'next-intl'

import type { MappingNodeData } from '../../../_types/nodes'
import { isMappingNodeData } from '../../../_types/nodes'
import { BasePipelineNode } from '../base/BasePipelineNode'

// ============================================================================
// Component
// ============================================================================

/**
 * Mapping node component - activity style with Workflow icon.
 * Displays mapping configuration status or "Not configured" state.
 *
 */
export const MappingNode: React.FC<NodeProps> = ({ data, selected: isSelected = false }) => {
  const t = useTranslations('datastructures.pipelineEditor')

  // Type guard to ensure we have the correct data shape
  const nodeData = data as MappingNodeData

  // Determine if node is configured (has mapping code)
  const isConfigured = isMappingNodeData(nodeData) && nodeData.mappingCode !== ''

  // Show line count as sublabel when configured
  const lineCount = nodeData.mappingCode.split('\n').length
  const sublabel = isConfigured ? t('lines', { count: lineCount }) : undefined

  return (
    <BasePipelineNode
      category="transformation"
      isConfigured={isConfigured}
      isSelected={isSelected}
      label={nodeData.label || 'Mapping'}
      sublabel={sublabel}
      icon={Workflow}
    />
  )
}
