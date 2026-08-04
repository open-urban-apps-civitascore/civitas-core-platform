import { describe, expect, it, vi } from 'vitest'

import type { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import type { DatastructureVersion } from '@/types/datastructures'

import { versionToSchemaTree } from './versionTree'

const model = {
  title: 'FromModel',
  type: 'object',
  properties: { thing: { $ref: '#/$defs/Thing' } },
  $defs: { Thing: { type: 'object', properties: { modelField: { type: 'string' } } } },
}

const brokenModel = {
  title: 'Broken',
  type: 'object',
  properties: { thing: { $ref: '#/$defs/Missing' } },
  $defs: {},
}

const styles = {
  id: 'd',
  name: 'FromDiagram',
  nodes: [
    {
      id: 'cls',
      type: 'class',
      position: { x: 0, y: 0 },
      data: {
        element: {
          id: 'cls',
          name: 'FromDiagram',
          type: 'class',
          attributes: [{ id: 'a', name: 'diagramField', type: 'String', visibility: 'public' }],
          operations: [],
        },
        label: 'FromDiagram',
      },
    },
  ],
  edges: [],
} as unknown as UMLDiagram

const version = (over: Partial<Pick<DatastructureVersion, 'model' | 'styles'>>) =>
  ({ model: null, styles: null, ...over }) as Pick<DatastructureVersion, 'model' | 'styles'>

describe('versionToSchemaTree', () => {
  it('prefers the persisted model over the diagram when both exist', () => {
    const { tree, isModelBroken } = versionToSchemaTree(version({ model, styles }), 'fallback')
    expect(isModelBroken).toBe(false)
    expect(tree.name).toBe('FromModel')
    expect(tree.fields[0].children?.map(f => f.name)).toEqual(['modelField'])
  })

  it('falls back to the diagram and flags the broken model', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { tree, isModelBroken } = versionToSchemaTree(version({ model: brokenModel, styles }), 'fallback')
    expect(isModelBroken).toBe(true)
    expect(tree.name).toBe('FromDiagram')
    expect(tree.fields.map(f => f.name)).toEqual(['diagramField'])
    expect(warn).toHaveBeenCalled()
    warn.mockRestore()
  })

  it('uses the diagram without a broken flag when no model exists (draft)', () => {
    const { tree, isModelBroken } = versionToSchemaTree(version({ styles }), 'fallback')
    expect(isModelBroken).toBe(false)
    expect(tree.name).toBe('FromDiagram')
  })

  it('yields an empty tree when neither model nor diagram exist', () => {
    expect(versionToSchemaTree(version({}), 'fallback')).toEqual({
      tree: { name: 'fallback', fields: [] },
      isModelBroken: false,
      diagramFailure: null,
    })
    expect(versionToSchemaTree(undefined, 'fallback')).toEqual({
      tree: { name: 'fallback', fields: [] },
      isModelBroken: false,
      diagramFailure: null,
    })
  })

  it('surfaces the root failure of a rootless diagram saved without a model', () => {
    const ambiguous = {
      ...styles,
      nodes: [
        styles.nodes[0],
        {
          ...styles.nodes[0],
          id: 'cls2',
          data: {
            ...styles.nodes[0].data,
            element: { ...styles.nodes[0].data.element, id: 'cls2', name: 'Second' },
          },
        },
      ],
    } as unknown as UMLDiagram

    const { tree, diagramFailure } = versionToSchemaTree(version({ styles: ambiguous }), 'fallback')
    expect(tree.fields).toEqual([])
    expect(diagramFailure).toEqual({ code: 'ambiguousRoot', candidateNames: ['FromDiagram', 'Second'] })
  })
})
