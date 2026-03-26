import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { DatasourceOverview } from './DatasourceOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockCurrentUser = (permissions: PermissionName[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'current',
      email: 'current@test.com',
      title: 'MR' as const,
      firstName: 'Current',
      lastName: 'User',
      assignments: [
        {
          scopeType: 'DATASOURCE',
          scopeId: 'test-id',
          permissions,
        },
      ],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

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
    hasStatusChanged: false,
    handleStatusChange: vi.fn(),
    canSetAvailable: false,
    completedTabs: [],
    submitDatasource: mockSubmitDatasource,
    resetToInitialState: vi.fn(),
    isLoading: false,
  }),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useGetDatastructureVersion: () => ({
    data: undefined,
  }),
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

vi.mock('./datastructure-tab/DatastructureTab', () => ({
  DatastructureTab: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="datastructureTab" data-readonly={isReadOnly} />
  ),
}))

vi.mock('@/components/ui/form', () => ({
  Form: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/page-container/PageContainer', () => ({
  PageContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/page-header/PageHeader', () => ({
  PageHeader: ({
    customElement,
    segmentedControlBarProps,
  }: {
    customElement: React.ReactNode
    segmentedControlBarProps?: {
      tabs: { value: string; label: string }[]
      onTabChange: (tab: string) => void
    }
  }) => (
    <div>
      {customElement}
      {segmentedControlBarProps && (
        <div data-testid="segmentedControlBar">
          {segmentedControlBarProps.tabs.map(tab => (
            <button
              key={tab.value}
              data-testid={`tab-${tab.value}`}
              onClick={() => segmentedControlBarProps.onTabChange(tab.value)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}
    </div>
  ),
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
}

describe('DatasourceOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    mockForm.formState.isDirty = false
    mockCurrentUser([
      PERMISSION_NAMES.DATASOURCE_UPDATE,
      PERMISSION_NAMES.ASSIGNMENT_READ,
      PERMISSION_NAMES.DATASTRUCTURE_READ,
    ])
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
      expect(mockReplace).toHaveBeenCalledWith('/datasources/test-id', { scroll: false })
    })

    it('shows exit warning modal when form is dirty', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      mockForm.formState.isDirty = true
      render(<DatasourceOverview {...defaultProps} />)

      fireEvent.click(screen.getByTestId('cancelButton'))

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('discards changes and returns to view mode via modal', async () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      mockForm.formState.isDirty = true
      render(<DatasourceOverview {...defaultProps} />)

      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('discardButton'))

      await waitFor(() => {
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
        expect(mockReplace).toHaveBeenCalledWith('/datasources/test-id', { scroll: false })
      })
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

  describe('Tab switching', () => {
    it('shows basicInfo tab by default', () => {
      render(<DatasourceOverview {...defaultProps} />)
      expect(screen.getByTestId('basicInfoTab')).toBeInTheDocument()
      expect(screen.queryByTestId('datastructureTab')).not.toBeInTheDocument()
    })

    it('switches to dataStructure tab when clicked', () => {
      render(<DatasourceOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('tab-dataStructure'))

      expect(screen.getByTestId('datastructureTab')).toBeInTheDocument()
      expect(screen.queryByTestId('basicInfoTab')).not.toBeInTheDocument()
    })

    it('switches to connector tab when clicked', () => {
      render(<DatasourceOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('tab-connector'))

      expect(screen.getByTestId('connectorTab')).toBeInTheDocument()
      expect(screen.queryByTestId('basicInfoTab')).not.toBeInTheDocument()
    })

    it('switches to accessManagement tab when clicked', () => {
      render(<DatasourceOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('tab-accessManagement'))

      expect(screen.getByTestId('accessManagementTab')).toBeInTheDocument()
      expect(screen.queryByTestId('basicInfoTab')).not.toBeInTheDocument()
    })

    it('passes isReadOnly to dataStructure tab', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatasourceOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('tab-dataStructure'))

      expect(screen.getByTestId('datastructureTab')).toHaveAttribute('data-readonly', 'false')
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

  describe('Permission gating', () => {
    describe('Edit button (canUpdate)', () => {
      it('shows Edit button when user has DATASOURCE_UPDATE permission', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASOURCE_UPDATE])
        render(<DatasourceOverview {...defaultProps} />)
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })

      it('hides Edit button when user lacks DATASOURCE_UPDATE permission', () => {
        mockCurrentUser([])
        render(<DatasourceOverview {...defaultProps} />)
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      })
    })

    describe('Datastructure tab gating (DATASTRUCTURE_READ)', () => {
      it('shows dataStructure tab when user has DATASTRUCTURE_READ', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASOURCE_UPDATE, PERMISSION_NAMES.DATASTRUCTURE_READ])
        render(<DatasourceOverview {...defaultProps} />)
        expect(screen.getByTestId('tab-dataStructure')).toBeInTheDocument()
      })

      it('hides dataStructure tab when user lacks DATASTRUCTURE_READ', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASOURCE_UPDATE])
        render(<DatasourceOverview {...defaultProps} />)
        expect(screen.queryByTestId('tab-dataStructure')).not.toBeInTheDocument()
      })
    })

    describe('Edit button gating when AVAILABLE', () => {
      const availableDatasource = {
        ...datasource,
        dataSourceStatus: 'AVAILABLE' as const,
      }
      const availableProps = { ...defaultProps, datasource: availableDatasource }

      it('shows Edit button when AVAILABLE and user has both UPDATE and RELEASE', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASOURCE_UPDATE, PERMISSION_NAMES.DATASOURCE_RELEASE])
        render(<DatasourceOverview {...availableProps} />)
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })

      it('hides Edit button when AVAILABLE and user has UPDATE but lacks RELEASE', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASOURCE_UPDATE])
        render(<DatasourceOverview {...availableProps} />)
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      })
    })
  })
})
