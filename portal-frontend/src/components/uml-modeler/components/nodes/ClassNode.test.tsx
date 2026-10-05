import { render, screen } from '@testing-library/react'
import type { NodeProps } from '@xyflow/react'
import { ReactFlowProvider } from '@xyflow/react'

import { cls } from '@/test-support/datastructureFixtures'
import { MARKUP_NAME } from '@/test-support/markup'

import type { UMLNode } from '../../types/diagram'
import { ClassNode } from './ClassNode'

let nodeId: string | null = null
let diagramNodes: UMLNode[] = []

vi.mock('@xyflow/react', async importOriginal => ({
  ...(await importOriginal<typeof import('@xyflow/react')>()),
  useNodeId: () => nodeId,
}))

vi.mock('../../hooks/use-active-diagram', () => ({
  useActiveDiagram: () => ({ diagram: { nodes: diagramNodes } }),
}))

const renderNode = (node: ReturnType<typeof cls>, isSelected = false) =>
  render(
    <ReactFlowProvider>
      <ClassNode {...({ id: node.id, data: node.data, selected: isSelected } as unknown as NodeProps)} />
    </ReactFlowProvider>,
  )

const nodeBorder = (container: HTMLElement) => container.querySelector('.uml-node')?.getAttribute('style') ?? ''

describe('ClassNode', () => {
  beforeEach(() => {
    nodeId = null
    diagramNodes = []
  })

  it('shows a class and an attribute name with markup characters as text', () => {
    const node = cls('c1', MARKUP_NAME, [{ id: 'a1', name: MARKUP_NAME }])

    const { container } = renderNode(node)

    expect(screen.getByText(MARKUP_NAME)).toBeInTheDocument()
    expect(screen.getByText(`${MARKUP_NAME}:`, { exact: false })).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('has a red border when the name has invalid characters', () => {
    const node = cls('c1', 'Foo Bar', [])
    nodeId = node.id
    diagramNodes = [node as unknown as UMLNode]

    const { container } = renderNode(node)

    expect(nodeBorder(container)).toContain('var(--destructive)')
  })

  it('has a red border when another element has the same name', () => {
    const node = cls('c1', 'Sensor', [])
    nodeId = node.id
    diagramNodes = [node, cls('c2', 'Sensor', [])] as unknown as UMLNode[]

    const { container } = renderNode(node)

    expect(nodeBorder(container)).toContain('var(--destructive)')
  })

  it('keeps a visible selection shadow next to the red border', () => {
    const node = cls('c1', 'Foo Bar', [])
    nodeId = node.id
    diagramNodes = [node as unknown as UMLNode]

    const { container } = renderNode(node, true)

    expect(nodeBorder(container)).toContain('var(--destructive)')
    expect(nodeBorder(container)).toContain('rgba(0, 102, 204, 0.8)')
  })

  it('has no red border when the name is valid and unique', () => {
    const node = cls('c1', 'Sensor', [])
    nodeId = node.id
    diagramNodes = [node, cls('c2', 'Station', [])] as unknown as UMLNode[]

    const { container } = renderNode(node)

    expect(nodeBorder(container)).not.toContain('var(--destructive)')
  })
})
