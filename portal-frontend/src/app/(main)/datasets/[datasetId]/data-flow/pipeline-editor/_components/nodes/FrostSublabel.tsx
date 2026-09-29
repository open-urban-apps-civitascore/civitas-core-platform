'use client'

/**
 * FrostSublabel — renders the selected port under the sink node's label.
 *
 * The node keeps its name, because the node is the sink. What changes between two FROST nodes is
 * the port, so that is what the canvas shows.
 */

import { isFrostNodeData, type PipelineNodeData } from '../../_types/nodes'

export const FrostSublabel: React.FC<{ data: PipelineNodeData }> = ({ data }) =>
  isFrostNodeData(data) ? (data.port ?? null) : null
