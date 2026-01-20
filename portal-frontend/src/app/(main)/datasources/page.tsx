import { headers } from 'next/headers'

import { Datasource } from '@/types/datasources'
import { getJsonServerRequestParams, JsonServerRequestParams } from '@/utils/requestParams'

import { DatasourcesList } from './components/datasources-list/DatasourcesList'

type Props = {
  searchParams: Promise<JsonServerRequestParams>
}

const getDatasourcesListData = async (params: URLSearchParams) => {
  try {
    const response = await fetch(`${process.env.NEXTAUTH_URL}/api/datasources?${params.toString()}`, {
      headers: {
        cookie: (await headers()).get('cookie') || '',
      },
      cache: 'no-store',
    })

    if (!response.ok) {
      console.error('An error occurred while loading datasources list data')
      return { datasources: [], totalCount: 0 }
    }

    const datasourcesData: Datasource[] = await response.json()

    const totalCount = Number(response.headers.get('X-Total-Count')) || 0

    return { datasources: datasourcesData || [], totalCount }
  } catch (error) {
    console.error(error)
    return { datasources: [], totalCount: 0 }
  }
}

const Datasources = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { jsonServerParams } = getJsonServerRequestParams(params)
  const { datasources, totalCount } = await getDatasourcesListData(jsonServerParams)

  return <DatasourcesList datasources={datasources} rowCount={totalCount} />
}

export default Datasources
