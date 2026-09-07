import { describe, expect, it } from 'vitest'

import type { DatastructureVersion } from '@/types/datastructures'
import { buildSessionFromVersion } from '@/utils/datastructures'

import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type { UMLElement } from '../types/uml'
import { exportToJsonSchema } from './jsonSchemaExportService'
import { importDiagramFromJsonSchema } from './jsonSchemaImportService'
import { resolveRootElement } from './umlContainment'

const node = (element: UMLElement): UMLNode => ({
  id: element.id,
  type: element.type,
  position: { x: 0, y: 0 },
  data: { element, label: element.name },
})

const edge = (relationship: UMLEdge['data']['relationship']): UMLEdge => ({
  id: `edge-${relationship.id}`,
  type: relationship.type,
  source: relationship.source,
  target: relationship.target,
  data: { relationship },
})

/**
 * A diagram exercising every reversible mapping at once: primitives with
 * multiplicities/defaults/PK, a geometry with CRS, an enum-typed attribute, an
 * external reference, single and many compositions, and inheritance.
 */
const richDiagram = (): UMLDiagram => {
  const station: UMLElement = {
    id: 'st',
    name: 'Station',
    type: 'class',
    documentation: 'A measuring station',
    isRoot: true,
    attributes: [
      { id: 'a1', name: 'stationId', type: 'String', isId: true },
      { id: 'a2', name: 'label', type: 'String', multiplicity: '0..1', defaultValue: 'unbenannt' },
      { id: 'a3', name: 'tags', type: 'String', multiplicity: '*' },
      { id: 'a4', name: 'segments', type: 'Integer', multiplicity: '2..5' },
      { id: 'a5', name: 'position', type: 'Point', meta: { gisInfo: { crs: 'EPSG:4326' } } },
      { id: 'a6', name: 'quality', type: { id: 'qual', name: 'Quality' } },
      {
        id: 'a7',
        name: 'sensorSpec',
        type: { id: 'x1', name: 'spec.json', isExternal: true, href: 'https://example.org/spec.json' },
      },
      { id: 'a8', name: 'installedAt', type: 'DateTime', multiplicity: '0..1' },
    ],
    operations: [],
  }
  const series: UMLElement = {
    id: 'se',
    name: 'Messreihe',
    type: 'class',
    attributes: [{ id: 'b1', name: 'unit', type: 'String' }],
    operations: [],
  }
  const address: UMLElement = {
    id: 'ad',
    name: 'Adresse',
    type: 'class',
    attributes: [{ id: 'c1', name: 'street', type: 'String', multiplicity: '0..1' }],
    operations: [],
  }
  const base: UMLElement = {
    id: 'ba',
    name: 'Messpunkt',
    type: 'class',
    attributes: [{ id: 'd1', name: 'createdAt', type: 'DateTime' }],
    operations: [],
  }
  const quality: UMLElement = {
    id: 'qual',
    name: 'Quality',
    type: 'enumeration',
    literals: [
      { id: 'l1', name: 'good', value: 'good' },
      { id: 'l2', name: 'bad', value: 'bad' },
    ],
  }

  return {
    id: 'diagram-1',
    name: 'Luftstation',
    nodes: [node(station), node(series), node(address), node(base), node(quality)],
    edges: [
      // Station contains many Messreihen (diamond/container at the TARGET end).
      edge({
        id: 'r1',
        type: 'composition',
        source: 'se',
        target: 'st',
        sourceRole: 'messreihen',
        sourceMultiplicity: '1..*',
      }),
      // Station has exactly one optional Adresse.
      edge({
        id: 'r2',
        type: 'composition',
        source: 'ad',
        target: 'st',
        sourceRole: 'adresse',
        sourceMultiplicity: '0..1',
      }),
      // Station inherits from Messpunkt.
      edge({ id: 'r3', type: 'inheritance', source: 'st', target: 'ba' }),
    ],
    lastModified: new Date('2026-01-01'),
    isDirty: false,
  }
}

