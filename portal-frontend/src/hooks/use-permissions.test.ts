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

describe('hasAnyPermission', () => {
  it('returns true when user has at least one of the listed permissions', () => {
    mockCurrentUser([PERMISSION_NAMES.USER_READ])
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasAnyPermission(PERMISSION_NAMES.USER_CREATE, PERMISSION_NAMES.USER_READ)).toBe(true)
  })

  it('returns false when user has none of the listed permissions', () => {
    mockCurrentUser([PERMISSION_NAMES.USER_READ])
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasAnyPermission(PERMISSION_NAMES.USER_CREATE, PERMISSION_NAMES.DATASET_CREATE)).toBe(false)
  })
})

describe('hasScopedPermission', () => {
  it('returns true when user has permission via TENANT assignment', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASET_READ])
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasScopedPermission(PERMISSION_NAMES.DATASET_READ, 'DATASET', 'abc-123')).toBe(true)
  })

  it('returns true when user has permission for the exact scope (scopeType + scopeId match)', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [{ scopeType: 'DATASET', scopeId: 'abc-123', permissions: [PERMISSION_NAMES.DATASET_READ] }],
      },
    } as ReturnType<typeof useGetCurrentUser>)
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasScopedPermission(PERMISSION_NAMES.DATASET_READ, 'DATASET', 'abc-123')).toBe(true)
  })

  it('returns false when user has permission for a different scopeId', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [{ scopeType: 'DATASET', scopeId: 'xyz-789', permissions: [PERMISSION_NAMES.DATASET_READ] }],
      },
    } as ReturnType<typeof useGetCurrentUser>)
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasScopedPermission(PERMISSION_NAMES.DATASET_READ, 'DATASET', 'abc-123')).toBe(false)
  })

  it('returns false when user lacks the permission entirely', () => {
    mockCurrentUser([PERMISSION_NAMES.USER_READ])
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasScopedPermission(PERMISSION_NAMES.DATASET_READ, 'DATASET', 'abc-123')).toBe(false)
  })

  it('returns false when currentUser is undefined', () => {
    vi.mocked(useGetCurrentUser).mockReturnValue({ data: undefined } as ReturnType<typeof useGetCurrentUser>)
    const { result } = renderHook(() => usePermissions())
    expect(result.current.hasScopedPermission(PERMISSION_NAMES.DATASET_READ, 'DATASET', 'abc-123')).toBe(false)
  })
})
