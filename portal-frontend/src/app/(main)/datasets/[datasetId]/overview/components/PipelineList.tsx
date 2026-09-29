'use client'

import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef } from 'react'
import { toast } from 'sonner'

import { DataSourceNodeData } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/nodes'
import {
  PIPELINE_NODE_TYPES,
  PipelineOutputDTO,
} from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { PipelineBasicInfo } from '@/types/datasets'
import { DATASINK_TYPES, type DataSinkType } from '@/types/datasinks'

import { PipelineCard } from './PipelineCard'

const hasStoredNodes = (dto: PipelineOutputDTO): boolean => {
  const storedNodes = dto.styles?.nodes ?? []
  return storedNodes.length > 0
}

/**
 * The badges of a pipeline that has no graph stored by the editor, as an installed package leaves
 * it. The pipeline still names its data sources and data sinks by id, so the badges come from those.
 */
const getBadgesOfLinkedEntities = (
  dto: PipelineOutputDTO,
  datasourceConnectors: Map<string, string>,
  dataSinkTypes: Map<string, DataSinkType>,
): string[] => {
  const connectors = new Set<string>()
  for (const dataSourceId of dto.dataSourceIds ?? []) {
    const connector = datasourceConnectors.get(dataSourceId)
    if (connector) connectors.add(connector)
  }

  const sinkTypes = new Set<DataSinkType>()
  for (const dataSinkId of dto.dataSinkIds ?? []) {
    const sinkType = dataSinkTypes.get(dataSinkId)
    if (sinkType) sinkTypes.add(sinkType)
  }

  const badges: string[] = Array.from(connectors)
  if (sinkTypes.has(DATASINK_TYPES.FROST)) badges.push(DATASINK_TYPES.FROST)
  if (sinkTypes.has(DATASINK_TYPES.POSTGIS)) badges.push(DATASINK_TYPES.POSTGIS)
  return badges
}

const getPipelineBadges = (
  dto: PipelineOutputDTO,
  datasourceConnectors: Map<string, string>,
  dataSinkTypes: Map<string, DataSinkType>,
): string[] => {
  if (!hasStoredNodes(dto)) return getBadgesOfLinkedEntities(dto, datasourceConnectors, dataSinkTypes)

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
    badges.push(DATASINK_TYPES.FROST)
  }
  if (nodes.some(node => node.type === PIPELINE_NODE_TYPES.GeoPersistence)) {
    badges.push(DATASINK_TYPES.POSTGIS)
  }

  return badges
}

interface PipelineListProps {
  datasetId: string
  pipelines: PipelineBasicInfo[]
  canCreatePipeline: boolean
  canDeletePipeline: boolean
}

export const PipelineList = ({ datasetId, pipelines, canCreatePipeline, canDeletePipeline }: PipelineListProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.pipelines')
  const { hasPermission } = usePermissions()
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)
  const { data: pipelinesData } = useGetPipelines(datasetId)
  const { data: datasourcesData } = useGetDatasources({ isEnabled: canReadDatasources })
  // Only a pipeline without a stored graph needs the data sinks, so a dataset without one asks for nothing.
  const hasPipelineWithoutGraph = (pipelinesData?.data ?? []).some(dto => !hasStoredNodes(dto))
  const { data: dataSinksData } = useGetDataSinks(datasetId, { isEnabled: hasPipelineWithoutGraph })
  const seenErrors = useRef(new Set<string>())

  useEffect(() => {
    for (const pipeline of pipelines) {
      const status = pipeline.runtimeStatus
      if (status?.state !== 'ERROR') continue
      const key = `${pipeline.id}:${status.lastEventId ?? status.occurredAt ?? status.message ?? 'error'}`
      if (seenErrors.current.has(key)) continue
      seenErrors.current.add(key)
      toast.error(
        t('errorToast', {
          name: pipeline.name,
          message: status.message ?? t('unknownError'),
        }),
      )
    }
  }, [pipelines, t])

  const datasourceConnectors = useMemo(() => {
    const map = new Map<string, string>()
    for (const ds of datasourcesData?.data ?? []) {
      if (ds.connectorType) map.set(ds.id, ds.connectorType)
    }
    return map
  }, [datasourcesData?.data])

  const dataSinkTypes = useMemo(() => {
    const map = new Map<string, DataSinkType>()
    for (const sink of dataSinksData?.data ?? []) {
      map.set(sink.id, sink.dataSinkType)
    }
    return map
  }, [dataSinksData?.data])

  const badgesByPipelineId = useMemo(() => {
    const map = new Map<string, string[]>()
    for (const dto of pipelinesData?.data ?? []) {
      map.set(dto.id, getPipelineBadges(dto, datasourceConnectors, dataSinkTypes))
    }
    return map
  }, [pipelinesData?.data, datasourceConnectors, dataSinkTypes])

  return (
    <div className="py-3 mb-6">
      <div className="flex items-center justify-between gap-4">
        <h4 className="font-semibold">{t('title')}</h4>
        {canCreatePipeline && (
          <Button asChild variant="outline">
            <GuardedLink href={`/datasets/${datasetId}/data-flow/pipeline-editor`}>{t('addButton')}</GuardedLink>
          </Button>
        )}
      </div>

      {pipelines.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4 mt-4">
          {pipelines.map(pipeline => (
            <PipelineCard
              key={pipeline.id}
              datasetId={datasetId}
              pipeline={pipeline}
              badges={badgesByPipelineId.get(pipeline.id) ?? []}
              canDelete={canDeletePipeline}
            />
          ))}
        </div>
      ) : (
        <p className="mt-4 text-sm font-normal">{t('empty')}</p>
      )}
    </div>
  )
}
