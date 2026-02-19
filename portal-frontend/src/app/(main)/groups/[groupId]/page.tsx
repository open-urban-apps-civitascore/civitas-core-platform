

import { getGroup } from '@/app/services/api/groups/serverRequests'
import { GroupDetails } from '../components/GroupDetails'

interface PageProps {
  params: Promise<{ groupId: string }>
}

const EditGroupPage = async (props: PageProps) => {
  const { params } = props
  const { groupId } = await params

  const groupData = await getGroup(groupId)

  return <GroupDetails title={groupData?.data.name || ''} groupData={groupData?.data} isEditMode />
}

export default EditGroupPage
