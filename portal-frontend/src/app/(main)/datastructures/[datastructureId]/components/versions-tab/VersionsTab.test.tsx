import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { DatastructureVersionsListData } from '@/types/datastructures'

import { VersionsTab } from './VersionsTab'

const mockRefresh = vi.fn()
const mockDuplicateMutate = vi.fn()
const mockDuplicateState = { isPending: false }

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/app/services/api/datastructures/versions/clientRequests', () => ({
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
    onDuplicateVersionClick,
  }: {
    versions: { id: string }[]
    isDuplicating: boolean
    onDuplicateVersionClick: (id: string) => void
  }) => (
    <div>
      {versions.map(version => (
        <button
          key={version.id}
          data-testid={`duplicate-${version.id}`}
          disabled={isDuplicating}
          onClick={() => onDuplicateVersionClick(version.id)}
        />
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

const renderComponent = () =>
  render(<VersionsTab datastructureId="ds-1" versions={[version]} isReadOnly={false} rowCount={1} />)

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
