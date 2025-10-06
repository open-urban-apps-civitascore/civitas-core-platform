import { TitleSchemaType } from '@/types/users'

import { FormUser } from './components/UserForm'

export const defaultFormUser: FormUser = {
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
  position: '',
  positionDescription: '',
}
