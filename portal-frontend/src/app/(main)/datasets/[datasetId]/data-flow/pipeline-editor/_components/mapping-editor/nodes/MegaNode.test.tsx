import { render, screen } from '@testing-library/react'
import type { NodeProps } from '@xyflow/react'
import { ReactFlowProvider } from '@xyflow/react'
import { NextIntlClientProvider } from 'next-intl'

import messages from '@/messages/de.json'
import { MARKUP_NAME } from '@/test-support/markup'

import type { MegaNodeData } from './MegaNode'
import { MegaNode } from './MegaNode'

describe('MegaNode', () => {
  it('shows a field name with markup characters as text', () => {
    const data: MegaNodeData = {
      role: 'source',
      schemaName: 'Sensor',
      fields: [{ path: '$.field', name: MARKUP_NAME, type: 'str', portType: 'scalar' }],
    }

    const { container } = render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <ReactFlowProvider>
          <MegaNode {...({ id: 'source', data } as unknown as NodeProps)} />
        </ReactFlowProvider>
      </NextIntlClientProvider>,
    )

    expect(screen.getByText(MARKUP_NAME)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })
})
