'use client'

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
  if (apiType === API_TYPE_QUERY.WFS_WMS) {
    return <WfsWmsApiConfigPage {...props} />
  }
  return <StaApiConfigPage {...props} />
}
