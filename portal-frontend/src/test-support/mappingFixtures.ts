/**
 * Shared canvas fixtures for the Mapping editor conformance tests — one per supported operation.
 *
 * Ports and output handles come from the transform registry, not from the fixture, so a test built
 * on these proves the registry's own shape rather than a second copy of it.
 */

import type { Edge, Node } from '@xyflow/react'

import type {
  FieldNode,
  SchemaTree,
} from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/_types'
import { field } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/_types'
import {
  SOURCE_NODE_ID,
  TARGET_NODE_ID,
} from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/compile'
import { transformDef } from '@/app/(main)/datasets/[datasetId]/data-flow/pipeline-editor/_components/mapping-editor/transforms'

/** Every field operation mapping.schema.json defines. */
export const CONTRACT_OPERATIONS = [
  'copy',
  'concat',
  'const',
  'toString',
  'toInt',
  'toFloat',
  'toDate',
  'format',
  'geoPoint',
] as const

export type ContractOperation = (typeof CONTRACT_OPERATIONS)[number]

export const sourceTree: SchemaTree = {
  name: 'src',
  fields: [
    field('$.id', 'id', 'int', false),
    field('$.suffix', 'suffix', 'str', false),
    field('$.name', 'name', 'str', false),
    field('$.longitude', 'longitude', 'number', false),
    field('$.latitude', 'latitude', 'number', false),
    field('$.day', 'day', 'str', false),
    field('$.when', 'when', 'datetime', false),
  ],
}

export interface MappingFixture {
  operation: ContractOperation
  targetTree: SchemaTree
  targetPath: string
  nodes: Node[]
  edges: Edge[]
}

const wire = (source: string, sourceHandle: string, target: string, targetHandle: string): Edge => ({
  id: `${source}:${sourceHandle}->${target}:${targetHandle}`,
  source,
  sourceHandle,
  target,
  targetHandle,
})

const megaNodes = (target: FieldNode): Node[] => [
  {
    id: SOURCE_NODE_ID,
    type: 'mega',
    position: { x: 0, y: 0 },
    data: { role: 'source', schemaName: sourceTree.name, fields: sourceTree.fields },
  },
  {
    id: TARGET_NODE_ID,
    type: 'mega',
    position: { x: 760, y: 0 },
    data: { role: 'target', schemaName: 'tgt', fields: [target] },
  },
]

/** A direct source-to-target edge: the one operation without a canvas node of its own. */
const copyFixture = (target: FieldNode, sourcePath: string): MappingFixture => ({
  operation: 'copy',
  targetTree: { name: 'tgt', fields: [target] },
  targetPath: target.path,
  nodes: megaNodes(target),
  edges: [wire(SOURCE_NODE_ID, sourcePath, TARGET_NODE_ID, target.path)],
})

const opFixture = (
  operation: Exclude<ContractOperation, 'copy'>,
  target: FieldNode,
  sourcePaths: string[],
  config: Record<string, unknown> = {},
): MappingFixture => {
  const def = transformDef(operation)
  return {
    operation,
    targetTree: { name: 'tgt', fields: [target] },
    targetPath: target.path,
    nodes: [
      ...megaNodes(target),
      {
        id: operation,
        type: 'transform',
        position: { x: 380, y: 40 },
        data: { defType: operation, config, inputs: def.inputs, outputs: def.outputs },
      },
    ],
    edges: [
      ...sourcePaths.map((path, index) => wire(SOURCE_NODE_ID, path, operation, def.inputs[index].id)),
      wire(operation, def.outputs[0].id, TARGET_NODE_ID, target.path),
    ],
  }
}

export const mappingFixtures: MappingFixture[] = [
  copyFixture(field('$.title', 'title', 'str', false), '$.name'),
  opFixture('concat', field('$.fullCode', 'fullCode', 'str', false), ['$.id', '$.suffix'], { separator: '-' }),
  opFixture('const', field('$.label', 'label', 'str', false), [], { value: 'fixed', type: 'String' }),
  opFixture('toString', field('$.text', 'text', 'str', false), ['$.id']),
  opFixture('toInt', field('$.count', 'count', 'int', false), ['$.id']),
  opFixture('toFloat', field('$.value', 'value', 'number', false), ['$.id']),
  opFixture('toDate', field('$.observedOn', 'observedOn', 'date', false), ['$.day'], { pattern: 'yyyy-MM-dd' }),
  opFixture('format', field('$.printed', 'printed', 'str', false), ['$.when'], { pattern: 'yyyy-MM-dd' }),
  opFixture('geoPoint', field('$.geometry', 'geometry', 'Point', false), ['$.longitude', '$.latitude']),
]
