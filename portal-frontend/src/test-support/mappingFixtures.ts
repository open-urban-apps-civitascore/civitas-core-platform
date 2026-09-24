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

const megaNodes = (...targets: FieldNode[]): Node[] => [
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
    data: { role: 'target', schemaName: 'tgt', fields: targets },
  },
]

const transformNode = (id: string, operation: Exclude<ContractOperation, 'copy'>, config = {}): Node => {
  const def = transformDef(operation)
  return {
    id,
    type: 'transform',
    position: { x: 380, y: 40 },
    data: { defType: operation, config, inputs: def.inputs, outputs: def.outputs },
  }
}

const out = (operation: Exclude<ContractOperation, 'copy'>) => transformDef(operation).outputs[0].id

const input = (operation: Exclude<ContractOperation, 'copy'>, index: number) => transformDef(operation).inputs[index].id

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
): MappingFixture => ({
  operation,
  targetTree: { name: 'tgt', fields: [target] },
  targetPath: target.path,
  nodes: [...megaNodes(target), transformNode(operation, operation, config)],
  edges: [
    ...sourcePaths.map((path, index) => wire(SOURCE_NODE_ID, path, operation, input(operation, index))),
    wire(operation, out(operation), TARGET_NODE_ID, target.path),
  ],
})

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

export interface NestedFixture {
  label: string
  targetTree: SchemaTree
  nodes: Node[]
  edges: Edge[]
}

const PATTERN = 'yyyy-MM-dd'

const nested = (label: string, targets: FieldNode[], transforms: Node[], edges: Edge[]): NestedFixture => ({
  label,
  targetTree: { name: 'tgt', fields: targets },
  nodes: [...megaNodes(...targets), ...transforms],
  edges,
})

const fullCode = field('$.fullCode', 'fullCode', 'str', false)
const geometry = field('$.geometry', 'geometry', 'Point', false)
const printed = field('$.printed', 'printed', 'str', false)
const text = field('$.text', 'text', 'str', false)

export const nestedFixtures: NestedFixture[] = [
  nested(
    'three levels',
    [fullCode],
    [
      transformNode('toDate', 'toDate', { pattern: PATTERN }),
      transformNode('format', 'format', { pattern: PATTERN }),
      transformNode('concat', 'concat', { separator: '-' }),
    ],
    [
      wire(SOURCE_NODE_ID, '$.day', 'toDate', input('toDate', 0)),
      wire('toDate', out('toDate'), 'format', input('format', 0)),
      wire('format', out('format'), 'concat', input('concat', 0)),
      wire(SOURCE_NODE_ID, '$.suffix', 'concat', input('concat', 1)),
      wire('concat', out('concat'), TARGET_NODE_ID, fullCode.path),
    ],
  ),
  nested(
    'both fixed inputs of geoPoint',
    [geometry],
    [transformNode('lonConv', 'toFloat'), transformNode('latConv', 'toFloat'), transformNode('geoPoint', 'geoPoint')],
    [
      wire(SOURCE_NODE_ID, '$.longitude', 'lonConv', input('toFloat', 0)),
      wire(SOURCE_NODE_ID, '$.latitude', 'latConv', input('toFloat', 0)),
      wire('lonConv', out('toFloat'), 'geoPoint', input('geoPoint', 0)),
      wire('latConv', out('toFloat'), 'geoPoint', input('geoPoint', 1)),
      wire('geoPoint', out('geoPoint'), TARGET_NODE_ID, geometry.path),
    ],
  ),
  nested(
    'one transform chain feeding two target fields',
    [printed, text],
    [transformNode('toDate', 'toDate', { pattern: PATTERN }), transformNode('format', 'format', { pattern: PATTERN })],
    [
      wire(SOURCE_NODE_ID, '$.day', 'toDate', input('toDate', 0)),
      wire('toDate', out('toDate'), 'format', input('format', 0)),
      wire('format', out('format'), TARGET_NODE_ID, printed.path),
      wire('format', out('format'), TARGET_NODE_ID, text.path),
    ],
  ),
]
