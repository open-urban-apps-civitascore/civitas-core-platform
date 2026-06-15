'use client'

import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
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
  switch (apiType) {
    case API_TYPE_QUERY.OWS:
      return <OwsApiConfigPage {...props} />
    case API_TYPE_QUERY.SENSORTHINGS:
      return <StaApiConfigPage {...props} />
    default:
      return <NoDataPage title="Unknown API Type" />
  }
}
