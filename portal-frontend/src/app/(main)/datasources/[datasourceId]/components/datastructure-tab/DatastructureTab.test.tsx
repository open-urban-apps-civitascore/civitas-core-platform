import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { DatastructureTab } from './DatastructureTab'

vi.mock('next-intl', () => ({
  useTranslations: () =>
    Object.assign((key: string) => key, {
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
  useGetDatastructureVersion: () => ({ data: undefined }),
}))

vi.mock('@/app/(main)/datastructures/[datastructureId]/(versions)/hooks/useDatastructureVersion', () => ({
  defaultDatastructureVersionFormData: {},
  useDatastructureVersion: () => ({
    modelSessionManager: null,
    hasUserChanges: false,
    resetToInitialState: vi.fn(),
    resetFormAndSession: vi.fn(),
  }),
}))

const defaultProps = {
  datasourceTitle: 'Test Datasource',
  selectedVersionId: null,
  isReadOnly: false,
  isDatasourceInUseByReleased: false,
  isDatasourceReleased: false,
  onSelectDatastructureVersion: vi.fn(),
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
})
