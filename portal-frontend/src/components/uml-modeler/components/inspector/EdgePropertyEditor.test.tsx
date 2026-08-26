import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { UMLDiagram, UMLEdge } from '../../types/diagram'
import type { UMLElementType } from '../../types/uml'
import { EdgePropertyEditor } from './EdgePropertyEditor'

const node = (id: string, type: UMLElementType) => ({
  id,
  type,
  position: { x: 0, y: 0 },
  data: { element: { id, name: id, type, attributes: [], operations: [] }, label: id },
})

const diagram = {
  nodes: [node('Quality', 'enumeration'), node('Whole', 'class'), node('Other', 'class')],
  edges: [],
} as unknown as UMLDiagram

vi.mock('../../hooks/use-active-diagram', () => ({
  useActiveDiagram: () => ({ diagram, updateEdge: vi.fn() }),
}))

vi.mock('../../hooks/use-read-only', () => ({
  useReadOnly: () => ({ isReadOnly: false }),
}))

const edge = (source: string, target: string): UMLEdge =>
  ({
    id: 'edge-1',
    source,
    target,
    type: 'composition',
    data: { relationship: { id: 'rel-1', type: 'composition', source, target }, label: '' },
  }) as unknown as UMLEdge

/** The relationship-type select is the first one in the editor; its label is not wired to it. */
const typeOptionValues = (container: HTMLElement): string[] => {
  const select = container.querySelector('select')
  return Array.from(select?.querySelectorAll('option') ?? []).map(option => option.value)
}

describe('EdgePropertyEditor relationship type options', () => {
  it('does not offer inheritance for a composition between an enumeration and a class', () => {
    const { container } = render(<EdgePropertyEditor edge={edge('Quality', 'Whole')} />)

    // Retyping must obey the same rules as drawing: an enumeration can be composed into a class,
    // but it can neither inherit nor be inherited from.
    expect(typeOptionValues(container)).toEqual(['composition'])
  })

  it('offers both types between two classes', () => {
    const { container } = render(<EdgePropertyEditor edge={edge('Other', 'Whole')} />)

    expect(typeOptionValues(container)).toEqual(expect.arrayContaining(['composition', 'inheritance']))
  })
})
