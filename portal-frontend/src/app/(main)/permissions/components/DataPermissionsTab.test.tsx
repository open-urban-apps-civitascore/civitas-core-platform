import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { Permission } from '@/types/permissions'

import { DataPermissionsTab } from './DataPermissionsTab'

vi.mock('next-intl', () => ({
  useTranslations: () => {
    const translations: Record<string, string> = {
      'values.DATASET': 'Dataset',
      'values.USER': 'User',
    }
    const t = (key: string, params?: Record<string, string>) => {
      if (params?.defaultValue && !(key in translations)) return params.defaultValue
      return translations[key] ?? key
    }
    t.has = (key: string) => key in translations
    return t
  },
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    pageIndex: 0,
    pageSize: 10,
    sorting: [],
    totalPages: 1,
    setSortingParams: vi.fn(),
    setPaginationParams: vi.fn(),
  }),
}))

const createPermission = (name: string): Permission => ({
  id: name.toLowerCase(),
  name,
  category: 'DATA',
  permissionType: 'DATA',
})

describe('DataPermissionsTab', () => {
  test('groups permissions by entity and shows permission columns', () => {
    const permissions = [
      createPermission('DATASET_READ'),
      createPermission('DATASET_CREATE'),
      createPermission('DATASET_DELETE'),
      createPermission('USER_READ'),
      createPermission('USER_UPDATE'),
    ]

    render(<DataPermissionsTab permissions={permissions} isLoading={false} rowCount={2} />)

    expect(screen.getByText('Dataset')).toBeInTheDocument()
    expect(screen.getByText('User')).toBeInTheDocument()

    expect(screen.getByText('actions.read')).toBeInTheDocument()
    expect(screen.getByText('actions.create')).toBeInTheDocument()
  })

  test('uses entity name as fallback when translation is missing', () => {
    const permissions = [createPermission('UNKNOWN_ENTITY_READ')]

    render(<DataPermissionsTab permissions={permissions} isLoading={false} rowCount={1} />)

    expect(screen.getByText('UNKNOWN_ENTITY')).toBeInTheDocument()
  })

  test('correctly maps actions to boolean columns', () => {
    const permissions = [createPermission('DATASET_READ'), createPermission('DATASET_DELETE')]

    render(<DataPermissionsTab permissions={permissions} isLoading={false} rowCount={1} />)

    const row = screen.getAllByRole('row')[1]
    const cells = row.querySelectorAll('td')

    // read=true, create=false, update=false, delete=true, release=false
    expect(cells[1].querySelector('[aria-label*="actions.read: accessible.granted"]')).toBeInTheDocument()
    expect(cells[2].querySelector('[aria-label*="accessible.notGranted"]')).toBeInTheDocument()
    expect(cells[3].querySelector('[aria-label*="accessible.notGranted"]')).toBeInTheDocument()
    expect(cells[4].querySelector('[aria-label*="actions.delete: accessible.granted"]')).toBeInTheDocument()
    expect(cells[5].querySelector('[aria-label*="accessible.notGranted"]')).toBeInTheDocument()
  })

  test('renders empty table when no permissions are provided', () => {
    render(<DataPermissionsTab permissions={[]} isLoading={false} rowCount={0} />)

    const rows = screen.getAllByRole('row')
    // header + no data rows
    expect(rows).toHaveLength(2)
  })
})
