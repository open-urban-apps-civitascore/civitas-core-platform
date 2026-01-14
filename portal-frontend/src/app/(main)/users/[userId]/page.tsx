import { getTranslations } from 'next-intl/server'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { getServerRequestHeaders } from '@/app/services/api/request/getServerRequestHeaders'
import { Group } from '@/types/groups'
import { UserResponse } from '@/types/users'

import { UserDetails } from '../components/UserDetails'

interface PageProps {
  params: Promise<{ userId: string }>
}

const EditUserPage = async (props: PageProps) => {
  const { params } = props
  const { userId } = await params
  const t = await getTranslations('users')

  const getUserData = async (userId: string) => {
    const headers = await getServerRequestHeaders()
    const userRequest = apiRequest<UserResponse>({
      method: 'GET',
      endpoint: `/users/${userId}`,
      headers,
      errorMessage: 'An error occurred while fetching user data.',
    })
    const groupsRequest = apiRequest<Group[]>({
      method: 'GET',
      endpoint: `/groups`,
      headers,
      errorMessage: 'An error occurred while fetching groups data.',
    })
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
