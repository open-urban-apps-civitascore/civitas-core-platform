import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { DataStructureSchema } from '@/generated/core'

import type { UMLDiagram } from '../types/diagram'
import {
  buildDiagramExport,
  buildDiagramFileName,
  cleanDiagramForExport,
  downloadDiagramFile,
} from './diagramFileService'
import { SchemaExportError } from './jsonSchemaExportService'

describe('diagramFileService', () => {
  const sampleDiagram: UMLDiagram = {
    id: 'diagram-1',
    name: 'Sample Diagram',
    nodes: [
      {
        id: 'node-1',
        type: 'class',
        position: { x: 50, y: 100 },
        data: {
          element: {
            id: 'node-1',
            name: 'RootClass',
            type: 'class',
            isRoot: true,
            attributes: [
              {
                id: 'attr-1',
                name: 'title',
                type: 'String',
              },
            ],
            operations: [],
          },
          label: 'RootClass',
        },
      },
    ],
    edges: [],
    viewport: { x: 10, y: 20, zoom: 1.5 },
    lastModified: new Date('2026-09-01T12:00:00Z'),
    isDirty: true,
  }

  describe('buildDiagramFileName', () => {
    it('formats filename with sanitized name and version', () => {
      expect(buildDiagramFileName('School Structure', '1.0.0')).toBe('School_Structure-1_0_0.json')
    })

    it('falls back to draft when version is null or undefined or empty', () => {
      expect(buildDiagramFileName('School', null)).toBe('School-draft.json')
      expect(buildDiagramFileName('School', undefined)).toBe('School-draft.json')
      expect(buildDiagramFileName('School', '  ')).toBe('School-draft.json')
    })

    it('falls back to datastructure when name is missing or empty', () => {
      expect(buildDiagramFileName('', '2.0.0')).toBe('datastructure-2_0_0.json')
      expect(buildDiagramFileName(undefined, undefined)).toBe('datastructure-draft.json')
    })

    it('sanitizes special characters to underscores', () => {
      expect(buildDiagramFileName('Lärm-Karte / 2026', 'v1.0')).toBe('L_rm_Karte___2026-v1_0.json')
    })
  })

  describe('cleanDiagramForExport', () => {
    it('removes transient and editor-only fields from diagram, nodes and edges', () => {
      const dirtyDiagram: UMLDiagram = {
        ...sampleDiagram,
        nodes: [
          {
            ...sampleDiagram.nodes[0],
            data: {
              ...sampleDiagram.nodes[0].data,
              isSelected: true,
              isDirty: true,
            },
            measured: { width: 120, height: 80 },
            selected: true,
            dragging: false,
            style: { background: '#fff' },
            dragHandle: '.node-header',
          } as unknown as (typeof sampleDiagram.nodes)[0],
        ],
        edges: [
          {
            id: 'edge-1',
            type: 'composition',
            source: 'node-1',
            target: 'node-1',
            selected: true,
            data: {
              relationship: {
                id: 'rel-1',
                type: 'composition',
                source: 'node-1',
                target: 'node-1',
              },
              isSelected: true,
              isDirty: true,
            },
          } as unknown as (typeof sampleDiagram.edges)[0],
        ],
      }

      const cleaned = cleanDiagramForExport(dirtyDiagram)

      expect(cleaned.lastModified).toBeUndefined()
      expect(cleaned.isDirty).toBeUndefined()

      const cleanedNode = (cleaned.nodes as Record<string, unknown>[])[0]
      expect(cleanedNode.measured).toBeUndefined()
      expect(cleanedNode.selected).toBeUndefined()
      expect(cleanedNode.dragging).toBeUndefined()
      expect(cleanedNode.style).toBeUndefined()
      expect(cleanedNode.dragHandle).toBeUndefined()

      const cleanedNodeData = cleanedNode.data as Record<string, unknown>
      expect(cleanedNodeData.isSelected).toBeUndefined()
      expect(cleanedNodeData.isDirty).toBeUndefined()
      expect(cleanedNodeData.label).toBe('RootClass')

      const cleanedEdge = (cleaned.edges as Record<string, unknown>[])[0]
      expect(cleanedEdge.selected).toBeUndefined()
      const cleanedEdgeData = cleanedEdge.data as Record<string, unknown>
      expect(cleanedEdgeData.isSelected).toBeUndefined()
      expect(cleanedEdgeData.isDirty).toBeUndefined()
    })
  })

  describe('buildDiagramExport', () => {
    it('builds a valid CORE DataStructure document with x-ui-styles', () => {
      const exported = buildDiagramExport(sampleDiagram, {
        dataStructureName: 'MyStructure',
        datastructureId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
      })

      expect(exported.$schema).toBe('https://json-schema.org/draft/2020-12/schema')
      expect(exported.$id).toMatch(/^urn:core:platform:civitas:datastructure:common:MyStructure:/)
      expect(exported['x-ui-styles']).toBeDefined()

      const styles = exported['x-ui-styles'] as Record<string, unknown>
      expect(styles.id).toBe('diagram-1')
      expect(styles.nodes).toHaveLength(1)

      const validation = DataStructureSchema.safeParse(exported)
      expect(validation.success).toBe(true)
    })

    it('throws SchemaExportError when root resolution fails', () => {
      const cycleDiagram: UMLDiagram = {
        id: 'diagram-cycle',
        name: 'Cyclic Diagram',
        nodes: [
          {
            id: 'n-1',
            type: 'class',
            position: { x: 0, y: 0 },
            data: {
              element: { id: 'n-1', name: 'A', type: 'class', attributes: [], operations: [] },
              label: 'A',
            },
          },
          {
            id: 'n-2',
            type: 'class',
            position: { x: 100, y: 0 },
            data: {
              element: { id: 'n-2', name: 'B', type: 'class', attributes: [], operations: [] },
              label: 'B',
            },
          },
        ],
        edges: [
          {
            id: 'e-1',
            type: 'composition',
            source: 'n-1',
            target: 'n-2',
          },
          {
            id: 'e-2',
            type: 'composition',
            source: 'n-2',
            target: 'n-1',
          },
        ],
        lastModified: new Date(),
        isDirty: false,
      }

      expect(() => buildDiagramExport(cycleDiagram)).toThrow(SchemaExportError)
    })
  })

  describe('downloadDiagramFile', () => {
    beforeEach(() => {
      vi.stubGlobal('URL', {
        createObjectURL: vi.fn(() => 'blob:http://localhost/mock-blob-id'),
        revokeObjectURL: vi.fn(),
      })
    })

    afterEach(() => {
      vi.restoreAllMocks()
    })

    it('creates a blob URL and clicks a temporary download anchor', () => {
      const clickMock = vi.fn()
      const createElementSpy = vi.spyOn(window.document, 'createElement').mockReturnValue({
        set href(_val: string) {},
        set download(_val: string) {},
        click: clickMock,
      } as unknown as HTMLAnchorElement)

      const appendChildSpy = vi.spyOn(window.document.body, 'appendChild').mockImplementation(node => node)
      const removeChildSpy = vi.spyOn(window.document.body, 'removeChild').mockImplementation(node => node)

      downloadDiagramFile({ test: true }, 'export.json')

      expect(window.URL.createObjectURL).toHaveBeenCalled()
      expect(createElementSpy).toHaveBeenCalledWith('a')
      expect(appendChildSpy).toHaveBeenCalled()
      expect(clickMock).toHaveBeenCalled()
      expect(removeChildSpy).toHaveBeenCalled()
      expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/mock-blob-id')
    })
  })
})
