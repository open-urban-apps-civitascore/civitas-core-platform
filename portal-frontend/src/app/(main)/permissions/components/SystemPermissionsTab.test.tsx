import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { Permission } from '@/types/permissions'

import { SystemPermissionsTab } from './SystemPermissionsTab'

vi.mock('next-intl', () => ({
  useTranslations: () => {
    const t = (key: string, params?: Record<string, string>) => {
      if (params) return Object.entries(params).reduce((acc, [k, v]) => acc.replace(`{${k}}`, v), key)
      return key
    }
    t.has = () => true
    return t
  },
}))

const mockSetSearchParam = vi.fn()
vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    search: '',
    setSearchParam: mockSetSearchParam,
  }),
}))

const createPermission = (name: string, category: string): Permission => ({
  id: name.toLowerCase(),
  name,
  category,
  permissionType: 'SYSTEM',
})

const mockPermissions: Permission[] = [
  createPermission('USER_READ', 'TENANT_ADMINISTRATION'),
  createPermission('USER_CREATE', 'TENANT_ADMINISTRATION'),
  createPermission('DATASET_READ', 'DATA'),
  createPermission('DATASET_DELETE', 'DATA'),
  createPermission('AUDIT_READ', 'AUDIT'),
]

describe('SystemPermissionsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders a PermissionsList for each category', () => {
    render(<SystemPermissionsTab permissions={mockPermissions} isLoading={false} />)

    expect(screen.getByText('permissions.systemPermissions.categories.TENANT_ADMINISTRATION')).toBeInTheDocument()
    expect(screen.getByText('permissions.systemPermissions.categories.DATA')).toBeInTheDocument()
    expect(screen.getByText('permissions.systemPermissions.categories.OTHER')).toBeInTheDocument()
  })

  test('groups permissions into correct categories', () => {
    render(<SystemPermissionsTab permissions={mockPermissions} isLoading={false} />)

    const tables = screen.getAllByRole('table')
    expect(tables).toHaveLength(3)

    const tenantRows = tables[0].querySelectorAll('tbody tr')
    expect(tenantRows).toHaveLength(2)

    const dataRows = tables[1].querySelectorAll('tbody tr')
    expect(dataRows).toHaveLength(2)

    const otherRows = tables[2].querySelectorAll('tbody tr')
    expect(otherRows).toHaveLength(1)
  })

  test('renders loading skeletons when isLoading is true', () => {
    render(<SystemPermissionsTab permissions={[]} isLoading={true} />)

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  test('renders empty lists when no permissions are provided', () => {
    render(<SystemPermissionsTab permissions={[]} isLoading={false} />)

    const tables = screen.getAllByRole('table')
    expect(tables).toHaveLength(3)

    const noResultsCells = screen.getAllByText('common.noResults')
    expect(noResultsCells).toHaveLength(3)
  })

  test('renders search header', () => {
    render(<SystemPermissionsTab permissions={mockPermissions} isLoading={false} />)

    expect(screen.getByTestId('searchArea')).toBeInTheDocument()
  })
})
