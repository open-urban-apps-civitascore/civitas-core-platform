import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import type { UMLElement, UMLRelationship } from '../types/uml'
import {
  classifyStructuralEdge,
  collectContainedIds,
  collectParentIds,
  isManyMultiplicity,
  parseMultiplicity,
  selectRootElement,
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
  it('orients composition/aggregation with the container at the target', () => {
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

  it('keeps association in its drawn direction', () => {
    const c = classifyStructuralEdge(
      relationship({
        type: 'association',
        source: 'order',
        target: 'item',
        targetRole: 'items',
        targetMultiplicity: '1',
      }),
    )
    expect(c).toMatchObject({ containerId: 'order', partId: 'item', role: 'items', isMany: false })
  })

  it('returns null for inheritance, realization and dependency edges', () => {
    for (const type of ['inheritance', 'realization', 'dependency'] as const) {
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
  it('embeds composition/aggregation parts and inheritance/realization parents, not associations', () => {
    const d = diagram([
      edge(relationship({ type: 'composition', source: 'part', target: 'whole' })),
      edge(relationship({ type: 'inheritance', source: 'sub', target: 'parent' })),
      edge(relationship({ type: 'association', source: 'order', target: 'item' })),
    ])
    const contained = collectContainedIds(d)
    expect([...contained].sort()).toEqual(['parent', 'part'])
  })
})

describe('collectParentIds', () => {
  it('returns inheritance/realization parents of one element in edge order', () => {
    const d = diagram([
      edge(relationship({ type: 'inheritance', source: 'sub', target: 'a' })),
      edge(relationship({ type: 'realization', source: 'sub', target: 'b' })),
      edge(relationship({ type: 'inheritance', source: 'other', target: 'c' })),
    ])
    expect(collectParentIds(d, 'sub')).toEqual(['a', 'b'])
    expect(collectParentIds(d, 'other')).toEqual(['c'])
  })
})

describe('selectRootElement', () => {
  const els = [element('p', 'Parent'), element('s', 'Sub'), element('e', 'Status', 'enumeration')]

  it('excludes embedded and enumeration elements', () => {
    expect(selectRootElement(els, new Set(['p']), undefined)?.name).toBe('Sub')
  })

  it('prefers the primary name, then the secondary, then the first candidate', () => {
    expect(selectRootElement(els, new Set(), 'sub')?.name).toBe('Sub')
    expect(selectRootElement(els, new Set(), undefined, 'parent')?.name).toBe('Parent')
    expect(selectRootElement(els, new Set(), undefined)?.name).toBe('Parent')
  })
})
