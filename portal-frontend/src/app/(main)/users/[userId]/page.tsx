import { UserDetails } from '../components/UserDetails'
import { Authority, FormUser } from '../components/UserForm'
import { Category, UserResponse } from '../page'

interface PageProps {
  params: { userId: string }
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const transformUserData = (userResponse: UserResponse): FormUser | null =>
  userResponse && Object.keys(userResponse).length > 0
    ? {
        ...userResponse,
        group: userResponse.group ? userResponse.group.id : '',
        authority: userResponse.authority ? userResponse.authority.id : '',
        department: userResponse.authority ? userResponse.authority.department.id : '',
      }
    : null

export const getFormData = async (userId: string) => {
  try {
    const [userGroupsResponse, authoritiesResponse, userResponse] = await Promise.all([
      fetch(`${URL}/userGroups`, {
        cache: 'no-store',
      }),
      fetch(`${URL}/authorities`, {
        cache: 'no-store',
      }),
      fetch(`${URL}/users/${userId}`, {
        cache: 'no-store',
      }),
    ])
    if (!userGroupsResponse || !authoritiesResponse || !userResponse) {
      throw new Error('An error occurred while loading form data')
    }

    const [userGroupsData, authoritiesData, userData]: [Category[], Authority[], UserResponse] = await Promise.all([
      userGroupsResponse.json(),
      authoritiesResponse.json(),
      userResponse.json(),
    ])
    return { userGroupsData, authoritiesData, userData: transformUserData(userData) }
  } catch (error) {
    throw new Error('An error occurred while loading form data')
  }
}

const page = async (props: PageProps) => {
  const { params } = props

  const { userData, authoritiesData, userGroupsData } = await getFormData(params.userId)

  const userGroups = userGroupsData?.map(group => ({ label: group.title, value: group.id }))

  return <UserDetails userData={userData} isEditMode userGroups={userGroups} authorities={authoritiesData} />
}

export default page
