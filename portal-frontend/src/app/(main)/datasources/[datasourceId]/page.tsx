import { getTranslations } from 'next-intl/server'

import { getDatasource, getDatasourceAssignments } from '@/app/services/api/datasources/serverRequests'
import { getGroups } from '@/app/services/api/groups/serverRequests'
import { getRoles } from '@/app/services/api/roles/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatasourceApiResponseSchema } from '@/types/datasources'
import { ROLE_TYPES } from '@/types/roles'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { DatasourceOverview } from './components/DatasourceOverview'

type Props = {
  params: Promise<{ datasourceId: string }>
}

const DatasourceDetailsPage = async ({ params }: Props) => {
  const { datasourceId } = await params
  const t = await getTranslations('common')

  const [datasourceResponse, assignmentsResponse, groupsResponse, rolesResponse] = await Promise.all([
    getDatasource(datasourceId),
    getDatasourceAssignments(datasourceId),
    getGroups(),
    getRoles(),
  ])

  const parsedDatasource = DatasourceApiResponseSchema.safeParse(datasourceResponse.data)
  if (!parsedDatasource.success) {
    console.error(t('errors.loadingError'), parsedDatasource)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  const groups = groupsResponse.data ?? []
  const roles = (rolesResponse.data ?? []).filter(role => role.roleType === ROLE_TYPES.DATA)
  const initialAssignments = mapAssignmentApiResponseToTable(assignmentsResponse.data, groups)

  return (
    <DatasourceOverview
      datasource={parsedDatasource.data}
      initialAssignments={initialAssignments}
      groups={groups}
      roles={roles}
    />
  )
}

export default DatasourceDetailsPage
