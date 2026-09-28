import { render, screen, waitFor } from '@testing-library/react'
import userEvent, { UserEvent } from '@testing-library/user-event'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Datastructure, DATASTRUCTURE_STATUS_TYPES, DatastructureVersion } from '@/types/datastructures'

import { VersionOverview } from './VersionOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

let mockSearchParams = new URLSearchParams('mode=edit')
const mockPush = vi.fn()
const mockRefresh = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    refresh: mockRefresh,
  }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/datastructures/test-id/versions/v-1',
}))

let mockSubTabValue = 'versionInfo'

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: vi.fn(),
    subTabValue: mockSubTabValue,
  }),
}))

const mockUpdateMutateAsync = vi.fn()
const mockCreateMutateAsync = vi.fn()
const mockStatusUpdateMutateAsync = vi.fn()

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useCreateDatastructureVersion: () => ({
    mutateAsync: mockCreateMutateAsync,
    isPending: false,
  }),
  useUpdateDatastructureVersion: () => ({
    mutateAsync: mockUpdateMutateAsync,
    isPending: false,
  }),
  useStatusUpdateDatastructureVersion: () => ({
    mutateAsync: mockStatusUpdateMutateAsync,
    isPending: false,
  }),
}))

const mockDatastructure: Datastructure = {
  id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
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
  modelName: null,
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  model: null,
  styles: null,
  dataStructure: mockDatastructure,
}

const mockVersionWithModel: DatastructureVersion = {
  ...mockVersion,
  modelName: 'Test Model',
  styles: {
    id: 'diagram-1',
    name: 'Test Model',
    nodes: [
      {
        id: 'n-1',
        type: 'class',
        position: { x: 100, y: 100 },
        data: {
          element: {
            id: 'element-1',
            name: 'TestClass',
            type: 'class',
            attributes: [],
            operations: [],
          },
          label: 'TestClass',
        },
      },
    ],
    edges: [],
    lastModified: new Date('2024-01-01'),
    isDirty: false,
  },
}

const setCurrentUserPermissions = (permissions: PermissionName[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test-user-1',
      email: 'test.user1@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType: 'TENANT', scopeId: null, permissions }],
    },
  } as ReturnType<typeof useGetCurrentUser>)
}

