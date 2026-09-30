import { describe, expect, it } from 'vitest'

import { exportToJsonSchema, SchemaExportError } from '@/components/uml-modeler/services/jsonSchemaExportService'
import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { cls, datastructureFixtures, diagram } from '@/test-support/datastructureFixtures'

import type { FieldNode } from '../_types'
import { umlDiagramToSchemaTree } from './adapter'
import { modelToSchemaTree } from './modelAdapter'

const treeOf = (diagram: UMLDiagram, fallbackName: string) => umlDiagramToSchemaTree(diagram, fallbackName).tree

const fixtures = datastructureFixtures

type JsonSchema = {
  title?: string
  properties?: Record<string, unknown>
  allOf?: JsonSchema[]
  $ref?: string
  $defs?: Record<string, JsonSchema>
}

/** Top-level property names of a generated schema node, following allOf branches and local $refs. */
const resolveTopLevelProperties = (node: JsonSchema | undefined, defs: Record<string, JsonSchema>): string[] => {
  if (!node) return []
  const names: string[] = []
  const push = (name: string) => {
    if (!names.includes(name)) names.push(name)
  }
  for (const branch of node.allOf ?? []) resolveTopLevelProperties(branch, defs).forEach(push)
  if (node.$ref?.startsWith('#/$defs/')) {
    resolveTopLevelProperties(defs[node.$ref.slice('#/$defs/'.length)], defs).forEach(push)
  }
  Object.keys(node.properties ?? {}).forEach(push)
  return names
}

/** The single root class the document root designates: the `$defs` key of its root `$ref`. */
const rootClassOf = (schema: JsonSchema): string | undefined => {
  // Canonical form: the root carries a bare top-level $ref (a local #/$defs/ pointer) to the root member.
  if (schema.$ref?.startsWith('#/$defs/')) return schema.$ref.slice('#/$defs/'.length)
  // Legacy wrapper form: root.properties = { <name>: { $ref: '#/$defs/<root>' } }.
  const ref = Object.values(schema.properties ?? {})
    .map(value => (value as JsonSchema).$ref)
    .find((r): r is string => !!r?.startsWith('#/$defs/'))
  return ref?.slice('#/$defs/'.length)
}

describe('root-selection consistency (adapter vs generator)', () => {
  // The generator's document root is the (virtual) data structure, titled after the diagram; it is
  // not itself a class. The editor adapter still roots on the single root class, which the generator
  // references via its one $ref property — so the adapter root equals that referenced class.
  it.each(fixtures)('agrees on the root class for $label', ({ diagram }) => {
    const adapterRoot = treeOf(diagram, diagram.name).name
    const generatorRootClass = rootClassOf(exportToJsonSchema(diagram) as JsonSchema)
    expect(adapterRoot).toBe(generatorRootClass)
  })

  it.each(fixtures)('agrees on the root field set for $label', ({ diagram }) => {
    const adapterFields = treeOf(diagram, diagram.name).fields.map(f => f.name)
    const schema = exportToJsonSchema(diagram) as JsonSchema
    const defs = schema.$defs ?? {}
    const rootClass = rootClassOf(schema)
    // Compare against the referenced root class's fields, not the virtual root (which only holds the
    // single $ref property).
    const generatorFields = resolveTopLevelProperties(rootClass ? defs[rootClass] : undefined, defs)
    expect([...adapterFields].sort()).toEqual([...generatorFields].sort())
  })
})

const leafPaths = (fields: FieldNode[] | undefined): string[] =>
  (fields ?? []).flatMap(field => (field.children?.length ? leafPaths(field.children) : [field.path]))

describe('model-walker consistency (modelToSchemaTree vs adapter)', () => {
  // The model walker reads the generated schema; its `$` node is the resolved root class, so its
  // subtree must expose exactly the leaf paths the diagram adapter derives directly — the paths are
  // the mapping contract and must not depend on which representation the editor happens to read.
  it.each(fixtures)('agrees on root class and leaf paths for $label', ({ diagram }) => {
    const adapterTree = treeOf(diagram, diagram.name)
    const modelTree = modelToSchemaTree(exportToJsonSchema(diagram), diagram.name)

    expect(modelTree.name).toBe(diagram.name)
    expect(modelTree.fields).toHaveLength(1)
    expect(modelTree.fields[0].path).toBe('$')
    expect(modelTree.fields[0].name).toBe(adapterTree.name)
    expect(leafPaths(modelTree.fields[0].children).sort()).toEqual(leafPaths(adapterTree.fields).sort())
  })

  it('rejects a diagram with several unconnected roots consistently in export and adapter', () => {
    const multiRoot = diagram(
      'TrafficSensor',
      [
        cls('building', 'Building', [{ id: 'a1', name: 'floors' }]),
        cls('street', 'Street', [{ id: 'a2', name: 'name' }]),
      ],
      [],
    )
    // The export refuses to persist a model for the invalid diagram, and the adapter's fallback
    // tree agrees by exposing nothing mappable — neither side invents a record shape.
    expect(() => exportToJsonSchema(multiRoot)).toThrow(SchemaExportError)
    expect(treeOf(multiRoot, multiRoot.name)).toEqual({ name: 'TrafficSensor', fields: [] })
  })
})
