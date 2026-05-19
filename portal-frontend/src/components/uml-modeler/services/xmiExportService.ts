/**
 * XMI Export Service
 *
 * Converts UML diagram data to XMI format compatible with Eclipse UML2 5.0.0.
 * Designed for easy extension to support import functionality in the future.
 */

import { UML_GEOMETRY_TYPES, UML_PRIMITIVE_TYPES } from '../constants/umlTypes'
import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type {
  AttributeMeta,
  UMLAbstractClass,
  UMLAttribute,
  UMLClass,
  UMLElement,
  UMLEnumeration,
  UMLInterface,
  UMLOperation,
  UMLParameter,
  UMLRelationship,
  UMLType,
  Visibility,
} from '../types/uml'

// XMI Namespaces - Eclipse UML2 5.0.0 compatible
const XMI_NAMESPACE = 'http://www.omg.org/spec/XMI/20131001'
const UML_NAMESPACE = 'http://www.eclipse.org/uml2/5.0.0/UML'
const XMI_VERSION = '20131001'

// Package configuration
const BASE_PACKAGE_URI = 'http://civitas.org/model'

// Visibility mapping to UML
const VISIBILITY_XMI_MAP: Record<Visibility, string> = {
  public: 'public',
  private: 'private',
  protected: 'protected',
  package: 'package',
}

/**
 * Generates a unique ID (UUID v4 format)
 */
const generateId = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * Escapes special XML characters
 */
const escapeXml = (text: string): string => {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}

/**
 * Converts a UML type to XMI type reference
 */
const typeToXmi = (type: UMLType, indent: string): string => {
  if (typeof type !== 'string') {
    return `${indent}<type xmi:type="uml:Class" xmi:idref="${type.id}"/>`
  }

  const primitiveHref = UML_PRIMITIVE_TYPES[type as keyof typeof UML_PRIMITIVE_TYPES]?.uri
  if (primitiveHref) return `${indent}<type xmi:type="uml:PrimitiveType" href="${primitiveHref}"/>`

  const geometryHref = UML_GEOMETRY_TYPES[type as keyof typeof UML_GEOMETRY_TYPES]?.uri
  if (geometryHref) return `${indent}<type xmi:type="uml:GeometryType" href="${geometryHref}"/>`

  return `${indent}<type xmi:type="uml:PrimitiveType" href="${UML_PRIMITIVE_TYPES.String.uri}"/>`
}

/**
 * Converts multiplicity to XMI lower/upper values
 */
const parseMultiplicity = (multiplicity?: string): { lower: string; upper: string } => {
  if (!multiplicity) {
    return { lower: '1', upper: '1' }
  }

  const trimmed = multiplicity.trim()

  if (trimmed === '*') {
    return { lower: '0', upper: '*' }
  }

  const rangeMatch = trimmed.match(/^(\d+|\*)\.\.(\d+|\*)$/)
  if (rangeMatch) {
    return { lower: rangeMatch[1], upper: rangeMatch[2] }
  }

  // Single value
  return { lower: trimmed, upper: trimmed }
}

/**
 * Converts a UML parameter to XMI
 */
const parameterToXmi = (param: UMLParameter, indent: string): string => {
  const lines: string[] = []
  const direction = param.direction || 'in'
  const childIndent = `${indent}  `

  lines.push(`${indent}<ownedParameter xmi:id="${param.id}" name="${escapeXml(param.name)}" direction="${direction}">`)
  lines.push(typeToXmi(param.type, childIndent))

  if (param.multiplicity) {
    const { lower, upper } = parseMultiplicity(param.multiplicity)
    lines.push(`${childIndent}<lowerValue xmi:type="uml:LiteralInteger" value="${lower}"/>`)
    lines.push(`${childIndent}<upperValue xmi:type="uml:LiteralUnlimitedNatural" value="${upper}"/>`)
  }

  lines.push(`${indent}</ownedParameter>`)
  return lines.join('\n')
}

const metaToXmi = (meta: AttributeMeta, indent: string) => {
  const lines: string[] = []
  const childIndent = `${indent}  `
  const metaInfoKey = Object.keys(meta) as (keyof AttributeMeta)[]

  for (const infoKey of metaInfoKey) {
    const infoValue = meta[infoKey]
    if (!infoValue) continue
    const detailsKey = Object.keys(infoValue) as (keyof typeof infoValue)[]
    lines.push(`${indent}<eAnnotations xmi:id="${crypto.randomUUID()}" source="${infoKey}">`)
    for (const detail of detailsKey) {
      lines.push(
        `${childIndent}<details xmi:id="${crypto.randomUUID()}" key="${detail}" value="${infoValue[detail]}"/>`,
      )
    }
    lines.push(`${indent}</eAnnotations>`)
  }
  return lines.join('\n')
}

