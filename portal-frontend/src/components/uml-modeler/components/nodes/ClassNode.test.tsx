import { render, screen } from '@testing-library/react'
import type { NodeProps } from '@xyflow/react'
import { ReactFlowProvider } from '@xyflow/react'

import { cls } from '@/test-support/datastructureFixtures'
import { MARKUP_NAME } from '@/test-support/markup'

import { ClassNode } from './ClassNode'

describe('ClassNode', () => {
  it('shows a class and an attribute name with markup characters as text', () => {
    const node = cls('c1', MARKUP_NAME, [{ id: 'a1', name: MARKUP_NAME }])

    const { container } = render(
      <ReactFlowProvider>
        <ClassNode {...({ id: node.id, data: node.data } as unknown as NodeProps)} />
      </ReactFlowProvider>,
    )

    expect(screen.getByText(MARKUP_NAME)).toBeInTheDocument()
    expect(screen.getByText(`${MARKUP_NAME}:`, { exact: false })).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })
})
