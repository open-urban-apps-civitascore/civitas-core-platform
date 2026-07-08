import { describe, expect, it } from 'vitest'

import type { UMLDiagram } from '../types/diagram'
import type { UMLElement, UMLRelationship } from '../types/uml'
import {
  classifyStructuralEdge,
  collectContainedIds,
  collectParentIds,
  isManyMultiplicity,
  parseMultiplicity,
  resolveRootElement,
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

const element = (id: string, name: string, type: UMLElement['type'] = 'class') =>
  ({
    id,
    name,
    type,
    ...(type === 'enumeration' ? { literals: [] } : { attributes: [], operations: [] }),
  }) as UMLElement

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

describe('resolveRootElement', () => {
  const node = (el: UMLElement) => ({ id: el.id, data: { element: el } })
  const fullDiagram = (elements: UMLElement[], edges: ReturnType<typeof edge>[] = []): UMLDiagram =>
    ({ nodes: elements.map(node), edges }) as unknown as UMLDiagram

  it('resolves the single non-embedded class and reaches parts and inheritance parents', () => {
    const els = [element('r', 'Root'), element('p', 'Part'), element('b', 'Base')]
    const d = fullDiagram(els, [
      edge(relationship({ type: 'composition', source: 'p', target: 'r' })),
      edge(relationship({ type: 'inheritance', source: 'r', target: 'b' })),
    ])
    expect(resolveRootElement(d)).toEqual({ kind: 'class', root: els[0] })
  })

  it('reaches an element referenced only as an attribute type', () => {
    const status = element('e', 'Status', 'enumeration')
    const root = {
      ...element('r', 'Root'),
      attributes: [{ id: 'a1', name: 'status', type: { id: 'e' } }],
    } as UMLElement
    expect(resolveRootElement(fullDiagram([root, status]))).toEqual({ kind: 'class', root })
  })

  it('rejects several root candidates as ambiguous instead of picking one', () => {
    const d = fullDiagram([element('p', 'Parent'), element('s', 'Sub')])
    expect(resolveRootElement(d)).toEqual({
      kind: 'invalid',
      failure: { code: 'ambiguousRoot', candidateNames: ['Parent', 'Sub'] },
    })
  })

  it('rejects a fully embedded (cyclic) diagram as having no root', () => {
    const d = fullDiagram(
      [element('a', 'A'), element('b', 'B')],
      [
        edge(relationship({ type: 'composition', source: 'a', target: 'b' })),
        edge(relationship({ type: 'composition', source: 'b', target: 'a' })),
      ],
    )
    expect(resolveRootElement(d)).toEqual({ kind: 'invalid', failure: { code: 'noRoot' } })
  })

  it('rejects two containers sharing a part as ambiguous', () => {
    const els = [element('r', 'Root'), element('p', 'Part'), element('x', 'Stray')]
    const d = fullDiagram(els, [
      edge(relationship({ type: 'composition', source: 'p', target: 'r' })),
      edge(relationship({ type: 'composition', source: 'p', target: 'x' })),
    ])
    // Part hangs under both Root and Stray, so nothing is fully unconnected — Stray itself is
    // still not reachable from Root and must be flagged.
    expect(resolveRootElement(d)).toEqual({
      kind: 'invalid',
      failure: { code: 'ambiguousRoot', candidateNames: ['Root', 'Stray'] },
    })
  })

  it('rejects a sibling subclass of the root as a second candidate', () => {
    const els = [element('r', 'Root'), element('b', 'Base'), element('s', 'Sub')]
    const d = fullDiagram(els, [
      edge(relationship({ type: 'inheritance', source: 'r', target: 'b' })),
      edge(relationship({ type: 'inheritance', source: 's', target: 'b' })),
    ])
    // Both Root and Sub are non-embedded — ambiguous before reachability even matters.
    expect(resolveRootElement(d)).toEqual({
      kind: 'invalid',
      failure: { code: 'ambiguousRoot', candidateNames: ['Root', 'Sub'] },
    })
  })

  it('flags an isolated enumeration as unreachable from the root', () => {
    const els = [element('r', 'Root'), element('e', 'Status', 'enumeration')]
    expect(resolveRootElement(fullDiagram(els))).toEqual({
      kind: 'invalid',
      failure: { code: 'unreachable', rootName: 'Root', unreachableNames: ['Status'] },
    })
  })

  it('keeps the single-enumeration diagram as an enum root, rejects several enums', () => {
    const status = element('e1', 'Status', 'enumeration')
    expect(resolveRootElement(fullDiagram([status]))).toEqual({ kind: 'enum', root: status })

    const enums = [status, element('e2', 'Kind', 'enumeration')]
    expect(resolveRootElement(fullDiagram(enums))).toEqual({
      kind: 'invalid',
      failure: { code: 'ambiguousRoot', candidateNames: ['Status', 'Kind'] },
    })
  })

  it('returns empty for a diagram without elements', () => {
    expect(resolveRootElement(fullDiagram([]))).toEqual({ kind: 'empty' })
  })

  it('lets the designated root (isRoot) resolve an otherwise ambiguous diagram', () => {
    const alpha = { ...element('a', 'Alpha'), isRoot: true } as UMLElement
    const beta = element('b', 'Beta')
    // Both classes are non-embedded, but Beta must still hang under the designated root.
    const d = fullDiagram([alpha, beta], [edge(relationship({ type: 'association', source: 'a', target: 'b' }))])
    expect(resolveRootElement(d)).toEqual({ kind: 'class', root: alpha })
  })

  it('checks reachability from the designated root too', () => {
    const alpha = { ...element('a', 'Alpha'), isRoot: true } as UMLElement
    const beta = element('b', 'Beta')
    expect(resolveRootElement(fullDiagram([alpha, beta]))).toEqual({
      kind: 'invalid',
      failure: { code: 'unreachable', rootName: 'Alpha', unreachableNames: ['Beta'] },
    })
  })

  it('rejects several designated roots (corrupt persisted state) as ambiguous', () => {
    const alpha = { ...element('a', 'Alpha'), isRoot: true } as UMLElement
    const beta = { ...element('b', 'Beta'), isRoot: true } as UMLElement
    expect(resolveRootElement(fullDiagram([alpha, beta]))).toEqual({
      kind: 'invalid',
      failure: { code: 'ambiguousRoot', candidateNames: ['Alpha', 'Beta'] },
    })
  })

  it('prefers the designated root over the containment derivation', () => {
    const part = { ...element('p', 'Part'), isRoot: true } as UMLElement
    const whole = element('w', 'Whole')
    // The flagged class is embedded; its container is then unreachable from it — surfaced as such
    // rather than silently ignoring the designation.
    const d = fullDiagram([whole, part], [edge(relationship({ type: 'composition', source: 'p', target: 'w' }))])
    expect(resolveRootElement(d)).toEqual({
      kind: 'invalid',
      failure: { code: 'unreachable', rootName: 'Part', unreachableNames: ['Whole'] },
    })
  })
})
