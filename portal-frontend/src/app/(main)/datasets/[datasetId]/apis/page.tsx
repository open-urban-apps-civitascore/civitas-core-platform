import { redirect } from 'next/navigation'

import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { isApiTypeQuery } from '@/types/namedApis'

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

  return <ApiConfigPage testId="apiConfigPage" dataset={dataset} apiType={type} />
}

export default ApisPage
