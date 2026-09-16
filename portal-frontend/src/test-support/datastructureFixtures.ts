/**
 * Shared UML diagram fixtures for the editor conformance and consistency tests.
 *
 * A UMLDiagram is a plain data object, so a fixture needs no provider, store or canvas — the
 * builders below are enough to drive exportToJsonSchema and the schema adapters.
 */

import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'

export const cls = (id: string, name: string, attrs: { id: string; name: string; type?: string }[]) => ({
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
]
