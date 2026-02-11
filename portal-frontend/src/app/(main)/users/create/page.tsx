import { getTranslations } from 'next-intl/server'

import { User } from '@/types/users'

import { UserDetails } from '../components/UserOverview'

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
  return <UserDetails testId="createUserPage" userData={defaultFormUser} title={t('newUser')} isCreateMode />
}

export default CreateUserPage
