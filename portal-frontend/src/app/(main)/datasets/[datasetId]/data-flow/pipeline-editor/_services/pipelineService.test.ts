import { describe, expect, it } from 'vitest'

import type { PipelineEdge, PipelineNode } from '../_types/pipeline'
import { PIPELINE_NODE_TYPES } from '../_types/pipeline'
import { omitSelectedKey } from './pipelineService'

const node = (id: string, selected?: boolean): PipelineNode => ({
  id,
  type: PIPELINE_NODE_TYPES.Start,
  position: { x: 1, y: 2 },
  data: { label: 'Start', configured: true, nodeType: PIPELINE_NODE_TYPES.Start, description: '' },
  ...(selected === undefined ? {} : { selected }),
})

const edge = (id: string, selected?: boolean): PipelineEdge => ({
  id,
  source: 'node-a',
  target: 'node-b',
  ...(selected === undefined ? {} : { selected }),
})

describe('omitSelectedKey', () => {
  it.each([true, false])('removes selected: %s from nodes and edges', selected => {
    const result = omitSelectedKey({ nodes: [node('node-a', selected)], edges: [edge('edge-1', selected)] })

    expect(result).toEqual({ nodes: [node('node-a')], edges: [edge('edge-1')] })
    expect(result.nodes[0]).not.toHaveProperty('selected')
    expect(result.edges[0]).not.toHaveProperty('selected')
  })

  it('keeps nodes and edges without selection unchanged', () => {
    const nodes = [node('node-a')]
    const edges = [edge('edge-1')]

    expect(omitSelectedKey({ nodes, edges })).toEqual({ nodes, edges })
  })
})
