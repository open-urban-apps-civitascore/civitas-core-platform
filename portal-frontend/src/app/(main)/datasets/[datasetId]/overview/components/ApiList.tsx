'use client'

import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { useGetDatasinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { DATASINK_TYPES } from '@/types/datasinks'
import { API_STANDARDS, NamedApi } from '@/types/namedApis'
import { hasApiType } from '@/utils/namedApis'

import { ApiCard } from './ApiCard'

interface ApiListProps {
  datasetId: string
  apis: NamedApi[]
  canEdit: boolean
  isOpenDataAccess: boolean
}

export const ApiList = ({ datasetId, apis, canEdit, isOpenDataAccess }: ApiListProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.apis')
  const hasWfsWms = hasApiType(apis, API_STANDARDS.WFS) || hasApiType(apis, API_STANDARDS.WMS)

  const { data: datasinksData } = useGetDatasinks(datasetId)

  const postgisDatasinks =
    datasinksData?.data.filter(datasink => datasink.dataSinkType === DATASINK_TYPES.POSTGIS) || []
  const hasPostgisDatasinks = postgisDatasinks.length > 0
  console.log('postgisDatasinks', postgisDatasinks)

  const canCreateWfsWmsApi = !hasWfsWms && hasPostgisDatasinks

  return (
    <div className="py-3">
      <div className="flex items-center justify-between gap-4">
        <h4 className="font-semibold">{t('title')}</h4>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              {t('addButton')}
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem asChild>
              <GuardedLink href={`/datasets/${datasetId}/apis?type=sensorthings`} className="flex items-center gap-3">
                <span>{t('sensorThings')}</span>
                <span className="text-xs text-muted-foreground">{t('sensorThingsSubtitle')}</span>
              </GuardedLink>
            </DropdownMenuItem>
            <DropdownMenuItem disabled={!canCreateWfsWmsApi} asChild={!hasWfsWms}>
              {canCreateWfsWmsApi ? (
                <GuardedLink href={`/datasets/${datasetId}/apis?type=wfs-wms`} className="flex items-center gap-3">
                  <span>{t('wfsWms')}</span>
                  <span className="text-xs text-muted-foreground">{t('wfsWmsSubtitle')}</span>
                </GuardedLink>
              ) : (
                <div className="flex items-center gap-3">
                  <span className="text-muted-foreground">{t('wfsWms')}</span>
                  <span className="text-xs text-muted-foreground">{t('wfsWmsSubtitle')}</span>
                </div>
              )}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {apis.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
          {apis.map(api => (
            <ApiCard
              key={api.slug}
              api={api}
              datasetId={datasetId}
              existingApis={apis}
              canEdit={canEdit}
              isOpenDataAccess={isOpenDataAccess}
            />
          ))}
        </div>
      ) : (
        <p className="mt-4 text-sm font-normal">{t('empty')}</p>
      )}
    </div>
  )
}