/**
 * Converts a UML attribute to XMI
 */
const attributeToXmi = (attr: UMLAttribute, indent: string): string => {
  const lines: string[] = []
  const visibility = VISIBILITY_XMI_MAP[attr.visibility]
  const childIndent = `${indent}  `

  let propertyAttrs = `xmi:id="${attr.id}" name="${escapeXml(attr.name)}" visibility="${visibility}"`

  if (attr.isId) {
    propertyAttrs += ' isID="true"'
  }

  if (attr.isStatic) {
    propertyAttrs += ' isStatic="true"'
  }
  if (attr.isReadonly) {
    propertyAttrs += ' isReadOnly="true"'
  }

  lines.push(`${indent}<ownedAttribute ${propertyAttrs}>`)
  if (attr.meta) {
    lines.push(metaToXmi(attr.meta, childIndent))
  }
  lines.push(typeToXmi(attr.type, childIndent))

  if (attr.multiplicity) {
    const { lower, upper } = parseMultiplicity(attr.multiplicity)
    lines.push(`${childIndent}<lowerValue xmi:type="uml:LiteralInteger" value="${lower}"/>`)
    lines.push(`${childIndent}<upperValue xmi:type="uml:LiteralUnlimitedNatural" value="${upper}"/>`)
  }

  if (attr.defaultValue) {
    lines.push(`${childIndent}<defaultValue xmi:type="uml:LiteralString" value="${escapeXml(attr.defaultValue)}"/>`)
  }

  lines.push(`${indent}</ownedAttribute>`)
  return lines.join('\n')
}

/**
 * Converts a UML operation to XMI
 */
const operationToXmi = (op: UMLOperation, indent: string): string => {
  const lines: string[] = []
  const visibility = VISIBILITY_XMI_MAP[op.visibility]
  const childIndent = `${indent}  `
  const grandchildIndent = `${indent}    `

  let opAttrs = `xmi:id="${op.id}" name="${escapeXml(op.name)}" visibility="${visibility}"`

  if (op.isStatic) {
    opAttrs += ' isStatic="true"'
  }
  if (op.isAbstract) {
    opAttrs += ' isAbstract="true"'
  }

  lines.push(`${indent}<ownedOperation ${opAttrs}>`)

  // Return parameter
  if (op.returnType) {
    const returnId = `${op.id}_return`
    lines.push(`${childIndent}<ownedParameter xmi:id="${returnId}" direction="return">`)
    lines.push(typeToXmi(op.returnType, grandchildIndent))
    lines.push(`${childIndent}</ownedParameter>`)
  }

  // Method parameters
  for (const param of op.parameters) {
    lines.push(parameterToXmi(param, childIndent))
  }

  lines.push(`${indent}</ownedOperation>`)
  return lines.join('\n')
}

/**
 * Converts a UML Class to XMI
 */
const classToXmi = (element: UMLClass, node: UMLNode, indent: string): string => {
  const lines: string[] = []
  const childIndent = `${indent}  `

  let classAttrs = `xmi:type="uml:Class" xmi:id="${element.id}" name="${escapeXml(element.name)}"`
  if (element.isAbstract) {
    classAttrs += ' isAbstract="true"'
  }

  lines.push(`${indent}<packagedElement ${classAttrs}>`)

  // Documentation as comment
  if (element.documentation) {
    const commentId = `${element.id}_comment`
    lines.push(
      `${childIndent}<ownedComment xmi:type="uml:Comment" xmi:id="${commentId}" body="${escapeXml(element.documentation)}"/>`,
    )
  }

  // Attributes
  for (const attr of element.attributes) {
    lines.push(attributeToXmi(attr, childIndent))
  }

  // Operations
  for (const op of element.operations) {
    lines.push(operationToXmi(op, childIndent))
  }

  lines.push(`${indent}</packagedElement>`)

  return lines.join('\n')
}

/**
 * Converts a UML Interface to XMI
 */
const interfaceToXmi = (element: UMLInterface, node: UMLNode, indent: string): string => {
  const lines: string[] = []
  const childIndent = `${indent}  `

  lines.push(
    `${indent}<packagedElement xmi:type="uml:Interface" xmi:id="${element.id}" name="${escapeXml(element.name)}">`,
  )

  // Documentation as comment
  if (element.documentation) {
    const commentId = `${element.id}_comment`
    lines.push(
      `${childIndent}<ownedComment xmi:type="uml:Comment" xmi:id="${commentId}" body="${escapeXml(element.documentation)}"/>`,
    )
  }

  // Operations
  for (const op of element.operations) {
    lines.push(operationToXmi(op, childIndent))
  }

  lines.push(`${indent}</packagedElement>`)

  return lines.join('\n')
}

