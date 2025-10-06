import { describe, it, expect } from 'vitest'
import { mapApiUserData, mapFormUserData } from './users'
import { UserFormSchema, UserFormData, UserResponse, TitleSchema, TitleSchemaType } from '@/types/users'

const baseFormData = {
  id: '12345',
  title: 'male' as TitleSchemaType,
  firstName: 'Max',
  lastName: 'Mustermann',
  displayName: 'Max Mustermann',
  email: 'maxmustermann@test.de',
  phone: '+49 152 1111111',
  active: true,
  positionDescription: null,
  role: 'admin',
  group: '1',
  authority: 'auth-123',
  department: 'dep-456',
  position: 'Manager',
}
describe('mapApiUserData', () => {
  it('should map form data correctly to API data', () => {
    const result = mapApiUserData(baseFormData)

    expect(result).toEqual({
      ...UserFormSchema.parse(baseFormData),
      group: '1',
      authority: { id: 'auth-123', department: { id: 'dep-456' } },
      position: 'Manager',
      positionDescription: 'Manager',
      displayName: 'Max Mustermann',
    })
  })

  it('should handle missing text input', () => {
    const formData: UserFormData = { ...baseFormData, group: '', authority: '', department: '', position: '' }

    const result = mapApiUserData(formData)

    expect(result.group).toBeNull()
    expect(result.authority).toBeNull()
    expect(result.position).toBeNull()
    expect(result.positionDescription).toBeNull()
  })
})

describe('mapFormUserData', () => {
  it('should map API response correctly to form data', () => {
    const userResponse: UserResponse = {
      ...baseFormData,
      authority: { id: 'auth-999', department: { id: 'dep-888' } },
      position: 'Developer',
      positionDescription: 'Frontend Dev',
      displayName: 'Lisa Schneider',
    }

    const result = mapFormUserData(userResponse)

    expect(result).toEqual({
      ...userResponse,
      authority: 'auth-999',
      department: 'dep-888',
      position: 'Developer',
      positionDescription: 'Frontend Dev',
    })
  })

  it('should return null if the user data is null', () => {
    expect(mapFormUserData(null)).toBeNull()
  })

  it('should return set the form field value to "" if the DB value is null', () => {
    const userResponse: UserResponse = {
      ...baseFormData,
      group: null,
      authority: null,
      position: null,
      positionDescription: null,
    }

    const result = mapFormUserData(userResponse)

    expect(result?.group).toBe('')
    expect(result?.authority).toBe('')
    expect(result?.department).toBe('')
    expect(result?.position).toBe('')
    expect(result?.positionDescription).toBe('')
  })
})
