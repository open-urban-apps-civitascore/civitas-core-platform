import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { DataStructureSchema } from '@/generated/core'

import type { UMLDiagram } from '../types/diagram'
import type { UMLClass } from '../types/uml'
import {
  buildDiagramExport,
  buildDiagramFileName,
  cleanDiagramForExport,
  DiagramImportError,
  downloadDiagramFile,
  MAX_IMPORT_FILE_SIZE_BYTES,
  readDiagramFile,
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
            data: {
              relationship: {
                id: 'rel-1',
                type: 'composition',
                source: 'n-1',
                target: 'n-2',
              },
            },
          },
          {
            id: 'e-2',
            type: 'composition',
            source: 'n-2',
            target: 'n-1',
            data: {
              relationship: {
                id: 'rel-2',
                type: 'composition',
                source: 'n-2',
                target: 'n-1',
              },
            },
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

  describe('readDiagramFile', () => {
    it('rejects with FILE_TOO_LARGE when file exceeds MAX_IMPORT_FILE_SIZE_BYTES', async () => {
      const oversizedFile = new File(['{}'], 'oversized.json', { type: 'application/json' })
      Object.defineProperty(oversizedFile, 'size', { value: MAX_IMPORT_FILE_SIZE_BYTES + 1 })

      await expect(readDiagramFile(oversizedFile)).rejects.toThrow(DiagramImportError)
      await expect(readDiagramFile(oversizedFile)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'FILE_TOO_LARGE',
      })
    })

    it('rejects with INVALID_JSON when file contains malformed JSON', async () => {
      const malformedFile = new File(['{ invalid json content'], 'broken.json', { type: 'application/json' })

      await expect(readDiagramFile(malformedFile)).rejects.toThrow(DiagramImportError)
      await expect(readDiagramFile(malformedFile)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'INVALID_JSON',
      })
    })

    it('rejects with INVALID_DATASTRUCTURE_DOCUMENT when JSON root is not an object', async () => {
      const arrayFile = new File(['[1, 2, 3]'], 'array.json', { type: 'application/json' })
      await expect(readDiagramFile(arrayFile)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'INVALID_DATASTRUCTURE_DOCUMENT',
      })

      const primitiveFile = new File(['"not an object"'], 'primitive.json', { type: 'application/json' })
      await expect(readDiagramFile(primitiveFile)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'INVALID_DATASTRUCTURE_DOCUMENT',
      })
    })

    it('rejects with INVALID_DATASTRUCTURE_DOCUMENT when JSON violates CORE DataStructureSchema', async () => {
      const invalidCoreFile = new File([JSON.stringify({ notAValidCoreDocument: true })], 'invalid-core.json', {
        type: 'application/json',
      })

      await expect(readDiagramFile(invalidCoreFile)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'INVALID_DATASTRUCTURE_DOCUMENT',
      })
    })

    it('rejects with MISSING_UI_STYLES when valid CORE document lacks x-ui-styles', async () => {
      const exportedDoc = buildDiagramExport(sampleDiagram)
      const { 'x-ui-styles': _styles, ...docWithoutStyles } = exportedDoc

      const file = new File([JSON.stringify(docWithoutStyles)], 'no-styles.json', { type: 'application/json' })

      await expect(readDiagramFile(file)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'MISSING_UI_STYLES',
      })
    })

    it('rejects with INVALID_DIAGRAM_SCHEMA when x-ui-styles contains orphan edges', async () => {
      const exportedDoc = buildDiagramExport(sampleDiagram)
      const corruptedDoc = {
        ...exportedDoc,
        'x-ui-styles': {
          ...(exportedDoc['x-ui-styles'] as Record<string, unknown>),
          edges: [
            {
              id: 'edge-orphan',
              type: 'composition',
              source: 'non-existent-source',
              target: 'non-existent-target',
            },
          ],
        },
      }

      const file = new File([JSON.stringify(corruptedDoc)], 'corrupted.json', { type: 'application/json' })

      await expect(readDiagramFile(file)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'INVALID_DIAGRAM_SCHEMA',
      })
    })

    it('rejects with INVALID_DIAGRAM_SCHEMA when x-ui-styles has multiple root elements', async () => {
      const exportedDoc = buildDiagramExport(sampleDiagram)
      const styles = exportedDoc['x-ui-styles'] as Record<string, unknown>
      const multiRootDoc = {
        ...exportedDoc,
        'x-ui-styles': {
          ...styles,
          nodes: [
            sampleDiagram.nodes[0],
            {
              ...sampleDiagram.nodes[0],
              id: 'node-2',
              data: {
                ...sampleDiagram.nodes[0].data,
                element: {
                  ...sampleDiagram.nodes[0].data.element,
                  id: 'node-2',
                  name: 'SecondRoot',
                  isRoot: true,
                },
              },
            },
          ],
        },
      }

      const file = new File([JSON.stringify(multiRootDoc)], 'multi-root.json', { type: 'application/json' })

      await expect(readDiagramFile(file)).rejects.toMatchObject({
        name: 'DiagramImportError',
        code: 'INVALID_DIAGRAM_SCHEMA',
      })
    })

    it('successfully imports a valid diagram document and normalizes state', async () => {
      const exportedDoc = buildDiagramExport(sampleDiagram)
      const file = new File([JSON.stringify(exportedDoc)], 'valid-structure.json', { type: 'application/json' })

      const imported = await readDiagramFile(file)

      expect(imported.id).toBeDefined()
      expect(imported.id).not.toBe(sampleDiagram.id)
      expect(imported.name).toBe('Sample Diagram')
      expect(imported.nodes).toHaveLength(1)
      expect(imported.nodes[0].id).toBe('node-1')
      expect(imported.nodes[0].position).toEqual({ x: 50, y: 100 })
      expect(imported.nodes[0].data.element.name).toBe('RootClass')
      expect(imported.nodes[0].data.label).toBe('RootClass')
      expect(imported.isDirty).toBe(true)
      expect(imported.lastModified).toBeInstanceOf(Date)
    })

    it('falls back to doc title or filename when diagram name is empty', async () => {
      const exportedDoc = buildDiagramExport(sampleDiagram)
      const styles = exportedDoc['x-ui-styles'] as Record<string, unknown>
      const docWithTitleFallback = {
        ...exportedDoc,
        title: 'Title From Doc',
        'x-ui-styles': {
          ...styles,
          name: '',
        },
      }

      const file1 = new File([JSON.stringify(docWithTitleFallback)], 'ignored-filename.json', {
        type: 'application/json',
      })
      const imported1 = await readDiagramFile(file1)
      expect(imported1.name).toBe('Title From Doc')

      const docWithFilenameFallback = {
        ...exportedDoc,
        title: undefined,
        'x-ui-styles': {
          ...styles,
          name: '  ',
        },
      }

      const file2 = new File([JSON.stringify(docWithFilenameFallback)], 'Emergency_Contact_Model.json', {
        type: 'application/json',
      })
      const imported2 = await readDiagramFile(file2)
      expect(imported2.name).toBe('Emergency_Contact_Model')
    })

    it('performs a complete roundtrip (buildDiagramExport -> readDiagramFile)', async () => {
      const complexDiagram: UMLDiagram = {
        id: 'diagram-roundtrip',
        name: 'School Model',
        description: 'Comprehensive school model for roundtrip test',
        nodes: [
          {
            id: 'node-school',
            type: 'class',
            position: { x: 120, y: 80 },
            data: {
              element: {
                id: 'node-school',
                name: 'School',
                type: 'class',
                isRoot: true,
                attributes: [
                  { id: 'attr-1', name: 'schoolName', type: 'String' },
                  { id: 'attr-2', name: 'studentCount', type: 'Integer' },
                ],
                operations: [],
              },
              label: 'School',
            },
          },
          {
            id: 'node-classroom',
            type: 'class',
            position: { x: 420, y: 220 },
            data: {
              element: {
                id: 'node-classroom',
                name: 'Classroom',
                type: 'class',
                attributes: [{ id: 'attr-3', name: 'roomNumber', type: 'String' }],
                operations: [],
              },
              label: 'Classroom',
            },
          },
        ],
        edges: [
          {
            id: 'edge-school-classroom',
            type: 'composition',
            source: 'node-classroom',
            target: 'node-school',
            data: {
              relationship: {
                id: 'rel-school-classroom',
                type: 'composition',
                source: 'node-classroom',
                target: 'node-school',
              },
            },
          },
        ],
        viewport: { x: 15, y: 25, zoom: 1.25 },
        lastModified: new Date('2026-09-01T08:00:00Z'),
        isDirty: false,
      }

      const exported = buildDiagramExport(complexDiagram, {
        dataStructureName: 'SchoolStructure',
        datastructureId: 'c1d2e3f4-5678-90ab-cdef-1234567890ab',
      })

      const file = new File([JSON.stringify(exported, null, 2)], 'SchoolStructure-1.0.0.json', {
        type: 'application/json',
      })

      const reimported = await readDiagramFile(file)

      expect(reimported.id).toBeDefined()
      expect(reimported.id).not.toBe(complexDiagram.id)
      expect(reimported.name).toBe('School Model')
      expect(reimported.description).toBe('Comprehensive school model for roundtrip test')
      expect(reimported.viewport).toEqual({ x: 15, y: 25, zoom: 1.25 })
      expect(reimported.isDirty).toBe(true)

      expect(reimported.nodes).toHaveLength(2)
      expect(reimported.nodes[0].id).toBe('node-school')
      expect(reimported.nodes[0].position).toEqual({ x: 120, y: 80 })
      expect(reimported.nodes[0].data.element.name).toBe('School')
      expect(reimported.nodes[0].data.element.isRoot).toBe(true)
      expect((reimported.nodes[0].data.element as UMLClass).attributes).toHaveLength(2)

      expect(reimported.nodes[1].id).toBe('node-classroom')
      expect(reimported.nodes[1].position).toEqual({ x: 420, y: 220 })
      expect(reimported.nodes[1].data.element.name).toBe('Classroom')

      expect(reimported.edges).toHaveLength(1)
      expect(reimported.edges[0].id).toBe('edge-school-classroom')
      expect(reimported.edges[0].source).toBe('node-classroom')
      expect(reimported.edges[0].target).toBe('node-school')
      expect(reimported.edges[0].data.relationship.type).toBe('composition')
    })
  })
})
