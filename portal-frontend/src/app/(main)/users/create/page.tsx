import { getTranslations } from 'next-intl/server'

import { TitleSchemaType, UserResponse } from '@/types/users'

import { UserDetails } from '../components/UserDetails'

export const defaultFormUser: UserResponse = {
  id: '',
  displayName: '',
  firstName: '',
  lastName: '',
  title: 'male' as TitleSchemaType,
  email: '',
  active: false,
  authority: null,
  groups: [],
  phone: '',
  positionDescription: '',
}

const CreateUserPage = async () => {
  const t = await getTranslations('users')
  return <UserDetails userData={defaultFormUser} title={t('newUser')} />
}

export default CreateUserPage
