import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { JSX } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { type Role, ROLE_TYPES, type RoleType } from '@/types/roles'

import { AssignRoleModal } from './AssignRoleModal'

const mockRoles: Role[] = [
  {
    id: 'r1',
    name: 'System Admin',
    description: 'Administrates the system',
    roleType: ROLE_TYPES.SYSTEM,
    permissions: [],
    readonly: true,
    modifiedBy: null,
    modifiedAt: null,
    createdAt: '',
    groupCount: 0,
    userCount: 0,
  },
  {
    id: 'r2',
    name: 'Data Manager',
    description: 'Manages data',
    roleType: ROLE_TYPES.DATA,
    permissions: [],
    readonly: false,
    modifiedBy: null,
    modifiedAt: null,
    createdAt: '',
    groupCount: 0,
    userCount: 0,
  },
  {
    id: 'r3',
    name: 'Data Lead',
    description: 'Data role',
    roleType: ROLE_TYPES.DATA,
    permissions: [],
    readonly: true,
    modifiedBy: null,
    modifiedAt: null,
    createdAt: '',
    groupCount: 0,
    userCount: 0,
  },
  {
    id: 'r-assigned',
    name: 'Already Assigned',
    description: 'Already assigned role',
    roleType: ROLE_TYPES.SYSTEM,
    permissions: [],
    readonly: true,
    modifiedBy: null,
    modifiedAt: null,
    createdAt: '',
    groupCount: 0,
    userCount: 0,
  },
]

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params?: Record<string, string>) => {
    if (params) return `${key} ${JSON.stringify(params)}`
    return key
  },
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams('page=1'),
  usePathname: vi.fn(),
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    getApiRequestParams: vi.fn(() => new URLSearchParams()),
  }),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: ({ params }: { params?: URLSearchParams } = {}) => {
    const roleTypeFilter = params?.get('roleType')
    const filtered = roleTypeFilter ? mockRoles.filter(r => roleTypeFilter.split(',').includes(r.roleType)) : mockRoles
    return {
      data: { data: filtered, totalElements: filtered.length },
      isFetching: false,
    }
  },
}))

const queryClient = new QueryClient()

const renderWithProvider = (children: JSX.Element) =>
  render(<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>)

const defaultProps = {
  open: true,
  onOpenChange: vi.fn(),
  groupId: 'g1',
  groupName: 'Test Group',
  roleType: ROLE_TYPES.SYSTEM as RoleType,
  assignedRoleIds: ['r-assigned'],
  onAssignRoles: vi.fn(),
}

describe('AssignRoleModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderModal = async (overrides?: Partial<typeof defaultProps>) => {
    return renderWithProvider(<AssignRoleModal {...defaultProps} {...overrides} />)
  }

  it('renders system role modal title and description', async () => {
    await renderModal({ roleType: ROLE_TYPES.SYSTEM })

    expect(await screen.findByText('roles.assignSystemRole')).toBeInTheDocument()
    expect(await screen.findByText(/roles.assignSystemRoleDescription/)).toBeInTheDocument()
  })

  it('renders data role modal title and description', async () => {
    await renderModal({ roleType: ROLE_TYPES.DATA })

    expect(await screen.findByText('roles.assignDataRole')).toBeInTheDocument()
    expect(await screen.findByText(/roles.assignDataRoleDescription/)).toBeInTheDocument()
  })

  it('shows platform-wide warning banner for data role modal', async () => {
    await renderModal({ roleType: ROLE_TYPES.DATA })

    expect(screen.getByText('roles.dataRolePlatformWarning')).toBeInTheDocument()
  })

  it('does not show warning banner for system role modal', async () => {
    await renderModal({ roleType: ROLE_TYPES.SYSTEM })

    expect(screen.queryByText('roles.dataRolePlatformWarning')).not.toBeInTheDocument()
  })

  it('shows only system roles when roleType is system', async () => {
    await renderModal({ roleType: ROLE_TYPES.SYSTEM })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'System Admin' })).toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Data Manager' })).not.toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Data Lead' })).not.toBeInTheDocument()
    })
  })

  it('shows data roles when roleType is data', async () => {
    await renderModal({ roleType: ROLE_TYPES.DATA })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'Data Manager' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: 'Data Lead' })).toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'System Admin' })).not.toBeInTheDocument()
    })
  })

  it('shows already assigned roles with disabled checkbox', async () => {
    await renderModal({ roleType: ROLE_TYPES.SYSTEM })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'Already Assigned' })).toBeInTheDocument()
      const checkbox = screen.getByRole('checkbox', { name: 'Select role Already Assigned' })
      expect(checkbox).toBeDisabled()
      expect(checkbox).toBeChecked()
    })
  })

  it('confirm button is disabled when no roles are selected', async () => {
    await renderModal()

    const confirmButton = screen.getByText('actions.add') as HTMLButtonElement
    expect(confirmButton).toBeDisabled()
  })

  it('calls onAssignRoles with selected Role objects on confirm', async () => {
    await renderModal({ roleType: ROLE_TYPES.SYSTEM })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'System Admin' })).toBeInTheDocument()
    })

    const checkbox = screen.getByRole('checkbox', { name: 'Select role System Admin' })
    fireEvent.click(checkbox)

    const confirmButton = screen.getByText('actions.add')
    fireEvent.click(confirmButton)

    expect(defaultProps.onAssignRoles).toHaveBeenCalledWith([
      expect.objectContaining({ id: 'r1', name: 'System Admin', roleType: ROLE_TYPES.SYSTEM }),
    ])
  })

  it('calls onOpenChange(false) on cancel click', async () => {
    await renderModal()

    const cancelButton = screen.getByText('actions.cancel')
    fireEvent.click(cancelButton)

    expect(defaultProps.onOpenChange).toHaveBeenCalledWith(false)
  })
})
