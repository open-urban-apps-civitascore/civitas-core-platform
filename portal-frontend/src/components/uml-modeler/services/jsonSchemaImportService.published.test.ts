import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import { exportToJsonSchema } from './jsonSchemaExportService'
import { diagramFromJsonSchema, importFromJsonSchema } from './jsonSchemaImportService'
import { mergeStructureIntoDiagram } from './structureMergeService'

/**
 * The documents the platform actually publishes, read from where the backend ships them.
 *
 * The hand-written fixtures next door prove each mapping rule in isolation; they cannot prove that
 * the generator and the reader agree, because both are written from the same idea of the format.
 * These read the real files, so a shape the generator emits and the reader does not know fails
 * here instead of in front of a modeller.
 */
const publishedStructure = (name: string): Record<string, unknown> =>
  JSON.parse(
    readFileSync(
      join(
        process.cwd(),
        '..',
        'portal-backend',
        'src',
        'main',
        'resources',
        'frost',
        'port-structure',
        `${name}.json`,
      ),
      'utf-8',
    ),
  )

describe.each(['things', 'observations', 'thing-tree'])('the published structure %s', name => {
  const document = publishedStructure(name)

  it('reads into classes with a root', () => {
    const imported = importFromJsonSchema(document)

    expect(imported.nodes.length).toBeGreaterThan(0)
    expect(imported.rootElementId).not.toBeNull()
  })

  it('exports again to the document it came from', () => {
    // Not byte-identical to the published file: that one carries the platform's own $id and a
    // wrapper root, while the export writes its canonical form. What has to survive is the
    // content — the classes, their fields and their relations — so the check is export-to-export.
    const once = exportToJsonSchema(diagramFromJsonSchema(document, name))
    const twice = exportToJsonSchema(diagramFromJsonSchema(once, name))

    expect(twice).toEqual(once)
  })
})

describe('the ThingTree structure', () => {
  it('carries the whole chain and its references', () => {
    const imported = importFromJsonSchema(publishedStructure('thing-tree'))
    const names = imported.nodes.map(node => node.data.element.name)

    expect(names).toContain('Thing')
    expect(names).toContain('Datastream')
    expect(names).toContain('Sensor')
    expect(names).toContain('ObservedProperty')
    expect(names).toContain('Observation')
    // Every class but the root hangs under another one, so the export accepts the diagram.
    expect(imported.edges.length).toBeGreaterThanOrEqual(names.length - 1)
  })

  it('merges into a diagram that is already there, and that diagram still exports', () => {
    const existing: UMLDiagram = {
      id: 'd1',
      name: 'Sensors',
      nodes: [
        {
          id: 'e1',
          type: 'class',
          position: { x: 0, y: 0 },
          data: {
            element: { id: 'e1', name: 'Reading', type: 'class', attributes: [], operations: [], isRoot: true },
            label: 'Reading',
          },
        } as unknown as UMLDiagram['nodes'][number],
      ],
      edges: [],
      lastModified: new Date(0),
      isDirty: false,
    }

    const result = mergeStructureIntoDiagram(existing, publishedStructure('thing-tree'), {
      urn: 'urn:core:platform:civitas:datastructure:frost:ThingTree:r32e0hmgn1:1.0.0',
      name: 'ThingTree',
    })
    const merged: UMLDiagram = {
      ...existing,
      nodes: [...existing.nodes, ...result.nodes],
      edges: [...existing.edges, ...result.edges],
    }

    // This is the modeller's click: the loaded chain hangs under what was there, and the whole
    // diagram is still exportable.
    expect(exportToJsonSchema(merged).$ref).toBe('#/$defs/Reading')
  })

  it('reads a free JSON value as an opaque type, not as text', () => {
    const imported = importFromJsonSchema(publishedStructure('thing-tree'))
    const location = imported.nodes
      .map(node => node.data.element)
      .flatMap(element => (element.type === 'class' ? element.attributes : []))
      .find(attribute => attribute.name === 'location')

    // A geometry is not a string: reading it as one would change the document on the next save.
    expect(typeof location?.type).toBe('object')
  })
})
