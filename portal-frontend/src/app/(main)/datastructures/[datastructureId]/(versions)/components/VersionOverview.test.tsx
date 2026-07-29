import { render, screen, waitFor } from '@testing-library/react'
import userEvent, { UserEvent } from '@testing-library/user-event'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { useMultiSessionManager } from '@/components/uml-modeler/hooks/use-multi-session-manager'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
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
const mockRefresh = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    refresh: mockRefresh,
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
  useMultiSessionManager: vi.fn(({ initialSession }: { initialSession: { id: string } | null }) => ({
    activeSession: null,
    activeSessionId: initialSession?.id ?? null,
    setSession: vi.fn(),
    markSessionDirty: vi.fn(),
  })),
}))

const mockUpdateMutateAsync = vi.fn()
const mockUpdateReleasedMutateAsync = vi.fn()
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
  useUpdateDatastructureVersionReleased: () => ({
    mutateAsync: mockUpdateReleasedMutateAsync,
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

const createModelSessionManagerMock = (version: DatastructureVersion) =>
  ({
    activeSession: {
      id: version.styles?.id ?? 'session-1',
      isDirty: false,
      diagram: version.styles!,
      dirtyFields: new Set(),
      lastModified: version.styles?.lastModified ?? new Date('2024-01-01'),
      created: version.styles?.lastModified ?? new Date('2024-01-01'),
      name: version.modelName ?? version.styles?.name ?? 'Test Model',
    },
    activeSessionId: version.styles?.id ?? 'session-1',
    setSession: vi.fn(),
    markSessionDirty: vi.fn(),
  }) as unknown as ReturnType<typeof useMultiSessionManager>

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
    mockUpdateReleasedMutateAsync.mockResolvedValue({ data: mockVersion })
    mockCreateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockStatusUpdateMutateAsync.mockResolvedValue({ data: mockVersion })
    mockSearchParams = new URLSearchParams('mode=edit')
    vi.mocked(useMultiSessionManager).mockReset()
    vi.mocked(useMultiSessionManager).mockImplementation(
      ({ initialSession }) =>
        ({
          activeSession: null,
          activeSessionId: initialSession?.id ?? null,
          setSession: vi.fn(),
          markSessionDirty: vi.fn(),
        }) as unknown as ReturnType<typeof useMultiSessionManager>,
    )
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

    it('version field is enabled in edit mode', () => {
      renderComponent()

      expect(screen.getByTestId('versionTextField')).not.toBeDisabled()
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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(versionInput, '1.0.0')
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
      vi.mocked(useMultiSessionManager).mockReturnValue(createModelSessionManagerMock(mockVersionWithModel))
      renderComponent({ version: mockVersionWithModel })

      await waitFor(() => {
        const structureTab = screen.getByTestId('tab-structure')
        expect(structureTab.querySelector('svg')).toHaveClass('text-green-600')
      })
    })
  })

  describe('canSetDraft logic', () => {
    it('disables DRAFT option when the version is in use', async () => {
      const user = userEvent.setup()
      const inUseVersion: DatastructureVersion = { ...mockVersion, inUse: true }
      renderComponent({ version: inUseVersion })

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-draft')).toHaveAttribute('data-disabled')
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

    it('enables DRAFT option when version is not in use and not the last available version', async () => {
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
      vi.mocked(useMultiSessionManager).mockReturnValue(createModelSessionManagerMock(mockVersionWithModel))
      renderComponent({ version: mockVersionWithModel })

      await openStatusDropdown(user)

      expect(screen.getByTestId('statusOption-available')).not.toHaveAttribute('data-disabled')
    })

    it('reverts status from AVAILABLE to DRAFT when the form does not meet the AVAILABLE schema', async () => {
      const availableVersionWithNoModel: DatastructureVersion = {
        ...mockVersion,
        dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
        modelName: null,
      }
      renderComponent({ version: availableVersionWithNoModel })

      await waitFor(() => {
        expect(screen.getByTestId('statusDropdown')).toHaveTextContent('Entwurf')
      })
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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

      await waitFor(() => {
        expect(screen.getAllByTestId('confirmButton')[0]).not.toBeDisabled()
      })
    })

    it('calls update API when save is clicked after changes in edit mode', async () => {
      const user = userEvent.setup()
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

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

    it('calls the meta endpoint when an AVAILABLE version is updated', async () => {
      const user = userEvent.setup()
      const availableVersionWithModel = {
        ...mockVersionWithModel,
        dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      }
      vi.mocked(useMultiSessionManager).mockReturnValue(createModelSessionManagerMock(availableVersionWithModel))
      renderComponent({ version: availableVersionWithModel })

      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(descriptionInput, 'Updated description')

      const confirmButtons = screen.getAllByTestId('confirmButton')
      await user.click(confirmButtons[0])

      await waitFor(() => {
        expect(mockUpdateReleasedMutateAsync).toHaveBeenCalledWith(
          expect.objectContaining({
            endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}/released/meta`,
          }),
        )
      })
    })

    it('calls create API when save is clicked in create mode', async () => {
      const user = userEvent.setup()
      renderComponent({ version: null, isCreateMode: true })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(versionInput, '1.0.0')
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
      vi.mocked(useMultiSessionManager).mockReturnValue(createModelSessionManagerMock(mockVersionWithModel))
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
      vi.mocked(useMultiSessionManager).mockReturnValue(createModelSessionManagerMock(availableVersionWithModel))
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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

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
      const user = userEvent.setup()

      const datastructureWithOtherVersion: Datastructure = {
        ...mockDatastructure,
        dataStructureVersions: [otherVersionSummary],
      }
      renderComponent({ datastructure: datastructureWithOtherVersion })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '2.0.0')

      await waitFor(() => {
        expect(screen.getByTestId('versionManualFormMessage')).toBeInTheDocument()
      })
    })

    it('shows no error when the entered version is unique', async () => {
      const user = userEvent.setup()

      const datastructureWithOtherVersion: Datastructure = {
        ...mockDatastructure,
        dataStructureVersions: [otherVersionSummary],
      }
      renderComponent({ datastructure: datastructureWithOtherVersion })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.clear(versionInput)
      await user.type(versionInput, '3.0.0')

      await waitFor(() => {
        expect(screen.queryByTestId('versionManualFormMessage')).not.toBeInTheDocument()
      })
    })
  })

  describe('exit behavior - create mode', () => {
    it('shows ExitWarningModal when version field is filled in create mode', async () => {
      const user = userEvent.setup()

      renderComponent({
        version: null,
        isCreateMode: true,
      })

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.type(versionInput, '1.0.0')

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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      await user.type(versionInput, '1.0.0')

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

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      const descriptionInput = screen.getByTestId('descriptionTextArea') as HTMLTextAreaElement
      await user.type(versionInput, '1.0.0')
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
})
