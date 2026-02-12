import { getTranslations } from 'next-intl/server'

import { User } from '@/types/users'

import { UserOverview } from '../components/UserOverview'

export const defaultFormUser: User = {
  id: '',
  firstName: '',
  lastName: '',
  title: 'MR',
  email: '',
  active: false,
  groups: [],
  phone: '',
}

const CreateUserPage = async () => {
  const t = await getTranslations('users')
  return <UserOverview testId="createUserPage" userData={defaultFormUser} title={t('newUser')} isCreateMode />
}

export default CreateUserPage
