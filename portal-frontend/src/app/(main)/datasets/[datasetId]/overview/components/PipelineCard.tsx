'use client'

import { MoreVertical, Workflow } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useLocale, useTranslations } from 'next-intl'
import { useState } from 'react'
import { toast } from 'sonner'

import { useDeletePipeline } from '@/app/services/api/pipelines/clientRequests'
import { ActivityBadge } from '@/components/activity-badge/ActivityBadge'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { InfoModal } from '@/components/modals/info-modal/InfoModal'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { BasicTooltip } from '@/components/tooltip/Tooltip'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { PipelineBasicInfo } from '@/types/datasets'
import { formatDate } from '@/utils/formatDate'
import { PipelineDeleteBlockReason } from '@/utils/pipelineDeleteGuard'

interface RuntimeStatusBadgeProps {
  title: string
  ariaLabel: string
  cause?: string | null
  occurredAt?: string | null
  stacktrace?: string | null
  hint?: string
}

const RuntimeStatusBadge = (props: RuntimeStatusBadgeProps) => {
  const { title, ariaLabel, cause, occurredAt, stacktrace, hint } = props
  return (
    <BasicTooltip
      tooltipContent={
        <div className="space-y-2 text-left text-sm">
          {cause && <p>{cause}</p>}
          {occurredAt && <p>{occurredAt}</p>}
          {stacktrace && (
            <pre className="max-h-48 max-w-[480px] overflow-auto whitespace-pre-wrap text-xs">{stacktrace}</pre>
          )}
          {hint && <p>{hint}</p>}
        </div>
      }
    >
      <button
        type="button"
        aria-label={ariaLabel}
        className="inline-flex items-center border-none bg-transparent p-0"
        onClick={e => {
          e.preventDefault()
          e.stopPropagation()
        }}
      >
        <ActivityBadge isActive={false} title={title} />
      </button>
    </BasicTooltip>
  )
}

interface PipelineCardProps {
  datasetId: string
  pipeline: PipelineBasicInfo
  badges: string[]
  canDelete: boolean
  deleteBlockReason: PipelineDeleteBlockReason | null
  isDeleteDisabled: boolean
}

export const PipelineCard = ({
  datasetId,
  pipeline,
  badges,
  canDelete,
  deleteBlockReason,
  isDeleteDisabled,
}: PipelineCardProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.pipelines')
  const locale = useLocale()
  const router = useRouter()
  const deletePipeline = useDeletePipeline(datasetId)
  const [isDeleteOpen, setIsDeleteOpen] = useState(false)
  const [isDeleteBlockedOpen, setIsDeleteBlockedOpen] = useState(false)

  const runtimeStatus = pipeline.runtimeStatus
  const hasError = runtimeStatus?.state === 'ERROR'
  const isStopped = runtimeStatus?.state === 'STOPPED'
  const isActive = runtimeStatus?.state === 'OK'
  const hasBadges = hasError || isStopped || isActive || badges.length > 0
  const occurredAt = runtimeStatus?.occurredAt ? formatDate(runtimeStatus.occurredAt, locale) : null

  const editorUrl = `/datasets/${datasetId}/data-flow/pipeline-editor?pipeline=${pipeline.id}`

  const handleConfirmDelete = async () => {
    try {
      await deletePipeline.mutateAsync(pipeline.id)
      toast.success(t('messages.deleteSuccess'))
      setIsDeleteOpen(false)
      router.refresh()
    } catch {
      toast.error(t('messages.deleteError'))
    }
  }

  return (
    <>
      <GuardedLink
        data-testid={`pipelineCard-${pipeline.id}`}
        className="flex flex-col bg-white border rounded-sm overflow-hidden group cursor-pointer"
        href={editorUrl}
      >
        <div className="flex items-start gap-3 p-5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-muted">
            <Workflow className="h-5 w-5 text-muted-foreground" />
          </div>

          <div className="flex min-w-0 flex-1 flex-col gap-2">
            <span className="font-medium truncate group-hover:underline">{pipeline.name}</span>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 shrink-0"
                data-testid={`pipelineCardMenu-${pipeline.id}`}
                aria-label={t('menuLabel', { name: pipeline.name })}
                onClick={e => {
                  e.preventDefault()
                  e.stopPropagation()
                }}
              >
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem asChild data-testid={`pipelineCardMenuOpen-${pipeline.id}`}>
                <GuardedLink href={editorUrl}>{t('actions.open')}</GuardedLink>
              </DropdownMenuItem>
              {canDelete && (
                <DropdownMenuItem
                  variant="destructive"
                  disabled={isDeleteDisabled}
                  onClick={e => {
                    e.stopPropagation()
                    if (deleteBlockReason) {
                      setIsDeleteBlockedOpen(true)
                      return
                    }
                    setIsDeleteOpen(true)
                  }}
                  data-testid={`pipelineCardMenuDelete-${pipeline.id}`}
                >
                  {t('actions.delete')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {hasBadges && (
          <>
            <div className="border-t" />

            <div
              className="pl-17 pr-4 py-4 flex items-center gap-2 flex-wrap"
              data-testid={`pipelineCardBadges-${pipeline.id}`}
            >
              {hasError && (
                <RuntimeStatusBadge
                  title={t('errorLabel')}
                  ariaLabel={t('errorDetails', { name: pipeline.name })}
                  cause={runtimeStatus?.message ?? t('pipelineError')}
                  occurredAt={occurredAt}
                  stacktrace={runtimeStatus?.sanitizedStacktrace}
                />
              )}
              {isStopped && (
                <RuntimeStatusBadge
                  title={t('stoppedLabel')}
                  ariaLabel={t('stoppedDetails', { name: pipeline.name })}
                  cause={runtimeStatus?.message}
                  occurredAt={occurredAt}
                  hint={t('restartHint')}
                />
              )}
              {isActive && <ActivityBadge isActive title={t('activeLabel')} />}
              {badges.map(label => (
                <Badge key={label} variant="secondary">
                  {label}
                </Badge>
              ))}
            </div>
          </>
        )}
      </GuardedLink>

      <WarningModal
        open={isDeleteOpen}
        onOpenChange={setIsDeleteOpen}
        title={t('deleteConfirm.title')}
        description={t('deleteConfirm.description', { name: pipeline.name })}
        confirmButtonTitle={t('deleteConfirm.confirm')}
        onDiscard={() => setIsDeleteOpen(false)}
        onConfirm={handleConfirmDelete}
        isLoading={deletePipeline.isPending}
      />

      {deleteBlockReason && (
        <InfoModal
          open={isDeleteBlockedOpen}
          onOpenChange={setIsDeleteBlockedOpen}
          title={t('deleteBlocked.title')}
          description={t(`deleteBlocked.${deleteBlockReason}`)}
          onClose={() => setIsDeleteBlockedOpen(false)}
        />
      )}
    </>
  )
}
