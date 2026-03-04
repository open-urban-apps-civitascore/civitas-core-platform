import { describe, expect, it } from 'vitest'

import type { Group } from '@/types/groups'

import { mapGroupApiToFormData, mapGroupDetailsData, mapGroupFormToApiata, mapGroupsApiToListData } from './groups'

const baseGroup: Group = {
  id: '1',
  name: 'Test Group',
  description: 'Description',
  roles: [
    {
      id: 'r1',
      name: 'Admin',
      roleType: 'system',
    },
  ],
  members: [
    { id: 'm1', name: 'User 1' },
    { id: 'm2', name: 'User 2' },
  ],
  contactUser: { id: 'u1', name: 'Max' },
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-02',
}

describe('mapGroupDetailsData', () => {
  it('returns null if input is null', () => {
    expect(mapGroupDetailsData(null)).toBeNull()
  })

  it('keeps existing contactUser and roles', () => {
    const result = mapGroupDetailsData(baseGroup)

    expect(result).toEqual(baseGroup)
  })

  it('adds fallback contactUser and roles if null', () => {
    const group: Group = {
      ...baseGroup,
      contactUser: null,
      roles: null,
    }

    const result = mapGroupDetailsData(group)

    expect(result?.contactUser).toEqual({ id: '', name: '' })
    expect(result?.roles).toEqual([])
  })
})

describe('mapGroupApiToFormData', () => {
  it('maps correctly with full data', () => {
    const result = mapGroupApiToFormData(baseGroup)

    expect(result).toEqual({
      id: '1',
      name: 'Test Group',
      description: 'Description',
      contactUserId: 'u1',
      members: ['m1', 'm2'],
    })
  })

  it('uses fallback values for nullable fields', () => {
    const group: Group = {
      ...baseGroup,
      members: null,
      description: '',
      contactUser: null,
    }

    const result = mapGroupApiToFormData(group)

    expect(result).toEqual({
      id: '1',
      name: 'Test Group',
      description: '',
      contactUserId: '',
      members: [],
    })
  })
})

describe('mapGroupsApiToListData', () => {
  it('maps groups to list data correctly', () => {
    const result = mapGroupsApiToListData([baseGroup])

    expect(result).toEqual([
      {
        id: '1',
        name: 'Test Group',
        description: 'Description',
        membersCount: 2,
        contactUser: { id: 'u1', name: 'Max' },
      },
    ])
  })

  it('handles null members', () => {
    const group: Group = {
      ...baseGroup,
      members: null,
    }

    const result = mapGroupsApiToListData([group])

    expect(result[0].membersCount).toBe(0)
  })

  it('returns empty array for empty input', () => {
    expect(mapGroupsApiToListData([])).toEqual([])
  })
})

describe('mapGroupFormToApiata', () => {
  it('maps form data to API data correctly', () => {
    const formData = {
      id: baseGroup.id,
      name: baseGroup.name,
      description: baseGroup.description,
      contactUserId: 'm1',
      members: ['m1', 'm2'],
    }

    const result = mapGroupFormToApiata(formData)

    expect(result).toEqual({
      id: '1',
      name: 'Test Group',
      description: 'Description',
      contactUserId: 'm1',
      memberIds: ['m1', 'm2'],
    })
  })

  it('maps empty members array correctly', () => {
    const formData = {
      id: baseGroup.id,
      name: baseGroup.name,
      description: baseGroup.description,
      contactUserId: 'm1',
      members: [],
    }

    const result = mapGroupFormToApiata(formData)

    expect(result.memberIds).toEqual([])
  })
})

//TODO: reimplement this test when flattengroups for subgroups is reimplemented
// describe('flattenGroups', () => {
//   it('should flatten groups and subgroups into a single array', () => {
//     const expected = [
//       {
//         id: '1',
//         title: 'Group 1',
//         description: 'description for Group 1',
//         roles: [],
//         users: [
//           { id: 'user1', assignedAt: '' },
//           { id: 'user2', assignedAt: '' },
//         ],
//         contact: { id: 'c1', displayName: 'Contact 1' },
//         parent: null,
//         dataspace: null,
//         subgroups: [],
//       },
//       {
//         id: '2',
//         title: 'SubGroup 1-1',
//         description: 'description for SubGroup 1-1',
//         roles: [],
//         users: [],
//         contact: null,
//         parent: '1',
//         dataspace: null,
//         subgroups: [],
//       },
//       {
//         id: '3',
//         title: 'SubGroup 1-1-1',
//         description: 'description for SubGroup 1-1-1',
//         roles: [],
//         users: [],
//         contact: null,
//         parent: '2',
//         dataspace: null,
//         subgroups: [],
//       },
//       {
//         id: '4',
//         title: 'SubGroup 1-1-2',
//         description: 'description for SubGroup 1-1-2',
//         roles: [],
//         users: [],
//         contact: null,
//         parent: '2',
//         dataspace: null,
//         subgroups: [],
//       },
//       {
//         id: '5',
//         title: 'SubGroup 1-2',
//         description: 'description for SubGroup 1-2',
//         roles: [],
//         users: [],
//         contact: null,
//         parent: '1',
//         dataspace: null,
//         subgroups: [],
//       },
//       {
//         id: '6',
//         title: 'Group 2',
//         description: 'description for Group 2',
//         roles: [],
//         users: [],
//         contact: { id: 'c2', displayName: 'Contact 2' },
//         parent: null,
//         dataspace: null,
//         subgroups: [],
//       },
//     ]

//     const result = flattenGroups(groups)

//     expect(result).toEqual(expected)
//   })
// })
