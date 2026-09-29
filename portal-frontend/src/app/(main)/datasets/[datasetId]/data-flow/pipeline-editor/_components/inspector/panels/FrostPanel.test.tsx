import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import type { FrostNodeData } from '../../../_types/nodes'
import { FrostPanel } from './FrostPanel'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const portStructure = vi.fn()
vi.mock('@/app/services/api/published-structures/clientRequests', () => ({
  useGetPublishedStructure: (options: { structureKey?: string }) => portStructure(options),
}))

beforeEach(() => {
  portStructure.mockReturnValue({ data: undefined })
})

/** The structure a port publishes, in the shape the generator writes. */
const observationsModel = {
  title: 'Observations',
  type: 'object',
  properties: { observation: { $ref: '#/$defs/Observation' } },
  $defs: {
    Observation: {
      type: 'object',
      title: 'Observation',
      properties: {
        result: {},
        resultTime: { type: 'string', format: 'date-time' },
        parameters: { $ref: '#/$defs/ObservationParameters' },
      },
      required: ['result', 'parameters'],
    },
    ObservationParameters: {
      type: 'object',
      title: 'ObservationParameters',
      properties: {
        reference: { type: 'string', 'x-core-primaryKey': true },
        thingReference: { type: 'string' },
      },
      required: ['thingReference'],
    },
  },
}

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

  it('shows the structure the selected port publishes', () => {
    portStructure.mockReturnValue({ data: { data: observationsModel } })
    renderPanel({ port: 'Observations', configured: true })

    expect(screen.getByText('frostPanel.ports.Observations.description')).toBeInTheDocument()
    expect(screen.getByText('result')).toBeInTheDocument()
    expect(screen.getByText('thingReference')).toBeInTheDocument()
    // The data type of a field.
    expect(screen.getByText('datetime')).toBeInTheDocument()
    // The field the port finds the entity by is marked as such. Under the test's identity
    // translator the marker renders as the same word as the field's own name, so it appears twice.
    expect(screen.getAllByText('reference')).toHaveLength(2)
    // Three mandatory fields: result, parameters and thingReference.
    expect(screen.getAllByText('*')).toHaveLength(3)
  })

  it('names the references while the structure is not there', () => {
    // A failed or pending request must not leave the panel empty: what the port resolves is known
    // here, and a modeller who reads it can start.
    renderPanel({ port: 'Observations', configured: true })

    expect(screen.getByText('frostPanel.expects')).toBeInTheDocument()
    expect(screen.getByText('frostPanel.ports.Observations.expects')).toBeInTheDocument()
  })

  it('shows no port summary while no port is selected', () => {
    renderPanel()

    expect(screen.queryByText('frostPanel.expects')).not.toBeInTheDocument()
  })
})
