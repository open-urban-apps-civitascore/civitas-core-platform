import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Datastructure } from '@/types/datastructures'

import { useDatastructure } from './useDatastructure'

const mockUpdateDatastructure = vi.fn()
const mockUpdateDatastructureReleased = vi.fn()

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useUpdateDatastructure: () => ({ mutateAsync: mockUpdateDatastructure, isPending: false }),
  useUpdateDatastructureReleased: () => ({ mutateAsync: mockUpdateDatastructureReleased, isPending: false }),
  useReleaseDatastructure: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUnreleaseDatastructure: () => ({ mutateAsync: vi.fn(), isPending: false }),
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

const availableDatastructure: Datastructure = {
  id: 'structure-1',
  name: 'Test Datastructure',
  description: 'A test datastructure',
  dataStructureStatus: 'AVAILABLE',
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

describe('useDatastructure', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends only id, name and description to the released endpoint for an AVAILABLE data structure', async () => {
    mockUpdateDatastructureReleased.mockResolvedValue({ data: availableDatastructure })
    const { result } = renderHook(
      () =>
        useDatastructure({
          datastructure: availableDatastructure,
          assignedGroups: [],
          initialAssignments: [],
          canRelease: true,
        }),
      { wrapper },
    )
    act(() => {
      result.current.form.setValue('description', 'Changed description', { shouldDirty: true })
    })

    await act(async () => {
      await result.current.saveDatastructure()
    })

    expect(mockUpdateDatastructure).not.toHaveBeenCalled()
    expect(mockUpdateDatastructureReleased).toHaveBeenCalledWith({
      id: 'structure-1',
      name: 'Test Datastructure',
      description: 'Changed description',
    })
  })
})
