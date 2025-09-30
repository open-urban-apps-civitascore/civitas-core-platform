import { SelectOption } from '@/components/form/text-field/Select'
import { UserAuthority, Category, UserResponse } from '../page'
import { Authority, UserForm } from './components/UserForm'
import { TitleSchemaType } from '@/types/users'

export type FormUser = Omit<UserResponse, 'group' | 'authority' | 'department'> & {
  group: string | null
  authority: string
  department: string
}
interface PageProps {
  params: { userId: string }
}

const defaultFormUser = {
  id: '',
  displayName: '',
  firstName: '',
  lastName: '',
  title: 'male' as TitleSchemaType,
  email: '',
  active: false,
  authority: '',
  department: '',
  group: '',
  phone: '',
  role: 'standarduser',
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const getUserData = async (userId: string) => {
  console.log('get user data, userId: ', userId)
  try {
    const userResponse = await fetch(`${URL}/users/${userId}`, {
      cache: 'no-store',
    })
    const userData: UserResponse = await userResponse.json()
    console.log('getuserdata: ', userData)
    if (userData && Object.keys(userData).length > 0) {
      return userData
    } else {
      throw new Error('An error occurred while loading user data')
    }
  } catch {
    throw new Error('An error occurred while loading user data')
  }
}

const getUserGroupsData = async () => {
  console.log('get user groups data')
  try {
    const userGroupsResponse = await fetch(`${URL}/userGroups`, {
      cache: 'no-store',
    })
    const userGroups: Category[] = await userGroupsResponse.json()
    console.log('getUserGroupsData: ', userGroups)
    if (userGroups) {
      return userGroups
    } else {
      throw new Error('An error occurred while loading user groups data')
    }
  } catch (error) {
    throw new Error('An error occurred while loading user groups data')
  }
}

const getAuthorityData = async () => {
  console.log('get authority data')
  try {
    const authoritiesResponse = await fetch(`${URL}/authorities`, {
      cache: 'no-store',
    })
    const authorities: Authority[] = await authoritiesResponse.json()
    console.log('getAuthorityData: ', authorities)
    if (authorities) {
      return authorities
    } else {
      throw new Error('An error occurred while loading user groups data')
    }
  } catch (error) {
    throw new Error('An error occurred while loading user groups data')
  }
}

const page = async (props: PageProps) => {
  const { params } = props
  const isEditMode = params?.userId !== 'add'

  const userDataResponse = await getUserData(params.userId)
  const userData = userDataResponse
    ? {
        ...userDataResponse,
        group: userDataResponse.group
          ?  userDataResponse.group.id
          : '',
          authority: userDataResponse.authority ? userDataResponse.authority.id : '',
          department: userDataResponse.authority ? userDataResponse.authority.department.id : '',

      }
    : null
  const userGroupsData = await getUserGroupsData()
  const authoritiesData = await getAuthorityData()
  console.log('userGroupsData: ', userGroupsData)
  console.log('userData: ', userData)

  const userGroups = userGroupsData?.map(group => ({ label: group.title, value: group.id }))
  const user = userData ?? defaultFormUser

  return userData ? (
    <UserForm userData={user} isEditMode={isEditMode} userGroups={userGroups} authorities={authoritiesData}/>
  ) : (
    <div>User not found</div>
  )
}

export default page
