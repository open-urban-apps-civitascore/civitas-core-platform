// mapRolesData.test.ts
import { describe, expect, it } from 'vitest'

import type { Group } from '@/types/groups'
import { type BaseRole, ROLE_TYPES } from '@/types/roles'

import { mapRolesData } from './mappers'

const roles: BaseRole[] = [
  { id: 'r1', name: 'Admin', type: ROLE_TYPES.SYSTEM },
  { id: 'r2', name: 'Editor', type: ROLE_TYPES.DATA },
  { id: 'r3', name: 'Support', type: ROLE_TYPES.GOVERNANCE },
]

const groups: Group[] = [
  {
    id: 'g1',
    name: 'Group 01',
    description: '',
    roles: ['r1', 'r2'],
    users: [],
    contactUser: null,
    parent: null,
    subgroups: [],
    dataspace: null,
  },
  {
    id: 'g2',
    name: 'Group 02',
    description: '',
    roles: ['r1', 'r3'],
    users: [],
    contactUser: null,
    parent: null,
    subgroups: [],
    dataspace: { id: 'ds1', title: 'Dataspace 01' },
  },
]

describe('mapRolesData', () => {
  it('maps roles across all groups', () => {
    const result = mapRolesData(roles, groups)

    expect(result).toEqual([
      {
        id: 'g1-r1',
        name: 'Admin',
        inherited: false,
        group: 'Group 01',
        dataspace: null,
        type: ROLE_TYPES.SYSTEM,
        roleId: 'r1',
      },
      {
        id: 'g1-r2',
        name: 'Editor',
        inherited: false,
        group: 'Group 01',
        dataspace: null,
        type: ROLE_TYPES.DATA,
        roleId: 'r2',
      },
      {
        id: 'g2-r1',
        name: 'Admin',
        inherited: false,
        group: 'Group 02',
        dataspace: { id: 'ds1', title: 'Dataspace 01' },
        type: ROLE_TYPES.SYSTEM,
        roleId: 'r1',
      },
      {
        id: 'g2-r3',
        name: 'Support',
        inherited: false,
        group: 'Group 02',
        dataspace: { id: 'ds1', title: 'Dataspace 01' },
        type: ROLE_TYPES.GOVERNANCE,
        roleId: 'r3',
      },
    ])
  })

  it('filters out roles not present in role map', () => {
    const result = mapRolesData(roles, [
      {
        ...groups[0],
        roles: ['r1', 'unknown'],
      },
    ])

    expect(result).toEqual([
      {
        id: 'g1-r1',
        name: 'Admin',
        inherited: false,
        group: 'Group 01',
        dataspace: null,
        type: ROLE_TYPES.SYSTEM,
        roleId: 'r1',
      },
    ])
  })

  it('returns empty array when group list is empty', () => {
    const result = mapRolesData(roles, [])
    expect(result).toEqual([])
  })
})
