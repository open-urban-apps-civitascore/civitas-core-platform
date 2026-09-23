import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent, { UserEvent } from '@testing-library/user-event'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import messages from '@/messages/de.json'
import { contractErrors } from '@/test-support/coreContracts'
import { cls, datastructureFixtures, type FixtureAttribute } from '@/test-support/datastructureFixtures'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import {
  Datastructure,
  DATASTRUCTURE_STATUS_TYPES,
  DatastructureVersion,
  DatastructureVersionPutData,
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

    type ClassDef = { properties?: Record<string, unknown>; required?: string[] }

    const savedDefs = () => {
      const { data } = mockUpdateMutateAsync.mock.calls[0][0] as { data: DatastructureVersionPutData }
      return { data, defs: (data.model as { $defs: Record<string, ClassDef> }).$defs }
    }

    // Pre-selected in the fixture rather than clicked: the inspector renders nothing without a
    // selected node, and React Flow needs real pointer events to select one.
    const versionSelecting = (attribute: FixtureAttribute): DatastructureVersion => ({
      ...mockVersionWithModel,
      styles: {
        ...mockVersionWithModel.styles!,
        nodes: [{ ...cls('elem-1', 'TestClass', [attribute]), selected: true }] as UMLDiagram['nodes'],
      },
    })

    const versionWithSelectedNode = versionSelecting({ id: 'attr-1', name: 'serial', type: 'Uuid' })

    it('enables the save button after the diagram was renamed', async () => {
      const user = userEvent.setup()
      renderComponent({ version: mockVersionWithModel })

      expect(screen.getByTestId('confirmButton')).toBeDisabled()

      await user.dblClick(screen.getByText('Test Model'))
      const diagramNameInput = screen.getByDisplayValue('Test Model')
      await user.clear(diagramNameInput)
      await user.type(diagramNameInput, 'Renamed Model{Enter}')

      await waitFor(() => expect(screen.getByTestId('confirmButton')).toBeEnabled())
    })

    it('enables the save button after a class was renamed in the inspector', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionWithSelectedNode })

      expect(screen.getByTestId('confirmButton')).toBeDisabled()

      await user.type(screen.getByPlaceholderText('Element name'), 'X')

      await waitFor(() => expect(screen.getByTestId('confirmButton')).toBeEnabled())
    })

    it('saves the renamed class into the CORE model, not just the styles', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionWithSelectedNode })

      await user.type(screen.getByPlaceholderText('Element name'), 'X')
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalled())

      const { data, defs } = savedDefs()
      expect(Object.keys(defs)).toEqual(['TestClassX'])
      expect(defs.TestClassX.properties).toEqual({ serial: { type: 'string', format: 'uuid' } })
      expect(data.styles?.nodes[0].data.element.name).toBe('TestClassX')
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves an attribute added in the inspector into the CORE model', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionWithSelectedNode })

      await user.click(screen.getByText('Attributes'))
      await user.click(screen.getByRole('button', { name: /Add Attribute/ }))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalled())

      const { data, defs } = savedDefs()
      expect(defs.TestClass.properties).toEqual({
        serial: { type: 'string', format: 'uuid' },
        neuesAttribut: { type: 'string' },
      })
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves a geometry attribute turned primitive without its CRS', async () => {
      const user = userEvent.setup()
      const geometry: FixtureAttribute = {
        id: 'attr-1',
        name: 'location',
        type: 'Point',
        meta: { gisInfo: { crs: 'EPSG:25832' } },
      }
      renderComponent({ version: versionSelecting(geometry) })

      await user.click(screen.getByText('Attributes'))
      const type = screen.getByText('Type').closest('div') as HTMLElement
      fireEvent.click(within(type).getByRole('combobox'))
      fireEvent.click(screen.getByRole('option', { name: 'String' }))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalled())

      // A leftover crs would still satisfy the contract, which does not know the keyword at all.
      const { data, defs } = savedDefs()
      expect(defs.TestClass.properties).toEqual({ location: { type: 'string' } })
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves a primitive attribute turned geometry with the default CRS', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionSelecting({ id: 'attr-1', name: 'location' }) })

      await user.click(screen.getByText('Attributes'))
      const type = screen.getByText('Type').closest('div') as HTMLElement
      fireEvent.click(within(type).getByRole('combobox'))
      fireEvent.click(screen.getByRole('option', { name: 'Point' }))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalled())

      const { data, defs } = savedDefs()
      expect(defs.TestClass.properties).toEqual({
        location: { $ref: 'https://geojson.org/schema/Point.json', crs: 'EPSG:4326' },
      })
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves a cardinality change into the CORE model', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionWithSelectedNode })

      await user.click(screen.getByText('Attributes'))
      // Radix Select opens on the raw pointer event, which userEvent does not emit in jsdom.
      const cardinality = screen.getByText('Cardinality').closest('div') as HTMLElement
      fireEvent.click(within(cardinality).getByRole('combobox'))
      fireEvent.click(screen.getByRole('option', { name: '0..*' }))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalled())

      const { data, defs } = savedDefs()
      expect(defs.TestClass.properties?.serial).toEqual({ type: 'array', items: { type: 'string', format: 'uuid' } })
      // A lower bound of 0 also drops the property from required.
      expect(defs.TestClass.required).toBeUndefined()
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves and publishes a one-class structure whose root was designated in the inspector', async () => {
      const user = userEvent.setup()
      renderComponent({ version: versionWithSelectedNode })

      await user.click(screen.getByRole('checkbox', { name: /Root class/ }))
      await openStatusDropdown(user)
      await user.click(screen.getByTestId('statusOption-available'))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockStatusUpdateMutateAsync).toHaveBeenCalled())
      expect(mockStatusUpdateMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}/release`,
        }),
      )

      // The field update carries the artifact; the release call itself sends no body.
      const { data, defs } = savedDefs()
      expect(data.styles?.nodes[0].data.element.isRoot).toBe(true)
      expect(Object.keys(defs)).toEqual(['TestClass'])

      const rootId = (defs.TestClass as { $id?: string }).$id
      expect(rootId).toMatch(/^urn:core:.*:element:.*:TestClass:/)
      expect((data.model as { $ref?: string }).$ref).toBe(rootId)
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves and publishes four cross-referencing Elements under one root', async () => {
      const user = userEvent.setup()
      const members = ['Station', 'Measurement', 'Reading', 'Alert']
      const crossReferencing = datastructureFixtures.find(
        fixture => fixture.label === 'four cross-referencing elements under one root',
      )
      if (!crossReferencing) throw new Error('fixture missing')

      // The inspector only shows a selected node.
      const nodes = crossReferencing.diagram.nodes.map((node, index) =>
        index === 0 ? { ...node, selected: true } : node,
      )
      renderComponent({
        version: { ...mockVersionWithModel, styles: { ...crossReferencing.diagram, nodes } } as DatastructureVersion,
      })

      // A status change alone sends no model.
      await user.click(screen.getByRole('checkbox', { name: /Root class/ }))
      await openStatusDropdown(user)
      await user.click(screen.getByTestId('statusOption-available'))
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockStatusUpdateMutateAsync).toHaveBeenCalled())

      const { data, defs } = savedDefs()
      const idOf = (name: string) => (defs[name] as { $id?: string }).$id

      expect(mockUpdateMutateAsync.mock.invocationCallOrder[0]).toBeLessThan(
        mockStatusUpdateMutateAsync.mock.invocationCallOrder[0],
      )
      expect(mockStatusUpdateMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          endpoint: `/datastructures/${mockDatastructure.id}/versions/${mockVersion.id}/release`,
        }),
      )

      expect(Object.keys(defs)).toEqual(members)
      expect((data.model as { $ref?: string }).$ref).toBe(idOf('Station'))
      // The saved URNs carry no version segment.
      for (const name of members) {
        expect(idOf(name)).toMatch(new RegExp(`:element:common:${name}:[^:]+$`))
      }
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })

    it('saves a class added on the canvas into the CORE model', async () => {
      const user = userEvent.setup()
      // The designation is what keeps the export unambiguous once a second, unconnected class exists.
      const rootNode = cls('elem-1', 'TestClass', [{ id: 'attr-1', name: 'serial', type: 'Uuid' }], { isRoot: true })
      const versionWithRoot: DatastructureVersion = {
        ...mockVersionWithModel,
        styles: { ...mockVersionWithModel.styles!, nodes: [rootNode] as UMLDiagram['nodes'] },
      }
      renderComponent({ version: versionWithRoot })

      // The palette hands the element type over in a drag payload; userEvent has no drag-and-drop.
      const canvas = document.querySelector('.react-flow') as HTMLElement
      fireEvent.drop(canvas, { dataTransfer: { getData: () => 'class' } })

      await waitFor(() => expect(screen.getByTestId('confirmButton')).toBeEnabled())
      await user.click(screen.getByTestId('confirmButton'))

      await waitFor(() => expect(mockUpdateMutateAsync).toHaveBeenCalled())

      const { data, defs } = savedDefs()
      expect(data.styles?.nodes).toHaveLength(2)

      const addedClass = data.styles?.nodes[1].data.element.name
      expect(addedClass).toMatch(/^NeueKlasse/)
      expect(Object.keys(defs)).toEqual(['TestClass', addedClass])
      // The class template seeds one attribute, so the new class is not empty.
      expect(defs[addedClass!].properties).toEqual({ attribut: { type: 'string' } })
      expect(contractErrors('datastructure', data.model)).toEqual([])
    })
  })
})
