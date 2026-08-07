import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetUsableDatasources } from '@/app/services/api/datasets/usable-datasources/clientRequests'
import { PipelineBasicInfo } from '@/types/datasets'

import { PipelineList } from './PipelineList'

vi.mock('@/components/guarded-link/GuardedLink', () => ({
  GuardedLink: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
  useLocale: () => 'de',
}))

vi.mock('sonner', () => ({
  toast: { error: vi.fn() },
}))

vi.mock('@/app/services/api/pipelines/clientRequests', () => ({
  useGetPipelines: vi.fn().mockReturnValue({ data: undefined }),
}))

vi.mock('@/app/services/api/datasets/usable-datasources/clientRequests', () => ({
  useGetUsableDatasources: vi.fn().mockReturnValue({ data: undefined }),
}))

const makePipeline = (overrides: Partial<PipelineBasicInfo> = {}): PipelineBasicInfo =>
  ({
    id: 'pipeline-1',
    name: 'My Pipeline',
    ...overrides,
  }) as PipelineBasicInfo

const renderComponent = (props: Partial<React.ComponentProps<typeof PipelineList>> = {}) =>
  render(
    <PipelineList
      datasetId="ds-1"
      pipelines={[makePipeline()]}
      canCreatePipeline={true}
      canEditDataset={true}
      {...props}
    />,
  )

describe('PipelineList', () => {
  beforeEach(() => {
    vi.mocked(useGetUsableDatasources).mockClear()
  })

  it('does not request the connector source for a viewer who cannot edit the dataset', () => {
    // The endpoint requires DATASET_UPDATE, so asking as a viewer only yields a denial per render.
    renderComponent({ canEditDataset: false })

    expect(vi.mocked(useGetUsableDatasources).mock.calls[0][1]?.isEnabled).toBe(false)
  })

  it('requests the connector source in edit mode', () => {
    renderComponent({ canEditDataset: true })

    expect(vi.mocked(useGetUsableDatasources).mock.calls[0][1]?.isEnabled).toBe(true)
  })

  it('offers pipeline creation when permitted', () => {
    renderComponent({ canCreatePipeline: true })

    expect(screen.getByText('addButton')).toBeInTheDocument()
  })

  it('hides pipeline creation when not permitted', () => {
    renderComponent({ canCreatePipeline: false })

    expect(screen.queryByText('addButton')).not.toBeInTheDocument()
  })
})
