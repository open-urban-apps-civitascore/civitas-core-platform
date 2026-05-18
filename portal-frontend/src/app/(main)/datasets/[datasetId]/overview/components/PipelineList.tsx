'use client'

import { useTranslations } from 'next-intl'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Button } from '@/components/ui/button'
import { PipelineBasicInfo } from '@/types/datasets'

interface PipelineListProps {
  datasetId: string
  pipelines: PipelineBasicInfo[]
  canAdd: boolean
}

export const PipelineList = ({ datasetId, pipelines, canAdd }: PipelineListProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.pipelines')

  return (
    <div className="py-3">
      <div className="flex items-center justify-between gap-4">
        <h4 className="font-semibold">{t('title')}</h4>
        {canAdd && (
          <Button asChild variant="outline">
            <GuardedLink href={`/datasets/${datasetId}/data-flow/pipeline-editor`}>{t('addButton')}</GuardedLink>
          </Button>
        )}
      </div>

      {pipelines.length > 0 ? (
        <ul className="mt-4 flex flex-col gap-2">
          {pipelines.map(pipeline => (
            <li
              key={pipeline.id}
              className="flex items-center justify-between gap-4"
              data-testid={`pipelineRow-${pipeline.id}`}
            >
              <GuardedLink
                href={`/datasets/${datasetId}/data-flow/pipeline-editor?pipeline=${pipeline.id}`}
                className="text-primary hover:underline text-sm font-medium truncate"
              >
                {pipeline.name}
              </GuardedLink>
              <div className="flex items-center gap-2" data-testid={`pipelineRowBadges-${pipeline.id}`} />
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-sm font-normal">{t('empty')}</p>
      )}
    </div>
  )
}
