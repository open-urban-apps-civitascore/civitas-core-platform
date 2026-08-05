import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Dataset } from '@/types/datasets'

import { DatasetOverview } from './DatasetOverview'

const mockPatchDataset = vi.fn().mockResolvedValue(undefined)
const mockStageDataset = vi.fn().mockResolvedValue(undefined)
const mockUnstageDataset = vi.fn().mockResolvedValue(undefined)
const mockReleaseDataset = vi.fn().mockResolvedValue(undefined)
const mockUnreleaseDataset = vi.fn().mockResolvedValue(undefined)
const mockUpdateReleasedDatasetMeta = vi.fn().mockResolvedValue(undefined)

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  usePatchDataset: () => ({ mutateAsync: mockPatchDataset, isPending: false }),
  useStageDataset: () => ({ mutateAsync: mockStageDataset, isPending: false }),
  useUnstageDataset: () => ({ mutateAsync: mockUnstageDataset, isPending: false }),
  useReleaseDataset: () => ({ mutateAsync: mockReleaseDataset, isPending: false }),
  useUnreleaseDataset: () => ({ mutateAsync: mockUnreleaseDataset, isPending: false }),
  useUpdateReleasedDatasetMeta: () => ({ mutateAsync: mockUpdateReleasedDatasetMeta, isPending: false }),
  useGetDataset: () => ({ data: undefined }),
}))

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

const mockCurrentUserWithDatapoolScope = (permissions: PermissionName[], datapoolId: string) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'current',
      email: 'current@test.com',
      title: 'MR' as const,
      firstName: 'Current',
      lastName: 'User',
      assignments: [{ scopeType: 'DATAPOOL', scopeId: datapoolId, permissions }],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

let mockSearchParams = new URLSearchParams()

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/datasets/ds-1',
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

vi.mock('@/components/ui/input', () => ({
  Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
}))

