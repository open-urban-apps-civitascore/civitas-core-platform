// mapFormGroupToApiData.test.ts
import { describe, expect, it } from 'vitest'

import type { Group, GroupBaseFormData } from '@/types/groups'
import type { Contact } from '@/types/users'

import { mapFormGroupToApiData } from './mappers'

const formData: GroupBaseFormData = {
  id: 'g1',
  title: 'New Group',
  description: 'New description',
  contact: 'u1',
}

const groupData: Group = {
  id: 'g1',
  name: 'Group 01',
  description: 'Group 01 description',
  roles: ['admin'],
  users: [{ id: 'u1', assignedAt: '2024-01-01' }],
  contactUser: null,
  parent: 'g0',
  subgroups: [],
  dataspace: { id: 'ds1', title: 'dataspace1' },
}

describe('mapFormGroupToApiData', () => {
  it('maps form data and preserves group data fields', () => {
    const result = mapFormGroupToApiData(formData, groupData, null)

    expect(result).toMatchObject({
      id: 'g1',
      title: 'New Group',
      description: 'New description',
      parent: 'g0',
      roles: ['admin'],
      users: groupData.users,
      subgroups: [],
      contact: null,
      dataspace: { id: 'ds1', title: 'dataspace1' },
    })
  })

  it('sets contact group contact field to the provided contact value', () => {
    const contact: Contact = {
      id: 'u1',
      displayName: 'Max Mustermann',
    } as Contact

    const result = mapFormGroupToApiData(formData, groupData, contact)

    expect(result.contactUser).toEqual({
      id: 'u1',
      displayName: 'Max Mustermann',
    })

    const contact2: Contact | null = null

    const result2 = mapFormGroupToApiData(formData, groupData, contact2)

    expect(result2.contactUser).toBeNull()
  })

  it('sets contact when contact to null when no contact is provided', () => {
    const contact: Contact = {
      id: 'u1',
      displayName: 'Max Mustermann',
    } as Contact

    const result = mapFormGroupToApiData(formData, groupData, contact)

    expect(result.contactUser).toEqual({
      id: 'u1',
      displayName: 'Max Mustermann',
    })
  })
})
