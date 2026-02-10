import { getTranslations } from 'next-intl/server'

import { getGroups } from '@/app/services/api/groups/serverRequests'
import { getUser } from '@/app/services/api/users/serverRequests'

import { UserDetails } from '../components/UserOverview'

interface PageProps {
  params: Promise<{ userId: string }>
}

const EditUserPage = async (props: PageProps) => {
  const { params } = props
  const { userId } = await params
  const t = await getTranslations('users')

  const getUserData = async (userId: string) => {
    const userRequest = getUser(userId)
    const groupsRequest = getGroups()
    const [userResponse, groupsResponse] = await Promise.all([userRequest, groupsRequest])
    const userData = userResponse.data
    const groupsData = groupsResponse.data
    const groupsContainingUser = groupsData.filter(group => group.users.find(user => user.id === userData.id))
    const user = { ...userData, groups: groupsContainingUser.map(group => group.id) }
    return user
  }

  const userData = await getUserData(userId)

  return (
    <UserDetails
      testId="userDetailsPage"
      userData={userData}
      isEditMode
      title={userData ? `${userData.firstName} ${userData.lastName}` : t('notFound')}
    />
  )
}

export default EditUserPage
