import {
  Authority,
  GroupAssignmentUser,
  GroupListUser,
  ListUser,
  UserFormData,
  UserFormSchema,
  UserResponse,
} from '@/types/users'

export const mapListUsers = (users: UserResponse[], authorities: Authority[]): ListUser[] =>
  users.map(user => {
    const authority = authorities.find(authority => user.authority?.id === authority.id)
    const department = authority?.departments.find(department => department.id === user.authority?.department?.id)
    return {
      id: user.id,
      displayName: `${user.firstName} ${user.lastName}`,
      authority: authority?.title ?? '-',
      department: department?.title ?? '-',
      email: user.email,
      isActive: user.active,
    }
  })

export const mapGroupListUsers = (
  users: UserResponse[],
  authorities: Authority[],
  userAssignments: { id: string; assignedAt: string }[],
): GroupListUser[] =>
  users.map(user => {
    const authority = authorities.find(authority => user.authority?.id === authority.id)
    const department = authority?.departments.find(department => department.id === user.authority?.department?.id)
    return {
      id: user.id,
      displayName: user.displayName,
      authority: authority?.title ?? '',
      department: department?.title ?? '',
      email: user.email,
      isActive: user.active,
      assignedAt: userAssignments.find(assignment => assignment.id === user.id)?.assignedAt ?? '',
    }
  })

export const mapGoupAssignmentUsers = (users: UserResponse[]): GroupAssignmentUser[] =>
  users.map(user => {
    return {
      id: user.id,
      displayName: user.displayName,
      email: user.email,
      isActive: user.active,
    }
  })

export const mapFormUserToApiData = (formData: UserFormData) => {
  const parsed = UserFormSchema.parse(formData)

  const userData = {
    ...parsed,
    authority: !!parsed.authority
      ? { id: parsed.authority, department: !!parsed.department ? { id: parsed.department } : null }
      : null,
    positionDescription: parsed.positionDescription || null,
    displayName: `${parsed.firstName} ${parsed.lastName}`,
  }
  return userData
}

export const mapUserToFormData = (userResponse: UserResponse | null): UserFormData => ({
  id: userResponse?.id || '',
  firstName: userResponse?.firstName || '',
  lastName: userResponse?.lastName || '',
  email: userResponse?.email || '',
  phone: userResponse?.phone || '',
  title: userResponse?.title || 'male',
  active: userResponse?.active || false,
  authority: userResponse?.authority?.id || '',
  department: userResponse?.authority?.department?.id || '',
  positionDescription: userResponse?.positionDescription || '',
})