/**
 * Converts a UML AbstractClass to XMI
 */
const abstractClassToXmi = (element: UMLAbstractClass, node: UMLNode, indent: string): string => {
  const lines: string[] = []
  const childIndent = `${indent}  `

  lines.push(
    `${indent}<packagedElement xmi:type="uml:Class" xmi:id="${element.id}" name="${escapeXml(element.name)}" isAbstract="true">`,
  )

  // Documentation as comment
  if (element.documentation) {
    const commentId = `${element.id}_comment`
    lines.push(
      `${childIndent}<ownedComment xmi:type="uml:Comment" xmi:id="${commentId}" body="${escapeXml(element.documentation)}"/>`,
    )
  }

  // Attributes
  for (const attr of element.attributes) {
    lines.push(attributeToXmi(attr, childIndent))
  }

  // Operations
  for (const op of element.operations) {
    lines.push(operationToXmi(op, childIndent))
  }

  lines.push(`${indent}</packagedElement>`)

  return lines.join('\n')
}

/**
 * Converts a UML Enumeration to XMI
 */
const enumerationToXmi = (element: UMLEnumeration, node: UMLNode, indent: string): string => {
  const lines: string[] = []
  const childIndent = `${indent}  `

  lines.push(
    `${indent}<packagedElement xmi:type="uml:Enumeration" xmi:id="${element.id}" name="${escapeXml(element.name)}">`,
  )

  // Documentation as comment
  if (element.documentation) {
    const commentId = `${element.id}_comment`
    lines.push(
      `${childIndent}<ownedComment xmi:type="uml:Comment" xmi:id="${commentId}" body="${escapeXml(element.documentation)}"/>`,
    )
  }

  // Literals
  for (const literal of element.literals) {
    lines.push(
      `${childIndent}<ownedLiteral xmi:type="uml:EnumerationLiteral" xmi:id="${literal.id}" name="${escapeXml(literal.name)}"/>`,
    )
  }

  lines.push(`${indent}</packagedElement>`)

  return lines.join('\n')
}

/**
 * Converts a UML element to XMI based on its type
 */
const elementToXmi = (element: UMLElement, node: UMLNode, indent: string): string => {
  switch (element.type) {
    case 'class':
      return classToXmi(element as UMLClass, node, indent)
    case 'interface':
      return interfaceToXmi(element as UMLInterface, node, indent)
    case 'abstractClass':
      return abstractClassToXmi(element as UMLAbstractClass, node, indent)
    case 'enumeration':
      return enumerationToXmi(element as UMLEnumeration, node, indent)
    default:
      return ''
  }
}

/**
 * Converts a UML relationship to XMI
 */
