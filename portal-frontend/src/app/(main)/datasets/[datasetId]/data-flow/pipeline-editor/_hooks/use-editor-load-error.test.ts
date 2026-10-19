import { renderHook } from '@testing-library/react'
import { toast } from 'sonner'

import { useGetDataset } from '@/app/services/api/datasets/clientRequests'
import { useGetDataSinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'

import { useEditorLoadError } from './use-editor-load-error'

const translate = vi.hoisted(() => (key: string) => key)

vi.mock('next-intl', () => ({
  useTranslations: () => translate,
}))

vi.mock('sonner', () => ({
  toast: { error: vi.fn() },
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useGetDataset: vi.fn(),
}))

vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useGetDataSinks: vi.fn(),
}))

vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useGetPipelines: vi.fn(),
}))

type FailingQuery = 'pipelines' | 'dataSinks' | 'dataset' | null

const mockQueries = (failing: FailingQuery) => {
  const result = (isLoadingError: boolean) => ({ isLoadingError })
  vi.mocked(useGetPipelines).mockReturnValue(result(failing === 'pipelines') as ReturnType<typeof useGetPipelines>)
  vi.mocked(useGetDataSinks).mockReturnValue(result(failing === 'dataSinks') as ReturnType<typeof useGetDataSinks>)
  vi.mocked(useGetDataset).mockReturnValue(result(failing === 'dataset') as ReturnType<typeof useGetDataset>)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useEditorLoadError', () => {
  it('reports no error and shows no toast when all requests load', () => {
    mockQueries(null)

    const { result } = renderHook(() => useEditorLoadError('dataset-1'))

    expect(result.current).toBe(false)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it.each<FailingQuery>(['pipelines', 'dataSinks', 'dataset'])(
    'reports an error and shows one toast when the %s request fails',
    failing => {
      mockQueries(failing)

      const { result, rerender } = renderHook(() => useEditorLoadError('dataset-1'))
      rerender()

      expect(result.current).toBe(true)
      expect(toast.error).toHaveBeenCalledOnce()
      expect(toast.error).toHaveBeenCalledWith('header.loadFailed', { id: 'pipeline-editor-load-failed' })
    },
  )
})
