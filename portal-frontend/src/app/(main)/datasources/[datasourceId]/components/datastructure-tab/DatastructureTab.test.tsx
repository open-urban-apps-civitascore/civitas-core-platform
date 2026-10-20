import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { ComponentProps } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { AssignmentScope } from '@/types/assignments'
import { MeAssignment, PERMISSION_NAMES } from '@/types/currentUser'
import { DatastructureVersion } from '@/types/datastructures'

import { DatastructureTab } from './DatastructureTab'

const { mockUseGetDatastructureVersion } = vi.hoisted(() => ({
  mockUseGetDatastructureVersion: vi.fn(),
}))

vi.mock('next-intl', () => ({
  useTranslations: () =>
    Object.assign((key: string, params?: Record<string, unknown>) => (params ? key + JSON.stringify(params) : key), {
      rich: (key: string) => key,
    }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    getApiRequestParams: vi.fn().mockReturnValue(new URLSearchParams()),
  }),
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useGetDatastructures: () => ({ data: undefined, isFetching: false }),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: mockUseGetDatastructureVersion,
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockAssignments = (assignments: Partial<MeAssignment>[]) =>
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: { assignments },
  } as unknown as ReturnType<typeof useGetCurrentUser>)

const readAssignment = (scopeType: AssignmentScope, scopeId: string | null) => ({
  scopeType,
  scopeId,
  permissions: [PERMISSION_NAMES.DATASTRUCTURE_READ],
})

vi.mock('@/app/(main)/datastructures/[datastructureId]/(versions)/hooks/useDatastructureVersion', () => ({
  defaultDatastructureVersionFormData: {},
  useDatastructureVersion: () => ({
    modelSessionManager: null,
    hasUserChanges: false,
    resetToInitialState: vi.fn(),
    resetFormAndSession: vi.fn(),
  }),
}))

const defaultProps: ComponentProps<typeof DatastructureTab> = {
  datasourceTitle: 'Test Datasource',
  selectedVersionId: null as string | null,
  isReadOnly: false,
  isDatasourceInUseByReleased: false,
  isDatasourceReleased: false,
  onSelectDatastructureVersion: vi.fn(),
}

const mockVersion: DatastructureVersion = {
  id: 'version-1',
  version: '2',
  description: null,
  dataStructureVersionStatus: 'AVAILABLE',
  dataStructureVersionSource: 'OWN',
  modelName: null,
  model: null,
  styles: null,
  dataStructure: { id: 'datastructure-1', name: 'Test Datastructure' },
  createdAt: '2026-01-01T00:00:00Z',
  modifiedAt: '2026-01-01T00:00:00Z',
}

const renderComponent = (props = defaultProps) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <DatastructureTab {...props} />
    </QueryClientProvider>,
  )
}

