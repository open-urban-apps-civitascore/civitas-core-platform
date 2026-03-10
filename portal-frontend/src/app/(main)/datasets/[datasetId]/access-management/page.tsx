import { getDatasetAssignments } from '@/app/services/api/datasets/serverRequests'
import { getGroups } from '@/app/services/api/groups/serverRequests'
import { getRoles } from '@/app/services/api/roles/serverRequests'
import { ROLE_TYPES } from '@/types/roles'
import { mapAssignmentApiResponseToTable } from '@/utils/assignmentMapper'

import { AssignmentsList } from './components/AssignmentsList'

interface PageProps {
  params: Promise<{ datasetId: string }>
}

const AccessManagementPage = async (props: PageProps) => {
  const { params } = props
  const { datasetId } = await params

  const { data: datasetAssignments } = await getDatasetAssignments(datasetId)
  const { data: groups } = await getGroups()
  const { data: roles } = await getRoles()

  const groupRoleAssignments = mapAssignmentApiResponseToTable(datasetAssignments, groups)

  const dataRoles = roles.filter(role => role.roleType === ROLE_TYPES.DATA)

  return (
    <AssignmentsList
      datasetId={datasetId}
      initialAssignments={groupRoleAssignments}
      groups={groups}
      roles={dataRoles}
    />
  )
}

export default AccessManagementPage
