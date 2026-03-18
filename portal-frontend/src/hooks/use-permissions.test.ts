import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { usePermissions } from './use-permissions'

vi.mock('@/app/services/api/users/clientRequests')

const mockCurrentUser = (permissions: PermissionName[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test',
      email: 'test@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType: 'TENANT', scopeId: null, permissions }],
    },
  } as ReturnType<typeof useGetCurrentUser>)
}

describe('usePermissions', () => {
  it('returns true for a permission the user has', () => {
    mockCurrentUser([PERMISSION_NAMES.USER_CREATE])
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasPermission(PERMISSION_NAMES.USER_CREATE)).toBe(true)
  })

  it('returns false for a permission the user lacks', () => {
    mockCurrentUser([PERMISSION_NAMES.USER_READ])
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasPermission(PERMISSION_NAMES.USER_CREATE)).toBe(false)
  })

  it('returns false when currentUser is undefined', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({ data: undefined } as ReturnType<typeof useGetCurrentUser>)
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasPermission(PERMISSION_NAMES.USER_CREATE)).toBe(false)
  })

  // Intentionally scope-blind: permissions are aggregated globally across all assignments.
  // A scoped permission (e.g. DATASET_CREATE on dataset "abc") is treated as globally present.
  // Scope-aware checks will be added when resource-level gating is implemented.
  it('aggregates permissions globally across all assignments regardless of scope', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [
          { scopeType: 'TENANT', scopeId: null, permissions: [PERMISSION_NAMES.USER_READ] },
          { scopeType: 'DATASET', scopeId: 'abc', permissions: [PERMISSION_NAMES.DATASET_CREATE] },
        ],
      },
    } as ReturnType<typeof useGetCurrentUser>)
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasPermission(PERMISSION_NAMES.USER_READ)).toBe(true)
    expect(result.current.hasPermission(PERMISSION_NAMES.DATASET_CREATE)).toBe(true)
  })
})
