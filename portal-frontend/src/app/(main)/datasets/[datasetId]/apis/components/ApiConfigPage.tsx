'use client'

import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'
import { Dataset } from '@/types/datasets'
import { API_TYPE_QUERY, ApiTypeQuery, NamedApi } from '@/types/namedApis'

import { StaApiConfigPage } from './sta-api/StaApiConfigPage'
import { WfsWmsApiConfigPage } from './wfs-wms-api/WfsWmsApiConfigPage'

interface ApiConfigPageProps {
  dataset: Dataset
  apiType: ApiTypeQuery
  existingApi?: NamedApi
  testId?: string
}

export const ApiConfigPage = ({ apiType, ...props }: ApiConfigPageProps) => {
  switch (apiType) {
    case API_TYPE_QUERY.WFS_WMS:
      return <WfsWmsApiConfigPage {...props} />
    case API_TYPE_QUERY.SENSORTHINGS:
      return <StaApiConfigPage {...props} />
    default:
      return <NoDataPage title="Unknown API Type" />
  }
}
