import { act, renderHook } from '@testing-library/react'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useDatastructureCreation } from './useDatastructureCreation'

const mockMutateAsync = vi.fn()

vi.mock('@/app/services/api/datastructures/clientRequests', () => ({
  useCreateDatastructure: () => ({ mutateAsync: mockMutateAsync, isPending: false }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

describe('useDatastructureCreation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const setupValidForm = () => {
    const { result } = renderHook(() => useDatastructureCreation())
    act(() => {
      result.current.form.setValue('name', 'Test Datastructure', { shouldDirty: true })
      result.current.form.setValue('description', 'A description', { shouldDirty: true })
    })
    return result
  }

  it('resolves to the created data structure on success', async () => {
    mockMutateAsync.mockResolvedValue({ data: { id: 'structure-1' } })
    const result = setupValidForm()

    await act(async () => {
      await expect(result.current.saveDatastructure()).resolves.toEqual({ id: 'structure-1' })
    })

    expect(toast.success).toHaveBeenCalled()
  })

  it('resolves without throwing when the API call fails', async () => {
    mockMutateAsync.mockRejectedValue(new Error('Unexpected error'))
    const result = setupValidForm()

    await act(async () => {
      await expect(result.current.saveDatastructure()).resolves.toBeUndefined()
    })

    expect(toast.error).toHaveBeenCalledWith('errors.creationError')
  })
})
