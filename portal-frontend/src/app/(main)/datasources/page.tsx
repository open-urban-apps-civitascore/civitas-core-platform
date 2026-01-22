import { getDatasources } from '@/app/services/api/datasources/serverRequests'
import { getJsonServerRequestParams, JsonServerRequestParams } from '@/utils/requestParams'

import { DatasourcesList } from './components/DatasourcesList'

type Props = {
  searchParams: Promise<JsonServerRequestParams>
}

const Datasources = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { jsonServerParams } = getJsonServerRequestParams(params)
  const datasourcesResponse = await getDatasources(jsonServerParams)

  return <DatasourcesList datasources={datasourcesResponse.data} rowCount={datasourcesResponse.totalElements || 0} />
}

export default Datasources
