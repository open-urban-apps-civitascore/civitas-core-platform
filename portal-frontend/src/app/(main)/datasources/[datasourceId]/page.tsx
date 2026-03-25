import { getTranslations } from 'next-intl/server'

import { getDatasource, getDatasourceAssignments } from '@/app/services/api/datasources/serverRequests'
import { getDatastructure } from '@/app/services/api/datastructures/serverRequests'
import { getDatastructureVersion } from '@/app/services/api/datastructures/versions/serverRequests'
import { DatasourceApiResponseSchema } from '@/types/datasources'
import {
  Datastructure,
  DatastructureApiResponseSchema,
  DatastructureVersion,
  DatastructureVersionApiResponseSchema,
} from '@/types/datastructures'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { DatasourceOverview } from './components/DatasourceOverview'

type Props = {
  params: Promise<{ datasourceId: string }>
}

const DatasourceDetailsPage = async ({ params }: Props) => {
  const { datasourceId } = await params
  const t = await getTranslations('common')
  let datastructure: Datastructure | null = null
  let datastructureVersion: DatastructureVersion | null = null
  const [datasourceResponse, assignmentsResponse] = await Promise.all([
    getDatasource(datasourceId),
    getDatasourceAssignments(datasourceId),
  ])
  const parsedDatasource = DatasourceApiResponseSchema.safeParse(datasourceResponse.data)
  if (!parsedDatasource.success) {
    console.error(t('errors.loadingError'), parsedDatasource)
    throw new Error()
  }
  const datastructureVersionId = parsedDatasource.data.dataStructureVersion?.id
  const datastructureId = parsedDatasource.data.dataStructureVersion?.dataStructureId

  if (datastructureId) {
    const datastructuresResponse = await getDatastructure(datastructureId)
    const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructuresResponse.data)
    if (!parsedDatastructure.data) {
      console.error(t('errors.loadingError'))
      throw new Error()
    }
    datastructure = parsedDatastructure.data
  }

  if (datastructureVersionId && datastructureId) {
    const datastructureVersionResponse = await getDatastructureVersion(datastructureId, datastructureVersionId)
    const parsedVersion = DatastructureVersionApiResponseSchema.safeParse(datastructureVersionResponse.data)
    if (!parsedVersion.success) {
      console.error(t('errors.loadingError'), parsedVersion)
      throw new Error()
    }
    datastructureVersion = parsedVersion.data
  }

  const initialAssignments = mapAssignmentApiResponseToTable(assignmentsResponse.data)
  return (
    <DatasourceOverview
      datasource={parsedDatasource.data}
      datastructure={datastructure}
      datastructureVersion={datastructureVersion}
      initialAssignments={initialAssignments}
    />
  )
}

export default DatasourceDetailsPage
