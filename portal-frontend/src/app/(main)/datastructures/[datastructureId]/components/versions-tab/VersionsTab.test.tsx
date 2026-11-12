import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { mockApiError } from '@/__mocks__/errors/apiError.mock'
import { DatastructureVersionsListData } from '@/types/datastructures'

import { VersionsTab } from './VersionsTab'

const mockRefresh = vi.fn()
const mockDuplicateMutate = vi.fn()
const mockDuplicateState = { isPending: false }
const mockDeleteMutate = vi.fn()

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
  useDeleteDatastructureVersion: vi.fn(() => ({ mutate: mockDeleteMutate, isPending: false })),
  useDuplicateDatastructureVersion: vi.fn(() => ({
    mutate: mockDuplicateMutate,
    isPending: mockDuplicateState.isPending,
  })),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), refresh: mockRefresh }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    setSortingParams: vi.fn(),
    setPaginationParams: vi.fn(),
    setSearchParam: vi.fn(),
    setTotalPages: vi.fn(),
    pageIndex: 0,
    pageSize: 10,
    sorting: [],
    search: '',
    totalPages: 0,
  }),
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: false,
    requestNavigation: vi.fn(),
    requestBack: vi.fn(),
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

vi.mock('./VersionsTable', () => ({
  VersionsTable: ({
    versions,
    isDuplicating,
    onDeleteVersionClick,
    onDuplicateVersionClick,
  }: {
    versions: { id: string }[]
    isDuplicating: boolean
    onDeleteVersionClick: (id: string) => void
    onDuplicateVersionClick: (id: string) => void
  }) => (
    <div>
      {versions.map(version => (
        <div key={version.id}>
          <button
            data-testid={`duplicate-${version.id}`}
            disabled={isDuplicating}
            onClick={() => onDuplicateVersionClick(version.id)}
          />
          <button data-testid={`delete-${version.id}`} onClick={() => onDeleteVersionClick(version.id)} />
        </div>
      ))}
    </div>
  ),
}))

const version: DatastructureVersionsListData = {
  id: 'v1',
  name: 'Version 1.0.0',
  description: 'First version',
  status: 'DRAFT',
  source: 'OWN',
  versionNumber: '1.0.0',
}

const usedVersion: DatastructureVersionsListData = { ...version, id: 'v2', versionNumber: '2.0.0', inUse: true }

const renderComponent = () =>
  render(<VersionsTab datastructureId="ds-1" versions={[version, usedVersion]} isReadOnly={false} rowCount={2} />)

describe('VersionsTab duplication', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDuplicateState.isPending = false
  })

  it('disables duplication in the table while a copy is being made', () => {
    mockDuplicateState.isPending = true
    renderComponent()

    expect(screen.getByTestId('duplicate-v1')).toBeDisabled()
  })

  it('duplicates the version and shows a success toast', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('duplicate-v1'))

    expect(mockDuplicateMutate).toHaveBeenCalledWith(
      { data: undefined, endpoint: '/datastructures/ds-1/versions/v1/duplicate' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    )

    const [, { onSuccess }] = mockDuplicateMutate.mock.calls[0]
    onSuccess()

    expect(toast.success).toHaveBeenCalledWith('messages.duplicateSuccess')
    expect(mockRefresh).toHaveBeenCalled()
  })

  it('shows an error toast when duplication fails', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('duplicate-v1'))

    const [, { onError }] = mockDuplicateMutate.mock.calls[0]
    onError(new Error('network failure'))

    expect(toast.error).toHaveBeenCalledWith('errors.duplicationError')
  })
})

describe('VersionsTab deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('explains that a version in use cannot be deleted, without asking to confirm', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('delete-v2'))

    expect(screen.getByTestId('infoModal')).toHaveTextContent('infoModal.title')
    expect(screen.queryByTestId('confirmButton')).not.toBeInTheDocument()
  })

  it('deletes a version that is not in use after confirmation and refreshes the list', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('delete-v1'))
    expect(screen.getByText('deleteConfirm.title')).toBeInTheDocument()
    await userEvent.click(screen.getByTestId('confirmButton'))

    expect(mockDeleteMutate).toHaveBeenCalledWith(
      { data: undefined, endpoint: '/datastructures/ds-1/versions/v1' },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) }),
    )

    const [, { onSuccess }] = mockDeleteMutate.mock.calls[0]
    onSuccess()

    expect(toast.success).toHaveBeenCalledWith('success.deletionSuccess')
    expect(mockRefresh).toHaveBeenCalled()
  })

  it('explains that the version cannot be deleted when the delete answers that it is in use', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('delete-v1'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    const [, { onError }] = mockDeleteMutate.mock.calls[0]
    act(() => onError(mockApiError(409, 'still referenced', 'urn:civitas:error:RESOURCE_IN_USE')))

    expect(screen.getByTestId('infoModal')).toHaveTextContent('infoModal.title')
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('shows a generic error toast for any other delete failure', async () => {
    renderComponent()
    await userEvent.click(screen.getByTestId('delete-v1'))
    await userEvent.click(screen.getByTestId('confirmButton'))

    const [, { onError }] = mockDeleteMutate.mock.calls[0]
    onError(new Error('network failure'))

    expect(toast.error).toHaveBeenCalledWith('errors.deletionError')
  })
})
