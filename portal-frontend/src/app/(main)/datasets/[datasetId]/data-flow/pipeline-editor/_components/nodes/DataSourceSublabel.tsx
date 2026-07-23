'use client'

/**
 * DataSourceSublabel — resolves and renders a datasource node's canvas sublabel.
 *
 * The datasource name is not stored on the node; it is resolved from the shared datasources
 * list via {@link usePipelineDatasources}, which falls back to an anonymous label when the id
 * is not accessible.
 */

import { usePipelineDatasources } from '../../_hooks/use-pipeline-datasources'
import { isDataSourceNodeData, type PipelineNodeData } from '../../_types/nodes'

export const DataSourceSublabel: React.FC<{ data: PipelineNodeData }> = ({ data }) => {
  const entityId = isDataSourceNodeData(data) ? data.entityId : undefined
  const { getName } = usePipelineDatasources()
  return getName(entityId) ?? null
}
