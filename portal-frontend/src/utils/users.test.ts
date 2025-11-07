import { describe, expect, it } from 'vitest'

import { Authority, TitleSchemaType, UserFormData, UserFormSchema, UserResponse } from '@/types/users'

import { mapApiUserData, mapUserToFormData, mapListUsers } from './users'

const baseFormData = {
  id: '12345',
  title: 'male' as TitleSchemaType,
  firstName: 'Max',
  lastName: 'Mustermann',
  displayName: 'Max Mustermann',
  email: 'maxmustermann@test.de',
  phone: '+49 152 1111111',
  active: true,
  roles: ['1'],
  group: '1',
  authority: 'auth-1',
  department: 'dep-1',
  position: 'Manager',
  positionDescription: '',
}

const baseUserResponse = {
  id: '2',
  firstName: 'Sophia',
  title: 'female' as TitleSchemaType,
  lastName: 'Fischer',
  email: 'sophie.fischer@test.com',
  authority: {
    id: 'auth-1',
    department: {
      id: 'dep-1',
    },
  },
  group: '3',
  phone: '+49 75 5576070',
  active: true,
  position: 'Bauingenieur für Kanalisationsbau',
  positionDescription: 'Bauingenieur für Kanalisationsbau',
  roles: ['1'],
  displayName: 'Sophia Fischer',
}

const authorities: Authority[] = [
  {
    id: 'auth-1',
    title: 'IT-Abteilung',
    departments: [
      { id: 'dep-1', title: 'Backend' },
      { id: 'dep-2', title: 'Frontend' },
    ],
  },
  {
    id: 'auth-2',
    title: 'HR',
    departments: [
      { id: 'dep-1', title: 'Recruiting' },
      { id: 'dep-2', title: 'Payroll' },
    ],
  },
]

describe('MapListUsers', () => {
  it('should map users correctly with matching authority and department', () => {
    const users: UserResponse[] = [baseUserResponse]
    const rolesMap: Record<string, string> = { '1': 'admin', '2': 'user' }

    const result = mapListUsers(users, authorities, rolesMap)

    expect(result).toEqual([
      {
        id: baseUserResponse.id,
        displayName: baseUserResponse.displayName,
        authority: authorities[0].title,
        department: authorities[0].departments[0].title,
        roles: ['admin'],
        email: baseUserResponse.email,
        isactive: baseUserResponse.active,
      },
    ])
  })

  it('should return empty strings if authority or department are null', () => {
    const users: UserResponse[] = [{ ...baseUserResponse, authority: null }]
    const rolesMap: Record<string, string> = { '1': 'admin', '2': 'user' }

    const result = mapListUsers(users, authorities, rolesMap)

    expect(result[0].authority).toBe('')
    expect(result[0].department).toBe('')

    const users2: UserResponse[] = [
      { ...baseUserResponse, authority: { ...baseUserResponse.authority, department: null } },
    ]
    const result2 = mapListUsers(users2, authorities, rolesMap)
    expect(result2[0].authority).toBe(authorities[0].title)
    expect(result2[0].department).toBe('')
  })
})

describe('mapApiUserData', () => {
  it('should map form data correctly to API data', () => {
    const result = mapApiUserData(baseFormData)

    expect(result).toEqual({
      ...UserFormSchema.parse(baseFormData),
      group: '1',
      authority: { id: 'auth-1', department: { id: 'dep-1' } },
      position: 'Manager',
      positionDescription: null,
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
      authority: { id: 'auth-2', department: { id: 'dep-1' } },
      position: 'Developer',
      positionDescription: 'Frontend Dev',
      displayName: 'Lisa Schneider',
    }

    const result = mapUserToFormData(userResponse)

    expect(result).toEqual({
      ...userResponse,
      authority: 'auth-2',
      department: 'dep-1',
      position: 'Developer',
      positionDescription: 'Frontend Dev',
    })
  })

  it('should return null if the user data is null', () => {
    expect(mapUserToFormData(null)).toBeNull()
  })

  it('should return set the form field value to "" if the DB value is null', () => {
    const userResponse: UserResponse = {
      ...baseFormData,
      group: null,
      authority: null,
      position: null,
      positionDescription: null,
    }

    const result = mapUserToFormData(userResponse)

    expect(result?.group).toBe('')
    expect(result?.authority).toBe('')
    expect(result?.department).toBe('')
    expect(result?.position).toBe('')
    expect(result?.positionDescription).toBe('')
  })
})
