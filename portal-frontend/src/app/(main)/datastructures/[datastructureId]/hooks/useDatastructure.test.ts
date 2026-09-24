import { act, renderHook } from '@testing-library/react'

import {
  useReleaseDatastructure,
  useUnreleaseDatastructure,
  useUpdateDatastructure,
  useUpdateDatastructureReleased,
} from '@/app/services/api/datastructures/clientRequests'
import { Datastructure, DATASTRUCTURE_STATUS_TYPES, DatastructureStatusType } from '@/types/datastructures'

import { useDatastructure } from './useDatastructure'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}))

vi.mock('next-intl', () => ({
  useTranslations: (namespace: string) => (key: string) => `${namespace}.${key}`,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/hooks/use-error', () => ({
  useError: () => ({ handleFormValidationError: vi.fn() }),
}))

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useReleaseDatastructure: vi.fn(),
  useUnreleaseDatastructure: vi.fn(),
  useUpdateDatastructure: vi.fn(),
  useUpdateDatastructureReleased: vi.fn(),
}))

const datastructure = (status: DatastructureStatusType): Datastructure => ({
  id: 'ds-1',
  name: 'Struct',
  description: 'desc',
  dataStructureStatus: status,
  createdFromDataSource: true,
  inUse: false,
  createdAt: '2026-01-01T00:00:00Z',
  modifiedAt: '2026-01-01T00:00:00Z',
  dataStructureVersions: [
    {
      id: 'v1',
      version: '1.0.0',
      description: null,
      dataStructureVersionStatus: DATASTRUCTURE_STATUS_TYPES.AVAILABLE,
      dataStructureVersionSource: 'OWN',
    } as Datastructure['dataStructureVersions'][number],
  ],
})

const mutation = (status: DatastructureStatusType) => ({
  mutateAsync: vi.fn().mockResolvedValue({ data: datastructure(status) }),
  isPending: false,
})

const saveChangedDescription = async (status: DatastructureStatusType) => {
  const update = mutation(status)
  const updateReleased = mutation(status)
  vi.mocked(useUpdateDatastructure).mockReturnValue(update as never)
  vi.mocked(useUpdateDatastructureReleased).mockReturnValue(updateReleased as never)
  vi.mocked(useReleaseDatastructure).mockReturnValue(mutation(status) as never)
  vi.mocked(useUnreleaseDatastructure).mockReturnValue(mutation(status) as never)

  const { result } = renderHook(() =>
    useDatastructure({
      datastructure: datastructure(status),
      assignedGroups: [],
      initialAssignments: [],
      canRelease: true,
    }),
  )

  act(() => result.current.form.setValue('description', 'changed', { shouldDirty: true }))
  await act(async () => {
    await result.current.saveDatastructure()
  })

  return { update, updateReleased }
}

describe('useDatastructure — saving changed metadata', () => {
  beforeEach(() => vi.clearAllMocks())

  // The released PUT nulls every field it omits, so a trimmed payload would clear createdFromDataSource.
  it('sends a released data structure through the released metadata update with its full metadata', async () => {
    const { update, updateReleased } = await saveChangedDescription(DATASTRUCTURE_STATUS_TYPES.AVAILABLE)

    expect(update.mutateAsync).not.toHaveBeenCalled()
    expect(updateReleased.mutateAsync).toHaveBeenCalledOnce()
    expect(updateReleased.mutateAsync.mock.calls[0][0]).toMatchObject({
      id: 'ds-1',
      name: 'Struct',
      description: 'changed',
      createdFromDataSource: true,
    })
  })

  it('sends a draft data structure through the draft update', async () => {
    const { update, updateReleased } = await saveChangedDescription(DATASTRUCTURE_STATUS_TYPES.DRAFT)

    expect(updateReleased.mutateAsync).not.toHaveBeenCalled()
    expect(update.mutateAsync).toHaveBeenCalledOnce()
    expect(update.mutateAsync.mock.calls[0][0]).toMatchObject({ id: 'ds-1', name: 'Struct', description: 'changed' })
  })
})
