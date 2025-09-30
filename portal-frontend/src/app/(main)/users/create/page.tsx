import { Authority, UserForm } from '../components/UserForm'
import { defaultFormUser } from '../formDefaults'
import { Category } from '../page'

export const getFormData = async () => {
  console.log('get form data')
  try {
    const [userGroupsResponse, authoritiesResponse] = await Promise.all([
      fetch(`${URL}/userGroups`, {
        cache: 'no-store',
      }),
      fetch(`${URL}/authorities`, {
        cache: 'no-store',
      }),
    ])
    if (!userGroupsResponse || !authoritiesResponse) {
      throw new Error('An error occurred while loading form data')
    }

    const [userGroupsData, authoritiesData]: [Category[], Authority[]] = await Promise.all([
      userGroupsResponse.json(),
      authoritiesResponse.json(),
    ])
    console.log('userGroups: ', userGroupsData)
    console.log('authorities: ', authoritiesData)
    return { userGroupsData, authoritiesData }
  } catch (error) {
    throw new Error('An error occurred while loading form data')
  }
}

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

const page = async () => {

  const { userGroupsData, authoritiesData } = await getFormData()

  const userGroups = userGroupsData?.map(group => ({ label: group.title, value: group.id }))

  return <UserForm userData={defaultFormUser} userGroups={userGroups} authorities={authoritiesData} />
}

export default page
