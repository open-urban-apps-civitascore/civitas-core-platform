/**
 * Shared UML diagram fixtures for the editor conformance and consistency tests.
 *
 * A UMLDiagram is a plain data object, so a fixture needs no provider, store or canvas — the
 * builders below are enough to drive exportToJsonSchema and the schema adapters.
 */

import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'

export interface FixtureAttribute {
  id: string
  name: string
  type?: string
  multiplicity?: string
  isId?: boolean
  documentation?: string
  meta?: { gisInfo: { crs: string } }
}

export const cls = (id: string, name: string, attrs: FixtureAttribute[], options: { isRoot?: boolean } = {}) => ({
  id: `node-${id}`,
  type: 'class' as const,
  position: { x: 0, y: 0 },
  data: {
    element: {
      id,
      name,
      type: 'class' as const,
      attributes: attrs.map(a => ({ visibility: 'public' as const, type: 'String', ...a })),
      operations: [],
      ...(options.isRoot ? { isRoot: true } : {}),
    },
    label: name,
  },
})

export const enm = (id: string, name: string, literals: string[]) => ({
  id: `node-${id}`,
  type: 'enumeration' as const,
  position: { x: 0, y: 0 },
  data: {
    element: {
      id,
      name,
      type: 'enumeration' as const,
      literals: literals.map((literal, index) => ({ id: `${id}-l${index}`, name: literal })),
    },
    label: name,
  },
})

export const rel = (id: string, type: string, source: string, target: string, extra: Record<string, unknown> = {}) => ({
  id: `edge-${id}`,
  type,
  source: `node-${source}`,
  target: `node-${target}`,
  data: {
    relationship: { id: `rel-${id}`, type, source, target, ...extra },
    label: '',
    isSelected: false,
    isDirty: false,
  },
})

export const diagram = (name: string, nodes: unknown[], edges: unknown[]): UMLDiagram =>
  ({ id: 'd', name, nodes, edges, lastModified: new Date('2026-01-01'), isDirty: false }) as unknown as UMLDiagram

/** A versioned DataStructure CORE URN; passing one switches the export to the canonical saved form. */
export const DS_URN = 'urn:core:platform:civitas:datastructure:common:TrafficSensor:abc1234567:1.0.0'

export interface DatastructureFixture {
  label: string
  diagram: UMLDiagram
}

