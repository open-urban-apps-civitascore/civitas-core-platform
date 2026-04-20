import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Dataset } from '@/types/datasets'

import { DatasetOverview } from './DatasetOverview'

// ─── Mutable handles to replace per-test ───────────────────────────────────
const mockMutateAsync = vi.fn().mockResolvedValue(undefined)

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

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  usePatchDataset: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
  usePublishDataset: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
  useReleaseDataset: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
  useUnpublishDataset: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
  useUnreleaseDataset: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
  useUpdatePublishedDatasetMeta: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
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

vi.mock('@/components/tooltip/Tooltip', () => ({
  BasicTooltip: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/ui/checkbox', () => ({
  Checkbox: () => <input type="checkbox" />,
}))

// ─── Captured callbacks from child mocks ───────────────────────────────────
let capturedOnStatusChange: ((status: string) => void) | undefined
let capturedOnCancelClick: (() => void) | undefined
let capturedOnEditClick: (() => void) | undefined
let capturedCanSetAvailable: boolean | undefined

vi.mock('@/components/page-edit-controls/PageEditControls', () => ({
  __esModule: true,
  default: ({
    canEdit,
    isReadOnly,
    onEditClick,
    onCancelClick,
    onStatusChange,
    canSetAvailable,
    status,
  }: {
    canEdit: boolean
    isReadOnly: boolean
    onEditClick: () => void
    onCancelClick: () => void
    onStatusChange: (status: string) => void
    canSetAvailable: boolean
    status: string
  }) => {
    capturedOnStatusChange = onStatusChange
    capturedOnCancelClick = onCancelClick
    capturedOnEditClick = onEditClick
    capturedCanSetAvailable = canSetAvailable
    return (
      <div data-testid="pageEditControls" data-status={status} data-can-set-available={String(canSetAvailable)}>
        {canEdit && isReadOnly && (
          <button data-testid="editButton" onClick={onEditClick}>
            Edit
          </button>
        )}
        {canEdit && !isReadOnly && (
          <>
            <button data-testid="cancelButton" onClick={onCancelClick}>
              Cancel
            </button>
            <button data-testid="statusDraftButton" onClick={() => onStatusChange('DRAFT')}>
              Set Draft
            </button>
            <button data-testid="statusReadyButton" onClick={() => onStatusChange('READY')}>
              Set Ready
            </button>
            <button data-testid="statusAvailableButton" onClick={() => onStatusChange('AVAILABLE')}>
              Set Available
            </button>
          </>
        )}
      </div>
    )
  },
}))

let capturedOnDiscard: (() => void) | undefined
let capturedOnSave: (() => void) | undefined

vi.mock('@/components/modals/exit-warning-modal/ExitWarningModal', () => ({
  ExitWarningModal: ({
    open: isOpen,
    onDiscard,
    onConfirm,
  }: {
    // eslint-disable-next-line react/boolean-prop-naming
    open: boolean
    onDiscard: () => void
    onConfirm: () => void
  }) => {
    capturedOnDiscard = onDiscard
    capturedOnSave = onConfirm
    return isOpen ? (
      <div data-testid="exitWarningModal">
        <button data-testid="discardButton" onClick={onDiscard}>
          Discard
        </button>
        <button data-testid="saveButton" onClick={onConfirm}>
          Save
        </button>
      </div>
    ) : null
  },
}))

vi.mock('../../components/BaseInfoForm', () => ({
  BaseInfoForm: ({
    form,
    isReadOnly,
  }: {
    form: {
      register: (name: string) => { onChange: (e: React.ChangeEvent<HTMLInputElement>) => void }
      setValue: (name: string, value: unknown, opts?: object) => void
      formState: { errors: Record<string, { message?: string } | undefined> }
    }
    isReadOnly: boolean
  }) => (
    <div data-testid="baseInfoForm">
      <input
        data-testid="nameInput"
        disabled={isReadOnly}
        onChange={e => form.setValue('name', e.target.value, { shouldDirty: true, shouldTouch: true })}
      />
      {form.formState.errors.name && <span data-testid="nameError">{form.formState.errors.name.message}</span>}
      <textarea
        data-testid="descriptionInput"
        disabled={isReadOnly}
        onChange={e =>
          form.setValue('description', e.target.value, { shouldDirty: true, shouldValidate: true, shouldTouch: true })
        }
      />
    </div>
  ),
}))

