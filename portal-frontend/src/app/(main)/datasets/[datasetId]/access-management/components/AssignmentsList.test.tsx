import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { GroupRoleAssignmentTable } from '@/components/access-management/AccessManagementTable'
import messages from '@/messages/de.json'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Dataset } from '@/types/datasets'
import { hasAssignmentChanges } from '@/utils/assignments'

import { AssignmentsList } from './AssignmentsList'

const mockPatchDataset = vi.fn().mockResolvedValue(undefined)
const mockUpdateReadyMeta = vi.fn().mockResolvedValue(undefined)
const mockUpdateReleasedMeta = vi.fn().mockResolvedValue(undefined)
const mockPush = vi.fn()
const mockRefresh = vi.fn()
let mockSearchParams = new URLSearchParams()

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  usePatchDataset: () => ({ mutateAsync: mockPatchDataset, isPending: false }),
  useUpdateReadyDatasetMeta: () => ({ mutateAsync: mockUpdateReadyMeta, isPending: false }),
  useUpdateReleasedDatasetMeta: () => ({ mutateAsync: mockUpdateReleasedMeta, isPending: false }),
}))

vi.mock('@/app/services/api/groups/clientRequests', () => ({
  useGetGroups: () => ({ data: { data: [], totalElements: 0 }, isLoading: false, isError: false }),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: () => ({ data: { data: [], totalElements: 0 }, isLoading: false, isError: false }),
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
  useSearchParams: () => mockSearchParams,
  usePathname: () => '/datasets/ds-1/access-management',
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({ setHasUnsavedChanges: vi.fn(), setSaveHandler: vi.fn() }),
}))

const { hasAssignmentChanges: realHasAssignmentChanges } =
  await vi.importActual<typeof import('@/utils/assignments')>('@/utils/assignments')

vi.mock('@/utils/assignments', async importOriginal => {
  const actual = await importOriginal<typeof import('@/utils/assignments')>()
  return {
    ...actual,
    hasAssignmentChanges: vi.fn(actual.hasAssignmentChanges),
  }
})

const dataset: Dataset = {
  id: 'ds-1',
  name: 'Test Dataset',
  description: 'A test dataset',
  dataSetStatus: 'DRAFT',
  pipelines: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  openDataAccess: false,
  createdBy: { id: 'user-1', name: 'Test User 1' },
  datapool: null,
}

const mockAssignments: GroupRoleAssignmentTable[] = [
  {
    groupId: '1',
    groupName: 'Admin Group',
    groupDescription: 'Administrator group',
    assignedRoles: [{ roleId: '1', roleName: 'Admin' }],
  },
]

const mockCurrentUser = (permissions: PermissionName[]) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test',
      email: 'test.user1@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType: 'TENANT', scopeId: null, permissions }],
    },
  } as unknown as ReturnType<typeof useGetCurrentUser>)
}

const allPermissions = [
  PERMISSION_NAMES.DATASET_UPDATE,
  PERMISSION_NAMES.DATASET_RELEASE,
  PERMISSION_NAMES.DATASTRUCTURE_READ,
  PERMISSION_NAMES.GROUP_READ,
  PERMISSION_NAMES.ROLE_READ,
]

const renderList = (initialAssignments = mockAssignments, datasetOverrides: Partial<Dataset> = {}) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <AssignmentsList dataset={{ ...dataset, ...datasetOverrides }} initialAssignments={initialAssignments} />
    </NextIntlClientProvider>,
  )

const submitInEditMode = async () => {
  fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
  await waitFor(() => screen.getByRole('button', { name: /speichern/i }))
  fireEvent.click(screen.getByRole('button', { name: /speichern/i }))
}

