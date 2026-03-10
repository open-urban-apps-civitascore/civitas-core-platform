import { ListUser, User, UserFormData } from '@/types/users'

export const mapListUsers = (users: User[]): ListUser[] =>
  users.map(user => {
    return {
      id: user.id,
      fullName: `${user.firstName} ${user.lastName}`,
      email: user.email,
      active: user.active,
    }
  })

export const mapGroupListUsers = (users: User[]): ListUser[] =>
  users.map(user => {
    return {
      id: user.id,
      fullName: `${user.firstName} ${user.lastName}`,
      email: user.email,
    }
  })

export const mapUserToFormData = (userResponse: User): UserFormData => ({
  id: userResponse?.id || '',
  firstName: userResponse?.firstName || '',
  lastName: userResponse?.lastName || '',
  email: userResponse?.email || '',
  phone: userResponse?.phone || '',
  title: userResponse?.title || 'MR',
  active: userResponse?.active || true,
  groupIds: userResponse?.groups?.map(group => group.id) || [],
})
