import { TitleSchemaType } from "@/types/users";

export const defaultFormUser = {
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