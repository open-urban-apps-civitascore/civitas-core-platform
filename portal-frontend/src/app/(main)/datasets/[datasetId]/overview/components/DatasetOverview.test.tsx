import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

import { DatasetOverview } from './DatasetOverview'

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
          scopeType: 'DATASET',
          scopeId: 'test-id',
          permissions,
        },
      ],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  usePatchDataset: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePublishDataset: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useReleaseDataset: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnpublishDataset: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnreleaseDataset: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdatePublishedDatasetMeta: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

vi.mock('@/components/ui/form', () => ({
  Form: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  FormControl: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  FormField: ({ render }: { render: (args: { field: unknown }) => React.ReactNode }) =>
    render({ field: { value: false, onChange: vi.fn() } }),
  FormItem: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  FormLabel: ({ children }: { children: React.ReactNode }) => <label>{children}</label>,
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

vi.mock('@/components/content-card/ContentCard', () => ({
  ContentCard: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/form/FooterElement', () => ({
  FooterElement: () => null,
}))

vi.mock('@/components/modals/exit-warning-modal/ExitWarningModal', () => ({
  ExitWarningModal: () => null,
}))

vi.mock('@/components/tooltip/Tooltip', () => ({
  BasicTooltip: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/ui/checkbox', () => ({
  Checkbox: () => <input type="checkbox" />,
}))

vi.mock('@/components/page-edit-controls/PageEditControls', () => ({
  __esModule: true,
  default: ({
    canEdit,
    isReadOnly,
    onEditClick,
  }: {
    canEdit: boolean
    isReadOnly: boolean
    onEditClick: () => void
  }) =>
    canEdit && isReadOnly ? (
      <button data-testid="editButton" onClick={onEditClick}>
        Edit
      </button>
    ) : canEdit && !isReadOnly ? (
      <button data-testid="cancelButton">Cancel</button>
    ) : null,
}))

vi.mock('../../../utils/mappers', () => ({
  mapDatasetToFormData: () => ({
    name: 'Test Dataset',
    description: 'A test dataset',
    openDataAccess: false,
  }),
}))

vi.mock('../../components/BaseInfoForm', () => ({
  BaseInfoForm: () => <div data-testid="baseInfoForm" />,
}))

vi.mock('../../components/CompletionStep', () => ({
  CompletionStep: (props: { step: { title: string } }) => <div data-testid={`completionStep-${props.step.title}`} />,
}))

const dataset = {
  id: 'test-id',
  name: 'Test Dataset',
  description: 'A test dataset',
  dataSetStatus: 'DRAFT' as const,
  pipelines: [],
  distributions: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  openDataAccess: false,
}

const defaultProps = { dataset, groupCount: 1, roleCount: 1 }

describe('DatasetOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATASET_UPDATE])
  })

  describe('Permission gating', () => {
    it('shows Edit button when user has DATASET_UPDATE permission', () => {
      mockCurrentUser([PERMISSION_NAMES.DATASET_UPDATE])
      render(<DatasetOverview {...defaultProps} />)
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when user lacks DATASET_UPDATE permission', () => {
      mockCurrentUser([])
      render(<DatasetOverview {...defaultProps} />)
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('always shows access management completion card', () => {
      mockCurrentUser([])
      render(<DatasetOverview {...defaultProps} />)
      expect(screen.getByTestId('completionStep-overview.completion.accessManagement.title')).toBeInTheDocument()
    })
  })
})
