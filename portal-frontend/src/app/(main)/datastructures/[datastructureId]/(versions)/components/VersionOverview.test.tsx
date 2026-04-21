import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES } from '@/types/currentUser'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DATASTRUCTURE_VERSION_SOURCE,
  DatastructureVersion,
} from '@/types/datastructures'

import { VersionOverview } from './VersionOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

let mockSearchParams = new URLSearchParams('mode=edit')
const mockPush = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/datastructures/test-id/versions/v-1',
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: vi.fn(),
    subTabValue: 'versionInfo',
  }),
}))

vi.mock('@/components/uml-modeler/hooks/use-multi-session-manager', () => ({
  useMultiSessionManager: ({ initialSession }: { initialSession: { id: string } | null }) => ({
    activeSession: null,
    activeSessionId: initialSession?.id ?? null,
    setSession: vi.fn(),
    markSessionDirty: vi.fn(),
  }),
}))

const mockUpdateMutateAsync = vi.fn()
const mockCreateMutateAsync = vi.fn()

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useCreateDatastructureVersion: () => ({
    mutateAsync: mockCreateMutateAsync,
    isPending: false,
  }),
  useUpdateDatastructureVersion: () => ({
    mutateAsync: mockUpdateMutateAsync,
    isPending: false,
  }),
  useUpdateDatastructureVersionPublished: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useStatusUpdateDatastructureVersion: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}))

const mockDatastructure: Datastructure = {
  id: 'ds-1',
  name: 'Test Datastructure',
  description: 'Test Description',
  dataStructureStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
  dataStructureVersions: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  createdFromDataSource: false,
}

const mockVersion: DatastructureVersion = {
  id: 'v-1',
  version: '1.0.0',
  description: 'Version 1.0',
  dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
  dataStructureVersionSource: 'OWN',
  inUse: false,
  modelAtlasUri: null,
  modelName: null,
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  model: null,
  styles: null,
  dataStructure: mockDatastructure,
}

