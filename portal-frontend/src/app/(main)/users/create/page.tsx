import { getTranslations } from 'next-intl/server'

import { TitleType, User } from '@/types/users'

import { UserDetails } from '../components/UserDetails'

export const defaultFormUser: User = {
  id: '',
  firstName: '',
  lastName: '',
  title: 'male' as TitleType,
  email: '',
  active: false,
  authority: null,
  groups: [],
  phone: '',
  positionDescription: '',
}

const CreateUserPage = async () => {
  const t = await getTranslations('users')
  return <UserDetails testId="createUserPage" userData={defaultFormUser} title={t('newUser')} />
}

export default CreateUserPage
