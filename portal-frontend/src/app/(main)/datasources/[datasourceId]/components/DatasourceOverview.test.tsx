import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DatasourceOverview } from './DatasourceOverview'

const mockPush = vi.fn()
const mockReplace = vi.fn()
const mockRefresh = vi.fn()
let mockSearchParams = new URLSearchParams()

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    replace: mockReplace,
    refresh: mockRefresh,
  }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/datasources/test-id',
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const mockForm = {
  formState: { isDirty: false, errors: {}, dirtyFields: {} },
  reset: vi.fn(),
  getValues: vi.fn(),
  control: {},
  watch: vi.fn(),
  trigger: vi.fn(),
  clearErrors: vi.fn(),
  register: vi.fn(),
  handleSubmit: vi.fn(),
  setValue: vi.fn(),
  setError: vi.fn(),
  unregister: vi.fn(),
  getFieldState: vi.fn(),
  setFocus: vi.fn(),
  resetField: vi.fn(),
}

const mockSubmitDatasource = vi.fn()

vi.mock('../hooks/useDatasourceForm', () => ({
  useDatasourceForm: () => ({
    form: mockForm,
    readyConnectorType: 'MQTT',
    dataSourceStatus: 'DRAFT',
    handleStatusChange: vi.fn(),
    canSetAvailable: false,
    completedTabs: [],
    submitDatasource: mockSubmitDatasource,
    isLoading: false,
  }),
}))

vi.mock('./basic-info/BasicInfoTab', () => ({
  BasicInfoTab: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="basicInfoTab" data-readonly={isReadOnly} />
  ),
}))

vi.mock('./connector-tab/ConnectorTab', () => ({
  ConnectorTab: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="connectorTab" data-readonly={isReadOnly} />
  ),
}))

vi.mock('./access-management/AccessManagementTab', () => ({
  AccessManagementTab: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="accessManagementTab" data-readonly={isReadOnly} />
  ),
}))

vi.mock('@/components/ui/form', () => ({
  Form: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/page-container/PageContainer', () => ({
  PageContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/page-header/PageHeader', () => ({
  PageHeader: ({ customElement }: { customElement: React.ReactNode }) => <div>{customElement}</div>,
}))

vi.mock('@/components/page-background/PageBackground', () => ({
  PageBackground: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/loading-spinner/LoadingSpinner', () => ({
  LoadingSpinner: () => <div data-testid="loadingSpinner" />,
}))

/* eslint-disable react/boolean-prop-naming */
vi.mock('@/components/modals/exit-warning-modal/ExitWarningModal', () => ({
  ExitWarningModal: ({
    open,
    onDiscard,
    onConfirm,
  }: {
    open: boolean
    onDiscard: () => void
    onConfirm: () => void
  }) =>
    open ? (
      <div data-testid="exitWarningModal">
        <button data-testid="discardButton" onClick={onDiscard}>
          Discard
        </button>
        <button data-testid="confirmSaveButton" onClick={onConfirm}>
          Save
        </button>
      </div>
    ) : null,
}))
/* eslint-enable react/boolean-prop-naming */

const datasource = {
  id: 'test-id',
  name: 'Test Datasource',
  description: 'A test datasource',
  dataSourceStatus: 'DRAFT' as const,
  connectorType: 'MQTT' as const,
  configuration: { urls: 'mqtt://localhost' },
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  dataStructureVersion: null,
  inUse: false,
}

const defaultProps = {
  datasource,
  initialAssignments: [],
  groups: [],
  roles: [],
}

describe('DatasourceOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    mockForm.formState.isDirty = false
  })

  describe('View/Edit mode initialization', () => {
    it('starts in read-only mode when no mode param', () => {
      render(<DatasourceOverview {...defaultProps} />)
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('starts in edit mode when mode=edit param is present', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatasourceOverview {...defaultProps} />)
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })
  })

  describe('Mode transitions', () => {
    it('switches to edit mode and updates URL when edit button is clicked', () => {
      render(<DatasourceOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('editButton'))

      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
      expect(mockReplace).toHaveBeenCalledWith('/datasources/test-id?mode=edit', { scroll: false })
    })

    it('preserves existing search params when entering edit mode', () => {
      mockSearchParams = new URLSearchParams('page=2&search=foo')
      render(<DatasourceOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('editButton'))

      expect(mockReplace).toHaveBeenCalledWith(
        expect.stringMatching(/page=2.*mode=edit|mode=edit.*page=2/),
        expect.any(Object),
      )
    })

    it('exits to view mode and removes mode param when form is clean', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatasourceOverview {...defaultProps} />)

      fireEvent.click(screen.getByTestId('cancelButton'))

      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(mockForm.reset).toHaveBeenCalled()
      expect(mockReplace).toHaveBeenCalledWith('/datasources/test-id', { scroll: false })
    })

    it('shows exit warning modal when form is dirty', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      mockForm.formState.isDirty = true
      render(<DatasourceOverview {...defaultProps} />)

      fireEvent.click(screen.getByTestId('cancelButton'))

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('discards changes and returns to view mode via modal', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      mockForm.formState.isDirty = true
      render(<DatasourceOverview {...defaultProps} />)

      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('discardButton'))

      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(mockForm.reset).toHaveBeenCalled()
      expect(mockReplace).toHaveBeenCalledWith('/datasources/test-id', { scroll: false })
    })

    it('saves and returns to view mode via modal', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      mockForm.formState.isDirty = true
      mockSubmitDatasource.mockImplementation((cb: () => void) => cb())
      render(<DatasourceOverview {...defaultProps} />)

      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('confirmSaveButton'))

      await waitFor(() => {
        expect(mockSubmitDatasource).toHaveBeenCalled()
        expect(mockReplace).toHaveBeenCalledWith('/datasources/test-id', { scroll: false })
      })
    })
  })

  describe('Read-only mode behavior', () => {
    it('passes isReadOnly to tab content', () => {
      render(<DatasourceOverview {...defaultProps} />)
      expect(screen.getByTestId('basicInfoTab')).toHaveAttribute('data-readonly', 'true')
    })

    it('passes isReadOnly=false to tab content in edit mode', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatasourceOverview {...defaultProps} />)
      expect(screen.getByTestId('basicInfoTab')).toHaveAttribute('data-readonly', 'false')
    })
  })
})
