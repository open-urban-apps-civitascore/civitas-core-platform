import { redirect } from 'next/navigation'

import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { API_STANDARDS, API_TYPE_QUERY, isApiTypeQuery } from '@/types/namedApis'
import { hasApiType } from '@/utils/namedApis'

import { ApiConfigPage } from './components/ApiConfigPage'

interface ApisPageProps {
  params: Promise<{ datasetId: string }>
  searchParams: Promise<{ type?: string }>
}

const ApisPage = async (props: ApisPageProps) => {
  const { datasetId } = await props.params
  const { type } = await props.searchParams

  if (!isApiTypeQuery(type)) {
    redirect(`/datasets/${datasetId}`)
  }

  const { data: dataset } = await getDataset(datasetId)

  if (type === API_TYPE_QUERY.OWS && hasApiType(dataset.namedApis ?? [], API_STANDARDS.OWS)) {
    redirect(`/datasets/${datasetId}`)
  }

  return <ApiConfigPage testId="apiConfigPage" dataset={dataset} apiType={type} />
}

export default ApisPage
