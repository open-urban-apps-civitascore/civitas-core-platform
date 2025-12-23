import { getTranslations } from 'next-intl/server'

import { Group } from '@/types/groups'
import { UserResponse } from '@/types/users'

import { UserDetails } from '../components/UserDetails'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

interface PageProps {
  params: Promise<{ userId: string }>
}

const UserDetailsPage = async (props: PageProps) => {
  const { params } = props
  const { userId } = await params
  const t = await getTranslations('users')

  const getUserData = async (userId: string) => {
    try {
      const [userResponse, groupsResponse] = await Promise.all([
        fetch(`${URL}/users/${userId}`, {
          cache: 'no-store',
        }),
        fetch(`${URL}/groups`, {
          cache: 'no-store',
        }),
      ])
      if (!userResponse.ok || !groupsResponse.ok) {
        console.error('An error occurred while loading user data')
        return null
      }

      const userData: UserResponse = await userResponse.json()
      const groupsData: Group[] = await groupsResponse.json()

      if (!userData || Object.keys(userData).length === 0) {
        console.error('User not found')
        return null
      }
      const groupsContainingUser = groupsData.filter(group => group.users.find(user => user.id === userData.id))
      const user = { ...userData, groups: groupsContainingUser.map(group => group.id) }
      return user
    } catch (error) {
      console.error(error)
      throw new Error('An error occurred while loading form data')
    }
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

export default UserDetailsPage
