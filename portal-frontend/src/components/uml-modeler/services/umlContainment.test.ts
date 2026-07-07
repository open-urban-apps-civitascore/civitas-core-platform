import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import type { UMLElement, UMLRelationship } from '../types/uml'
import {
  classifyStructuralEdge,
  collectContainedIds,
  collectParentIds,
  isManyMultiplicity,
  parseMultiplicity,
  selectRootElements,
} from './umlContainment'

const relationship = (over: Partial<UMLRelationship> & Pick<UMLRelationship, 'type' | 'source' | 'target'>) =>
  ({ id: `${over.type}-${over.source}-${over.target}`, ...over }) as UMLRelationship

const edge = (rel: UMLRelationship) => ({
  id: rel.id,
  source: rel.source,
  target: rel.target,
  data: { relationship: rel },
})

const diagram = (edges: ReturnType<typeof edge>[]): UMLDiagram => ({ edges }) as unknown as UMLDiagram

const element = (id: string, name: string, type: UMLElement['type'] = 'class') => ({ id, name, type }) as UMLElement

describe('classifyStructuralEdge', () => {
  it('orients composition with the container at the target', () => {
    const c = classifyStructuralEdge(
      relationship({
        type: 'composition',
        source: 'part',
        target: 'whole',
        sourceRole: 'parts',
        sourceMultiplicity: '*',
      }),
    )
    expect(c).toEqual({ containerId: 'whole', partId: 'part', role: 'parts', multiplicity: '*', isMany: true })
  })

  it('returns null for every out-of-scope and non-structural type', () => {
    for (const type of ['association', 'aggregation', 'inheritance', 'realization', 'dependency'] as const) {
      expect(classifyStructuralEdge(relationship({ type, source: 'a', target: 'b' }))).toBeNull()
    }
  })
})

describe('parseMultiplicity / isManyMultiplicity', () => {
  it('parses bounds and treats >1 upper as many', () => {
    expect(parseMultiplicity('*')).toEqual({ lower: 0, upper: Infinity })
    expect(parseMultiplicity('1..5')).toEqual({ lower: 1, upper: 5 })
    expect(parseMultiplicity(undefined)).toEqual({ lower: 1, upper: 1 })
    expect(isManyMultiplicity('2..5')).toBe(true)
    expect(isManyMultiplicity('0..1')).toBe(false)
    expect(isManyMultiplicity('*')).toBe(true)
  })

  it('falls back to the UML default for blank or whitespace-only input instead of 0..0', () => {
    expect(parseMultiplicity('')).toEqual({ lower: 1, upper: 1 })
    expect(parseMultiplicity('   ')).toEqual({ lower: 1, upper: 1 })
  })

  it('treats bounded collections (not just *) as many', () => {
    expect(isManyMultiplicity('2')).toBe(true)
    expect(isManyMultiplicity('1..5')).toBe(true)
    expect(isManyMultiplicity('1')).toBe(false)
  })
})

describe('collectContainedIds', () => {
  it('embeds the part side of every structural edge and inheritance/realization parents', () => {
    const d = diagram([
      edge(relationship({ type: 'composition', source: 'part', target: 'whole' })),
      edge(relationship({ type: 'inheritance', source: 'sub', target: 'parent' })),
      // The association target becomes a property of the source, so it is embedded as well.
      edge(relationship({ type: 'association', source: 'order', target: 'item' })),
      edge(relationship({ type: 'aggregation', source: 'wheel', target: 'car' })),
    ])
    const contained = collectContainedIds(d)
    expect([...contained].sort()).toEqual(['item', 'parent', 'part'])
  })
})

describe('collectParentIds', () => {
  it('returns inheritance parents in edge order; ignores realization', () => {
    const d = diagram([
      edge(relationship({ type: 'inheritance', source: 'sub', target: 'a' })),
      edge(relationship({ type: 'realization', source: 'sub', target: 'b' })),
      edge(relationship({ type: 'inheritance', source: 'other', target: 'c' })),
    ])
    expect(collectParentIds(d, 'sub')).toEqual(['a'])
    expect(collectParentIds(d, 'other')).toEqual(['c'])
  })
})

describe('selectRootElements', () => {
  const els = [element('p', 'Parent'), element('s', 'Sub'), element('e', 'Status', 'enumeration')]

  it('excludes embedded and enumeration elements', () => {
    expect(selectRootElements(els, new Set(['p'])).map(e => e.name)).toEqual(['Sub'])
  })

  it('returns every unconnected class as a root, in node order', () => {
    expect(selectRootElements(els, new Set()).map(e => e.name)).toEqual(['Parent', 'Sub'])
  })

  it('falls back to all classes for fully embedded (cyclic) diagrams', () => {
    expect(selectRootElements(els, new Set(['p', 's'])).map(e => e.name)).toEqual(['Parent', 'Sub'])
  })

  it('falls back to the enumerations for an enumeration-only diagram', () => {
    const enums = [element('e1', 'Status', 'enumeration'), element('e2', 'Kind', 'enumeration')]
    expect(selectRootElements(enums, new Set()).map(e => e.name)).toEqual(['Status', 'Kind'])
  })
})
