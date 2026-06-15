import { notFound } from 'next/navigation'

import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { API_STANDARDS, API_TYPE_QUERY, ApiTypeQuery } from '@/types/namedApis'

import { ApiConfigPage } from '../components/ApiConfigPage'

interface ApiDetailPageProps {
  params: Promise<{ datasetId: string; slug: string }>
}

const inferApiType = (standard: string): ApiTypeQuery | undefined => {
  if (standard === API_STANDARDS.STA) return API_TYPE_QUERY.SENSORTHINGS
  if (standard === API_STANDARDS.OWS) return API_TYPE_QUERY.OWS
  return undefined
}

const ApiDetailPage = async (props: ApiDetailPageProps) => {
  const { datasetId, slug } = await props.params

  const { data: dataset } = await getDataset(datasetId)
  const namedApi = dataset.namedApis?.find(api => api.slug === slug)

  if (!namedApi) {
    notFound()
  }

  const apiType = inferApiType(namedApi.standard)
  if (!apiType) {
    notFound()
  }

  return <ApiConfigPage testId="apiDetailPage" dataset={dataset} apiType={apiType} existingApi={namedApi} />
}

export default ApiDetailPage
