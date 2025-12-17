/**
 * XMI Export Service
 *
 * Converts UML diagram data to XMI 2.5.1 format for export.
 * Designed for easy extension to support import functionality in the future.
 */

import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type {
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

// XMI Namespaces
const XMI_NAMESPACE = 'http://www.omg.org/spec/XMI/20131001'
const UML_NAMESPACE = 'http://www.omg.org/spec/UML/20161101'

// Visibility mapping to UML
const VISIBILITY_XMI_MAP: Record<Visibility, string> = {
  public: 'public',
  private: 'private',
  protected: 'protected',
  package: 'package',
}

// Primitive type mapping to XMI href
const PRIMITIVE_TYPE_HREF: Record<string, string> = {
  String: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#String',
  Integer: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Integer',
  Boolean: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Boolean',
  Real: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Real',
  UnlimitedNatural: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#UnlimitedNatural',
  // Extended types mapped to closest UML primitive
  Float: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Real',
  Double: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Real',
  Long: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Integer',
  Short: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Integer',
  Byte: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#Integer',
  Character: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#String',
  Date: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#String',
  void: 'http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#String',
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
  if (typeof type === 'string') {
    // Primitive type
    const href = PRIMITIVE_TYPE_HREF[type] || PRIMITIVE_TYPE_HREF['String']
    return `${indent}<type xmi:type="uml:PrimitiveType" href="${href}"/>`
  } else {
    // Type reference to another element in the model
    return `${indent}<type xmi:type="uml:Class" xmi:idref="${type.id}"/>`
  }
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

  lines.push(
    `${indent}<ownedParameter xmi:type="uml:Parameter" xmi:id="${param.id}" name="${escapeXml(param.name)}" direction="${direction}">`,
  )
  lines.push(typeToXmi(param.type, childIndent))

  if (param.multiplicity) {
    const { lower, upper } = parseMultiplicity(param.multiplicity)
    lines.push(`${childIndent}<lowerValue xmi:type="uml:LiteralInteger" value="${lower}"/>`)
    lines.push(`${childIndent}<upperValue xmi:type="uml:LiteralUnlimitedNatural" value="${upper}"/>`)
  }

  lines.push(`${indent}</ownedParameter>`)
  return lines.join('\n')
}

/**
 * Converts a UML attribute to XMI
 */
const attributeToXmi = (attr: UMLAttribute, indent: string): string => {
  const lines: string[] = []
  const visibility = VISIBILITY_XMI_MAP[attr.visibility]
  const childIndent = `${indent}  `

  let propertyAttrs = `xmi:type="uml:Property" xmi:id="${attr.id}" name="${escapeXml(attr.name)}" visibility="${visibility}"`

  if (attr.isStatic) {
    propertyAttrs += ' isStatic="true"'
  }
  if (attr.isReadonly) {
    propertyAttrs += ' isReadOnly="true"'
  }

  lines.push(`${indent}<ownedAttribute ${propertyAttrs}>`)
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

  let opAttrs = `xmi:type="uml:Operation" xmi:id="${op.id}" name="${escapeXml(op.name)}" visibility="${visibility}"`

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
    lines.push(`${childIndent}<ownedParameter xmi:type="uml:Parameter" xmi:id="${returnId}" direction="return">`)
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
      let sourceEndAttrs = `xmi:type="uml:Property" xmi:id="${sourceEndId}" type="${relationship.source}"`
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
      let targetEndAttrs = `xmi:type="uml:Property" xmi:id="${targetEndId}" type="${relationship.target}"`
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
 * Generates XMI diagram extensions for node positions
 * This preserves layout information for potential future import
 */
const generateDiagramExtension = (diagram: UMLDiagram, indent: string): string => {
  const lines: string[] = []
  const childIndent = `${indent}  `
  const grandchildIndent = `${indent}    `

  lines.push(`${indent}<xmi:Extension extender="civitas-uml-modeler">`)
  lines.push(`${childIndent}<diagram id="${diagram.id}" name="${escapeXml(diagram.name)}">`)

  // Node positions
  for (const node of diagram.nodes) {
    const widthAttr = node.width ? ` width="${node.width}"` : ''
    const heightAttr = node.height ? ` height="${node.height}"` : ''
    lines.push(
      `${grandchildIndent}<nodeLayout elementId="${node.data.element.id}" x="${node.position.x}" y="${node.position.y}"${widthAttr}${heightAttr}/>`,
    )
  }

  // Viewport
  if (diagram.viewport) {
    lines.push(
      `${grandchildIndent}<viewport x="${diagram.viewport.x}" y="${diagram.viewport.y}" zoom="${diagram.viewport.zoom}"/>`,
    )
  }

  lines.push(`${childIndent}</diagram>`)
  lines.push(`${indent}</xmi:Extension>`)

  return lines.join('\n')
}

/**
 * Main export function - converts a UMLDiagram to XMI format
 */
export const exportToXmi = (diagram: UMLDiagram): string => {
  const lines: string[] = []

  // XML declaration
  lines.push('<?xml version="1.0" encoding="UTF-8"?>')

  // XMI root element with namespaces
  lines.push(`<xmi:XMI xmlns:xmi="${XMI_NAMESPACE}" xmlns:uml="${UML_NAMESPACE}">`)

  // UML Model
  const modelId = `${diagram.id}_model`
  lines.push(`  <uml:Model xmi:id="${modelId}" name="${escapeXml(diagram.name)}">`)

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

  lines.push('  </uml:Model>')

  // Diagram extension for layout preservation
  lines.push(generateDiagramExtension(diagram, '  '))

  lines.push('</xmi:XMI>')

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
