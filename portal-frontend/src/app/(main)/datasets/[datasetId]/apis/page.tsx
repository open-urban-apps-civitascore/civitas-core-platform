import { redirect } from 'next/navigation'

import { getDataSinks } from '@/app/services/api/datasets/datasinks/serverRequests'
import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { DATASET_STATUS_TYPES } from '@/types/datasets'
import { API_TYPE_QUERY, isApiTypeQuery } from '@/types/namedApis'
import { canCreateOwsApi, canCreateStaApi } from '@/utils/namedApis'

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

  if (dataset.dataSetStatus !== DATASET_STATUS_TYPES.DRAFT) {
    redirect(`/datasets/${datasetId}`)
  }

  const { data: dataSinks } = await getDataSinks(datasetId)
  const apis = dataset.namedApis ?? []

  if (type === API_TYPE_QUERY.OWS && !canCreateOwsApi(apis, dataSinks)) {
    redirect(`/datasets/${datasetId}`)
  }

  if (type === API_TYPE_QUERY.SENSORTHINGS && !canCreateStaApi(apis, dataSinks)) {
    redirect(`/datasets/${datasetId}`)
  }

  return <ApiConfigPage testId="apiConfigPage" dataset={dataset} apiType={type} />
}

export default ApisPage
