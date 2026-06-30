import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { UMLAttribute, UMLClass } from '../../types/uml'
import { AttributeManager } from './AttributeManager'

const updateNode = vi.fn()

vi.mock('../../hooks/use-active-diagram', () => ({
  useActiveDiagram: () => ({ updateNode }),
}))

vi.mock('../../hooks/use-read-only', () => ({
  useReadOnly: () => ({ isReadOnly: false }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const NODE_ID = 'node-1'

const elementWith = (attribute: Partial<UMLAttribute>): UMLClass => ({
  id: 'elem-1',
  name: 'TrafficSensor',
  type: 'class',
  attributes: [{ id: 'a1', name: 'field', type: 'String', visibility: 'public', ...attribute }],
  operations: [],
})

// The Cardinality field is the only BasicSelect labelled "Cardinality"; scope all
// queries to its surrounding container to avoid the Type/Visibility selects.
const cardinalityField = () => screen.getByText('Cardinality').closest('div') as HTMLElement

const openCardinality = () => {
  const combobox = within(cardinalityField()).getByRole('combobox')
  fireEvent.click(combobox)
}

describe('AttributeManager cardinality dropdown', () => {
  beforeEach(() => updateNode.mockClear())

  it('displays an unset multiplicity as "1"', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: undefined })} />)
    expect(within(cardinalityField()).getByText('1')).toBeInTheDocument()
  })

  it('clears the field (writes undefined) when "1" is selected, keeping unset models intact', () => {
    // Start from a non-default value so selecting "1" is a real change Radix reports.
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: '0..*' })} />)
    openCardinality()
    // Two "1" nodes can exist (the live trigger value vs. the listbox option); pick the option.
    const option = screen.getByRole('option', { name: '1' })
    fireEvent.click(option)

    expect(updateNode).toHaveBeenCalledWith(NODE_ID, {
      attributes: [expect.objectContaining({ id: 'a1', multiplicity: undefined })],
    })
  })

  it('persists "0..*" verbatim when selected', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: undefined })} />)
    openCardinality()
    fireEvent.click(screen.getByRole('option', { name: '0..*' }))

    expect(updateNode).toHaveBeenCalledWith(NODE_ID, {
      attributes: [expect.objectContaining({ id: 'a1', multiplicity: '0..*' })],
    })
  })

  it('surfaces a non-dropdown multiplicity ("*") as an extra option so it is not silently lost', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: '*' })} />)
    // The out-of-dropdown value is shown on the trigger rather than rendering blank.
    expect(within(cardinalityField()).getByText('*')).toBeInTheDocument()
    // ...and it remains selectable as its own option alongside the four standard values.
    openCardinality()
    expect(screen.getByRole('option', { name: '*' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: '1..*' })).toBeInTheDocument()
  })
})

describe('AttributeManager primary key', () => {
  beforeEach(() => updateNode.mockClear())

  it('shows the Primary Key checkbox for an exactly-one attribute', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: '1' })} />)
    expect(screen.getByText(/Primary Key/)).toBeInTheDocument()
  })

  it('hides the Primary Key checkbox for an optional (0..1) attribute', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: '0..1' })} />)
    expect(screen.queryByText(/Primary Key/)).not.toBeInTheDocument()
  })

  it('hides the Primary Key checkbox for a multivalued attribute', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ multiplicity: '1..*' })} />)
    expect(screen.queryByText(/Primary Key/)).not.toBeInTheDocument()
  })

  it('clears isId when an attribute becomes non-key-eligible', () => {
    render(<AttributeManager nodeId={NODE_ID} element={elementWith({ isId: true })} />)
    const combobox = within(cardinalityField()).getByRole('combobox')
    fireEvent.click(combobox)
    fireEvent.click(screen.getByRole('option', { name: '0..1' }))

    expect(updateNode).toHaveBeenCalledWith(NODE_ID, {
      attributes: [expect.objectContaining({ id: 'a1', multiplicity: '0..1', isId: false })],
    })
  })
})
