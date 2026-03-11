// mapRolesData.test.ts
import { describe, expect, it } from 'vitest'

import { Assignment, ASSIGNMENT_SCOPE_TYPES, AssignmentRole } from '@/types/assignments'
import type { Group } from '@/types/groups'
import { ROLE_TYPES } from '@/types/roles'

import { mapRolesData } from './mappers'

const roles = [
  { id: 'r1', name: 'Admin', roleType: ROLE_TYPES.SYSTEM },
  { id: 'r2', name: 'Editor', roleType: ROLE_TYPES.DATA },
]

const groupRoles: AssignmentRole[] = [
  { id: 'r1', name: 'Admin', roleType: ROLE_TYPES.SYSTEM, description: '', readonly: false },
  { id: 'r2', name: 'Editor', roleType: ROLE_TYPES.DATA, description: '', readonly: false },
]

const baseAssignment: Assignment = {
  id: 'a1',
  createdAt: new Date().toISOString(),
  modifiedAt: new Date().toISOString(),
  group: { id: 'g1', name: 'Group 01' },
  role: groupRoles[0],
  scopeType: ASSIGNMENT_SCOPE_TYPES.DATASTRUCTURE,
  scope: { id: 's1', name: 'scope 01' },
}

const groups: Group[] = [
  {
    id: 'g1',
    name: 'Group 01',
    description: '',
    roles: null,
    assignments: [baseAssignment, { ...baseAssignment, role: groupRoles[1] }],
    members: null,
    contactUser: null,
    createdAt: '',
    modifiedAt: '',
  },
  {
    id: 'g2',
    name: 'Group 02',
    description: '',
    roles: null,
    assignments: [baseAssignment, { ...baseAssignment, role: groupRoles[2] }],
    members: [],
    contactUser: null,
    createdAt: '',
    modifiedAt: '',
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
        type: ROLE_TYPES.SYSTEM,
        roleId: 'r1',
      },
      {
        id: 'g1-r2',
        name: 'Editor',
        inherited: false,
        group: 'Group 01',
        type: ROLE_TYPES.DATA,
        roleId: 'r2',
      },
      {
        id: 'g2-r1',
        name: 'Admin',
        inherited: false,
        group: 'Group 02',
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
