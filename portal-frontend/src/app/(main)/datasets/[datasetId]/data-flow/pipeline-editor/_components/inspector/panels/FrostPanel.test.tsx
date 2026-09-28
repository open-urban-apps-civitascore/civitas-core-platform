import { render, screen } from '@testing-library/react'

import { frostNode } from '@/test-support/pipelineFixtures'

import type { FrostNodeData } from '../../../_types/nodes'
import { FrostPanel } from './FrostPanel'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const renderPanel = () => render(<FrostPanel data={frostNode().data as unknown as FrostNodeData} />)

describe('FrostPanel', () => {
  it('shows the fixed server name, the internal server and the version', () => {
    renderPanel()

    const valueOf = (label: string) => screen.getByText(label).nextElementSibling
    expect(valueOf('frostPanel.serverName')).toHaveTextContent('Sensor Data Storage')
    expect(valueOf('frostPanel.serverUrl')).toHaveTextContent('frostPanel.internalServer')
    expect(valueOf('frostPanel.version')).toHaveTextContent('1.1')
  })

  it('offers no input field', () => {
    renderPanel()

    expect(screen.queryAllByRole('textbox')).toEqual([])
  })
})
