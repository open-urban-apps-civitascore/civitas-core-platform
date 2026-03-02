import { getDatasets } from '@/app/services/api/datasets/serverRequests'
import { getJsonServerRequestParams, JsonServerRequestParams } from '@/utils/requestParams'

import DatasetsList from './components/DatasetsList'

type Props = {
  searchParams: Promise<JsonServerRequestParams>
}

const Datasets = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { jsonServerParams } = getJsonServerRequestParams(params)

  const datasetResponse = await getDatasets(jsonServerParams)
  const datasets = datasetResponse.data

  return <DatasetsList datasets={datasets} rowCount={datasetResponse.totalElements || 0} />
}

export default Datasets
