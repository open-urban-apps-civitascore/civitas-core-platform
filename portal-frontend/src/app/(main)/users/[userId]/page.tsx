import { UserResponse } from '@/types/users'
import { mapUserToFormData } from '@/utils/users'

import { UserDetails } from '../components/UserDetails'
import { getTranslations } from 'next-intl/server'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

interface PageProps {
  params: Promise<{ userId: string }>
}

const UserDetailsPage = async (props: PageProps) => {
  const { params } = props
  const { userId } = await params

  const getUserData = async (userId: string) => {
    try {
      const userResponse = await fetch(`${URL}/users/${userId}`, {
        cache: 'no-store',
      })

      if (!userResponse) {
        throw new Error('An error occurred while loading form data')
      }

      const userData: UserResponse = await userResponse.json()

      if (!userData || Object.keys(userData).length === 0) {
        throw new Error('User not found')
      }
      return userData
    } catch (error) {
      console.error(error)
      throw new Error('An error occurred while loading form data')
    }
  }

  const userData = await getUserData(userId)

  return <UserDetails userData={userData} isEditMode title={userData.displayName}/>
}

export default UserDetailsPage
