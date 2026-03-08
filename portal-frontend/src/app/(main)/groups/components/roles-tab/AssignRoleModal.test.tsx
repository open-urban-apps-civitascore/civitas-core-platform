import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { JSX } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Role } from '@/types/roles'

const mockRoles: Role[] = [
  {
    id: 'r1',
    name: 'System Admin',
    description: 'Administrates the system',
    type: 'SYSTEM',
    tenant: 't1',
    permissions: [],
    users: [],
    createdAt: '',
    lastUpdated: null,
    updatedBy: null,
    groups: [],
    roleOrigin: 'default',
  },
  {
    id: 'r2',
    name: 'Data Manager',
    description: 'Manages data',
    type: 'DATA',
    tenant: 't1',
    permissions: [],
    users: [],
    createdAt: '',
    lastUpdated: null,
    updatedBy: null,
    groups: [],
    roleOrigin: 'custom',
  },
  {
    id: 'r3',
    name: 'Governance Lead',
    description: 'Governance role',
    type: 'GOVERNANCE',
    tenant: 't1',
    permissions: [],
    users: [],
    createdAt: '',
    lastUpdated: null,
    updatedBy: null,
    groups: [],
    roleOrigin: 'default',
  },
  {
    id: 'r-assigned',
    name: 'Already Assigned',
    description: 'Already assigned role',
    type: 'SYSTEM',
    tenant: 't1',
    permissions: [],
    users: [],
    createdAt: '',
    lastUpdated: null,
    updatedBy: null,
    groups: [],
    roleOrigin: 'default',
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
    const filtered = roleTypeFilter ? mockRoles.filter(r => roleTypeFilter.split(',').includes(r.type)) : mockRoles
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
  roleType: 'SYSTEM' as const,
  assignedRoleIds: ['r-assigned'],
  onAssignRoles: vi.fn(),
}

describe('AssignRoleModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderModal = async (overrides?: Partial<typeof defaultProps>) => {
    const { AssignRoleModal } = await import('./AssignRoleModal')
    return renderWithProvider(<AssignRoleModal {...defaultProps} {...overrides} />)
  }

  it('renders system role modal title and description', async () => {
    await renderModal({ roleType: 'SYSTEM' })

    expect(screen.getByText('roles.assignSystemRole')).toBeInTheDocument()
    expect(screen.getByText(/roles.assignSystemRoleDescription/)).toBeInTheDocument()
  })

  it('renders data role modal title and description', async () => {
    await renderModal({ roleType: 'DATA' })

    expect(screen.getByText('roles.assignDataRole')).toBeInTheDocument()
    expect(screen.getByText(/roles.assignDataRoleDescription/)).toBeInTheDocument()
  })

  it('shows platform-wide warning banner for data role modal', async () => {
    await renderModal({ roleType: 'DATA' })

    expect(screen.getByText('roles.dataRolePlatformWarning')).toBeInTheDocument()
  })

  it('does not show warning banner for system role modal', async () => {
    await renderModal({ roleType: 'SYSTEM' })

    expect(screen.queryByText('roles.dataRolePlatformWarning')).not.toBeInTheDocument()
  })

  it('shows only system roles when roleType is system', async () => {
    await renderModal({ roleType: 'SYSTEM' })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'System Admin' })).toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Data Manager' })).not.toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Governance Lead' })).not.toBeInTheDocument()
    })
  })

  it('shows data and governance roles when roleType is data', async () => {
    await renderModal({ roleType: 'DATA' })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'Data Manager' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: 'Governance Lead' })).toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'System Admin' })).not.toBeInTheDocument()
    })
  })

  it('excludes already assigned roles from the list', async () => {
    await renderModal({ roleType: 'SYSTEM' })

    await waitFor(() => {
      expect(screen.queryByRole('cell', { name: 'Already Assigned' })).not.toBeInTheDocument()
    })
  })

  it('confirm button is disabled when no roles are selected', async () => {
    await renderModal()

    const confirmButton = screen.getByText('actions.add') as HTMLButtonElement
    expect(confirmButton).toBeDisabled()
  })

  it('calls onAssignRoles with selected Role objects on confirm', async () => {
    await renderModal({ roleType: 'SYSTEM' })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'System Admin' })).toBeInTheDocument()
    })

    const checkbox = screen.getByRole('checkbox', { name: 'Select role System Admin' })
    fireEvent.click(checkbox)

    const confirmButton = screen.getByText('actions.add')
    fireEvent.click(confirmButton)

    expect(defaultProps.onAssignRoles).toHaveBeenCalledWith([
      expect.objectContaining({ id: 'r1', name: 'System Admin', type: 'SYSTEM' }),
    ])
  })

  it('calls onOpenChange(false) on cancel click', async () => {
    await renderModal()

    const cancelButton = screen.getByText('actions.cancel')
    fireEvent.click(cancelButton)

    expect(defaultProps.onOpenChange).toHaveBeenCalledWith(false)
  })
})
