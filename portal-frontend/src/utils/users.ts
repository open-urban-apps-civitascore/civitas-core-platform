import { FormUser } from '@/app/(main)/users/components/UserForm'
import { UserFormData, UserFormSchema, UserResponse } from '@/types/users'

export const mapApiUserData = (formData: UserFormData) => {
  const parsed = UserFormSchema.parse(formData)

  const userData = {
    ...parsed,
    group: parsed.group || null,
    authority: parsed.authority
      ? { id: parsed.authority, department: parsed.department ? { id: parsed.department } : null }
      : null,
    position: parsed.position || null,
    positionDescription: parsed.positionDescription || null,
    displayName: `${parsed.firstName} ${parsed.lastName}`,
  }
  return userData
}

export const mapFormUserData = (userResponse: UserResponse | null): FormUser | null =>
  userResponse
    ? {
        ...userResponse,
        group: userResponse.group || '',
        authority: userResponse.authority?.id || '',
        department: userResponse.authority?.department?.id || '',
        position: userResponse.position || '',
        positionDescription: userResponse.positionDescription || '',
      }
    : null
