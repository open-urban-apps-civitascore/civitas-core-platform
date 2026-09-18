import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import type { FrostNodeData } from '../../../_types/nodes'
import { FrostPanel } from './FrostPanel'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

// The select is a Radix listbox, and jsdom implements neither pointer capture nor scrollIntoView.
// Without these the list never opens and every interaction test fails for the environment rather
// than for the component. Kept local: this is the first Radix select under test here.
beforeAll(() => {
  Element.prototype.hasPointerCapture = vi.fn(() => false)
  Element.prototype.setPointerCapture = vi.fn()
  Element.prototype.releasePointerCapture = vi.fn()
  Element.prototype.scrollIntoView = vi.fn()
})

const nodeData: FrostNodeData = {
  label: 'Sensor Data Storage',
  configured: false,
  entityType: 'frost',
  serverName: 'Sensor Data Storage',
  serverUrl: '',
  version: '1.1',
}

const renderPanel = (overrides: Partial<FrostNodeData> = {}, onUpdate = vi.fn()) => {
  render(<FrostPanel data={{ ...nodeData, ...overrides }} onUpdate={onUpdate} />)
  return onUpdate
}

describe('FrostPanel', () => {
  it('offers no port on a new node', () => {
    renderPanel()

    // An empty port is an error state, not an empty field: the platform must not pick the logic.
    expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('frostPanel.portPlaceholder')).toBeInTheDocument()
  })

  it('lists the ports grouped by logic class', async () => {
    renderPanel()

    await userEvent.click(screen.getByRole('combobox'))

    expect(screen.getByText('frostPanel.logic.ownKey')).toBeInTheDocument()
    expect(screen.getByText('frostPanel.logic.parentReference')).toBeInTheDocument()
    expect(screen.getByText('frostPanel.logic.composite')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Things/ })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /ThingTree/ })).toBeInTheDocument()
  })

  it('shows what each port costs for each record', async () => {
    renderPanel()

    await userEvent.click(screen.getByRole('combobox'))

    // The cost is what lets the modeller choose without knowing the implementation.
    expect(screen.getByText('frostPanel.ports.ThingTree.cost')).toBeInTheDocument()
  })

  it('marks the node configured when a port is selected', async () => {
    const onUpdate = renderPanel()

    await userEvent.click(screen.getByRole('combobox'))
    await userEvent.click(screen.getByRole('option', { name: /Observations/ }))

    expect(onUpdate).toHaveBeenCalledWith({ port: 'Observations', configured: true })
  })

  it('shows the description and the references of the selected port', () => {
    renderPanel({ port: 'Observations', configured: true })

    expect(screen.getByText('frostPanel.ports.Observations.description')).toBeInTheDocument()
    expect(screen.getByText('frostPanel.expects')).toBeInTheDocument()
    expect(screen.getByText('frostPanel.ports.Observations.expects')).toBeInTheDocument()
  })

  it('shows no port summary while no port is selected', () => {
    renderPanel()

    expect(screen.queryByText('frostPanel.expects')).not.toBeInTheDocument()
  })
})
