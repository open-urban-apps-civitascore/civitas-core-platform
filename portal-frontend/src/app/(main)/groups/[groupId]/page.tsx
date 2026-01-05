import { Group } from '@/types/groups'

import { GroupDetails } from '../components/GroupDetails'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

interface UpdateGroupPageProps {
  params: Promise<{ groupId: string }>
}

const EditGroupPage = async (props: UpdateGroupPageProps) => {
  const { params } = props
  const { groupId } = await params

  const getGroupData = async () => {
    try {
      const groupResponse = await fetch(`${URL}/groups/${groupId}`, {
        cache: 'no-store',
      })

      if (!groupResponse) {
        throw new Error('An error occurred while loading form data')
      }

      const groupData: Group = await groupResponse.json()

      if (!groupData || Object.keys(groupData).length === 0) {
        throw new Error('Group not found')
      }
      return groupData
    } catch (error) {
      console.error(error)
      throw new Error('An error occurred while loading form data')
    }
  }

  const groupData = await getGroupData()

  return <GroupDetails title={groupData?.title || ''} groupData={groupData} isEditMode />
}

export default EditGroupPage
