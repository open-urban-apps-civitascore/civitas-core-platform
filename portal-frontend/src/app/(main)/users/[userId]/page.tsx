import { getTranslations } from 'next-intl/server'

import { getServerRequestConfig } from '@/app/services/api/client/serverRequestConfig'
import { fetchGroups } from '@/app/services/api/groups/groupsService'
import { fetchUser } from '@/app/services/api/users/userService'
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
    const config = getServerRequestConfig()
    const [userData, groupsData] = await Promise.all([
      fetchUser(config, userId) as Promise<UserResponse>,
      fetchGroups(config) as Promise<Group[]>,
    ])

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
