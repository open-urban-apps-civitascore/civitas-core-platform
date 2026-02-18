import { getDatastructures } from '@/app/services/api/datastructures/serverRequests'
import { mapDatastructuresApiToListData } from '@/utils/datastructures'
import { getJsonServerRequestParams, JsonServerRequestParams } from '@/utils/requestParams'

import { DatastructuresList } from './components/list/DatastructuresList'

type Props = {
  searchParams: Promise<JsonServerRequestParams>
}

const Datastructures = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { jsonServerParams } = getJsonServerRequestParams(params)
  const datastructuresResponse = await getDatastructures(jsonServerParams)
  const datastructures = mapDatastructuresApiToListData(datastructuresResponse.data)

  return <DatastructuresList datastructures={datastructures} rowCount={datastructuresResponse.totalElements || 0} />
}

export default Datastructures
