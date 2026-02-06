import { TitleType, User, UserFormData } from '@/types/users'

import { mapListUsers, mapUserToFormData } from './users'

const userResponse: User = {
  id: '12345',
  firstName: 'Max',
  lastName: 'Mustermann',
  title: 'MR' as TitleType,
  email: 'maxmustermann@test.de',
  phone: '+49 152 1111111',
  groups: ['3'],
  active: true,
}

describe('mapListUsers', () => {
  it('maps users to ListUser correctly', () => {
    expect(mapListUsers([userResponse])).toEqual([
      {
        id: '12345',
        fullName: 'Max Mustermann',
        email: 'maxmustermann@test.de',
        active: true,
      },
    ])
  })
})

describe('mapUserToFormData', () => {
  it('maps User to UserFormData correctly', () => {
    const expected: UserFormData = {
      id: '12345',
      title: 'MR',
      firstName: 'Max',
      lastName: 'Mustermann',
      email: 'maxmustermann@test.de',
      phone: '+49 152 1111111',
      active: true,
    }

    expect(mapUserToFormData(userResponse)).toEqual(expected)
  })

  it('returns default values when user is null', () => {
    const expected: UserFormData = {
      id: '',
      title: 'MR',
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      active: true,
    }

    expect(mapUserToFormData(null)).toEqual(expected)
  })
})
