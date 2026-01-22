import { getDatasource } from '@/app/services/api/datasources/serverRequests'

import { DatasourceOverview } from '../components/DatasourceOverview'

type Props = {
  params: Promise<{ datasourceId: string }>
}

const DatasourceDetailsPage = async ({ params }: Props) => {
  const { datasourceId } = await params
  const datasourceResponse = await getDatasource(datasourceId)

  return <DatasourceOverview datasource={datasourceResponse.data} />
}

export default DatasourceDetailsPage
