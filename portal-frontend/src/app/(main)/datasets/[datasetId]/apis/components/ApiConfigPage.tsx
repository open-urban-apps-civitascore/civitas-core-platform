'use client'

import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { useDatasetPermissions } from '@/hooks/use-dataset-permissions'
import { Dataset } from '@/types/datasets'
import { API_TYPE_QUERY, ApiTypeQuery, NamedApi } from '@/types/namedApis'

import { OwsApiConfigPage } from './ows-api/OwsApiConfigPage'
import { StaApiConfigPage } from './sta-api/StaApiConfigPage'

interface ApiConfigPageProps {
  dataset: Dataset
  apiType: ApiTypeQuery
  existingApi?: NamedApi
  testId?: string
}

export const ApiConfigPage = ({ apiType, ...props }: ApiConfigPageProps) => {
  const { dataset, existingApi } = props
  const router = useRouter()
  const { canEditApis } = useDatasetPermissions(dataset)

  const isCreateBlocked = !existingApi && !canEditApis

  useEffect(() => {
    if (isCreateBlocked) router.replace(`/datasets/${dataset.id}`)
  }, [isCreateBlocked, router, dataset.id])

  // Without this line the locked form is visible until the redirect completes.
  if (isCreateBlocked) return <LoadingSpinner className="h-full" />

  switch (apiType) {
    case API_TYPE_QUERY.OWS:
      return <OwsApiConfigPage {...props} />
    case API_TYPE_QUERY.SENSORTHINGS:
      return <StaApiConfigPage {...props} />
    default:
      return <NoDataPage title="Unknown API Type" />
  }
}