describe('VersionOverview - hasUserChanges Modal', () => {
  const defaultProps = {
    title: 'Edit Version',
    datastructure: mockDatastructure,
    version: mockVersion,
    isCreateMode: false,
    testId: 'version-overview',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockPush.mockReset()
    mockUpdateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockCreateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockSearchParams = new URLSearchParams('mode=edit')
    vi.mocked(useGetCurrentUser).mockReturnValue({
      data: {
        username: 'test',
        email: 'test@test.com',
        title: 'MR' as const,
        firstName: 'Test',
        lastName: 'User',
        assignments: [
          {
            scopeType: 'TENANT',
            scopeId: null,
            permissions: [PERMISSION_NAMES.DATASTRUCTURE_UPDATE, PERMISSION_NAMES.DATASTRUCTURE_RELEASE],
          },
        ],
      },
    } as ReturnType<typeof useGetCurrentUser>)
  })

  const renderComponent = (props = {}) => {
    return render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <VersionOverview {...defaultProps} {...props} />
      </NextIntlClientProvider>,
    )
  }

  describe('rendering', () => {
    it('renders the version overview component with title', () => {
      renderComponent()
      expect(screen.getByTestId('version-overview')).toBeInTheDocument()
      expect(screen.getByText('Edit Version')).toBeInTheDocument()
    })

    it('renders tab navigation', () => {
      renderComponent()
      expect(screen.getByTestId('tab-versionInfo')).toBeInTheDocument()
      expect(screen.getByTestId('tab-structure')).toBeInTheDocument()
    })
  })

  describe('tab switching', () => {
    it('both tabs are rendered and clickable', () => {
      renderComponent()

      const versionInfoTab = screen.getByTestId('tab-versionInfo')
      const structureTab = screen.getByTestId('tab-structure')

      expect(versionInfoTab).toBeInTheDocument()
      expect(structureTab).toBeInTheDocument()
      expect(versionInfoTab).toBeEnabled()
      expect(structureTab).toBeEnabled()
    })
  })

  describe('form fields are editable', () => {
    it('version input can be changed', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      await waitFor(() => {
        expect(versionInput.value).toBe('2.0.0')
      })
    })

    it('description input can be changed', async () => {
      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      fireEvent.change(descriptionInput, { target: { value: 'Updated description' } })

      await waitFor(() => {
        expect(descriptionInput.value).toBe('Updated description')
      })
    })

    it('multiple fields can be changed together', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement

      fireEvent.change(versionInput, { target: { value: '2.0.0' } })
      fireEvent.change(descriptionInput, { target: { value: 'New description' } })

      await waitFor(() => {
        expect(versionInput.value).toBe('2.0.0')
        expect(descriptionInput.value).toBe('New description')
      })
    })
  })

  describe('Edit button gating when AVAILABLE', () => {
    const availableVersion: DatastructureVersion = {
      ...mockVersion,
      dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
    }

    it('shows Edit button when AVAILABLE and user has both UPDATE and RELEASE', () => {
      mockSearchParams = new URLSearchParams()
      vi.mocked(useGetCurrentUser).mockReturnValue({
        data: {
          username: 'test',
          email: 'test@test.com',
          title: 'MR' as const,
          firstName: 'Test',
          lastName: 'User',
          assignments: [
            {
              scopeType: 'TENANT',
              scopeId: null,
              permissions: [PERMISSION_NAMES.DATASTRUCTURE_UPDATE, PERMISSION_NAMES.DATASTRUCTURE_RELEASE],
            },
          ],
        },
      } as ReturnType<typeof useGetCurrentUser>)

      renderComponent({ version: availableVersion })
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when AVAILABLE and user has UPDATE but lacks RELEASE', () => {
      mockSearchParams = new URLSearchParams()
      vi.mocked(useGetCurrentUser).mockReturnValue({
        data: {
          username: 'test',
          email: 'test@test.com',
          title: 'MR' as const,
          firstName: 'Test',
          lastName: 'User',
          assignments: [
            {
              scopeType: 'TENANT',
              scopeId: null,
              permissions: [PERMISSION_NAMES.DATASTRUCTURE_UPDATE],
            },
          ],
        },
      } as ReturnType<typeof useGetCurrentUser>)

      renderComponent({ version: availableVersion })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
    })
  })

  describe('save button', () => {
    it('calls update API when save is clicked after changes in edit mode', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      const confirmButtons = screen.getAllByTestId('confirmButton')
      fireEvent.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}`,
          }),
        )
      })
    })

    it('calls create API when save is clicked in create mode', async () => {
      renderComponent({ version: null, isCreateMode: true })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      fireEvent.change(versionInput, { target: { value: '1.0.0' } })
      fireEvent.change(descriptionInput, { target: { value: 'Some description' } })

      const confirmButtons = screen.getAllByTestId('confirmButton')
      fireEvent.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions`,
          }),
        )
      })
    })
  })

  describe('exit behavior - edit mode', () => {
    it('switches to read only view when exit is clicked in edit mode without changes', async () => {
      renderComponent()

      const cancelButtons = screen.getAllByTestId('cancelButton')
      fireEvent.click(cancelButtons[0])

      await waitFor(() => {
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
        expect(mockPush).not.toHaveBeenCalled()
      })
    })

    it('shows ExitWarningModal when version field is changed', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      fireEvent.click(exitButton)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })
    })

    it('shows ExitWarningModal when description field is changed', async () => {
      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      fireEvent.change(descriptionInput, { target: { value: 'New description' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      fireEvent.click(exitButton)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })
    })

    it('resets form when discard button in ExitWarningModal is clicked', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const initialValue = versionInput.value

      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      fireEvent.click(exitButton)

      await waitFor(() => {
        const discardButton = screen.getByRole('button', { name: /discard|verwerfen/i })
        fireEvent.click(discardButton)
      })

      await waitFor(() => {
        expect(versionInput.value).toBe(initialValue)
      })
    })

    it('saves when save is clicked in ExitWarningModal', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      fireEvent.click(cancelButtons[0])

      await waitFor(() => {
        expect(screen.getByTestId('saveButton')).toBeInTheDocument()
        fireEvent.click(screen.getByTestId('saveButton'))
      })

      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}`,
          }),
        )
        expect(mockPush).not.toHaveBeenCalled()
      })
    })
  })

  describe('edit/read-only toggle', () => {
    it('renders edit button in read-only mode when mode param is absent', () => {
      mockSearchParams = new URLSearchParams()
      renderComponent()

      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryAllByTestId('confirmButton')).toHaveLength(0)
    })

    it('renders action buttons in edit mode', () => {
      renderComponent()

      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getAllByTestId('confirmButton').length).toBeGreaterThan(0)
    })

    it('switches to edit mode when edit button is clicked', async () => {
      mockSearchParams = new URLSearchParams()
      renderComponent()

      fireEvent.click(screen.getByTestId('editButton'))

      await waitFor(() => {
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
        expect(screen.getAllByTestId('confirmButton').length).toBeGreaterThan(0)
      })
    })

    it('version field is disabled in read-only mode', () => {
      mockSearchParams = new URLSearchParams()
      renderComponent()

      expect(screen.getByTestId('versionTextField')).toBeDisabled()
    })

    it('version field is enabled in edit mode', () => {
      renderComponent()

      expect(screen.getByTestId('versionTextField')).not.toBeDisabled()
    })
  })

  describe('canSetDraft logic', () => {
    const openStatusDropdown = async () => {
      const trigger = screen.getByTestId('statusDropdown')
      fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false })
      await waitFor(() => {
        expect(trigger).toHaveAttribute('data-state', 'open')
      })
    }

    it('disables DRAFT option when the version is in use', async () => {
      const inUseVersion: DatastructureVersion = { ...mockVersion, inUse: true }
      renderComponent({ version: inUseVersion })

      await openStatusDropdown()

      expect(screen.getByTestId('statusOption-draft')).toHaveAttribute('data-disabled')
    })

    it('disables DRAFT option when this is the last available version in an available datastructure', async () => {
      const availableDatastructure: Datastructure = {
        ...mockDatastructure,
        dataStructureStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
        dataStructureVersions: [],
      }
      renderComponent({ datastructure: availableDatastructure })

      await openStatusDropdown()

      expect(screen.getByTestId('statusOption-draft')).toHaveAttribute('data-disabled')
    })

    it('enables DRAFT option when version is not in use and datastructure is DRAFT', async () => {
      renderComponent()

      await openStatusDropdown()

      expect(screen.getByTestId('statusOption-draft')).not.toHaveAttribute('data-disabled')
    })
  })

  describe('versionAlreadyExistsError', () => {
    const otherVersionSummary = {
      id: 'v-2',
      version: '2.0.0',
      description: 'Other version',
      dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.DRAFT,
      dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
      createdAt: '2024-01-01',
      modifiedAt: '2024-01-01',
      dataStructureId: 'ds-1',
    }

    it('shows an error when the entered version already exists', async () => {
      const datastructureWithOtherVersion: Datastructure = {
        ...mockDatastructure,
        dataStructureVersions: [otherVersionSummary],
      }
      renderComponent({ datastructure: datastructureWithOtherVersion })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      await waitFor(() => {
        expect(screen.getByTestId('versionManualFormMessage')).toBeInTheDocument()
      })
    })

    it('shows no error when the entered version is unique', async () => {
      const datastructureWithOtherVersion: Datastructure = {
        ...mockDatastructure,
        dataStructureVersions: [otherVersionSummary],
      }
      renderComponent({ datastructure: datastructureWithOtherVersion })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '3.0.0' } })

      await waitFor(() => {
        expect(screen.queryByTestId('versionManualFormMessage')).not.toBeInTheDocument()
      })
    })
  })

  describe('completedTabs', () => {
    it('marks versionInfo tab as completed when version, description and source are filled', () => {
      renderComponent()

      const versionInfoTab = screen.getByTestId('tab-versionInfo')
      expect(versionInfoTab.querySelector('svg')).toHaveClass('text-green-600')
    })

    it('does not mark structure tab as completed when there are no nodes or model', () => {
      renderComponent()

      const structureTab = screen.getByTestId('tab-structure')
      expect(structureTab.querySelector('svg')).not.toHaveClass('text-green-600')
    })

    it('does not mark versionInfo tab as completed in create mode with empty fields', () => {
      renderComponent({ version: null, isCreateMode: true })

      const versionInfoTab = screen.getByTestId('tab-versionInfo')
      expect(versionInfoTab.querySelector('svg')).not.toHaveClass('text-green-600')
    })

    it('marks versionInfo tab as completed after filling version and description', async () => {
      renderComponent({ version: null, isCreateMode: true })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      fireEvent.change(versionInput, { target: { value: '1.0.0' } })
      fireEvent.change(descriptionInput, { target: { value: 'A description' } })

      await waitFor(() => {
        const versionInfoTab = screen.getByTestId('tab-versionInfo')
        expect(versionInfoTab.querySelector('svg')).toHaveClass('text-green-600')
      })
    })
  })

  describe('exit behavior - create mode', () => {
    it('shows ExitWarningModal when version field is filled in create mode', async () => {
      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '1.0.0' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      fireEvent.click(exitButton)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })
    })

    it('navigates back to the datastructure when exit is clicked without changes', async () => {
      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      fireEvent.click(cancelButtons[0])

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('navigates back to the datastructure when discard is clicked in ExitWarningModal', async () => {
      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '1.0.0' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      fireEvent.click(cancelButtons[0])

      await waitFor(() => {
        const discardButton = screen.getByRole('button', { name: /discard|verwerfen/i })
        fireEvent.click(discardButton)
      })

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('saves and navigates back to the datastructure when save is clicked in ExitWarningModal', async () => {
      renderComponent({ version: null, isCreateMode: true })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      fireEvent.change(versionInput, { target: { value: '1.0.0' } })
      fireEvent.change(descriptionInput, { target: { value: 'Some description' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      fireEvent.click(cancelButtons[0])

      await waitFor(() => {
        expect(screen.getByTestId('saveButton')).toBeInTheDocument()
        fireEvent.click(screen.getByTestId('saveButton'))
      })

      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions`,
          }),
        )
        expect(mockPush).toHaveBeenCalled()
      })
    })
  })
})
