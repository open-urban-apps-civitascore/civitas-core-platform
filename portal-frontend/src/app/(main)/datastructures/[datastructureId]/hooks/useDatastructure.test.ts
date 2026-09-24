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

describe('useDatastructure — metadata saves carry no version assignment', () => {
  beforeEach(() => vi.clearAllMocks())

  // A stale version list in the payload would overwrite versions added concurrently from another session.
  it('omits the version assignment from the released metadata save', async () => {
    const { updateReleased } = await saveChangedDescription(DATASTRUCTURE_STATUS_TYPES.AVAILABLE)

    expect(updateReleased.mutateAsync).toHaveBeenCalledOnce()
    const payload = updateReleased.mutateAsync.mock.calls[0][0]
    expect(payload).not.toHaveProperty('dataStructureVersionIds')
    expect(payload).toMatchObject({ id: 'ds-1', name: 'Struct', description: 'changed', createdFromDataSource: true })
  })

  it('omits the version assignment from the draft save', async () => {
    const { update } = await saveChangedDescription(DATASTRUCTURE_STATUS_TYPES.DRAFT)

    expect(update.mutateAsync).toHaveBeenCalledOnce()
    const payload = update.mutateAsync.mock.calls[0][0]
    expect(payload).not.toHaveProperty('dataStructureVersionIds')
    expect(payload).toMatchObject({ id: 'ds-1', name: 'Struct', description: 'changed' })
  })
})
