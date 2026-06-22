import { getDatapools } from '@/app/services/api/datapools/serverRequests'
import { ApiRequestParams, getApiRequestParams } from '@/utils/requestParams'

import DatapoolsList from './components/DatapoolsList'

type Props = {
  searchParams: Promise<ApiRequestParams>
}

const Datapools = async ({ searchParams }: Props) => {
  const params = await searchParams

  const { apiParams } = getApiRequestParams(params)

  const dataPoolResponse = await getDatapools(apiParams)
  const datapools = dataPoolResponse.data || []

  return <DatapoolsList datapools={datapools} rowCount={dataPoolResponse.totalElements || 0} />
}

export default Datapools
