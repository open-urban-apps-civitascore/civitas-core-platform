import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { RoleWithPermissions } from '@/types/permissions'

import { PermissionsTable } from './PermissionsTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const mockPermissions: RoleWithPermissions[] = [
  { name: 'DATASET', read: true, create: false, update: true, delete: false, release: false },
  { name: 'USER', read: true, create: true, update: false, delete: true, release: false },
]

const defaultProps = {
  permissions: mockPermissions,
  rowCount: 2,
  pageIndex: 0,
  pageSize: 10,
  totalPages: 1,
  sorting: [],
  onPaginationChange: vi.fn(),
  onSortingChange: vi.fn(),
  isLoading: false,
}

describe('PermissionsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders the name column with permission names', () => {
    render(<PermissionsTable {...defaultProps} />)

    expect(screen.getByText('DATASET')).toBeInTheDocument()
    expect(screen.getByText('USER')).toBeInTheDocument()
  })

  test('renders only the name column when shouldShowPermissionColumns is false', () => {
    render(<PermissionsTable {...defaultProps} shouldShowPermissionColumns={false} />)

    expect(screen.getByText('tableHeaders.name')).toBeInTheDocument()
    expect(screen.queryByText('actions.read')).not.toBeInTheDocument()
    expect(screen.queryByText('actions.create')).not.toBeInTheDocument()
  })

  test('renders permission action columns when shouldShowPermissionColumns is true', () => {
    render(<PermissionsTable {...defaultProps} shouldShowPermissionColumns={true} />)

    expect(screen.getByText('actions.read')).toBeInTheDocument()
    expect(screen.getByText('actions.create')).toBeInTheDocument()
    expect(screen.getByText('actions.update')).toBeInTheDocument()
    expect(screen.getByText('actions.delete')).toBeInTheDocument()
    expect(screen.getByText('actions.release')).toBeInTheDocument()
  })

  test('renders check icons for granted permissions with accessible labels', () => {
    render(<PermissionsTable {...defaultProps} shouldShowPermissionColumns={true} />)

    const rows = screen.getAllByRole('row')
    // row 0 = header, row 1 = DATASET, row 2 = USER
    const datasetCells = rows[1].querySelectorAll('td')
    const userCells = rows[2].querySelectorAll('td')

    // DATASET: read=true, create=false, update=true, delete=false, release=false
    expect(datasetCells[1].querySelector('[aria-label="actions.read: accessible.granted"]')).toBeInTheDocument()
    expect(datasetCells[2].querySelector('[aria-label="actions.create: accessible.notGranted"]')).toBeInTheDocument()
    expect(datasetCells[3].querySelector('[aria-label="actions.update: accessible.granted"]')).toBeInTheDocument()
    expect(datasetCells[4].querySelector('[aria-label="actions.delete: accessible.notGranted"]')).toBeInTheDocument()
    expect(datasetCells[5].querySelector('[aria-label="actions.release: accessible.notGranted"]')).toBeInTheDocument()

    // USER: read=true, create=true, update=false, delete=true, release=false
    expect(userCells[1].querySelector('[aria-label="actions.read: accessible.granted"]')).toBeInTheDocument()
    expect(userCells[2].querySelector('[aria-label="actions.create: accessible.granted"]')).toBeInTheDocument()
    expect(userCells[3].querySelector('[aria-label="actions.update: accessible.notGranted"]')).toBeInTheDocument()
    expect(userCells[4].querySelector('[aria-label="actions.delete: accessible.granted"]')).toBeInTheDocument()
    expect(userCells[5].querySelector('[aria-label="actions.release: accessible.notGranted"]')).toBeInTheDocument()
  })

  test('renders check icon only for granted permissions', () => {
    render(<PermissionsTable {...defaultProps} shouldShowPermissionColumns={true} />)

    const rows = screen.getAllByRole('row')
    const datasetCells = rows[1].querySelectorAll('td')

    // granted: has an SVG (Check icon)
    expect(datasetCells[1].querySelector('svg')).toBeInTheDocument()
    // not granted: no SVG
    expect(datasetCells[2].querySelector('svg')).not.toBeInTheDocument()
  })

  test('renders empty state when no permissions are provided', () => {
    render(<PermissionsTable {...defaultProps} permissions={[]} rowCount={0} />)

    expect(screen.queryByText('DATASET')).not.toBeInTheDocument()
  })

  test('does not show pagination', () => {
    render(<PermissionsTable {...defaultProps} />)

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })
})
