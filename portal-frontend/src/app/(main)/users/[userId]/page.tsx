import { UserResponse } from '@/types/users'
import { mapFormUserData } from '@/utils/users'

import { UserDetails } from '../components/UserDetails'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

interface PageProps {
  params: Promise<{ userId: string }>
}

const page = async (props: PageProps) => {
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

  const userData = mapFormUserData(await getUserData(userId))

  return <UserDetails userData={userData} isEditMode />
}

export default page
