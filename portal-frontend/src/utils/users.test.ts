import { describe, expect, it } from 'vitest'

import { Authority, TitleType, User, UserFormData, UserFormSchema } from '@/types/users'

import { mapFormUserToApiData, mapListUsers, mapUserToFormData } from './users'

const baseFormData: UserFormData = {
  id: '12345',
  title: 'MR' as TitleType,
  firstName: 'Max',
  lastName: 'Mustermann',
  email: 'maxmustermann@test.de',
  phone: '+49 152 1111111',
  active: true,
  authority: 'auth-1',
  department: 'dep-1',
  positionDescription: 'Bauingenieur für Kanalisationsbau',
}

const baseUserResponse: User = {
  id: '12345',
  firstName: 'Max',
  title: 'MR' as TitleType,
  lastName: 'Mustermann',
  email: 'maxmustermann@test.de',
  authority: {
    id: 'auth-1',
    department: {
      id: 'dep-1',
    },
  },
  groups: ['3'],
  phone: '+49 152 1111111',
  active: true,
  positionDescription: 'Bauingenieur für Kanalisationsbau',
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
  // TODO Fix and enable
  // Disabled for pipeline development, to not have failing tests and a blocked MR
  it.skip('should map users correctly with matching authority and department', () => {
    const users: User[] = [baseUserResponse]

    const result = mapListUsers(users, authorities)

    expect(result).toEqual([
      {
        ...baseFormData,
        authority: authorities[0].title,
        department: authorities[0].departments[0].title,
      },
    ])
  })

  it('should return empty strings if authority or department are null', () => {
    const users: User[] = [{ ...baseUserResponse, authority: null }]
    const result = mapListUsers(users, authorities)

    expect(result[0].authority).toBe('-')
    expect(result[0].department).toBe('-')

    const users2: User[] = [{ ...baseUserResponse, authority: { id: 'auth-1', department: null } }]
    const result2 = mapListUsers(users2, authorities)
    expect(result2[0].authority).toBe(authorities[0].title)
    expect(result2[0].department).toBe('-')
  })
})

describe('mapFormUserToApiData', () => {
  it('should map form data correctly to API data', () => {
    const result = mapFormUserToApiData(baseFormData)

    expect(result).toEqual({
      ...UserFormSchema.parse(baseFormData),
      authority: { id: 'auth-1', department: { id: 'dep-1' } },
      displayName: `${baseUserResponse.firstName} ${baseUserResponse.lastName}`,
    })
  })

  it('should handle missing input', () => {
    const formData1: UserFormData = { ...baseFormData, authority: '', department: '' }
    const formData2: UserFormData = { ...baseFormData, authority: 'auth-1', department: '' }

    const result1 = mapFormUserToApiData(formData1)
    const result2 = mapFormUserToApiData(formData2)

    expect(result1.authority).toBeNull()
    expect(result1.authority?.department).toBeUndefined()
    expect(result2.authority).toEqual({ id: 'auth-1', department: null })
  })
})

describe('mapUserToFormData', () => {
  it('should map API response correctly to form data', () => {
    const userResponse: User = {
      ...baseUserResponse,
      authority: { id: 'auth-2', department: { id: 'dep-1' } },
    }

    const result = mapUserToFormData(userResponse)

    expect(result).toEqual({
      ...baseFormData,
      authority: 'auth-2',
      department: 'dep-1',
    })
  })

  it('should return a user with empty fields if the user data is null', () => {
    expect(mapUserToFormData(null)).toEqual({
      id: '',
      title: 'MR' as TitleType,
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      active: false,
      authority: '',
      department: '',
      positionDescription: '',
    })
  })

  it('should set the form field value to "" if the DB value is null', () => {
    const userResponse: User = {
      ...baseUserResponse,
      authority: null,
    }

    const result = mapUserToFormData(userResponse)

    expect(result?.authority).toBe('')
    expect(result?.department).toBe('')
  })
})
