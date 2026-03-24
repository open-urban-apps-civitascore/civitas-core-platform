import { getDatasetAssignments } from '@/app/services/api/datasets/serverRequests'
import { mapAssignmentApiResponseToTable } from '@/utils/assignments'

import { AssignmentsList } from './components/AssignmentsList'

interface AccessManagementPageProps {
  params: Promise<{ datasetId: string }>
}

const AccessManagementPage = async (props: AccessManagementPageProps) => {
  const { params } = props
  const { datasetId } = await params

  const { data: datasetAssignments } = await getDatasetAssignments(datasetId)
  const groupRoleAssignments = mapAssignmentApiResponseToTable(datasetAssignments)

  return <AssignmentsList datasetId={datasetId} initialAssignments={groupRoleAssignments} />
}

export default AccessManagementPage
