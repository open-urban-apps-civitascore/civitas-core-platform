import { render, screen } from '@testing-library/react'

import { createEmptyPipeline } from '../../_services/pipelineService'
import type { PipelineSession } from '../../_types/session'
import { PipelineTabBar } from './PipelineTabBar'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const session = (id: string, name: string): PipelineSession =>
  ({ id, name, pipeline: createEmptyPipeline(name), isDirty: false }) as unknown as PipelineSession

const renderTabBar = (sessionIdsWithErrors: ReadonlySet<string>) =>
  render(
    <PipelineTabBar
      sessions={[session('session-1', 'Test Pipeline 1'), session('session-2', 'Test Pipeline 2')]}
      activeSessionId="session-1"
      sessionIdsWithErrors={sessionIdsWithErrors}
      onSelectSession={vi.fn()}
      onCreateSession={vi.fn()}
    />,
  )

describe('PipelineTabBar', () => {
  it('marks only the tab of a pipeline with errors', () => {
    renderTabBar(new Set(['session-2']))

    const markers = screen.getAllByRole('img', { name: 'tabs.hasErrors' })
    expect(markers).toHaveLength(1)
    expect(markers[0].parentElement).toHaveTextContent('Test Pipeline 2')
  })

  it('shows no error marker without errors', () => {
    renderTabBar(new Set())

    expect(screen.queryByLabelText('tabs.hasErrors')).not.toBeInTheDocument()
  })
})
