import { FormUser } from '../page'

interface UserFormProps {
  user: FormUser
}

export const UserForm = (props: UserFormProps) => {
  const { user } = props
  return <div>{JSON.stringify(user)}</div>
}
