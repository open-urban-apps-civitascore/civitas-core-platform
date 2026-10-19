import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ReadOnlyProvider } from '../../hooks/use-read-only'
import { ElementPalette } from './ElementPalette'

vi.mock('@xyflow/react', () => ({
  useReactFlow: () => ({ fitView: vi.fn() }),
}))

vi.mock('../../hooks/use-active-diagram', () => ({
  useActiveDiagram: () => ({ activeRelationshipType: null, setActiveRelationshipType: vi.fn() }),
}))

const renderPalette = (isReadOnly: boolean) =>
  render(
    <ReadOnlyProvider isReadOnly={isReadOnly}>
      <ElementPalette />
    </ReadOnlyProvider>,
  )

describe('ElementPalette', () => {
  it('renders nothing in read-only mode', () => {
    const { container } = renderPalette(true)

    expect(container).toBeEmptyDOMElement()
  })

  it('shows the expanded palette when the modeler is editable', () => {
    renderPalette(false)

    expect(screen.getByText('Elements')).toBeInTheDocument()
  })

  it('shows the expanded palette after leaving read-only mode', () => {
    const { rerender } = renderPalette(true)

    rerender(
      <ReadOnlyProvider isReadOnly={false}>
        <ElementPalette />
      </ReadOnlyProvider>,
    )

    expect(screen.getByText('Elements')).toBeInTheDocument()
  })
})
