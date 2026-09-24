'use client'

import { Copy, Globe, Layers, MoreVertical, Timer } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { ComponentType, MouseEvent, useState } from 'react'
import { toast } from 'sonner'

import { usePatchDataset } from '@/app/services/api/datasets/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { ApiStandard, NamedApi, namedApiPathPrefix, NamedApiPayload } from '@/types/namedApis'

const STANDARD_ICONS: Record<ApiStandard, ComponentType<{ className?: string }>> = {
  STA: Timer,
  OWS: Layers,
  CUSTOM: Globe,
}

interface ApiCardProps {
  api: NamedApi
  datasetId: string
  existingApis: NamedApi[]
  canEdit: boolean
  canView: boolean
  isOpenDataAccess: boolean
}

export const ApiCard = ({ api, datasetId, existingApis, canEdit, canView, isOpenDataAccess }: ApiCardProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.apis.card')
  const tStandard = useTranslations('datasets.overview.completion.dataFlow.apis.standardLabels')
  const tProtected = useTranslations('datasets.overview.completion.apis.config')

  const router = useRouter()
  const patchDataset = usePatchDataset()
  const [isDeleteOpen, setIsDeleteOpen] = useState(false)

  const Icon = STANDARD_ICONS[api.standard]
  const typeLabel = tStandard(api.standard)
  const pathPrefix = namedApiPathPrefix(datasetId)

  const viewUrl = `/datasets/${datasetId}/apis/${api.slug}`
  const editUrl = `/datasets/${datasetId}/apis/${api.slug}?mode=edit`

  const suppressCardNavigation = (e: MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
  }

  const handleCopyPath = async () => {
    try {
      await navigator.clipboard.writeText(api.previewUrl || `${window.location.origin}${pathPrefix}${api.slug}`)
      toast.success(t('messages.copySuccess'))
    } catch {
      // Clipboard access can fail in insecure contexts; stay silent rather than misleading the user.
    }
  }

  const handleConfirmDelete = async () => {
    const remainingInputs: NamedApiPayload[] = existingApis
      .filter(a => a.slug !== api.slug)
      .map(a => ({
        name: a.name,
        slug: a.slug,
        standard: a.standard,
        version: a.version,
        description: a.description,
      }))

    try {
      await patchDataset.mutateAsync({ id: datasetId, namedApis: remainingInputs })
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
        data-testid={`apiCard-${api.slug}`}
        className={cn('flex flex-col bg-white border rounded-sm overflow-hidden group ', canView && 'cursor-pointer')}
        href={viewUrl}
      >
        <div className="flex items-start gap-3 p-5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-muted">
            <Icon className="h-5 w-5 text-muted-foreground" />
          </div>

          <div className="flex min-w-0 flex-1 flex-col gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-normal text-muted-foreground">{typeLabel}</span>
              {isOpenDataAccess ? (
                <Badge variant="secondary">{tProtected('openDataBadge')}</Badge>
              ) : (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Badge variant="secondary">{tProtected('protectedBadge')}</Badge>
                  </TooltipTrigger>
                  <TooltipContent>{tProtected('protectedBadgeTooltip')}</TooltipContent>
                </Tooltip>
              )}
            </div>
            <span className={cn('font-medium truncate ', (canView || canEdit) && 'group-hover:underline')}>
              {api.name}
            </span>
            {api.description && (
              <span className="text-sm font-normal text-muted-foreground line-clamp-2">{api.description}</span>
            )}
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 shrink-0"
                data-testid={`apiCardMenu-${api.slug}`}
                aria-label={t('menuLabel', { name: api.name })}
                onClick={suppressCardNavigation}
              >
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {canEdit && (
                <DropdownMenuItem asChild data-testid={`apiCardMenuEdit-${api.slug}`}>
                  <GuardedLink href={editUrl}>{t('actions.edit')}</GuardedLink>
                </DropdownMenuItem>
              )}
              {canView && (
                <DropdownMenuItem asChild data-testid={`apiCardMenuView-${api.slug}`}>
                  <GuardedLink href={viewUrl}>{t('actions.view')}</GuardedLink>
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                onClick={e => {
                  e.stopPropagation()
                  handleCopyPath()
                }}
                data-testid={`apiCardMenuCopy-${api.slug}`}
              >
                {t('actions.copyPath')}
              </DropdownMenuItem>
              {canEdit && (
                <DropdownMenuItem
                  variant="destructive"
                  onClick={e => {
                    e.stopPropagation()
                    setIsDeleteOpen(true)
                  }}
                  data-testid={`apiCardMenuDelete-${api.slug}`}
                >
                  {t('actions.delete')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <div className="border-t" />

        <div className="pl-17 pr-4 py-6 text-sm break-all flex items-center gap-2">
          <span className="flex-1">
            <span className="text-muted-foreground font-normal">{pathPrefix}</span>
            <strong>{api.slug}</strong>
          </span>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={e => {
              suppressCardNavigation(e)
              handleCopyPath()
            }}
            className="h-8 w-8 shrink-0 text-muted-foreground hover:text-foreground"
            data-testid={`apiCardCopy-${api.slug}`}
            aria-label={t('actions.copyPath')}
          >
            <Copy className="h-4 w-4" />
          </Button>
        </div>
      </GuardedLink>

      <WarningModal
        open={isDeleteOpen}
        onOpenChange={setIsDeleteOpen}
        title={t('deleteConfirm.title')}
        description={t('deleteConfirm.description', { name: api.name })}
        confirmButtonTitle={t('deleteConfirm.confirm')}
        onDiscard={() => setIsDeleteOpen(false)}
        onConfirm={handleConfirmDelete}
        isLoading={patchDataset.isPending}
      />
    </>
  )
}
