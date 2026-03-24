import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { type Assignment } from '@/types/assignments'
import { PERMISSION_NAMES } from '@/types/currentUser'

import { RolesTab } from './RolesTab'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockCurrentUserWithPermissions = () => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test',
      email: 'test@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType: 'TENANT', scopeId: null, permissions: [PERMISSION_NAMES.ROLE_READ] }],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

const mockAssignments: Assignment[] = [
  {
    id: 'a1',
    createdAt: '2024-01-01T00:00:00Z',
    modifiedAt: '2024-01-01T00:00:00Z',
    group: { id: 'g1', name: 'Group 1' },
    role: { id: 'r1', name: 'Admin Role', roleType: 'SYSTEM', description: 'Admin desc', readonly: true },
    scopeType: 'TENANT',
    scope: null,
  },
]

const mockUseGetAssignments = vi.fn()

vi.mock('@/app/services/api/assignments/clientRequests', async () => {
  const actual = await vi.importActual('@/app/services/api/assignments/clientRequests')
  return {
    ...actual,
    useGetAssignments: (input: unknown) => mockUseGetAssignments(input),
  }
})

const renderRolesTab = (props = {}) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <RolesTab userId="user-1" {...props} />
    </NextIntlClientProvider>,
  )

describe('RolesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUserWithPermissions()
    mockUseGetAssignments.mockReturnValue({
      data: { data: mockAssignments, totalElements: 1 },
      isFetching: false,
      error: null,
    })
  })

  it('renders the segmented control with all tabs', () => {
    renderRolesTab()
    expect(screen.getByRole('tablist')).toBeDefined()
    ;['Plattformweit', 'Datensätze', 'Datenquellen', 'Datenstrukturen'].forEach(label => {
      expect(screen.getByRole('tab', { name: label })).toBeDefined()
    })
  })

  it('renders the search field', () => {
    renderRolesTab()
    expect(screen.getByTestId('searchArea')).toBeDefined()
  })

  it('renders the assignments table', () => {
    renderRolesTab()
    expect(screen.getByRole('table')).toBeDefined()
    expect(screen.getByText('Admin Role')).toBeDefined()
  })

  it('passes correct request params for platformWide segment', () => {
    renderRolesTab()
    expect(mockUseGetAssignments).toHaveBeenCalled()
    const lastCall = mockUseGetAssignments.mock.calls.at(-1)!
    const params: URLSearchParams = lastCall[0].params
    expect(params.get('userId')).toBe('user-1')
    expect(params.get('scopeType')).toBe('TENANT')
    expect(params.get('roleType')).toBe('SYSTEM')
  })

  it('updates request params when switching segments', () => {
    renderRolesTab()
    fireEvent.click(screen.getByRole('tab', { name: 'Datensätze' }))

    const lastCall = mockUseGetAssignments.mock.calls.at(-1)!
    const params: URLSearchParams = lastCall[0].params
    expect(params.get('scopeType')).toBe('DATASET')
    expect(params.has('roleType')).toBe(false)
  })

  it('sends search query as q param', async () => {
    renderRolesTab()
    const searchInput = screen.getByRole('searchbox')
    fireEvent.change(searchInput, { target: { value: 'admin' } })

    await waitFor(() => {
      const lastCall = mockUseGetAssignments.mock.calls.at(-1)!
      const params: URLSearchParams = lastCall[0].params
      expect(params.get('q')).toBe('admin')
    })
  })

  it('resets page index when switching segments', () => {
    renderRolesTab()
    fireEvent.click(screen.getByRole('tab', { name: 'Datenquellen' }))

    const lastCall = mockUseGetAssignments.mock.calls.at(-1)!
    const params: URLSearchParams = lastCall[0].params
    expect(params.get('page')).toBe('0')
  })

  it('shows error message when API returns error', () => {
    mockUseGetAssignments.mockReturnValue({
      data: null,
      isFetching: false,
      error: new Error('Network error'),
    })
    renderRolesTab()
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.getByText('Fehler beim Laden der Daten')).toBeDefined()
  })

  it('renders empty table when no assignments', () => {
    mockUseGetAssignments.mockReturnValue({
      data: { data: [], totalElements: 0 },
      isFetching: false,
      error: null,
    })
    renderRolesTab()
    expect(screen.getByRole('table')).toBeDefined()
  })
})