const relationshipToXmi = (relationship: UMLRelationship, edge: UMLEdge, indent: string): string => {
  const lines: string[] = []
  const childIndent = `${indent}  `
  const grandchildIndent = `${indent}    `

  switch (relationship.type) {
    case 'association':
    case 'aggregation':
    case 'composition': {
      // Association (with aggregation/composition as memberEnd aggregation kind)
      const aggregationKind =
        relationship.type === 'aggregation' ? 'shared' : relationship.type === 'composition' ? 'composite' : 'none'

      const nameAttr = relationship.name ? ` name="${escapeXml(relationship.name)}"` : ''
      lines.push(`${indent}<packagedElement xmi:type="uml:Association" xmi:id="${relationship.id}"${nameAttr}>`)

      // Member ends
      const sourceEndId = `${relationship.id}_source`
      const targetEndId = `${relationship.id}_target`

      // Source end
      let sourceEndAttrs = `xmi:id="${sourceEndId}" type="${relationship.source}"`
      if (relationship.sourceRole) {
        sourceEndAttrs += ` name="${escapeXml(relationship.sourceRole)}"`
      }
      lines.push(`${childIndent}<memberEnd ${sourceEndAttrs}>`)
      if (relationship.sourceMultiplicity) {
        const { lower, upper } = parseMultiplicity(relationship.sourceMultiplicity)
        lines.push(`${grandchildIndent}<lowerValue xmi:type="uml:LiteralInteger" value="${lower}"/>`)
        lines.push(`${grandchildIndent}<upperValue xmi:type="uml:LiteralUnlimitedNatural" value="${upper}"/>`)
      }
      lines.push(`${childIndent}</memberEnd>`)

      // Target end (with aggregation kind for aggregation/composition)
      let targetEndAttrs = `xmi:id="${targetEndId}" type="${relationship.target}"`
      if (relationship.targetRole) {
        targetEndAttrs += ` name="${escapeXml(relationship.targetRole)}"`
      }
      if (aggregationKind !== 'none') {
        targetEndAttrs += ` aggregation="${aggregationKind}"`
      }
      lines.push(`${childIndent}<memberEnd ${targetEndAttrs}>`)
      if (relationship.targetMultiplicity) {
        const { lower, upper } = parseMultiplicity(relationship.targetMultiplicity)
        lines.push(`${grandchildIndent}<lowerValue xmi:type="uml:LiteralInteger" value="${lower}"/>`)
        lines.push(`${grandchildIndent}<upperValue xmi:type="uml:LiteralUnlimitedNatural" value="${upper}"/>`)
      }
      lines.push(`${childIndent}</memberEnd>`)

      lines.push(`${indent}</packagedElement>`)
      break
    }

    case 'inheritance': {
      // Generalization - represented within the subclass element
      // We'll output a separate generalization element that can be associated
      lines.push(
        `${indent}<packagedElement xmi:type="uml:Generalization" xmi:id="${relationship.id}" general="${relationship.target}" specific="${relationship.source}"/>`,
      )
      break
    }

    case 'realization': {
      // InterfaceRealization
      const nameAttr = relationship.name ? ` name="${escapeXml(relationship.name)}"` : ''
      lines.push(
        `${indent}<packagedElement xmi:type="uml:InterfaceRealization" xmi:id="${relationship.id}" implementingClassifier="${relationship.source}" contract="${relationship.target}"${nameAttr}/>`,
      )
      break
    }

    case 'dependency': {
      // Dependency
      const nameAttr = relationship.name ? ` name="${escapeXml(relationship.name)}"` : ''
      lines.push(
        `${indent}<packagedElement xmi:type="uml:Dependency" xmi:id="${relationship.id}" client="${relationship.source}" supplier="${relationship.target}"${nameAttr}/>`,
      )
      break
    }
  }

  return lines.join('\n')
}

/**
 * Sanitizes a name for use in URIs and package names
 */
export const sanitizeName = (name: string): string => {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

/**
 * Main export function - converts a UMLDiagram to XMI format (Eclipse UML2 5.0.0 compatible)
 */
export const exportToXmi = (diagram: UMLDiagram, modelUri?: string): string => {
  const lines: string[] = []

  // Generate dynamic package name and URI from diagram name
  const sanitizedName = sanitizeName(diagram.name)
  const packageName = sanitizedName || 'untitled'
  const packageUri = `${BASE_PACKAGE_URI}/${packageName}`

  // XML declaration
  lines.push('<?xml version="1.0" encoding="UTF-8"?>')

  // UML Model as root element with namespaces (Eclipse UML2 format)
  const modelId = `${diagram.id}_model`
  lines.push(
    `<uml:Model xmi:version="${XMI_VERSION}" xmlns:xmi="${XMI_NAMESPACE}" xmlns:uml="${UML_NAMESPACE}" xmi:id="${modelId}" name="${escapeXml(diagram.name)}" ${modelUri && `URI="${modelUri}"`}>`,
  )

  // Package wrapper for all elements
  const packageId = generateId()
  lines.push(
    `  <packagedElement xmi:type="uml:Package" xmi:id="${packageId}" name="${packageName}" URI="${packageUri}">`,
  )

  // Export all elements (nodes)
  for (const node of diagram.nodes) {
    const elementXmi = elementToXmi(node.data.element, node, '    ')
    if (elementXmi) {
      lines.push(elementXmi)
    }
  }

  // Export all relationships (edges)
  for (const edge of diagram.edges) {
    const relationshipXmi = relationshipToXmi(edge.data.relationship, edge, '    ')
    if (relationshipXmi) {
      lines.push(relationshipXmi)
    }
  }

  // Close package
  lines.push('  </packagedElement>')

  // Close model
  lines.push('</uml:Model>')

  return lines.join('\n')
}

/**
 * Triggers a download of the XMI content as a file
 */
export const downloadXmi = (diagram: UMLDiagram, filename?: string): void => {
  const xmiContent = exportToXmi(diagram)
  const blob = new Blob([xmiContent], { type: 'application/xml' })
  const url = URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = filename || `${diagram.name.replace(/[^a-zA-Z0-9]/g, '_')}.xmi`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)

  URL.revokeObjectURL(url)
}