export const datastructureFixtures: DatastructureFixture[] = [
  {
    label: 'single class (its own root)',
    diagram: diagram('TrafficSensor', [cls('sensor', 'TrafficSensor', [{ id: 'a1', name: 'value' }])], []),
  },
  {
    label: 'pure inheritance (diagram name = parent)',
    diagram: diagram(
      'Animal',
      [cls('animal', 'Animal', [{ id: 'a1', name: 'name' }]), cls('dog', 'Dog', [{ id: 'a2', name: 'breed' }])],
      [rel('1', 'inheritance', 'dog', 'animal')],
    ),
  },
  {
    label: 'multi-level inheritance',
    diagram: diagram(
      'Base',
      [
        cls('base', 'Base', [{ id: 'a1', name: 'a' }]),
        cls('mid', 'Mid', [{ id: 'a2', name: 'b' }]),
        cls('leaf', 'Leaf', [{ id: 'a3', name: 'c' }]),
      ],
      [rel('1', 'inheritance', 'mid', 'base'), rel('2', 'inheritance', 'leaf', 'mid')],
    ),
  },
  {
    label: 'multiple inheritance',
    diagram: diagram(
      'P1',
      [
        cls('p1', 'P1', [{ id: 'a1', name: 'x' }]),
        cls('p2', 'P2', [{ id: 'a2', name: 'y' }]),
        cls('leaf', 'Leaf', [{ id: 'a3', name: 'own' }]),
      ],
      [rel('1', 'inheritance', 'leaf', 'p1'), rel('2', 'inheritance', 'leaf', 'p2')],
    ),
  },
  {
    label: 'composition',
    diagram: diagram(
      'Thing',
      [cls('thing', 'Thing', [{ id: 'a1', name: 'id' }]), cls('reading', 'Reading', [{ id: 'a2', name: 'value' }])],
      [rel('1', 'composition', 'reading', 'thing', { sourceRole: 'readings', sourceMultiplicity: '*' })],
    ),
  },
  {
    label: 'composition without role but with relationship name',
    diagram: diagram(
      'Sensor',
      [cls('sensor', 'Sensor', [{ id: 'a1', name: 'id' }]), cls('reading', 'Reading', [{ id: 'a2', name: 'value' }])],
      [rel('1', 'composition', 'reading', 'sensor', { name: 'measurements', sourceMultiplicity: '*' })],
    ),
  },
  {
    label: 'inheritance + composition on the subclass',
    diagram: diagram(
      'Vehicle',
      [
        cls('vehicle', 'Vehicle', [{ id: 'a1', name: 'vin' }]),
        cls('car', 'Car', [{ id: 'a2', name: 'doors' }]),
        cls('engine', 'Engine', [{ id: 'a3', name: 'power' }]),
      ],
      [rel('1', 'inheritance', 'car', 'vehicle'), rel('2', 'composition', 'engine', 'car', { sourceRole: 'engine' })],
    ),
  },
  {
    label: 'inheritance + composition on the parent',
    diagram: diagram(
      'Vehicle',
      [
        cls('vehicle', 'Vehicle', [{ id: 'a1', name: 'vin' }]),
        cls('car', 'Car', [{ id: 'a2', name: 'doors' }]),
        cls('engine', 'Engine', [{ id: 'a3', name: 'power' }]),
      ],
      [
        rel('1', 'inheritance', 'car', 'vehicle'),
        rel('2', 'composition', 'engine', 'vehicle', { sourceRole: 'engine' }),
      ],
    ),
  },
  {
    // Every other multi-class fixture names the diagram after its root class, which lets the model
    // walker's title tier resolve the root by accident. This one does not, so a reader that cannot
    // follow the canonical Element URN has nothing left to fall back on.
    label: 'composition, diagram name differs from the root class',
    diagram: diagram(
      'SensorNetwork',
      [cls('station', 'Station', [{ id: 'a1', name: 'id' }]), cls('reading', 'Reading', [{ id: 'a2', name: 'value' }])],
      [rel('1', 'composition', 'reading', 'station', { sourceRole: 'readings', sourceMultiplicity: '*' })],
    ),
  },

  // The fixtures above vary structure. Those below vary the constructs a single class can carry.
  {
    label: 'every primitive type',
    diagram: diagram(
      'Reading',
      [
        cls('reading', 'Reading', [
          { id: 'a1', name: 'label', type: 'String' },
          { id: 'a2', name: 'count', type: 'Integer' },
          { id: 'a3', name: 'active', type: 'Boolean' },
          { id: 'a4', name: 'value', type: 'Number' },
          { id: 'a5', name: 'day', type: 'Date' },
          { id: 'a6', name: 'takenAt', type: 'DateTime' },
          { id: 'a7', name: 'ref', type: 'Uuid' },
        ]),
      ],
      [],
    ),
  },
  {
    // The editor keeps every geometry attribute of a class on one CRS: only the first attribute's
    // select is enabled and changing it rewrites the others.
    label: 'geometry attributes sharing one CRS',
    diagram: diagram(
      'Site',
      [
        cls('site', 'Site', [
          { id: 'a1', name: 'location', type: 'Point', meta: { gisInfo: { crs: 'EPSG:25832' } } },
          { id: 'a2', name: 'outline', type: 'Polygon', meta: { gisInfo: { crs: 'EPSG:25832' } } },
        ]),
      ],
      [],
    ),
  },
  {
    label: 'every attribute cardinality',
    diagram: diagram(
      'Measurement',
      [
        cls('measurement', 'Measurement', [
          { id: 'a1', name: 'optional', multiplicity: '0..1' },
          { id: 'a2', name: 'exactlyOne', multiplicity: '1' },
          { id: 'a3', name: 'unset' },
          { id: 'a4', name: 'many', multiplicity: '0..*' },
          { id: 'a5', name: 'atLeastOne', multiplicity: '1..*' },
        ]),
      ],
      [],
    ),
  },
  {
    label: 'primary key',
    diagram: diagram(
      'Station',
      [
        cls('station', 'Station', [
          { id: 'a1', name: 'stationId', type: 'Uuid', isId: true },
          { id: 'a2', name: 'name' },
        ]),
      ],
      [],
    ),
  },
  {
    label: 'composite primary key',
    diagram: diagram(
      'Slot',
      [
        cls('slot', 'Slot', [
          { id: 'a1', name: 'stationId', type: 'Uuid', isId: true },
          { id: 'a2', name: 'day', type: 'Date', isId: true },
          { id: 'a3', name: 'value', type: 'Number' },
        ]),
      ],
      [],
    ),
  },
  {
    label: 'enumeration alongside a class',
    diagram: diagram(
      'Device',
      [cls('device', 'Device', [{ id: 'a1', name: 'id' }]), enm('status', 'Status', ['ACTIVE', 'INACTIVE'])],
      [],
    ),
  },
  {
    // No edge embeds either class, so without the designation both would be root candidates.
    label: 'explicitly designated root',
    diagram: diagram(
      'Catalog',
      [
        cls('alpha', 'Alpha', [{ id: 'a1', name: 'code' }], { isRoot: true }),
        cls('beta', 'Beta', [{ id: 'a2', name: 'value' }]),
      ],
      [],
    ),
  },
  {
    label: 'composition with roles and multiplicities on both ends',
    diagram: diagram(
      'Depot',
      [cls('depot', 'Depot', [{ id: 'a1', name: 'id' }]), cls('bay', 'Bay', [{ id: 'a2', name: 'number' }])],
      [
        rel('1', 'composition', 'bay', 'depot', {
          sourceRole: 'bays',
          sourceMultiplicity: '1..*',
          targetRole: 'depot',
          targetMultiplicity: '1',
        }),
      ],
    ),
  },
  {
    // Station composes Reading and Alert; both inherit Measurement.
    label: 'four cross-referencing elements under one root',
    diagram: diagram(
      'SensorNetwork',
      [
        cls('station', 'Station', [{ id: 'a1', name: 'code' }]),
        cls('measurement', 'Measurement', [{ id: 'a2', name: 'takenAt', type: 'DateTime' }]),
        cls('reading', 'Reading', [{ id: 'a3', name: 'value', type: 'Number' }]),
        cls('alert', 'Alert', [{ id: 'a4', name: 'severity' }]),
      ],
      [
        rel('1', 'composition', 'reading', 'station', { sourceRole: 'readings', sourceMultiplicity: '1..*' }),
        rel('2', 'composition', 'alert', 'station', { sourceRole: 'alerts', sourceMultiplicity: '0..*' }),
        rel('3', 'inheritance', 'reading', 'measurement'),
        rel('4', 'inheritance', 'alert', 'measurement'),
      ],
    ),
  },
]