describe('importDiagramFromJsonSchema', () => {
  it('round-trips: export → import → export yields the identical schema (local-ref form)', () => {
    const original = exportToJsonSchema(richDiagram())
    const imported = importDiagramFromJsonSchema(original as Record<string, unknown>)
    expect(imported).not.toBeNull()
    const reExported = exportToJsonSchema(imported as UMLDiagram)
    expect(reExported).toEqual(original)
  })

  it('round-trips the canonical URN-stamped form', () => {
    const urn = 'urn:core:platform:civitas:datastructure:common:Luftstation:0123456789:1.0.0'
    const original = exportToJsonSchema(richDiagram(), urn)
    const imported = importDiagramFromJsonSchema(original as Record<string, unknown>)
    expect(imported).not.toBeNull()
    const reExported = exportToJsonSchema(imported as UMLDiagram, urn)
    expect(reExported).toEqual(original)
  })

  it('restores the root designation so root resolution needs no re-derivation', () => {
    const schema = exportToJsonSchema(richDiagram()) as Record<string, unknown>
    const imported = importDiagramFromJsonSchema(schema) as UMLDiagram
    const flagged = imported.nodes.filter(n => n.data.element.isRoot === true)
    expect(flagged).toHaveLength(1)
    expect(flagged[0].data.element.name).toBe('Station')
    const resolution = resolveRootElement(imported)
    expect(resolution.kind).toBe('class')
  })

  it('draws class references as composition edges and enum references as typed attributes', () => {
    const schema = exportToJsonSchema(richDiagram()) as Record<string, unknown>
    const imported = importDiagramFromJsonSchema(schema) as UMLDiagram

    const compositionRoles = imported.edges
      .filter(e => e.data.relationship.type === 'composition')
      .map(e => e.data.relationship.sourceRole)
      .sort()
    expect(compositionRoles).toEqual(['adresse', 'messreihen'])

    const station = imported.nodes.find(n => n.data.element.name === 'Station')?.data.element
    expect(station && 'attributes' in station).toBe(true)
    const quality = station && 'attributes' in station ? station.attributes.find(a => a.name === 'quality') : undefined
    expect(quality).toBeDefined()
    expect(typeof quality?.type).not.toBe('string')
    expect((quality?.type as { name: string }).name).toBe('Quality')
  })

  it('returns null for models without drawable members', () => {
    expect(importDiagramFromJsonSchema({})).toBeNull()
    expect(importDiagramFromJsonSchema({ $defs: {} })).toBeNull()
    expect(importDiagramFromJsonSchema({ title: 'x', type: 'object', properties: {} })).toBeNull()
  })
})

describe('buildSessionFromVersion hydration gate', () => {
  const version = (overrides: Partial<DatastructureVersion>): DatastructureVersion =>
    ({
      id: 'v1',
      version: '1.0.0',
      description: '',
      dataStructureVersionStatus: 'AVAILABLE',
      dataStructureVersionSource: 'IMPORTED',
      modelName: 'Luftstation',
      model: null,
      styles: null,
      ...overrides,
    }) as DatastructureVersion

  it('hydrates a diagram from the model when no styles exist', () => {
    const model = exportToJsonSchema(richDiagram()) as Record<string, unknown>
    const session = buildSessionFromVersion(version({ model }))
    expect(session.diagram.nodes.length).toBe(5)
    expect(session.isDirty).toBe(false)
  })

  it('never touches an existing hand-drawn diagram', () => {
    const styles = richDiagram()
    const model = exportToJsonSchema(richDiagram()) as Record<string, unknown>
    const session = buildSessionFromVersion(version({ styles, model }))
    // The session carries the drawn diagram verbatim (same node ids), not a hydrated copy.
    expect(session.diagram.nodes.map(n => n.id)).toEqual(styles.nodes.map(n => n.id))
  })

  it('stays an empty session when neither styles nor model exist', () => {
    const session = buildSessionFromVersion(version({}))
    expect(session.diagram.nodes).toEqual([])
  })
})
