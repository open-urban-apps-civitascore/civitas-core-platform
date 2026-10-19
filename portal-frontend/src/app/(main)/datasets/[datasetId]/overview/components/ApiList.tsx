'use client'

import { ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { NamedApi } from '@/types/namedApis'
import { canCreateOwsApi, canCreateStaApi } from '@/utils/namedApis'

import { AddApiMenuItem } from './AddApiMenuItem'
import { ApiCard } from './ApiCard'

interface ApiListProps {
  datasetId: string
  apis: NamedApi[]
  canEdit: boolean
  canView: boolean
  isOpenDataAccess: boolean
}

export const ApiList = ({ datasetId, apis, canEdit, canView, isOpenDataAccess }: ApiListProps) => {
  const t = useTranslations('datasets.overview.completion.dataFlow.apis')
  const { data: dataSinksData } = useGetDataSinks(datasetId)
  const dataSinks = dataSinksData?.data ?? []

  const canCreateSta = canCreateStaApi(apis, dataSinks)
  const canCreateOws = canCreateOwsApi(apis, dataSinks)

  return (
    <div className="py-3">
      <div className="flex items-center justify-between gap-4">
        <h4 className="font-semibold">{t('title')}</h4>

        {canEdit && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline">
                {t('addButton')}
                <ChevronDown className="ml-2 h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <AddApiMenuItem
                href={`/datasets/${datasetId}/apis?type=sensorthings`}
                title={t('sensorThings')}
                subtitle={t('sensorThingsSubtitle')}
                canCreate={canCreateSta}
              />
              <AddApiMenuItem
                href={`/datasets/${datasetId}/apis?type=ows`}
                title={t('ows')}
                subtitle={t('owsSubtitle')}
                canCreate={canCreateOws}
              />
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>

      {apis.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4 mt-4">
          {apis.map(api => (
            <ApiCard
              key={api.slug}
              api={api}
              datasetId={datasetId}
              existingApis={apis}
              canEdit={canEdit}
              canView={canView}
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
