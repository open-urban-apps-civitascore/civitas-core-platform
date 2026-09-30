import { render, screen } from '@testing-library/react'
import type { NodeProps } from '@xyflow/react'
import { ReactFlowProvider } from '@xyflow/react'
import { NextIntlClientProvider } from 'next-intl'

import messages from '@/messages/de.json'
import { MARKUP_NAME } from '@/test-support/markup'
import { mappingNode } from '@/test-support/pipelineFixtures'

import { PIPELINE_NODE_TYPES } from '../../_types/pipeline'
import { createActivityNode } from './ActivityNode'

describe('ActivityNode', () => {
  it('shows a node label with markup characters as text', () => {
    const MappingActivityNode = createActivityNode(PIPELINE_NODE_TYPES.Mapping)
    const node = mappingNode({ data: { label: MARKUP_NAME, configured: false } })

    const { container } = render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <ReactFlowProvider>
          <MappingActivityNode {...({ id: node.id, data: node.data } as unknown as NodeProps)} />
        </ReactFlowProvider>
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(MARKUP_NAME)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })
})
