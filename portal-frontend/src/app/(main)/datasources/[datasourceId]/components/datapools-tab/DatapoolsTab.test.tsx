import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { Form } from '@/components/ui/form'
import { Datapool } from '@/types/datapools'
import { DATAPOOL_SCOPE_TYPES, DatasourceFormDraft, DatasourceFormDraftSchema } from '@/types/datasources'

import { DatapoolsTab } from './DatapoolsTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => '/',
  useSearchParams: () => new URLSearchParams(),
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const mockDatapools: Datapool[] = [
  {
    id: 'dp-1',
    name: 'Datapool Alpha',
    description: 'First datapool',
    contactPerson: null,
    createdAt: '2024-01-01T00:00:00Z',
    modifiedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'dp-2',
    name: 'Datapool Beta',
    description: 'Second datapool',
    contactPerson: { id: 'u-1', name: 'Alice' },
    createdAt: '2024-01-02T00:00:00Z',
    modifiedAt: '2024-01-02T00:00:00Z',
  },
]

const defaultFormValues: DatasourceFormDraft = {
  id: '1',
  name: 'Test Datasource',
  description: 'desc',
  dataSourceStatus: 'DRAFT',
  dataStructureVersionId: null,
  datapoolScope: { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: [] },
}

interface WrapperProps {
  isReadOnly?: boolean
  assignedDatapools?: Datapool[]
  isLoadingDatapools?: boolean
  initialValues?: Partial<DatasourceFormDraft>
  onDeleteDatapool?: (id: string) => void
  onOpenAddModal?: () => void
}

const TestWrapper = ({
  isReadOnly = false,
  assignedDatapools = [],
  isLoadingDatapools = false,
  initialValues = {},
  onDeleteDatapool = vi.fn(),
  onOpenAddModal = vi.fn(),
}: WrapperProps) => {
  const form = useForm<DatasourceFormDraft>({
    resolver: zodResolver(DatasourceFormDraftSchema),
    defaultValues: { ...defaultFormValues, ...initialValues },
  })

  return (
    <Form {...form}>
      <DatapoolsTab
        form={form}
        isReadOnly={isReadOnly}
        assignedDatapools={assignedDatapools}
        isLoadingDatapools={isLoadingDatapools}
        onDeleteDatapool={onDeleteDatapool}
        onOpenAddModal={onOpenAddModal}
      />
    </Form>
  )
}

const setup = (props: WrapperProps = {}) => render(<TestWrapper {...props} />)

describe('DatapoolsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('ALL scope', () => {
    it('shows the globe state card when scope is ALL', () => {
      setup({ initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL } } })
      expect(screen.getByText('allDatapoolsPage.title')).toBeInTheDocument()
    })

    it('shows the infobox when scope is ALL and not read-only', () => {
      setup({ initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL } } })
      expect(screen.getByText('allDatapoolsInfo')).toBeInTheDocument()
    })

    it('does not show the infobox when scope is ALL and read-only', () => {
      setup({ isReadOnly: true, initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL } } })
      expect(screen.queryByText('allDatapoolsInfo')).not.toBeInTheDocument()
    })

    it('does not show controls when read-only', () => {
      setup({ isReadOnly: true, initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL } } })
      expect(screen.queryByRole('switch')).not.toBeInTheDocument()
      expect(screen.queryByText('addDatapool')).not.toBeInTheDocument()
    })
  })

  describe('SPECIFIC scope — no datapools', () => {
    const specificScope = { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: [] as string[] } }

    it('shows the empty state card when no datapools are assigned', () => {
      setup({ initialValues: specificScope })
      expect(screen.getByText('noDataPage.title')).toBeInTheDocument()
    })

    it('shows the infobox when not read-only', () => {
      setup({ initialValues: specificScope })
      expect(screen.getByText('allDatapoolsInfo')).toBeInTheDocument()
    })

    it('does not show the infobox when read-only', () => {
      setup({ isReadOnly: true, initialValues: specificScope })
      expect(screen.queryByText('allDatapoolsInfo')).not.toBeInTheDocument()
    })

    it('calls onOpenAddModal when add button is clicked', () => {
      const onOpenAddModal = vi.fn()
      setup({ initialValues: specificScope, onOpenAddModal })
      fireEvent.click(screen.getByText('addDatapool'))
      expect(onOpenAddModal).toHaveBeenCalledOnce()
    })

    it('does not show controls when read-only', () => {
      setup({ isReadOnly: true, initialValues: specificScope })
      expect(screen.queryByRole('switch')).not.toBeInTheDocument()
      expect(screen.queryByText('addDatapool')).not.toBeInTheDocument()
    })
  })

  describe('SPECIFIC scope — with datapools', () => {
    const specificScope = {
      datapoolScope: { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: ['dp-1', 'dp-2'] },
    }

    it('renders the datapools table with assigned datapools', () => {
      setup({ assignedDatapools: mockDatapools, initialValues: specificScope })
      expect(screen.getByTestId('datapoolsTable')).toBeInTheDocument()
    })

    it('shows datapool names in the table', () => {
      setup({ assignedDatapools: mockDatapools, initialValues: specificScope })
      expect(screen.getByText('Datapool Alpha')).toBeInTheDocument()
      expect(screen.getByText('Datapool Beta')).toBeInTheDocument()
    })

    it('shows the infobox when not read-only', () => {
      setup({ assignedDatapools: mockDatapools, initialValues: specificScope })
      expect(screen.getByText('allDatapoolsInfo')).toBeInTheDocument()
    })

    it('does not show the infobox when read-only', () => {
      setup({ isReadOnly: true, assignedDatapools: mockDatapools, initialValues: specificScope })
      expect(screen.queryByText('allDatapoolsInfo')).not.toBeInTheDocument()
    })
  })

  describe('Loading state', () => {
    it('shows the table (not the empty state card) while loading', () => {
      setup({
        isLoadingDatapools: true,
        assignedDatapools: [],
        initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: [] as string[] } },
      })
      expect(screen.queryByText('noDataPage.title')).not.toBeInTheDocument()
      expect(screen.getByTestId('datapoolsTable')).toBeInTheDocument()
    })
  })

  describe('Switch control', () => {
    it('switch is checked when scope is ALL', () => {
      setup({ initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.ALL } } })
      expect(screen.getByRole('switch')).toBeChecked()
    })

    it('switch is unchecked when scope is SPECIFIC', () => {
      setup({ initialValues: { datapoolScope: { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: [] as string[] } } })
      expect(screen.getByRole('switch')).not.toBeChecked()
    })
  })
})
