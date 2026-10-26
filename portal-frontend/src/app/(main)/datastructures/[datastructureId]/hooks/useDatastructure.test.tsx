import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Datastructure } from '@/types/datastructures'

import { useDatastructure } from './useDatastructure'

const mockUpdateDatastructure = vi.fn()

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useUpdateDatastructure: () => ({ mutateAsync: mockUpdateDatastructure, isPending: false }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
}))

const datastructureWithAvailableVersion: Datastructure = {
  id: 'structure-1',
  name: 'Test Datastructure',
  description: 'A test datastructure',
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  dataStructureVersions: [
    {
      id: 'version-1',
      version: '1.0.0',
      description: null,
      dataStructureVersionStatus: 'AVAILABLE',
      dataStructureVersionSource: 'OWN',
      createdAt: '2024-01-01',
      modifiedAt: '2024-01-01',
      dataStructureId: 'structure-1',
    },
  ],
}

const wrapper = ({ children }: PropsWithChildren) => (
  <QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider>
)

const setup = () =>
  renderHook(
    () =>
      useDatastructure({
        datastructure: datastructureWithAvailableVersion,
        assignedGroups: [],
        initialAssignments: [],
      }),
    { wrapper },
  )

describe('useDatastructure', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('updates a data structure that has an available version through the update endpoint', async () => {
    mockUpdateDatastructure.mockResolvedValue({ data: datastructureWithAvailableVersion })
    const { result } = setup()

    act(() => {
      result.current.form.setValue('description', 'Changed description', { shouldDirty: true })
    })

    await act(async () => {
      await result.current.saveDatastructure()
    })

    expect(mockUpdateDatastructure).toHaveBeenCalledWith({
      id: 'structure-1',
      name: 'Test Datastructure',
      description: 'Changed description',
    })
  })

  it('marks the versions tab complete when a version is available', () => {
    const { result } = setup()

    expect(result.current.completedTabs).toContain('versions')
  })
})
