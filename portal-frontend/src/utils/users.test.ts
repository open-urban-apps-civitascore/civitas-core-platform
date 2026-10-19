import { TitleType, User, UserFormData } from '@/types/users'

import { mapListUsers, mapUserToFormData } from './users'

const userResponse: User = {
  id: '12345',
  firstName: 'Max',
  lastName: 'Mustermann',
  title: 'MR' as TitleType,
  email: 'maxmustermann@test.de',
  phone: '+49 152 1111111',
  groups: [{ id: 'g1', name: 'Group 1' }],
}

describe('mapListUsers', () => {
  it('maps users to ListUser correctly', () => {
    expect(mapListUsers([userResponse])).toEqual([
      {
        id: '12345',
        fullName: 'Max Mustermann',
        email: 'maxmustermann@test.de',
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
      groupIds: ['g1'],
    }

    expect(mapUserToFormData(userResponse)).toEqual(expected)
  })
})