vi.mock('@/components/ui/textarea', () => ({
  Textarea: (props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea {...props} />,
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
    datapoolId: dataset.datapool?.id ?? null,
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
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  openDataAccess: false,
  createdBy: { id: 'user-1', name: 'User One' },
  datapool: null,
  ...overrides,
})

const defaultDataset = makeDraftDataset()

const defaultProps = {
  dataset: defaultDataset,
  groupCount: 1,
  roleCount: 1,
  datapoolOptions: [
    { value: 'datapool-1', label: 'Datapool 1' },
    { value: 'datapool-2', label: 'Datapool 2' },
  ],
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<DatasetOverview {...defaultProps} {...props} />)

describe('DatasetOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    mockCurrentUser([PERMISSION_NAMES.DATASET_UPDATE, PERMISSION_NAMES.DATASET_RELEASE])
  })

  const getStatusOption = (status: string) => screen.getByTestId(`statusOption-${status.toLowerCase()}`)
  const clickEditButton = () => fireEvent.click(screen.getByTestId('editButton'))
  const openStatusDropdown = () => userEvent.click(screen.getByTestId('statusDropdown'))

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

    it('shows Edit button when user has DATASET_UPDATE via DATAPOOL assignment for the dataset pool', () => {
      mockCurrentUserWithDatapoolScope([PERMISSION_NAMES.DATASET_UPDATE], 'pool-1')
      renderComponent({ dataset: makeDraftDataset({ id: 'test-id', datapool: { id: 'pool-1', name: 'Pool 1' } }) })
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when DATAPOOL assignment is for a different pool', () => {
      mockCurrentUserWithDatapoolScope([PERMISSION_NAMES.DATASET_UPDATE], 'pool-2')
      renderComponent({ dataset: makeDraftDataset({ id: 'test-id', datapool: { id: 'pool-1', name: 'Pool 1' } }) })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('hides Edit button when dataset has no datapool and user only has DATAPOOL assignment', () => {
      mockCurrentUserWithDatapoolScope([PERMISSION_NAMES.DATASET_UPDATE], 'pool-1')
      renderComponent({ dataset: makeDraftDataset({ id: 'test-id', datapool: null }) })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })

    it('always shows access management completion card', () => {
      mockCurrentUser([])
      renderComponent()
      expect(screen.getByTestId('completionStep-overview.completion.accessManagement.title')).toBeInTheDocument()
    })
  })

  describe('Edit mode UI', () => {
    it('starts in edit mode when mode=edit param is present', () => {
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()

      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('nameTextField')).not.toBeDisabled()
    })

    it('stays in read-only mode when mode=edit param is present but the user lacks DATASET_UPDATE', () => {
      mockCurrentUser([])
      mockSearchParams = new URLSearchParams('mode=edit')
      renderComponent()

      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.queryByTestId('cancelButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('nameTextField')).toBeDisabled()
    })

    it('enters edit mode when Edit button is clicked', async () => {
      renderComponent()
      clickEditButton()

      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('form is editable after clicking Edit', async () => {
      renderComponent()
      expect(screen.getByTestId('nameTextField')).toBeDisabled()
      expect(screen.getByTestId('descriptionTextArea')).toBeDisabled()
      clickEditButton()
      expect(screen.getByTestId('nameTextField')).not.toBeDisabled()
      expect(screen.getByTestId('descriptionTextArea')).not.toBeDisabled()
    })
  })

  describe('Status availability and auto-revert', () => {
    describe('READY and AVAILABLE are disabled when canStage is false', () => {
      it('when no pipelines are present', async () => {
        renderComponent({
          dataset: makeDraftDataset({ pipelines: [] }),
          groupCount: 1,
          roleCount: 1,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).toHaveAttribute('aria-disabled', 'true')
      })

      it('when form name is too short', async () => {
        renderComponent({
          dataset: makeDraftDataset({
            name: 'AB',
            pipelines: [{ id: 'p1', name: 'Pipeline 1' }],
          }),
          groupCount: 1,
          roleCount: 1,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).toHaveAttribute('aria-disabled', 'true')
      })

      it('when description is empty', async () => {
        renderComponent({
          dataset: makeDraftDataset({
            description: '',
            namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
          }),
          groupCount: 1,
          roleCount: 1,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).toHaveAttribute('aria-disabled', 'true')
      })
    })

    describe('READY and AVAILABLE are enabled when canStage is true', () => {
      it('when pipelines present, assignments set, and form passes strict schema', async () => {
        renderComponent({
          dataset: makeDraftDataset({ pipelines: [{ id: 'p1', name: 'Pipeline 1' }] }),
          groupCount: 1,
          roleCount: 1,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).not.toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).not.toHaveAttribute('aria-disabled', 'true')
      })

      it('when namedApis present, assignments set, and form passes strict schema', async () => {
        renderComponent({
          dataset: makeDraftDataset({ namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }] }),
          groupCount: 1,
          roleCount: 1,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).not.toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).not.toHaveAttribute('aria-disabled', 'true')
      })

      it('when distribution present and form valid but no assignments (assignments not required)', async () => {
        renderComponent({
          dataset: makeDraftDataset({ namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }] }),
          groupCount: 0,
          roleCount: 0,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).not.toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).not.toHaveAttribute('aria-disabled', 'true')
      })

      it('becomes enabled when description is filled in edit mode', async () => {
        renderComponent({
          dataset: makeDraftDataset({
            description: '',
            namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
          }),
          groupCount: 1,
          roleCount: 1,
        })
        clickEditButton()
        await openStatusDropdown()
        expect(getStatusOption('READY')).toHaveAttribute('aria-disabled', 'true')

        await userEvent.keyboard('{Escape}')
        await act(async () => {
          fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: 'Now it has a description' } })
        })

        await openStatusDropdown()
        expect(getStatusOption('READY')).not.toHaveAttribute('aria-disabled', 'true')
        expect(getStatusOption('AVAILABLE')).not.toHaveAttribute('aria-disabled', 'true')
      })
    })

    describe('auto-reverts status to DRAFT and shows toast when canStage becomes false', () => {
      it.each([{ dataSetStatus: 'READY' as const }, { dataSetStatus: 'AVAILABLE' as const }])(
        'keeps $dataSetStatus when assignments drop to 0 (assignments not required)',
        async ({ dataSetStatus }) => {
          const { rerender } = renderComponent({
            dataset: makeDraftDataset({
              dataSetStatus,
              namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
            }),
            groupCount: 1,
            roleCount: 1,
          })

          expect(screen.getByTestId('statusDropdown')).toHaveTextContent(dataSetStatus)

          await act(async () => {
            rerender(
              <DatasetOverview
                dataset={makeDraftDataset({
                  dataSetStatus,
                  namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
                })}
                groupCount={0}
                roleCount={0}
                datapoolOptions={defaultProps.datapoolOptions}
              />,
            )
          })

          expect(screen.getByTestId('statusDropdown')).toHaveTextContent(dataSetStatus)
          expect(toast.info).not.toHaveBeenCalled()
        },
      )

      it('reverts from READY to DRAFT and shows toast when description is removed', async () => {
        const { rerender } = renderComponent({
          dataset: makeDraftDataset({
            dataSetStatus: 'READY',
            description: 'A meaningful description',
            namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
          }),
          groupCount: 1,
          roleCount: 1,
        })

        expect(screen.getByTestId('statusDropdown')).toHaveTextContent('READY')

        rerender(
          <DatasetOverview
            dataset={makeDraftDataset({
              dataSetStatus: 'READY',
              description: '',
              namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
            })}
            groupCount={1}
            roleCount={1}
            datapoolOptions={defaultProps.datapoolOptions}
          />,
        )

        expect(screen.getByTestId('statusDropdown')).toHaveTextContent('DRAFT')
        expect(toast.info).toHaveBeenCalledWith('info.switchMode')
      })
    })
  })

  describe('Save uses pickDirtyValues: only changed fields are sent', () => {
    it('calls mockPatchDataset with only changed fields (name) when only name is modified', async () => {
      renderComponent()
      clickEditButton()

      await act(async () => {
        fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      })

      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockPatchDataset).toHaveBeenCalledWith(expect.objectContaining({ name: 'Updated Name' }))
      })
    })

    it('calls mockPatchDataset with only changed fields (description) when only description is modified', async () => {
      renderComponent()
      clickEditButton()

      fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: 'New description text' } })
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockPatchDataset).toHaveBeenCalledWith(expect.objectContaining({ description: 'New description text' }))
      })
    })

    it('calls stageDataset when status changes from DRAFT to READY', async () => {
      renderComponent({
        dataset: makeDraftDataset({ namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }] }),
        groupCount: 1,
        roleCount: 1,
      })
      clickEditButton()

      await openStatusDropdown()
      await userEvent.click(getStatusOption('READY'))
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockStageDataset).toHaveBeenCalledWith('test-id')
      })
    })

    it('calls unstageDataset when status changes from READY to DRAFT', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'READY',
          namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      clickEditButton()

      await openStatusDropdown()
      await userEvent.click(getStatusOption('DRAFT'))
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockUnstageDataset).toHaveBeenCalledWith('test-id')
      })
    })

    it('calls unreleaseDataset when status changes from AVAILABLE to READY', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'AVAILABLE',
          namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      clickEditButton()

      await openStatusDropdown()
      await userEvent.click(getStatusOption('READY'))
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockUnreleaseDataset).toHaveBeenCalledWith('test-id')
      })
    })

    it('calls unreleaseDataset and unstageDataset when status changes from AVAILABLE to DRAFT', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'AVAILABLE',
          namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      clickEditButton()

      await openStatusDropdown()
      await userEvent.click(getStatusOption('DRAFT'))
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockUnreleaseDataset).toHaveBeenCalledWith('test-id')
        expect(mockUnstageDataset).toHaveBeenCalledWith('test-id')
      })
    })

    it('calls updateReleasedDatasetMeta when saving form changes on an AVAILABLE dataset', async () => {
      renderComponent({
        dataset: makeDraftDataset({
          dataSetStatus: 'AVAILABLE',
          namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }],
        }),
        groupCount: 1,
        roleCount: 1,
      })
      clickEditButton()

      await act(async () => {
        fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      })

      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(mockUpdateReleasedDatasetMeta).toHaveBeenCalledWith(
          expect.objectContaining({ name: 'Updated Name', id: 'test-id' }),
        )
      })
    })
  })

  describe('Save error handling: scope-violation vs. generic', () => {
    const axios422 = (type?: string) =>
      new AxiosError('request failed', undefined, undefined, undefined, {
        status: 422,
        statusText: '',
        headers: {},
        config: {} as never,
        data: { detail: 'scope violation', type },
      })

    it('shows the scope-violation toast when the save fails with a DATASOURCE_SCOPE_VIOLATION 422', async () => {
      mockPatchDataset.mockRejectedValueOnce(axios422('urn:civitas:error:DATASOURCE_SCOPE_VIOLATION'))
      renderComponent()
      clickEditButton()

      await act(async () => {
        fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      })
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('messages.datasourceScopeViolation')
      })
      expect(toast.error).not.toHaveBeenCalledWith('messages.transitionError')
    })

    it('shows the generic transition-error toast for any other save failure', async () => {
      mockPatchDataset.mockRejectedValueOnce(new Error('boom'))
      renderComponent()
      clickEditButton()

      await act(async () => {
        fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Updated Name' } })
      })
      fireEvent.submit(screen.getByTestId('datasetBaseInfoForm'))

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalledWith('messages.transitionError')
      })
      expect(toast.error).not.toHaveBeenCalledWith('messages.datasourceScopeViolation')
    })
  })

  describe('Exit and discard: form and status reset', () => {
    it('shows ExitWarningModal when Cancel is clicked with unsaved changes', async () => {
      renderComponent()
      clickEditButton()

      await act(async () => {
        fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Changed Name' } })
      })

      fireEvent.click(screen.getByTestId('cancelButton'))

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('exits to read-only mode without modal when no unsaved changes', async () => {
      renderComponent()
      clickEditButton()

      fireEvent.click(screen.getByTestId('cancelButton'))

      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('discarding resets form values and status to original dataset values and clears exit modal', async () => {
      renderComponent({
        dataset: makeDraftDataset({ namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }] }),
      })
      clickEditButton()

      await openStatusDropdown()
      await userEvent.click(getStatusOption('READY'))

      expect(screen.getByTestId('statusDropdown')).toHaveTextContent('READY')

      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('discardButton'))

      expect(screen.getByTestId('statusDropdown')).toHaveTextContent('DRAFT')
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
    })

    it('save-and-exit from modal calls mockStageDataset and closes modal on success', async () => {
      renderComponent({
        dataset: makeDraftDataset({ namedApis: [{ id: 'a1', name: 'My API', slug: 'my-api', standard: 'STA' }] }),
      })

      clickEditButton()
      await openStatusDropdown()
      await userEvent.click(getStatusOption('READY'))
      fireEvent.click(screen.getByTestId('cancelButton'))

      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('saveButton'))

      expect(mockStageDataset).toHaveBeenCalledWith('test-id')
      await waitFor(() => {
        expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      })
    })
  })

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
