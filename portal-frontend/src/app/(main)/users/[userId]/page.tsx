import { getUser } from '@/app/services/api/users/serverRequests'

import { UserOverview } from '../components/UserOverview'

interface EditUserPageProps {
  params: Promise<{ userId: string }>
}

const EditUserPage = async (props: EditUserPageProps) => {
  const { params } = props
  const { userId } = await params

  const userData = (await getUser(userId)).data

  return (
    <UserOverview testId="userDetailsPage" userData={userData} title={`${userData.firstName} ${userData.lastName}`} />
  )
}

export default EditUserPage
