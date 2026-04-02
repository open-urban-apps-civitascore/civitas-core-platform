import { getDatasets } from '@/app/services/api/datasets/serverRequests'
import { ApiRequestParams, getApiRequestParams } from '@/utils/requestParams'

import DatasetsList from './components/DatasetsList'

type Props = {
  searchParams: Promise<ApiRequestParams>
}

const Datasets = async ({ searchParams }: Props) => {
  const params = await searchParams

  const { apiParams } = getApiRequestParams(params)

  const datasetResponse = await getDatasets(apiParams)
  const datasets = datasetResponse.data

  return <DatasetsList datasets={datasets} rowCount={datasetResponse.totalElements || 0} />
}

export default Datasets
