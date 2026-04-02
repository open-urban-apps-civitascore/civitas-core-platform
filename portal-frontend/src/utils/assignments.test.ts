import { describe, expect, it } from 'vitest'

import { Assignment } from '@/types/assignments'

import { mapAssignmentApiResponseToTable } from './assignments'

describe('assignments utils', () => {
  it('maps assignments with group description from API response', () => {
    const assignments: Assignment[] = [
      {
        id: 'a1',
        createdAt: '2024-01-01',
        modifiedAt: '2024-01-01',
        group: { id: 'g1', name: 'Group 1', description: 'A test group' },
        role: { id: 'r1', name: 'Role 1', roleType: 'DATA', description: 'desc', readonly: false },
        scopeType: null,
        scope: null,
      },
    ]

    const result = mapAssignmentApiResponseToTable(assignments)

    expect(result).toEqual([
      {
        groupId: 'g1',
        groupName: 'Group 1',
        groupDescription: 'A test group',
        assignedRoles: [{ roleId: 'r1', roleName: 'Role 1' }],
      },
    ])
  })

  it('handles missing group description', () => {
    const assignments: Assignment[] = [
      {
        id: 'a1',
        createdAt: '2024-01-01',
        modifiedAt: '2024-01-01',
        group: { id: 'g1', name: 'Group 1' },
        role: { id: 'r1', name: 'Role 1', roleType: 'DATA', description: 'desc', readonly: false },
        scopeType: null,
        scope: null,
      },
    ]

    const result = mapAssignmentApiResponseToTable(assignments)

    expect(result).toEqual([
      {
        groupId: 'g1',
        groupName: 'Group 1',
        groupDescription: undefined,
        assignedRoles: [{ roleId: 'r1', roleName: 'Role 1' }],
      },
    ])
  })

  it('groups multiple roles under the same group', () => {
    const assignments: Assignment[] = [
      {
        id: 'a1',
        createdAt: '2024-01-01',
        modifiedAt: '2024-01-01',
        group: { id: 'g1', name: 'Group 1', description: 'desc' },
        role: { id: 'r1', name: 'Role 1', roleType: 'DATA', description: 'desc', readonly: false },
        scopeType: null,
        scope: null,
      },
      {
        id: 'a2',
        createdAt: '2024-01-01',
        modifiedAt: '2024-01-01',
        group: { id: 'g1', name: 'Group 1', description: 'desc' },
        role: { id: 'r2', name: 'Role 2', roleType: 'DATA', description: 'desc', readonly: false },
        scopeType: null,
        scope: null,
      },
    ]

    const result = mapAssignmentApiResponseToTable(assignments)

    expect(result).toHaveLength(1)
    expect(result[0].assignedRoles).toEqual([
      { roleId: 'r1', roleName: 'Role 1' },
      { roleId: 'r2', roleName: 'Role 2' },
    ])
  })
})
