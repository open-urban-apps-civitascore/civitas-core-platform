'use client'

/**
 * MappingSublabel — resolves and renders a mapping node's canvas sublabel ("source → target").
 *
 * The source/target datastructure names are not stored on the node; they are resolved from the
 * stored version references at render time via {@link useDatastructureVersionInfo}.
 */

import { useDatastructureVersionInfo } from '../../_hooks/use-datastructure-version-info'
import { isMappingNodeData, type PipelineNodeData } from '../../_types/nodes'

const compositeKey = (datastructureId?: string, versionId?: string): string | undefined =>
  datastructureId && versionId ? `${datastructureId}/${versionId}` : undefined

export const MappingSublabel: React.FC<{ data: PipelineNodeData }> = ({ data }) => {
  const mapping = isMappingNodeData(data) ? data : undefined
  const { name: sourceName } = useDatastructureVersionInfo(
    compositeKey(mapping?.sourceDatastructureId, mapping?.sourceVersionId),
  )
  const { name: targetName } = useDatastructureVersionInfo(
    compositeKey(mapping?.targetDatastructureId, mapping?.targetVersionId),
  )

  return sourceName && targetName ? `${sourceName} → ${targetName}` : null
}
