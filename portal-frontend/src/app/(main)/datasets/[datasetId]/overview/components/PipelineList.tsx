'use client'

import { useTranslations } from 'next-intl'
import { useMemo } from 'react'

import { DataSourceNodeData } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/nodes'
import {
  PIPELINE_NODE_TYPES,
  PipelineOutputDTO,
} from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'
import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { PipelineBasicInfo } from '@/types/datasets'

const getPipelineBadges = (dto: PipelineOutputDTO, datasourceConnectors: Map<string, string>): string[] => {
  const nodes = dto.styles?.nodes ?? []
  const connectors = new Set<string>()

  for (const node of nodes) {
    if (node.type === PIPELINE_NODE_TYPES.DataSource) {
      const data = node.data as DataSourceNodeData
      const connector =
        data.entityMetadata?.connector ?? (data.entityId ? datasourceConnectors.get(data.entityId) : undefined)
      if (connector) connectors.add(connector)
    }
  }

  const badges = Array.from(connectors)
  if (nodes.some(node => node.type === PIPELINE_NODE_TYPES.Frost)) {
    badges.push('FROST')
  }

  return badges
}

interface PipelineListProps {
  datasetId: string
  pipelines: PipelineBasicInfo[]
  canAdd: boolean
}

export const PipelineList = ({ datasetId, pipelines, canAdd }: PipelineListProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.pipelines')
  const { data: pipelinesData } = useGetPipelines(datasetId)
  const { data: datasourcesData } = useGetDatasources()

  const datasourceConnectors = useMemo(() => {
    const map = new Map<string, string>()
    for (const ds of datasourcesData?.data ?? []) {
      if (ds.connectorType) map.set(ds.id, ds.connectorType)
    }
    return map
  }, [datasourcesData?.data])

  const badgesByPipelineId = useMemo(() => {
    const map = new Map<string, string[]>()
    for (const dto of pipelinesData?.data ?? []) {
      map.set(dto.id, getPipelineBadges(dto, datasourceConnectors))
    }
    return map
  }, [pipelinesData?.data, datasourceConnectors])

  return (
    <div className="py-3 mb-6">
      <div className="flex items-center justify-between gap-4">
        <h4 className="font-semibold">{t('title')}</h4>
        {canAdd && (
          <Button asChild variant="outline">
            <GuardedLink href={`/datasets/${datasetId}/data-flow/pipeline-editor`}>{t('addButton')}</GuardedLink>
          </Button>
        )}
      </div>

      {pipelines.length > 0 ? (
        <ul className="mt-4 grid grid-cols-[auto_auto] justify-start gap-x-12 gap-y-2">
          {pipelines.map(pipeline => (
            <li key={pipeline.id} className="contents" data-testid={`pipelineRow-${pipeline.id}`}>
              <GuardedLink
                href={`/datasets/${datasetId}/data-flow/pipeline-editor?pipeline=${pipeline.id}`}
                className="text-primary hover:underline text-sm font-normal self-center"
              >
                {pipeline.name}
              </GuardedLink>
              <div className="flex items-center gap-2 self-center" data-testid={`pipelineRowBadges-${pipeline.id}`}>
                {(badgesByPipelineId.get(pipeline.id) ?? []).map(label => (
                  <Badge key={label} variant="secondary">
                    {label}
                  </Badge>
                ))}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-sm font-normal">{t('empty')}</p>
      )}
    </div>
  )
}
