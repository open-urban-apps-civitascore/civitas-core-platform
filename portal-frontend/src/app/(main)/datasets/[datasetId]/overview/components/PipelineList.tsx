'use client'

import { useLocale, useTranslations } from 'next-intl'
import { useEffect, useMemo, useRef } from 'react'
import { toast } from 'sonner'

import { DataSourceNodeData } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/nodes'
import {
  PIPELINE_NODE_TYPES,
  PipelineOutputDTO,
} from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_types/pipeline'
import { useGetDatasources } from '@/app/services/api/datasources/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { BasicTooltip } from '@/components/tooltip/Tooltip'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { usePermissions } from '@/hooks/use-permissions'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { PipelineBasicInfo } from '@/types/datasets'
import { DATASINK_TYPES } from '@/types/datasinks'
import { formatDate } from '@/utils/formatDate'

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
}

export const PipelineList = ({ datasetId, pipelines, canCreatePipeline }: PipelineListProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.pipelines')
  const locale = useLocale()
  const { hasPermission } = usePermissions()
  const canReadDatasources = hasPermission(PERMISSION_NAMES.DATASOURCE_READ)
  const { data: pipelinesData } = useGetPipelines(datasetId)
  const { data: datasourcesData } = useGetDatasources({ isEnabled: canReadDatasources })
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
        {canCreatePipeline && (
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
                {pipeline.runtimeStatus?.state === 'ERROR' && (
                  <BasicTooltip
                    tooltipContent={
                      <div className="space-y-2 text-left text-sm">
                        <p>{pipeline.runtimeStatus.message ?? t('pipelineError')}</p>
                        {pipeline.runtimeStatus.occurredAt && (
                          <p>{formatDate(pipeline.runtimeStatus.occurredAt, locale)}</p>
                        )}
                        {pipeline.runtimeStatus.sanitizedStacktrace && (
                          <pre className="max-h-48 max-w-[480px] overflow-auto whitespace-pre-wrap text-xs">
                            {pipeline.runtimeStatus.sanitizedStacktrace}
                          </pre>
                        )}
                      </div>
                    }
                  >
                    <button
                      type="button"
                      aria-label={t('errorDetails', { name: pipeline.name })}
                      className="text-destructive"
                    >
                      <Badge variant="destructive">{t('errorLabel')}</Badge>
                    </button>
                  </BasicTooltip>
                )}
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