describe('VersionOverview - hasUserChanges Modal', () => {
  const defaultProps = {
    title: 'Edit Version',
    datastructure: mockDatastructure,
    version: mockVersion,
    isCreateMode: false,
    testId: 'version-overview',
  }

  const openStatusDropdown = async (user: UserEvent) => {
    const trigger = screen.getByTestId('statusDropdown')
    await user.click(trigger)
    await waitFor(() => {
      expect(trigger).toHaveAttribute('data-state', 'open')
    })
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockPush.mockReset()
    mockRefresh.mockReset()
    mockUpdateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockCreateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockStatusUpdateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockSearchParams = new URLSearchParams('mode=edit')
    mockSubTabValue = 'versionInfo'
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

    it('renders tab navigation with structure tab first', () => {
      renderComponent()
      const tabs = screen.getAllByRole('tab')
      expect(tabs[0]).toHaveAttribute('data-testid', 'tab-structure')
      expect(tabs[1]).toHaveAttribute('data-testid', 'tab-versionInfo')
    })

    it('defaults to the structure tab when no subTabValue is set', () => {
      mockSubTabValue = ''
      renderComponent()
      expect(screen.getByTestId('umlModeler')).toBeInTheDocument()
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

  describe('Edit button permissions gating when AVAILABLE', () => {
    const availableVersion: DatastructureVersion = {
      ...mockVersion,
      dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
    }

    it('shows Edit button when user has DATASTRUCTURE_UPDATE and DATASTRUCTURE_RELEASE', async () => {
      mockSearchParams = new URLSearchParams()
      setCurrentUserPermissions([PERMISSION_NAMES.DATASTRUCTURE_UPDATE, PERMISSION_NAMES.DATASTRUCTURE_RELEASE])

      renderComponent({ version: availableVersion })
      await waitFor(() => {
        expect(screen.getByTestId('editButton')).toBeInTheDocument()
      })
    })

    it('hides Edit button when user has only DATASTRUCTURE_UPDATE or DATASTRUCTURE_RELEASE', async () => {
      mockSearchParams = new URLSearchParams()

      for (const permissions of [[PERMISSION_NAMES.DATASTRUCTURE_UPDATE], [PERMISSION_NAMES.DATASTRUCTURE_RELEASE]]) {
        setCurrentUserPermissions(permissions)

        renderComponent({ version: availableVersion })
        await waitFor(() => {
          expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
        })
      }
    })

    it('hides Edit button when user has neither DATASTRUCTURE_UPDATE nor DATASTRUCTURE_RELEASE', async () => {
      mockSearchParams = new URLSearchParams()
      setCurrentUserPermissions([])

      renderComponent({ version: availableVersion })
      await waitFor(() => {
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      })
    })
  })

  describe('Edit button permissions gating when DRAFT', () => {
    it('shows Edit button when user has DATASTRUCTURE_UPDATE (DATASTRUCTURE_RELEASE not required)', () => {
      mockSearchParams = new URLSearchParams()
      setCurrentUserPermissions([PERMISSION_NAMES.DATASTRUCTURE_UPDATE])

      renderComponent()
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
    })

    it('hides Edit button when user lacks DATASTRUCTURE_UPDATE', () => {
      mockSearchParams = new URLSearchParams()
      setCurrentUserPermissions([])

      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
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
      expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
    })

    it('switches to edit mode when edit button is clicked', async () => {
      const user = userEvent.setup()
      mockSearchParams = new URLSearchParams()
      renderComponent()

      await user.click(screen.getByTestId('editButton'))

      await waitFor(() => {
        expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
        expect(screen.getByTestId('confirmButton')).toBeInTheDocument()
      })
    })

    it('version field is disabled in read-only mode', () => {
      mockSearchParams = new URLSearchParams()
      renderComponent()

      expect(screen.getByTestId('versionTextField')).toBeDisabled()
    })

    it('version field stays read-only in edit mode', () => {
      renderComponent()

      // The registry assigns the version on store, so the field only ever displays it.
      expect(screen.getByTestId('versionTextField')).toBeDisabled()
    })
  })

  describe('completedTabs', () => {
    it('does not mark versionInfo tab as completed in create mode with empty fields', () => {
      renderComponent({ version: null, isCreateMode: true })

      const versionInfoTab = screen.getByTestId('tab-versionInfo')
      expect(versionInfoTab.querySelector('svg')).not.toHaveClass('text-green-600')
    })

    it('marks versionInfo tab as completed after filling version and description', async () => {
      const user = userEvent.setup()
      renderComponent({ version: null, isCreateMode: true })

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'A description')

      await waitFor(() => {
        const versionInfoTab = screen.getByTestId('tab-versionInfo')
        expect(versionInfoTab.querySelector('svg')).toHaveClass('text-green-600')
      })
    })

    it('does not mark structure tab as completed when there is no datastructure defined', () => {
      renderComponent()

      const structureTab = screen.getByTestId('tab-structure')
      expect(structureTab.querySelector('svg')).not.toHaveClass('text-green-600')
    })

    it('marks structure tab as completed when version has a data structure', async () => {
      renderComponent({ version: mockVersionWithModel })

      await waitFor(() => {
        const structureTab = screen.getByTestId('tab-structure')
        expect(structureTab.querySelector('svg')).toHaveClass('text-green-600')
      })
    })
  })

  describe('canSetDraft logic', () => {
    const inUseByReleasedVersion: DatastructureVersion = {
      ...mockVersion,
      dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      inUse: true,
      inUseByReleased: true,
    }

    it('keeps DRAFT selectable when a released entity references the version', async () => {
      const user = userEvent.setup()
      renderComponent({ version: inUseByReleasedVersion })

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-draft')).not.toHaveAttribute('data-disabled')
    })

    it('refuses the DRAFT selection and explains why when a released entity references the version', async () => {
      const user = userEvent.setup()
      renderComponent({ version: inUseByReleasedVersion })

      await openStatusDropdown(user)
      await user.click(screen.getByTestId('statusOption-draft'))

      expect(await screen.findByTestId('infoModal')).toBeInTheDocument()
      expect(screen.getByTestId('statusDropdown')).toHaveTextContent('Verfügbar')
    })

    it('keeps DRAFT selectable when only a draft entity references the version', async () => {
      const user = userEvent.setup()
      const draftReferencedVersion: DatastructureVersion = { ...mockVersion, inUse: true, inUseByReleased: false }
      renderComponent({ version: draftReferencedVersion })

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-draft')).not.toHaveAttribute('data-disabled')
    })

    it('disables DRAFT option when this is the last available version in an available datastructure', async () => {
      const user = userEvent.setup()
      const availableDatastructure: Datastructure = {
        ...mockDatastructure,
        dataStructureStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
        dataStructureVersions: [],
      }
      renderComponent({ datastructure: availableDatastructure })

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-draft')).toHaveAttribute('data-disabled')
    })

    it('enables DRAFT option when the version has no released referrer and is not the last available version', async () => {
      const user = userEvent.setup()
      renderComponent()

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-draft')).not.toHaveAttribute('data-disabled')
    })
  })

  describe('canStage logic', () => {
    it('disables AVAILABLE option when there is no datastructure defined', async () => {
      const user = userEvent.setup()
      renderComponent()

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-available')).toHaveAttribute('data-disabled')
    })

    it('disables AVAILABLE option when user lacks DATASTRUCTURE_RELEASE permission', async () => {
      const user = userEvent.setup()
      setCurrentUserPermissions([PERMISSION_NAMES.DATASTRUCTURE_UPDATE])
      renderComponent()

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-available')).toHaveAttribute('data-disabled')
    })

    it('enables AVAILABLE option when all tabs are completed', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-available')).not.toHaveAttribute('data-disabled')
    })
  })

  describe('required field validation per status', () => {
    const selectAvailableStatus = async (user: UserEvent) => {
      await openStatusDropdown(user)
      await user.click(screen.getByTestId('statusOption-available'))
    }

    it('marks the description as invalid when it is cleared while the status is AVAILABLE', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      await selectAvailableStatus(user)
      await user.clear(screen.getByTestId('descriptionTextArea'))

      await waitFor(() => {
        expect(screen.getByTestId('descriptionTextArea')).toHaveAttribute('aria-invalid', 'true')
      })
      expect(screen.getByText(messages.common.errors.required)).toBeInTheDocument()
    })

    it('keeps the description valid when it is cleared while the status is DRAFT', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      await user.clear(screen.getByTestId('descriptionTextArea'))

      expect(screen.getByTestId('descriptionTextArea')).toHaveAttribute('aria-invalid', 'false')
      expect(screen.queryByText(messages.common.errors.required)).not.toBeInTheDocument()
    })

    it('disables the save button while a required field is empty at status AVAILABLE', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      await selectAvailableStatus(user)
      expect(screen.getAllByTestId('confirmButton')[0]).toBeEnabled()

      await user.clear(screen.getByTestId('descriptionTextArea'))

      await waitFor(() => {
        expect(screen.getAllByTestId('confirmButton')[0]).toBeDisabled()
      })
    })

    it('keeps the save button enabled with an empty description at status DRAFT', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      await user.clear(screen.getByTestId('descriptionTextArea'))

      expect(screen.getAllByTestId('confirmButton')[0]).toBeEnabled()
    })
  })

  describe('save button', () => {
    it('is disabled when no values have been changed', () => {
      renderComponent()

      const confirmButton = screen.getAllByTestId('confirmButton')[0]
      expect(confirmButton).toBeDisabled()
    })

    it('is enabled after a value has been changed', async () => {
      const user = userEvent.setup()
      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.clear(descriptionInput)
      await user.type(descriptionInput, 'An edited description')

      await waitFor(() => {
        expect(screen.getAllByTestId('confirmButton')[0]).not.toBeDisabled()
      })
    })

    it('calls update API when save is clicked after changes in edit mode', async () => {
      const user = userEvent.setup()
      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.clear(descriptionInput)
      await user.type(descriptionInput, 'An edited description')

      const confirmButtons = screen.getAllByTestId('confirmButton')
      await user.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}`,
          }),
        )
      })
    })

    it('sends nothing but the status change for an AVAILABLE version', async () => {
      const user = userEvent.setup()
      const availableVersionWithModel = {
        ...mockVersionWithModel,
        dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      }
      renderComponent({ version: availableVersionWithModel })

      expect(screen.getByTestId('versionTextField')).toBeDisabled()
      expect(screen.getByTestId('descriptionTextArea')).toBeDisabled()

      await openStatusDropdown(user)
      await user.click(screen.getByTestId('statusOption-draft'))
      const confirmButtons = screen.getAllByTestId('confirmButton')
      await user.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockStatusUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}/unrelease`,
          }),
        )
      })
      expect(mockUpdateMutateAsync).not.toHaveBeenCalled()
    })

    it('calls create API when save is clicked in create mode', async () => {
      const user = userEvent.setup()
      renderComponent({ version: null, isCreateMode: true })

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'Some description')

      const confirmButtons = screen.getAllByTestId('confirmButton')
      await user.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockCreateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions`,
          }),
        )
      })
    })

    it('calls the publish API when status is set from DRAFT to AVAILABLE', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      await openStatusDropdown(user)

      await user.click(screen.getByTestId('statusOption-available'))
      const confirmButtons = screen.getAllByTestId('confirmButton')
      await user.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockStatusUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}/release`,
          }),
        )
      })
    })

    it('calls the unpublish API when status is set from AVAILABLE to DRAFT', async () => {
      const user = userEvent.setup()
      const availableVersionWithModel = {
        ...mockVersionWithModel,
        dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      }
      renderComponent({ version: availableVersionWithModel })

      await openStatusDropdown(user)

      await user.click(screen.getByTestId('statusOption-draft'))
      const confirmButtons = screen.getAllByTestId('confirmButton')
      await user.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockStatusUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}/unrelease`,
          }),
        )
      })
    })
  })

  describe('exit behavior - edit mode', () => {
    it('navigates back to the datastructure when exit is clicked in edit mode without changes', async () => {
      const user = userEvent.setup()

      renderComponent()

      const cancelButtons = screen.getAllByTestId('cancelButton')
      await user.click(cancelButtons[0])

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('shows ExitWarningModal when version field is changed', async () => {
      const user = userEvent.setup()

      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.clear(descriptionInput)
      await user.type(descriptionInput, 'An edited description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      await user.click(exitButton)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })
    })

    it('shows ExitWarningModal when description field is changed', async () => {
      const user = userEvent.setup()

      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'New description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      await user.click(exitButton)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })
    })

    it('navigates back to the datastructure when discard button in ExitWarningModal is clicked', async () => {
      const user = userEvent.setup()

      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.clear(descriptionInput)
      await user.type(descriptionInput, 'An edited description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      await user.click(exitButton)

      await waitFor(async () => {
        const discardButton = screen.getByRole('button', { name: /discard|verwerfen/i })
        await user.click(discardButton)
      })

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('saves and navigates back to the datastructure when save is clicked in ExitWarningModal', async () => {
      const user = userEvent.setup()

      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.clear(descriptionInput)
      await user.type(descriptionInput, 'An edited description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      await user.click(cancelButtons[0])

      await waitFor(async () => {
        expect(screen.getByTestId('saveButton')).toBeInTheDocument()
        await user.click(screen.getByTestId('saveButton'))
      })

      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}`,
          }),
        )
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('does not navigate away and closes the modal when save fails in ExitWarningModal', async () => {
      const user = userEvent.setup()
      mockUpdateMutateAsync.mockRejectedValueOnce(new Error('Request failed'))

      renderComponent()

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.clear(descriptionInput)
      await user.type(descriptionInput, 'An edited description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      await user.click(cancelButtons[0])

      await waitFor(async () => {
        expect(screen.getByTestId('saveButton')).toBeInTheDocument()
        await user.click(screen.getByTestId('saveButton'))
      })

      await waitFor(() => {
        expect(mockUpdateMutateAsync).toHaveBeenCalled()
      })

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      })
      expect(mockPush).not.toHaveBeenCalled()
    })
  })

  describe('exit behavior - create mode', () => {
    it('shows ExitWarningModal when a field is filled in create mode', async () => {
      const user = userEvent.setup()

      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'A description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      await user.click(exitButton)

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument()
      })
    })

    it('navigates back to the datastructure when exit is clicked without changes', async () => {
      const user = userEvent.setup()

      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      await user.click(cancelButtons[0])

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('navigates back to the datastructure when discard is clicked in ExitWarningModal', async () => {
      const user = userEvent.setup()

      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'A description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      await user.click(cancelButtons[0])

      await waitFor(async () => {
        const discardButton = screen.getByRole('button', { name: /discard|verwerfen/i })
        await user.click(discardButton)
      })

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith(`/datastructures/${mockDatastructure.id}`)
      })
    })

    it('saves and navigates back to the datastructure when save is clicked in ExitWarningModal', async () => {
      const user = userEvent.setup()

      renderComponent({ version: null, isCreateMode: true })

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'Some description')

      const cancelButtons = screen.getAllByTestId('cancelButton')
      await user.click(cancelButtons[0])

      await waitFor(async () => {
        expect(screen.getByTestId('saveButton')).toBeInTheDocument()
        await user.click(screen.getByTestId('saveButton'))
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

  describe('save button after model changes', () => {
    beforeEach(() => {
      mockSubTabValue = 'structure'
    })

    const versionWithSelectedNode: DatastructureVersion = {
      ...mockVersionWithModel,
      styles: {
        ...mockVersionWithModel.styles!,
        nodes: [{ ...mockVersionWithModel.styles!.nodes[0], selected: true }],
      },
    }

    it('enables the save button after a class was renamed in the inspector', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionWithSelectedNode })

      expect(screen.getByTestId('confirmButton')).toBeDisabled()

      await user.type(screen.getByPlaceholderText('Element name'), 'X')

      await waitFor(() => expect(screen.getByTestId('confirmButton')).toBeEnabled())
    })
  })
})
