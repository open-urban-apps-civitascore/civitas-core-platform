import { z } from 'zod'

export const UMLPrimitiveTypeSchema = z.enum(['String', 'Integer', 'Boolean', 'Number', 'Date', 'DateTime', 'Uuid'])

export const UMLGeometryTypeSchema = z.enum([
  'Point',
  'LineString',
  'Polygon',
  'MultiPoint',
  'MultiLineString',
  'MultiPolygon',
  'GeometryCollection',
])

export const UMLTypeReferenceSchema = z.object({
  id: z.string(),
  name: z.string(),
  isExternal: z.boolean().optional(),
  href: z.string().optional(),
})

export const UMLTypeSchema = z.union([UMLPrimitiveTypeSchema, UMLGeometryTypeSchema, UMLTypeReferenceSchema])

export const UMLReturnTypeSchema = z.union([UMLTypeSchema, z.literal('void')])

export const VisibilitySchema = z.enum(['public', 'private', 'protected', 'package'])

export const AttributeMetaSchema = z.object({
  gisInfo: z
    .object({
      crs: z.string(),
    })
    .optional(),
})

export const UMLAttributeSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: UMLTypeSchema,
  visibility: VisibilitySchema.optional(),
  isStatic: z.boolean().optional(),
  isId: z.boolean().optional(),
  isReadonly: z.boolean().optional(),
  multiplicity: z.string().optional(),
  defaultValue: z.string().optional(),
  meta: AttributeMetaSchema.optional(),
})

export const UMLParameterSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: UMLTypeSchema,
  direction: z.enum(['in', 'out', 'inout', 'return']).optional(),
  multiplicity: z.string().optional(),
})

export const UMLOperationSchema = z.object({
  id: z.string(),
  name: z.string(),
  returnType: UMLReturnTypeSchema.optional(),
  visibility: VisibilitySchema.optional(),
  isStatic: z.boolean().optional(),
  isAbstract: z.boolean().optional(),
  parameters: z.array(UMLParameterSchema).default([]),
})

export const UMLEnumLiteralSchema = z.object({
  id: z.string(),
  name: z.string(),
  value: z.union([z.string(), z.number()]).optional(),
})

const baseUMLElementFields = {
  id: z.string(),
  name: z.string(),
  stereotype: z.string().optional(),
  documentation: z.string().optional(),
  package: z.string().optional(),
  isRoot: z.boolean().optional(),
}

export const UMLClassElementSchema = z.object({
  ...baseUMLElementFields,
  type: z.literal('class'),
  attributes: z.array(UMLAttributeSchema).default([]),
  operations: z.array(UMLOperationSchema).default([]),
  isAbstract: z.boolean().optional(),
})

export const UMLAbstractClassElementSchema = z.object({
  ...baseUMLElementFields,
  type: z.literal('abstractClass'),
  attributes: z.array(UMLAttributeSchema).default([]),
  operations: z.array(UMLOperationSchema).default([]),
})

export const UMLInterfaceElementSchema = z.object({
  ...baseUMLElementFields,
  type: z.literal('interface'),
  operations: z.array(UMLOperationSchema).default([]),
})

export const UMLEnumerationElementSchema = z.object({
  ...baseUMLElementFields,
  type: z.literal('enumeration'),
  literals: z.array(UMLEnumLiteralSchema).default([]),
})

export const UMLElementSchema = z.discriminatedUnion('type', [
  UMLClassElementSchema,
  UMLAbstractClassElementSchema,
  UMLInterfaceElementSchema,
  UMLEnumerationElementSchema,
])

export const UMLRelationshipTypeSchema = z.enum([
  'association',
  'aggregation',
  'composition',
  'inheritance',
  'realization',
  'dependency',
])

export const UMLRelationshipSchema = z.object({
  id: z.string(),
  type: UMLRelationshipTypeSchema,
  source: z.string(),
  target: z.string(),
  sourceMultiplicity: z.string().optional(),
  targetMultiplicity: z.string().optional(),
  sourceRole: z.string().optional(),
  targetRole: z.string().optional(),
  name: z.string().optional(),
  isNavigable: z.boolean().optional(),
  isBidirectional: z.boolean().optional(),
})

export const UMLNodeDataSchema = z.object({
  element: UMLElementSchema,
  label: z.string().default(''),
})

export const UMLNodeSchema = z.object({
  id: z.string(),
  type: z.enum(['class', 'abstractClass', 'interface', 'enumeration']),
  position: z.object({
    x: z.number(),
    y: z.number(),
  }),
  data: UMLNodeDataSchema,
})

export const UMLEdgeDataSchema = z.object({
  relationship: UMLRelationshipSchema,
  label: z.string().optional(),
})

export const UMLEdgeSchema = z.object({
  id: z.string(),
  type: UMLRelationshipTypeSchema,
  source: z.string(),
  target: z.string(),
  data: UMLEdgeDataSchema.optional(),
})

export const ViewportSchema = z.object({
  x: z.number(),
  y: z.number(),
  zoom: z.number(),
})

export const RawUMLDiagramSchema = z.object({
  id: z.string().optional(),
  name: z.string().default(''),
  description: z.string().optional(),
  nodes: z.array(UMLNodeSchema).default([]),
  edges: z.array(UMLEdgeSchema).default([]),
  viewport: ViewportSchema.optional(),
  lastModified: z.union([z.string(), z.date()]).optional(),
  isDirty: z.boolean().optional(),
})

export const UMLDiagramSchema = RawUMLDiagramSchema.superRefine((diagram, ctx) => {
  const nodeIds = new Set(diagram.nodes.map(node => node.id))
  let rootCount = 0

  for (let i = 0; i < diagram.nodes.length; i++) {
    const node = diagram.nodes[i]
    if (node.data.element.id !== node.id) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Node ${node.id} element.id does not match node id`,
        path: ['nodes', i, 'data', 'element', 'id'],
      })
    }
    if (node.data.element.type !== node.type) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Node ${node.id} element.type does not match node type`,
        path: ['nodes', i, 'data', 'element', 'type'],
      })
    }
    if (node.data.element.isRoot) {
      rootCount++
    }
  }

  if (rootCount > 1) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Diagram cannot have more than one root element',
      path: ['nodes'],
    })
  }

  for (let i = 0; i < diagram.edges.length; i++) {
    const edge = diagram.edges[i]
    if (!nodeIds.has(edge.source)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Edge ${edge.id} source (${edge.source}) does not exist in nodes`,
        path: ['edges', i, 'source'],
      })
    }
    if (!nodeIds.has(edge.target)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Edge ${edge.id} target (${edge.target}) does not exist in nodes`,
        path: ['edges', i, 'target'],
      })
    }
    if (edge.data?.relationship) {
      const rel = edge.data.relationship
      if (!nodeIds.has(rel.source)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `Relationship in edge ${edge.id} source (${rel.source}) does not exist in nodes`,
          path: ['edges', i, 'data', 'relationship', 'source'],
        })
      }
      if (!nodeIds.has(rel.target)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: `Relationship in edge ${edge.id} target (${rel.target}) does not exist in nodes`,
          path: ['edges', i, 'data', 'relationship', 'target'],
        })
      }
    }
  }
})

export type ValidatedUMLDiagram = z.infer<typeof UMLDiagramSchema>