describe('AssignmentsList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams()
    vi.mocked(hasAssignmentChanges).mockImplementation(realHasAssignmentChanges)
    mockCurrentUser(allPermissions)
  })

  it('renders title, subtitle and the edit button in read-only mode', () => {
    renderList()

    expect(screen.getByText('Zugriffsberechtigungen')).toBeInTheDocument()
    expect(screen.getByText('Hier werden Zuständigkeiten und Zugriffsrechte definiert.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /bearbeiten/i })).toBeInTheDocument()
  })

  it('displays the assignments table', () => {
    renderList()

    expect(screen.getByText('Admin Group')).toBeInTheDocument()
    expect(screen.getByText('Admin')).toBeInTheDocument()
  })

  it('shows the no data page when there are no assignments', () => {
    renderList([])

    expect(screen.getByText('Keine Gruppen und Rollen vorhanden')).toBeInTheDocument()
  })

  it('switches to edit mode when the edit button is clicked', async () => {
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /speichern/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /beenden/i })).toBeInTheDocument()
    })
  })

  it('shows the add assignment button in edit mode', async () => {
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))

    await waitFor(() => expect(screen.getByText(/gruppe hinzufügen/i)).toBeInTheDocument())
  })

  it('disables the submit button when no changes have been made', async () => {
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))

    await waitFor(() => expect(screen.getByRole('button', { name: /speichern/i })).toBeDisabled())
  })

  it('shows only the first info box in read-only mode', () => {
    renderList()

    expect(
      screen.getByText(
        /gruppen, die plattformweite berechtigungen an allen datenbezogenen Elementen besitzen, haben zugriff/i,
      ),
    ).toBeInTheDocument()
    expect(screen.queryByText(/durch das entfernen der eigenen gruppen-rollen-zuordnung/i)).not.toBeInTheDocument()
  })

  it('shows both info boxes in edit mode', async () => {
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))

    await waitFor(() =>
      expect(screen.getByText(/durch das entfernen der eigenen gruppen-rollen-zuordnung/i)).toBeInTheDocument(),
    )
  })

  it('navigates back to the dataset when exiting without changes', async () => {
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
    await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
    fireEvent.click(screen.getByRole('button', { name: /beenden/i }))

    expect(mockPush).toHaveBeenCalledWith('/datasets/ds-1')
  })

  it('navigates back to the dataset when discarding changes via the exit modal', async () => {
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
    await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
    fireEvent.click(screen.getByRole('button', { name: /beenden/i }))
    await waitFor(() => screen.getByTestId('discardButton'))
    fireEvent.click(screen.getByTestId('discardButton'))

    expect(mockPush).toHaveBeenCalledWith('/datasets/ds-1')
  })

  it('patches the dataset and navigates back when saving via the exit modal', async () => {
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
    await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
    fireEvent.click(screen.getByRole('button', { name: /beenden/i }))
    await waitFor(() => screen.getByTestId('saveButton'))
    fireEvent.click(screen.getByTestId('saveButton'))

    await waitFor(() =>
      expect(mockPatchDataset).toHaveBeenCalledWith({
        id: 'ds-1',
        assignments: [{ groupId: '1', roleId: '1' }],
      }),
    )
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/datasets/ds-1'))
  })

  it('does not navigate back when saving via the exit modal fails', async () => {
    mockPatchDataset.mockRejectedValueOnce(new Error('save failed'))
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList()

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))
    await waitFor(() => screen.getByRole('button', { name: /beenden/i }))
    fireEvent.click(screen.getByRole('button', { name: /beenden/i }))
    await waitFor(() => screen.getByTestId('saveButton'))
    fireEvent.click(screen.getByTestId('saveButton'))

    await waitFor(() => expect(mockPatchDataset).toHaveBeenCalled())
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('patches the dataset when it is in DRAFT status', async () => {
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList()

    await submitInEditMode()

    await waitFor(() =>
      expect(mockPatchDataset).toHaveBeenCalledWith({
        id: 'ds-1',
        assignments: [{ groupId: '1', roleId: '1' }],
      }),
    )
    expect(mockUpdateReadyMeta).not.toHaveBeenCalled()
    expect(mockUpdateReleasedMeta).not.toHaveBeenCalled()
  })

  const unchangedMetaPayload = {
    id: 'ds-1',
    name: 'Test Dataset',
    description: 'A test dataset',
    openDataAccess: false,
    assignments: [{ groupId: '1', roleId: '1' }],
  }

  it('sends the assignments together with the unchanged metadata to ready/meta in READY status', async () => {
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList(mockAssignments, { dataSetStatus: 'READY' })

    await submitInEditMode()

    await waitFor(() => expect(mockUpdateReadyMeta).toHaveBeenCalledWith(unchangedMetaPayload))
    expect(mockPatchDataset).not.toHaveBeenCalled()
    expect(mockUpdateReleasedMeta).not.toHaveBeenCalled()
  })

  it('sends the assignments together with the unchanged metadata to released/meta in AVAILABLE status', async () => {
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList(mockAssignments, { dataSetStatus: 'AVAILABLE' })

    await submitInEditMode()

    await waitFor(() => expect(mockUpdateReleasedMeta).toHaveBeenCalledWith(unchangedMetaPayload))
    expect(mockPatchDataset).not.toHaveBeenCalled()
    expect(mockUpdateReadyMeta).not.toHaveBeenCalled()
  })

  it('shows the provisioning hint when released/meta conflicts with an in-flight saga', async () => {
    mockUpdateReleasedMeta.mockRejectedValueOnce({ response: { status: 409 } })
    vi.mocked(hasAssignmentChanges).mockReturnValue(true)
    renderList(mockAssignments, { dataSetStatus: 'AVAILABLE' })

    await submitInEditMode()

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(messages.accessManagement.messages.updateErrorSagaInFlight),
    )
  })

  it('does not patch the dataset while the submit button is disabled', async () => {
    renderList([])

    fireEvent.click(screen.getByRole('button', { name: /bearbeiten/i }))

    await waitFor(() => expect(screen.getByRole('button', { name: /speichern/i })).toBeDisabled())
    expect(mockPatchDataset).not.toHaveBeenCalled()
  })

  it('hides the edit button when the user must not update the dataset', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ, PERMISSION_NAMES.GROUP_READ, PERMISSION_NAMES.ROLE_READ])
    renderList()

    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument()
  })

  it('hides the edit button on an AVAILABLE dataset when the user must not release the dataset', () => {
    mockCurrentUser([
      PERMISSION_NAMES.DATASET_UPDATE,
      PERMISSION_NAMES.DATASTRUCTURE_READ,
      PERMISSION_NAMES.GROUP_READ,
      PERMISSION_NAMES.ROLE_READ,
    ])
    renderList(mockAssignments, { dataSetStatus: 'AVAILABLE' })

    expect(screen.queryByRole('button', { name: /bearbeiten/i })).not.toBeInTheDocument()
  })

  it('shows the edit button on a READY dataset without the release permission', () => {
    mockCurrentUser([
      PERMISSION_NAMES.DATASET_UPDATE,
      PERMISSION_NAMES.DATASTRUCTURE_READ,
      PERMISSION_NAMES.GROUP_READ,
      PERMISSION_NAMES.ROLE_READ,
    ])
    renderList(mockAssignments, { dataSetStatus: 'READY' })

    expect(screen.getByRole('button', { name: /bearbeiten/i })).toBeInTheDocument()
  })

  it('stays read-only on a mode=edit deep link when the user must not update the dataset', () => {
    mockCurrentUser([PERMISSION_NAMES.DATASTRUCTURE_READ, PERMISSION_NAMES.GROUP_READ, PERMISSION_NAMES.ROLE_READ])
    mockSearchParams = new URLSearchParams('mode=edit')
    renderList()

    expect(screen.queryByRole('button', { name: /speichern/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/gruppe hinzufügen/i)).not.toBeInTheDocument()
  })

  it('starts in edit mode on a mode=edit deep link when the user may update the dataset', () => {
    mockSearchParams = new URLSearchParams('mode=edit')
    renderList()

    expect(screen.getByRole('button', { name: /speichern/i })).toBeInTheDocument()
    expect(screen.getByText(/gruppe hinzufügen/i)).toBeInTheDocument()
  })
})