vi.mock('../../components/CompletionStep', () => ({
  CompletionStep: (props: { step: { title: string } }) => <div data-testid={`completionStep-${props.step.title}`} />,
}))

vi.mock('../../../utils/mappers', () => ({
  mapDatasetToFormData: (dataset: Dataset) => ({
    id: dataset.id,
    name: dataset.name,
    description: dataset.description ?? '',
    openDataAccess: dataset.openDataAccess,
  }),
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

const makeDraftDataset = (overrides: Partial<Dataset> = {}): Dataset => ({
  id: 'test-id',
  name: 'Test Dataset',
  description: 'A test dataset',
  dataSetStatus: 'DRAFT',
  pipelines: [],
  distributions: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  openDataAccess: false,
  createdBy: { id: 'user-1', name: 'User One' },
  ...overrides,
})

const defaultDataset = makeDraftDataset()
const defaultProps = { dataset: defaultDataset, groupCount: 1, roleCount: 1 }

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<DatasetOverview {...defaultProps} {...props} />)

describe('DatasetOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockMutateAsync.mockResolvedValue(undefined)
    capturedOnStatusChange = undefined
    capturedOnCancelClick = undefined
    capturedOnEditClick = undefined
    capturedCanSetAvailable = undefined
    capturedOnDiscard = undefined
    capturedOnSave = undefined
    mockCurrentUser([PERMISSION_NAMES.DATASET_UPDATE, PERMISSION_NAMES.DATASET_RELEASE])
  })

  // ── Permission gating ────────────────────────────────────────────────────
  describe('Permission gating', () => {
    it('shows Edit button when user has DATASET_UPDATE permission', () => {
      mockCurrentUser([PERMISSION_NAMES.DATASET_UPDATE])
      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when user lacks DATASET_UPDATE permission', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('always shows access management completion card', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.getByTestId('completionStep-overview.completion.accessManagement.title')).toBeInTheDocument()
    })
  })

  // ── Edit mode UI ─────────────────────────────────────────────────────────
  describe('Edit mode UI', () => {
    it('enters edit mode when Edit button is clicked', async () => {
      renderComponent()
      await act(async () => {
        fireEvent.click(screen.getByTestId('editButton'))
      })
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('form is editable after clicking Edit', async () => {
      renderComponent()
      await act(async () => {
        fireEvent.click(screen.getByTestId('editButton'))
      })
      expect(screen.getByTestId('nameInput')).not.toBeDisabled()
    })
  })

  // ── canSetAvailable ──────────────────────────────────────────────────────
  describe('canSetAvailable computation', () => {
    it('is false when no distributions and no pipelines, even with assignments and valid form', () => {
      renderComponent({
        dataset: makeDraftDataset({ pipelines: [], distributions: [] }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(capturedCanSetAvailable).toBe(false)
    })

    it('is false when there are distributions but no assignment', () => {
      renderComponent({
        dataset: makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] }),
        groupCount: 0,
        roleCount: 0,
      })
      expect(capturedCanSetAvailable).toBe(false)
    })

    it('is false when there are pipelines and assignments but the form name is too short', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          name: 'AB', // Too short (min 3 chars)
          pipelines: [{ id: 'p1', name: 'Pipeline 1' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(capturedCanSetAvailable).toBe(false)
    })

    it('is false when form description is empty (required for available)', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          description: '',
          pipelines: [{ id: 'p1', name: 'Pipeline 1' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(capturedCanSetAvailable).toBe(false)
    })

    it('is true when pipelines present, assignments set, and form passes strict schema', () => {
      renderComponent({
        dataset: makeDraftDataset({ pipelines: [{ id: 'p1', name: 'Pipeline 1' }] }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(capturedCanSetAvailable).toBe(true)
    })

    it('is true when distributions present (no pipelines needed), assignments set, valid form', () => {
      renderComponent({
        dataset: makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(capturedCanSetAvailable).toBe(true)
    })

    it('updates to false when assignments drop to 0 after re-render', () => {
      const { rerender } = renderComponent({
        dataset: makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(capturedCanSetAvailable).toBe(true)

      rerender(
        <DatasetOverview
          dataset={makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] })}
          groupCount={0}
          roleCount={0}
        />,
      )
      expect(capturedCanSetAvailable).toBe(false)
    })

    it('is false when description is empty', () => {
      renderComponent({
        dataset: makeDraftDataset({
          description: '',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })

      expect(screen.getByTestId('pageEditControls').dataset.canSetAvailable).toBe('false')
    })

    it('is true when description is filled', () => {
      renderComponent({
        dataset: makeDraftDataset({
          description: 'A meaningful description',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })

      expect(screen.getByTestId('pageEditControls').dataset.canSetAvailable).toBe('true')
    })

    it('becomes true when description is filled in edit mode', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          description: '',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(screen.getByTestId('pageEditControls').dataset.canSetAvailable).toBe('false')

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        fireEvent.change(screen.getByTestId('descriptionInput'), { target: { value: 'Now it has a description' } })
      })

      expect(screen.getByTestId('pageEditControls').dataset.canSetAvailable).toBe('true')
    })

    it('becomes false when description is removed from the dataset', () => {
      const { rerender } = renderComponent({
        dataset: makeDraftDataset({
          description: 'A meaningful description',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      expect(screen.getByTestId('pageEditControls').dataset.canSetAvailable).toBe('true')

      rerender(
        <DatasetOverview
          dataset={makeDraftDataset({
            description: '',
            distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
          })}
          groupCount={1}
          roleCount={1}
        />,
      )

      expect(screen.getByTestId('pageEditControls').dataset.canSetAvailable).toBe('false')
    })
  })

  // ── Auto-revert from READY/AVAILABLE to DRAFT ────────────────────────────
  describe('Auto-revert: revalidateDraftMode', () => {
    it('reverts status from READY to DRAFT when canSetAvailable becomes false', async () => {
      // Start with a dataset in READY status and valid conditions
      const { rerender } = renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'READY',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
      })

      // Status should still be READY
      expect(screen.getByTestId('pageEditControls').dataset.status).toBe('READY')

      // Remove all assignments so canSetAvailable becomes false
      await act(async () => {
        rerender(
          <DatasetOverview
            dataset={makeDraftDataset({
              dataSetStatus: 'READY',
              distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
            })}
            groupCount={0}
            roleCount={0}
          />,
        )
      })

      expect(screen.getByTestId('pageEditControls').dataset.status).toBe('DRAFT')
    })

    it('reverts status from AVAILABLE to DRAFT when canSetAvailable becomes false', async () => {
      const { rerender } = renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'AVAILABLE',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
      })

      expect(screen.getByTestId('pageEditControls').dataset.status).toBe('AVAILABLE')

      await act(async () => {
        rerender(
          <DatasetOverview
            dataset={makeDraftDataset({
              dataSetStatus: 'AVAILABLE',
              distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
            })}
            groupCount={0}
            roleCount={0}
          />,
        )
      })

      expect(screen.getByTestId('pageEditControls').dataset.status).toBe('DRAFT')
    })

    it('shows a toast notification when status is reverted to DRAFT', async () => {
      const { toast } = await import('sonner')
      const { rerender } = renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'READY',
          distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
        }),
      })

      await act(async () => {
        rerender(
          <DatasetOverview
            dataset={makeDraftDataset({
              dataSetStatus: 'READY',
              distributions: [{ id: 'd1', accessUrl: 'http://example.com' }],
            })}
            groupCount={0}
            roleCount={0}
          />,
        )
      })

      expect(toast.info).toHaveBeenCalledWith('info.switchMode')
    })
  })

  // ── Save uses pickDirtyValues ─────────────────────────────────────────────
  describe('Save uses pickDirtyValues: only changed fields are sent', () => {
    it('calls mutateAsync with only changed fields (name) when only name is modified', async () => {
      renderComponent()

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        fireEvent.change(screen.getByTestId('nameInput'), { target: { value: 'Updated Name' } })
      })

      const form = document.querySelector('#dataset-form')
      if (form) {
        await act(async () => {
          fireEvent.submit(form)
        })
      }

      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ name: 'Updated Name' }))
      })
    })

    it('calls mutateAsync with only changed fields (description) when only description is modified', async () => {
      renderComponent()

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        fireEvent.change(screen.getByTestId('descriptionInput'), { target: { value: 'New description text' } })
      })

      const form = document.querySelector('#dataset-form')
      if (form) {
        await act(async () => {
          fireEvent.submit(form)
        })
      }

      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledWith(expect.objectContaining({ description: 'New description text' }))
      })
    })

    it('calls publishDataset when status changes from DRAFT to READY', async () => {
      renderComponent({
        dataset: makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] }),
        groupCount: 1,
        roleCount: 1,
      })

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        capturedOnStatusChange?.('READY')
      })

      const form = document.querySelector('#dataset-form')
      if (form) {
        await act(async () => {
          fireEvent.submit(form)
        })
      }

      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledWith('test-id')
      })
    })

    it('shows name error and toast when mutateAsync rejects with a 409 name conflict', async () => {
      const { toast } = await import('sonner')
      const conflictError = new AxiosError('Conflict', 'ERR_BAD_REQUEST', {} as InternalAxiosRequestConfig, undefined, {
        status: 409,
        statusText: 'Conflict',
        headers: {},
        config: {} as InternalAxiosRequestConfig,
        data: { detail: 'Dataset with name "Duplicate Name" already exists' },
      })
      mockMutateAsync.mockRejectedValueOnce(conflictError)

      renderComponent()

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        fireEvent.change(screen.getByTestId('nameInput'), { target: { value: 'Duplicate Name' } })
      })

      const form = document.querySelector('#dataset-form')
      if (form) {
        await act(async () => {
          fireEvent.submit(form)
        })
      }

      await waitFor(() => {
        expect(screen.getByTestId('nameError')).toHaveTextContent('common.errors.nameExists')
        expect(toast.error).toHaveBeenCalled()
      })
    })
  })

  // ── Exit / Discard resets form and status ────────────────────────────────
  describe('Exit and discard: form and status reset', () => {
    it('shows ExitWarningModal when Cancel is clicked with unsaved changes', async () => {
      renderComponent()

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        fireEvent.change(screen.getByTestId('nameInput'), { target: { value: 'Changed Name' } })
      })
      await act(async () => {
        capturedOnCancelClick?.()
      })

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('exits to read-only mode without modal when no unsaved changes', async () => {
      renderComponent()

      await act(async () => {
        capturedOnEditClick?.()
      })
      // Cancel without editing
      await act(async () => {
        capturedOnCancelClick?.()
      })

      // No modal, back to read-only (Edit button visible)
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('discarding resets form values and status to original dataset values', async () => {
      renderComponent({
        dataset: makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] }),
      })

      await act(async () => {
        capturedOnEditClick?.()
      })
      // Change status to READY
      await act(async () => {
        capturedOnStatusChange?.('READY')
      })
      expect(screen.getByTestId('pageEditControls').dataset.status).toBe('READY')

      // Trigger cancel → modal
      await act(async () => {
        capturedOnCancelClick?.()
      })
      // Click Discard
      await act(async () => {
        capturedOnDiscard?.()
      })

      // Status should revert to DRAFT
      expect(screen.getByTestId('pageEditControls').dataset.status).toBe('DRAFT')
      // Should be back in read-only mode
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('discarding clears exit modal', async () => {
      renderComponent()

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        fireEvent.change(screen.getByTestId('nameInput'), { target: { value: 'Changed' } })
      })
      await act(async () => {
        capturedOnCancelClick?.()
      })

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()

      await act(async () => {
        capturedOnDiscard?.()
      })

      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
    })

    it('save-and-exit from modal calls mutateAsync and closes modal on success', async () => {
      renderComponent({
        dataset: makeDraftDataset({ distributions: [{ id: 'd1', accessUrl: 'http://example.com' }] }),
      })

      await act(async () => {
        capturedOnEditClick?.()
      })
      await act(async () => {
        capturedOnStatusChange?.('READY')
      })
      await act(async () => {
        capturedOnCancelClick?.()
      })

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()

      await act(async () => {
        capturedOnSave?.()
      })

      expect(mockMutateAsync).toHaveBeenCalledWith('test-id')
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      })
    })
  })

  // ── Completion step indicators ───────────────────────────────────────────
  describe('Completion steps', () => {
    it('renders data flow completion card', () => {
      renderComponent()
      expect(screen.getByTestId('completionStep-overview.completion.dataFlow.title')).toBeInTheDocument()
    })

    it('renders access management completion card', () => {
      renderComponent()
      expect(screen.getByTestId('completionStep-overview.completion.accessManagement.title')).toBeInTheDocument()
    })
  })
})
