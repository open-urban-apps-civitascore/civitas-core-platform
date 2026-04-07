import { getDatastructures } from '@/app/services/api/datastructures/serverRequests'
import { mapDatastructuresApiToListData } from '@/utils/datastructures'
import { ApiRequestParams, getApiRequestParams } from '@/utils/requestParams'

import { DatastructuresList } from './components/list/DatastructuresList'

type Props = {
  searchParams: Promise<ApiRequestParams>
}

const Datastructures = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { apiParams } = getApiRequestParams(params)
  const datastructuresResponse = await getDatastructures(apiParams)
  const datastructures = mapDatastructuresApiToListData(datastructuresResponse.data)

  return <DatastructuresList datastructures={datastructures} rowCount={datastructuresResponse.totalElements || 0} />
}

export default Datastructures
