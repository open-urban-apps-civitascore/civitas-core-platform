import { getGroup } from '@/app/services/api/groups/serverRequests'

import { GroupOverview } from '../components/GroupOverview'

interface PageProps {
  params: Promise<{ groupId: string }>
}

const EditGroupPage = async (props: PageProps) => {
  const { params } = props
  const { groupId } = await params

  const groupData = await getGroup(groupId)

  return <GroupOverview title={groupData?.data.name || ''} groupData={groupData?.data} isCreateMode={false} />
}

export default EditGroupPage
