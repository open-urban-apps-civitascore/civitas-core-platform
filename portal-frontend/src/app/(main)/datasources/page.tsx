import { headers } from 'next/headers'

import { getRequestParams, RequestParams } from '@/utils/getRequestParams'
import { DatasourcesList } from './components/datasources-list/DatasourcesList'

type Props = {
  searchParams: Promise<RequestParams>
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
      return { datasources: [], totalElements: 0 }
    }

    const datasourcesData = await response.json()

    const totalElements = Number(datasourcesData.totalElements) || 0

    return { datasources: datasourcesData || [], totalElements }
  } catch (error) {
    console.error(error)
    return { datasources: [], totalElements: 0 }
  }
}

const Datasources = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { apiParams } = getRequestParams(params)
  const { datasources, totalElements } = await getDatasourcesListData(apiParams)

  return <DatasourcesList datasources={datasources} rowCount={totalElements} />
}

export default Datasources
