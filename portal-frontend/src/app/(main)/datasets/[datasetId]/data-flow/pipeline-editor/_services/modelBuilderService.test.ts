import { describe, expect, it } from 'vitest'

import type { FrostNodeData, PipelineNodeData } from '../_types/nodes'
import { ENTITY_TYPES } from '../_types/nodes'
import type { Pipeline } from '../_types/pipeline'
import { PIPELINE_NODE_TYPES } from '../_types/pipeline'
import { buildRedPandaConnectModel } from './modelBuilderService'

const createNode = (
  id: string,
  type: (typeof PIPELINE_NODE_TYPES)[keyof typeof PIPELINE_NODE_TYPES],
  data: PipelineNodeData,
) => ({
  id,
  type,
  position: { x: 0, y: 0 },
  data,
})

describe('buildRedPandaConnectModel', () => {
  it('uses config-adapter FROST_BASE placeholders instead of env lookups in frost mappings', () => {
    const frostData: FrostNodeData = {
      label: 'Storage',
      configured: true,
      entityType: ENTITY_TYPES.Frost,
      serverName: 'Frost Server',
      serverUrl: 'https://frost.example.com/v1.1',
      version: '1.1',
    }

    const pipeline: Pipeline = {
      name: 'FROST pipeline',
      description: '',
      nodes: [
        createNode('start', PIPELINE_NODE_TYPES.Start, {
          nodeType: PIPELINE_NODE_TYPES.Start,
          label: 'Start',
          configured: true,
          description: 'Entry point',
        }),
        createNode('ds', PIPELINE_NODE_TYPES.DataSource, {
          label: 'Datasource',
          configured: true,
          entityType: ENTITY_TYPES.Datasource,
          entityId: 'datasource-id',
          entityName: 'Datasource',
        }),
        createNode('frost', PIPELINE_NODE_TYPES.Frost, frostData),
        createNode('end', PIPELINE_NODE_TYPES.End, {
          nodeType: PIPELINE_NODE_TYPES.End,
          label: 'End',
          configured: true,
          description: 'Exit point',
        }),
      ],
      edges: [
        { id: 'e1', source: 'start', target: 'ds' },
        { id: 'e2', source: 'ds', target: 'frost' },
        { id: 'e3', source: 'frost', target: 'end' },
      ],
      createdAt: new Date('2026-04-02T00:00:00Z'),
      updatedAt: new Date('2026-04-02T00:00:00Z'),
      isDirty: false,
    }

    const model = buildRedPandaConnectModel(pipeline) as {
      pipeline: { processors: Array<Record<string, unknown>> }
    } | null

    expect(model).not.toBeNull()

    const processors = model?.pipeline.processors ?? []
    const serialized = JSON.stringify(model)
    const mappings = processors
      .flatMap(processor => {
        if (!('switch' in processor) || !Array.isArray(processor.switch)) return []
        return processor.switch.flatMap((branch: Record<string, unknown>) =>
          Array.isArray(branch.processors)
            ? branch.processors
                .filter(
                  (candidate): candidate is { mapping: string } =>
                    typeof candidate === 'object' &&
                    candidate !== null &&
                    'mapping' in candidate &&
                    typeof candidate.mapping === 'string',
                )
                .map(candidate => candidate.mapping)
            : [],
        )
      })
      .join('\n')

    expect(serialized).toContain('${FROST_BASE}/Things?$filter=')
    expect(serialized).toContain('${FROST_BASE}/Datastreams?$filter=')
    expect(serialized).toContain('"Authorization":"Basic YWRtaW46YWRtaW4="')
    expect(mappings).toContain('"${FROST_BASE}/Things(" + ($first."@iot.id").string() + ")"')
    expect(mappings).toContain('"${FROST_BASE}/Things"')
    expect(mappings).toContain('"${FROST_BASE}/Observations"')
    expect(mappings).not.toContain('env("FROST_BASE")')
  })
})
