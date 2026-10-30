import { getTranslations } from 'next-intl/server'

import { getDatasource, getDatasourceAssignments } from '@/app/services/api/datasources/serverRequests'
import { ErrorPage } from '@/components/error-page/ErrorPage'
import { DatasourceApiResponseSchema, getConnectorIssues } from '@/types/datasources'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { DatasourceOverview } from './components/DatasourceOverview'

type Props = {
  params: Promise<{ datasourceId: string }>
}

const DatasourceDetailsPage = async ({ params }: Props) => {
  const { datasourceId } = await params
  const t = await getTranslations('common')
  const tErrors = await getTranslations('datasources.errors')
  const [datasourceResponse, assignmentsResponse] = await Promise.all([
    getDatasource(datasourceId),
    getDatasourceAssignments(datasourceId),
  ])
  const parsedDatasource = DatasourceApiResponseSchema.safeParse(datasourceResponse.data)
  if (!parsedDatasource.success) {
    console.error(t('errors.loadingError'), parsedDatasource)
    throw new Error()
  }

  const connectorIssues = getConnectorIssues(parsedDatasource.data.connector)
  if (connectorIssues.length > 0) {
    console.error(t('errors.loadingError'), connectorIssues)
    return <ErrorPage testId="datasourceLoadingErrorPage" errorText={tErrors('unreadableDatasource')} />
  }

  const initialAssignments = mapAssignmentApiResponseToTable(assignmentsResponse.data)
  return <DatasourceOverview datasource={parsedDatasource.data} initialAssignments={initialAssignments} />
}

export default DatasourceDetailsPage
