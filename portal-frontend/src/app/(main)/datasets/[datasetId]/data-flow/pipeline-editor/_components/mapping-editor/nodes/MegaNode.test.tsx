import { render, screen } from '@testing-library/react'
import type { NodeProps } from '@xyflow/react'

import { MARKUP_NAME } from '@/test-support/markup'

import { field } from '../_types'
import { MegaNode, type MegaNodeData } from './MegaNode'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@xyflow/react', () => ({
  useUpdateNodeInternals: () => vi.fn(),
}))

vi.mock('@/components/node-editor', async importOriginal => ({
  ...(await importOriginal<typeof import('@/components/node-editor')>()),
  PortHandle: ({ id, portType, side, status }: { id: string; portType: string; side: string; status?: string }) => (
    <span
      data-testid="port-handle"
      data-field-id={id}
      data-port-type={portType}
      data-side={side}
      data-status={status}
    />
  ),
}))

const fields = [
  field('name', 'name', 'str', true),
  field('age', 'age', 'int', false),
  field('point', 'point', 'Point', false),
  field('address', 'address', 'object', false, [field('address.zip', 'zip', 'number', false)]),
]

const renderNode = (data: MegaNodeData) => render(<MegaNode {...({ id: 'node-1', data } as unknown as NodeProps)} />)

const hasHeaderText = (expected: string) => (_content: string, element: Element | null) =>
  element?.textContent === expected

describe('MegaNode', () => {
  it('renders a source node as the input side, with handles on the right', () => {
    renderNode({ role: 'source', schemaName: 'My Schema', fields })

    expect(screen.getByText(hasHeaderText('input · My Schema'))).toBeInTheDocument()
    const handles = screen.getAllByTestId('port-handle')
    expect(handles.length).toBe(5)
    handles.forEach(handle => expect(handle).toHaveAttribute('data-side', 'right'))
  })

  it('renders a target node as the output side, with handles on the left', () => {
    renderNode({ role: 'target', schemaName: 'Target Schema', fields })

    expect(screen.getByText(hasHeaderText('output · Target Schema'))).toBeInTheDocument()
    const handles = screen.getAllByTestId('port-handle')
    expect(handles.length).toBe(5)
    handles.forEach(handle => expect(handle).toHaveAttribute('data-side', 'left'))
  })

  it('appends the data structure version to the header when present', () => {
    renderNode({ role: 'source', schemaName: 'My Schema', version: '4.0.0', fields })

    expect(screen.getByText(hasHeaderText('input · My Schema · v4.0.0'))).toBeInTheDocument()
  })

  it('omits the version segment from the header for a draft (no version yet)', () => {
    renderNode({ role: 'source', schemaName: 'My Schema', version: null, fields })

    expect(screen.getByText(hasHeaderText('input · My Schema'))).toBeInTheDocument()
  })

  it('lists each row with its own field type, including nested fields', () => {
    renderNode({ role: 'source', schemaName: 'Schema', fields })

    expect(screen.getByText('str')).toBeInTheDocument()
    expect(screen.getByText('int')).toBeInTheDocument()
    expect(screen.getByText('Point')).toBeInTheDocument()
    expect(screen.getByText('object')).toBeInTheDocument()
    expect(screen.getByText('number')).toBeInTheDocument()
  })

  it('shows a field name with markup characters as text', () => {
    const data: MegaNodeData = {
      role: 'source',
      schemaName: 'Sensor',
      fields: [{ path: '$.field', name: MARKUP_NAME, type: 'str', portType: 'scalar' }],
    }

    const { container } = renderNode(data)

    expect(screen.getByText(MARKUP_NAME)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('divides every entity from what precedes it, including one entity from the next', () => {
    const twoEntities = [
      field('name', 'name', 'str', true),
      field('address', 'address', 'object', false, [field('address.zip', 'zip', 'number', false)]),
      field('billing', 'billing', 'object', false, [field('billing.zip', 'zip', 'number', false)]),
    ]
    const { container } = renderNode({ role: 'source', schemaName: 'Schema', fields: twoEntities })

    // One divider before `address` (attribute -> entity) and one before `billing` (entity -> entity).
    expect(container.querySelectorAll('.border-t.border-border').length).toBe(2)
  })
})
