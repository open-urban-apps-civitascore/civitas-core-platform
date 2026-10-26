'use client'

import { useTranslations } from 'next-intl'
import { useCallback } from 'react'

import { getNodeDef } from '../_config/nodeRegistry'
import type { PipelineNode } from '../_types/pipeline'

/** Returns the UI name of a node: its own label if editable, else its translated type name. */
export const useNodeName = () => {
  const t = useTranslations('pipelineEditor')

  return useCallback(
    (node: Pick<PipelineNode, 'type' | 'data'>): string => {
      const def = getNodeDef(node.type)
      return def && !def.hasEditableLabel ? t(`nodeTypes.${def.inspectorKey}`) : node.data.label || node.type
    },
    [t],
  )
}
