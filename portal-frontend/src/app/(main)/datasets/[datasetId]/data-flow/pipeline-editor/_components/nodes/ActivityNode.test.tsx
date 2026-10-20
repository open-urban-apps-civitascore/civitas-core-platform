import { render, screen } from '@testing-library/react'
import type { NodeProps } from '@xyflow/react'
import { ReactFlowProvider } from '@xyflow/react'
import { NextIntlClientProvider } from 'next-intl'

import messages from '@/messages/de.json'
import { MARKUP_NAME } from '@/test-support/markup'
import { mappingNode } from '@/test-support/pipelineFixtures'

import { PIPELINE_COLORS } from '../../_constants/pipelineStyles'
import { ActivePipelineContext } from '../../_hooks/use-active-pipeline'
import type { ActivePipelineContextValue } from '../../_types/context'
import { PIPELINE_NODE_TYPES } from '../../_types/pipeline'
import { createActivityNode } from './ActivityNode'

const MappingActivityNode = createActivityNode(PIPELINE_NODE_TYPES.Mapping)

const renderNode = (label: string, severity: 'error' | 'warning' | 'none' = 'none') => {
  const node = mappingNode({ data: { label, configured: false } })
  const contextValue = { getNodeValidationSeverity: () => severity } as unknown as ActivePipelineContextValue

  return render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <ActivePipelineContext.Provider value={contextValue}>
        <ReactFlowProvider>
          <MappingActivityNode {...({ id: node.id, data: node.data } as unknown as NodeProps)} />
        </ReactFlowProvider>
      </ActivePipelineContext.Provider>
    </NextIntlClientProvider>,
  )
}

describe('ActivityNode', () => {
  it('shows a node label with markup characters as text', () => {
    const { container } = renderNode(MARKUP_NAME)

    expect(screen.getByText(MARKUP_NAME)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('marks a node with a validation error with a red border and no badge', () => {
    const { container } = renderNode('Test Mapping', 'error')

    expect(container.querySelector('.pipeline-node')).toHaveStyle({
      border: `2px solid ${PIPELINE_COLORS.validationError}`,
    })
    expect(screen.queryByText('!')).not.toBeInTheDocument()
  })

  it('marks a node with a validation warning with a yellow border and no badge', () => {
    const { container } = renderNode('Test Mapping', 'warning')

    expect(container.querySelector('.pipeline-node')).toHaveStyle({
      border: `2px solid ${PIPELINE_COLORS.validationWarning}`,
    })
    expect(screen.queryByText('⚠')).not.toBeInTheDocument()
  })

  it('shows no validation border for a valid node', () => {
    const { container } = renderNode('Test Mapping')

    expect(container.querySelector('.pipeline-node')).not.toHaveStyle({
      border: `2px solid ${PIPELINE_COLORS.validationError}`,
    })
    expect(container.querySelector('.pipeline-node')).not.toHaveStyle({
      border: `2px solid ${PIPELINE_COLORS.validationWarning}`,
    })
  })
})