describe('DatastructureTab', () => {
  beforeEach(() => {
    mockUseGetDatastructureVersion.mockReset().mockReturnValue({ data: undefined })
    mockAssignments([readAssignment('TENANT', null)])
  })

  it('renders UmlModeler', () => {
    renderComponent()
    expect(screen.getByTestId('umlModeler')).toBeInTheDocument()
  })

  it('shows placeholder when no datastructure version is selected', () => {
    renderComponent()
    expect(screen.getByText('noDatastructures')).toBeInTheDocument()
    expect(screen.getByText('creationSteps.step1')).toBeInTheDocument()
    expect(screen.getByText('creationSteps.step2')).toBeInTheDocument()
    expect(screen.getByText('creationSteps.step3')).toBeInTheDocument()
  })

  it('does not show import modal initially', () => {
    renderComponent()
    expect(screen.queryByTestId('dataModelImportModal')).not.toBeInTheDocument()
  })

  it('opens import modal when clicking import from platform', () => {
    renderComponent()

    const trigger = screen.getByLabelText('Open menu')
    fireEvent.pointerDown(trigger, { button: 0, pointerType: 'mouse' })
    fireEvent.click(screen.getByText('import.fromPlatform'))

    expect(screen.getByTestId('dataModelImportModal')).toBeInTheDocument()
  })

  it('offers no import and shows the released info for a released data source', () => {
    renderComponent({ ...defaultProps, isDatasourceReleased: true })

    expect(screen.queryByLabelText('Open menu')).not.toBeInTheDocument()
    expect(screen.getByText('availableInfo')).toBeInTheDocument()
  })

  it('shows no released info for a draft data source', () => {
    renderComponent()

    expect(screen.queryByText('availableInfo')).not.toBeInTheDocument()
  })

  it('shows name, version number and status of the assigned version', () => {
    mockUseGetDatastructureVersion.mockReturnValue({ data: { data: mockVersion } })
    renderComponent({ ...defaultProps, selectedVersionId: 'datastructure-1/version-1' })

    const header = screen.getByTestId('datastructureVersionInfo')
    expect(header).toHaveTextContent('Test Datastructure')
    expect(header).toHaveTextContent('version{"version":"2"}')
    expect(header).toHaveTextContent('status.AVAILABLE')
  })

  it('shows the fallback when the assigned version has no number', () => {
    mockUseGetDatastructureVersion.mockReturnValue({
      data: { data: { ...mockVersion, version: null, dataStructureVersionStatus: 'DRAFT' } },
    })
    renderComponent({ ...defaultProps, selectedVersionId: 'datastructure-1/version-1' })

    const header = screen.getByTestId('datastructureVersionInfo')
    expect(header).toHaveTextContent('noVersion')
    expect(header).toHaveTextContent('status.DRAFT')
  })

  it('keeps the toolbar with an empty placeholder while the assigned version loads', () => {
    mockUseGetDatastructureVersion.mockReturnValue({ data: undefined, isLoading: true })
    renderComponent({ ...defaultProps, isReadOnly: true, selectedVersionId: 'datastructure-1/version-1' })

    expect(screen.getByTestId('datastructureVersionInfoPlaceholder')).toBeInTheDocument()
    expect(screen.queryByTestId('datastructureVersionInfo')).not.toBeInTheDocument()
  })

  it('shows no version info when no version is assigned', () => {
    renderComponent()

    expect(screen.queryByTestId('datastructureVersionInfo')).not.toBeInTheDocument()
  })
})

describe('DatastructureTab with an assigned data structure', () => {
  const assignedProps = { ...defaultProps, selectedVersionId: 'ds-1/v-1' }

  beforeEach(() => {
    mockUseGetDatastructureVersion.mockReset().mockReturnValue({ data: undefined })
  })

  it('shows the restricted state and does not fetch the version without read access', () => {
    mockAssignments([])
    renderComponent(assignedProps)

    expect(screen.getByText('title')).toBeInTheDocument()
    expect(screen.getByText('description')).toBeInTheDocument()
    expect(screen.queryByText('noDatastructures')).not.toBeInTheDocument()
    expect(mockUseGetDatastructureVersion).toHaveBeenLastCalledWith(expect.objectContaining({ isEnabled: false }))
  })

  it('shows the restricted state when the user can read only another data structure', () => {
    mockAssignments([readAssignment('DATASTRUCTURE', 'ds-2')])
    renderComponent(assignedProps)

    expect(screen.getByText('description')).toBeInTheDocument()
    expect(mockUseGetDatastructureVersion).toHaveBeenLastCalledWith(expect.objectContaining({ isEnabled: false }))
  })

  it('fetches the version when the user can read the assigned data structure', () => {
    mockAssignments([readAssignment('DATASTRUCTURE', 'ds-1')])
    renderComponent(assignedProps)

    expect(screen.queryByText('description')).not.toBeInTheDocument()
    expect(mockUseGetDatastructureVersion).toHaveBeenLastCalledWith(
      expect.objectContaining({ datastructureId: 'ds-1', versionId: 'v-1', isEnabled: true }),
    )
  })
})
