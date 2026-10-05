import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { UMLNode } from '../../types/diagram'
import type { UMLElement } from '../../types/uml'
import { NodePropertyEditor } from './NodePropertyEditor'

const updateNode = vi.fn()
const setRootNode = vi.fn()
let isReadOnly = false
let diagramNodes: UMLNode[] = []

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('../../hooks/use-active-diagram', () => ({
  useActiveDiagram: () => ({ diagram: { nodes: diagramNodes }, updateNode, setRootNode }),
}))

vi.mock('../../hooks/use-read-only', () => ({
  useReadOnly: () => ({ isReadOnly }),
}))

const node = (element: Partial<UMLElement>): UMLNode =>
  ({
    id: 'node-1',
    type: 'class',
    position: { x: 0, y: 0 },
    data: {
      element: { id: 'elem-1', name: 'Thing', type: 'class', attributes: [], operations: [], ...element },
      label: 'Thing',
    },
  }) as unknown as UMLNode

const rootCheckbox = () => screen.getByRole('checkbox', { name: /root class/i })

describe('NodePropertyEditor root checkbox', () => {
  beforeEach(() => {
    updateNode.mockClear()
    setRootNode.mockClear()
    isReadOnly = false
  })

  it('designates the node on check', () => {
    render(<NodePropertyEditor node={node({})} />)
    fireEvent.click(rootCheckbox())
    expect(setRootNode).toHaveBeenCalledWith('node-1')
  })

  it('clears the designation entirely on uncheck', () => {
    render(<NodePropertyEditor node={node({ isRoot: true })} />)
    expect(rootCheckbox()).toBeChecked()
    fireEvent.click(rootCheckbox())
    expect(setRootNode).toHaveBeenCalledWith(null)
  })

  it('is hidden for enumerations', () => {
    render(<NodePropertyEditor node={node({ type: 'enumeration', literals: [] } as Partial<UMLElement>)} />)
    expect(screen.queryByRole('checkbox', { name: /root class/i })).not.toBeInTheDocument()
  })

  it('is disabled in read-only mode', () => {
    isReadOnly = true
    render(<NodePropertyEditor node={node({})} />)
    expect(rootCheckbox()).toBeDisabled()
  })
})

describe('NodePropertyEditor name validation', () => {
  beforeEach(() => {
    isReadOnly = false
    diagramNodes = []
  })

  it('shows no error for a valid unique name', () => {
    render(<NodePropertyEditor node={node({})} />)
    expect(screen.getByPlaceholderText('Element name')).toHaveAttribute('aria-invalid', 'false')
    expect(screen.queryByText('invalidCharacters')).not.toBeInTheDocument()
  })

  it('shows the character error for a name with a space', () => {
    render(<NodePropertyEditor node={node({ name: 'Foo Bar' })} />)
    expect(screen.getByText('invalidCharacters')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Element name')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows the duplicate error when another class has the same name', () => {
    const other = { ...node({}), id: 'node-2' } as UMLNode
    diagramNodes = [node({}), other]
    render(<NodePropertyEditor node={node({})} />)
    expect(screen.getByText('duplicateElement')).toBeInTheDocument()
  })

  it('shows the duplicate error on an enumeration named like a class', () => {
    const enumeration = node({ type: 'enumeration', literals: [] } as Partial<UMLElement>)
    diagramNodes = [enumeration, { ...node({}), id: 'node-2' } as UMLNode]
    render(<NodePropertyEditor node={enumeration} />)
    expect(screen.getByText('duplicateElement')).toBeInTheDocument()
  })

  it('shows the character error on an enumeration', () => {
    render(
      <NodePropertyEditor
        node={node({ type: 'enumeration', name: 'Neue Aufzählung', literals: [] } as Partial<UMLElement>)}
      />,
    )
    expect(screen.getByText('invalidCharacters')).toBeInTheDocument()
  })

  it('shows the required error for an empty name', () => {
    render(<NodePropertyEditor node={node({ name: '' })} />)
    expect(screen.getByText('required')).toBeInTheDocument()
  })
})
