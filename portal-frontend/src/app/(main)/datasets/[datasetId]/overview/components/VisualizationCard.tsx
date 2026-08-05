'use client'

import { ExternalLink, MoreVertical, PieChart } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { ComponentType, useState } from 'react'
import { toast } from 'sonner'

import { useDeleteVisualization } from '@/app/services/api/visualizations/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { Visualization, VISUALIZATION_TYPE_QUERY, VisualizationTypeQuery } from '@/types/visualizations'

const TYPE_ICONS: Record<VisualizationTypeQuery, ComponentType<{ className?: string }>> = {
  [VISUALIZATION_TYPE_QUERY.DASHBOARD]: PieChart,
}

interface VisualizationCardProps {
  visualization: Visualization
  datasetId: string
  canEdit: boolean
  isOpenDataAccess: boolean
}

export const VisualizationCard = ({ visualization, datasetId, canEdit, isOpenDataAccess }: VisualizationCardProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations')
  const tCard = useTranslations('datasets.overview.completion.visualizations.card')
  const tConfig = useTranslations('datasets.overview.completion.visualizations.config')

  const router = useRouter()
  const [isDeleteOpen, setIsDeleteOpen] = useState(false)
  const deleteVisualization = useDeleteVisualization()

  const Icon = TYPE_ICONS[visualization.type]
  const typeLabel = tConfig(`type.${visualization.type}`)

  const goToView = () => router.push(`/datasets/${datasetId}/visualizations/${visualization.id}`)

  const supersetUrl = visualization.externalDashboardId
    ? `${process.env.NEXT_PUBLIC_SUPERSET_URL}/dashboard/${visualization.externalDashboardId}/`
    : undefined

  const handleConfirmDelete = async () => {
    try {
      await deleteVisualization.mutateAsync({ datasetId, visualizationId: visualization.id })
      toast.success(tCard('messages.deleteSuccess'))
    } catch {
      toast.error(tCard('messages.deleteError'))
    }
    setIsDeleteOpen(false)
    router.refresh()
  }

  return (
    <>
      <div
        data-testid={`visualizationCard-${visualization.id}`}
        className="flex flex-col bg-white border rounded-sm overflow-hidden group cursor-pointer"
        onClick={goToView}
      >
        <div className="flex items-start gap-3 p-5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted">
            <Icon className="h-5 w-5 text-muted-foreground" />
          </div>

          <div className="flex min-w-0 flex-1 flex-col gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-normal text-muted-foreground">{typeLabel}</span>
              {isOpenDataAccess ? (
                <Badge variant="secondary">{tConfig('openDataBadge')}</Badge>
              ) : (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Badge variant="secondary">{tConfig('protectedBadge')}</Badge>
                  </TooltipTrigger>
                  <TooltipContent>{tConfig('protectedBadgeTooltip')}</TooltipContent>
                </Tooltip>
              )}
            </div>
            <span className="font-medium truncate group-hover:underline">{visualization.name}</span>
            {visualization.description ? (
              <span className="text-sm font-normal text-muted-foreground line-clamp-2">
                {visualization.description}
              </span>
            ) : (
              <span className="text-sm font-normal text-muted-foreground">{t('noDescription')}</span>
            )}
          </div>

          <div className="flex items-center shrink-0">
            {supersetUrl ? (
              <Button variant="ghost" size="icon" className="h-8 w-8" asChild>
                <a
                  href={supersetUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={tCard('actions.openInSuperset')}
                  onClick={e => e.stopPropagation()}
                >
                  <ExternalLink className="h-4 w-4" />
                </a>
              </Button>
            ) : (
              <Tooltip>
                <TooltipTrigger asChild>
                  <span>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-8 w-8"
                      disabled
                      aria-label={tCard('actions.openInSuperset')}
                      onClick={e => e.stopPropagation()}
                    >
                      <ExternalLink className="h-4 w-4" />
                    </Button>
                  </span>
                </TooltipTrigger>
                <TooltipContent>{tCard('notYetProvisionedTooltip')}</TooltipContent>
              </Tooltip>
            )}

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  data-testid={`visualizationCardMenu-${visualization.id}`}
                  aria-label={visualization.name}
                  onClick={e => e.stopPropagation()}
                >
                  <MoreVertical className="h-4 w-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {canEdit && (
                  <DropdownMenuItem
                    variant="destructive"
                    onClick={e => {
                      e.stopPropagation()
                      setIsDeleteOpen(true)
                    }}
                    data-testid={`visualizationCardMenuDelete-${visualization.id}`}
                  >
                    {tCard('actions.delete')}
                  </DropdownMenuItem>
                )}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>

      <WarningModal
        open={isDeleteOpen}
        onOpenChange={setIsDeleteOpen}
        title={tCard('deleteConfirm.title')}
        description={tCard('deleteConfirm.description', { name: visualization.name })}
        confirmButtonTitle={tCard('deleteConfirm.confirm')}
        onDiscard={() => setIsDeleteOpen(false)}
        onConfirm={handleConfirmDelete}
        isLoading={false}
      />
    </>
  )
}
