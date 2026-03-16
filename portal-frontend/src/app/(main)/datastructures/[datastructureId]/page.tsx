import { getTranslations } from 'next-intl/server'

import { getDatastructure, getDatastructureAssignments } from '@/app/services/api/datastructures/serverRequests'
import { getGroups } from '@/app/services/api/groups/serverRequests'
import { getRoles } from '@/app/services/api/roles/serverRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { DatastructureApiResponseSchema } from '@/types/datastructures'
import { ROLE_TYPES } from '@/types/roles'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { DatastructureOverview } from './components/DatastructureOverview'

type EditDatastructurePageProps = {
  params: Promise<{ datastructureId: string }>
}

const EditDatastructurePage = async ({ params }: EditDatastructurePageProps) => {
  const { datastructureId } = await params
  const t = await getTranslations('common')

  const [datastructureResponse, assignmentsResponse, groupsResponse, rolesResponse] = await Promise.all([
    getDatastructure(datastructureId),
    getDatastructureAssignments(datastructureId),
    getGroups(),
    getRoles(),
  ])

  const parsedDatastructure = DatastructureApiResponseSchema.safeParse(datastructureResponse.data)
  if (!parsedDatastructure.success) {
    console.error(t('errors.loadingError'), parsedDatastructure)
    return <NoDataPage title={t('errors.loadingError')} />
  }

  const groups = groupsResponse.data ?? []
  const roles = (rolesResponse.data ?? []).filter(role => role.roleType === ROLE_TYPES.DATA)
  const initialAssignments = mapAssignmentApiResponseToTable(assignmentsResponse.data, groups)

  return (
    <DatastructureOverview
      datastructure={parsedDatastructure.data}
      groups={groups}
      roles={roles}
      initialAssignments={initialAssignments}
    />
  )
}

export default EditDatastructurePage
