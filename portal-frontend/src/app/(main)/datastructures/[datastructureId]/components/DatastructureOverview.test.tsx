import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { DatastructureOverview } from './DatastructureOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

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
  usePathname: () => '/datastructures/test-id',
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

vi.mock('../hooks/useDatastructure', () => ({
  useDatastructure: () => ({
    areAssignmentsDirty: false,
    canStage: false,
    canSetDraft: true,
    completedTabs: [],
    form: mockForm,
    handleStatusChange: vi.fn(),
    isConfirmButtonDisabled: true,
    isLoading: false,
    resetToInitialState: vi.fn(),
    saveDatastructure: vi.fn(),
    selectedTab: 'basicInfo',
    setSelectedTab: vi.fn(),
    statusHint: undefined,
    statusWatch: 'DRAFT',
  }),
}))

vi.mock('./basic-info-tab/BasicInfoTab', () => ({
  BasicInfoTab: ({ isReadOnly }: { isReadOnly: boolean }) => (
    <div data-testid="basicInfoTab" data-readonly={isReadOnly} />
  ),
}))

vi.mock('./versions-tab/VersionsTab', () => ({
  VersionsTab: () => <div data-testid="versionsTab" />,
}))

vi.mock('./access-management-tab/AccessManagementTab', () => ({
  AccessManagementTab: () => <div data-testid="accessManagementTab" />,
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

const datastructure = {
  id: 'test-id',
  name: 'Test Datastructure',
  description: 'A test datastructure',
  dataStructureStatus: 'DRAFT' as const,
  createdFromDataSource: false,
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  dataStructureVersions: [],
}

const defaultProps = {
  datastructure,
  initialAssignments: [],
}

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
          scopeType: 'DATASTRUCTURE',
          scopeId: 'test-id',
          permissions,
        },
      ],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

describe('DatastructureOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    mockForm.formState.isDirty = false
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE, PERMISSION_NAMES.ASSIGNMENT_READ])
  })

  describe('Permission gating', () => {
    describe('Edit button (canUpdate)', () => {
      it('shows Edit button when user has DATASTRUCTURE_UPDATE permission', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE])
        render(<DatastructureOverview {...defaultProps} />)
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })

      it('hides Edit button when user lacks DATASTRUCTURE_UPDATE permission', () => {
        mockCurrentUser([])
        render(<DatastructureOverview {...defaultProps} />)
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      })
    })

    describe('Edit button gating when AVAILABLE', () => {
      const availableDatastructure = {
        ...datastructure,
        dataStructureStatus: 'AVAILABLE' as const,
      }
      const availableProps = { ...defaultProps, datastructure: availableDatastructure }

      it('shows Edit button when AVAILABLE and user has both UPDATE and RELEASE', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE, PERMISSION_NAMES.DATASTRUCTURE_RELEASE])
        render(<DatastructureOverview {...availableProps} />)
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })

      it('hides Edit button when AVAILABLE and user has UPDATE but lacks RELEASE', () => {
        mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_UPDATE])
        render(<DatastructureOverview {...availableProps} />)
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      })
    })
  })

  describe('Read-only mode behavior', () => {
    it('starts in read-only mode', () => {
      render(<DatastructureOverview {...defaultProps} />)
      expect(screen.getByTestId('basicInfoTab')).toHaveAttribute('data-readonly', 'true')
    })

    it('switches to edit mode when Edit button is clicked', () => {
      render(<DatastructureOverview {...defaultProps} />)
      fireEvent.click(screen.getByTestId('editButton'))
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
      expect(mockReplace).toHaveBeenCalledWith('/datastructures/test-id?mode=edit', { scroll: false })
    })

    it('starts in edit mode when mode=edit param is present', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatastructureOverview {...defaultProps} />)
      expect(screen.getByTestId('basicInfoTab')).toHaveAttribute('data-readonly', 'false')
    })

    it('stays in read-only mode when mode=edit param is present but the user lacks DATASTRUCTURE_UPDATE', () => {
      mockCurrentUser([])
      mockSearchParams = new URLSearchParams('mode=edit')
      render(<DatastructureOverview {...defaultProps} />)
      expect(screen.getByTestId('basicInfoTab')).toHaveAttribute('data-readonly', 'true')
      expect(screen.queryByTestId('cancelButton')).not.toBeInTheDocument()
    })
  })
})
