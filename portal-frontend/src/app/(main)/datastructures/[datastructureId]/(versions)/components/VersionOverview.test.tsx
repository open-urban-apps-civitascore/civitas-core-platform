import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES } from '@/types/currentUser'
import { Datastructure, DATASTRUCTURE_STATUS_TYPES, DatastructureVersion } from '@/types/datastructures'

import { VersionOverview } from './VersionOverview'

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

let mockSearchParams = new URLSearchParams('mode=edit')

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
  useSearchParams: () => mockSearchParams,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSubTabValueParam: vi.fn(),
    subTabValue: 'versionInfo',
  }),
}))

vi.mock('@/components/uml-modeler/hooks/use-multi-session-manager', () => ({
  useMultiSessionManager: () => ({
    activeSession: null,
    activeSessionId: null,
    setSession: vi.fn(),
    markSessionDirty: vi.fn(),
  }),
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useCreateDatastructureVersion: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useUpdateDatastructureVersion: () => ({
    mutateAsync: vi.fn(),
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

  describe('exit modal - edit mode with hasUserChanges', () => {
    it('shows modal when version field is changed', async () => {
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

    it('shows modal when description field is changed', async () => {
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

    it('resets form when discard button in modal is clicked', async () => {
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

    it('closes modal after discard action', async () => {
      renderComponent()

      const versionInput = screen.getByTestId('versionTextField') as HTMLInputElement
      fireEvent.change(versionInput, { target: { value: '2.0.0' } })

      const cancelButtons = screen.getAllByTestId('cancelButton')
      const exitButton = cancelButtons[0]
      fireEvent.click(exitButton)

      await waitFor(() => {
        const discardButton = screen.getByRole('button', { name: /discard|verwerfen/i })
        fireEvent.click(discardButton)
      })

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
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

  describe('exit modal - create mode with hasUserChanges', () => {
    it('shows modal when version field is filled in create mode', async () => {
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

    it('resets form to empty when discard is clicked in create mode', async () => {
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
        const discardButton = screen.getByRole('button', { name: /discard|verwerfen/i })
        fireEvent.click(discardButton)
      })

      await waitFor(() => {
        expect(versionInput.value).toBe('')
      })
    })
  })
})
