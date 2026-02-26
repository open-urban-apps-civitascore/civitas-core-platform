/**
 * XMI Import Service
 *
 * Parses XMI files and converts them to UMLDiagram format.
 * Supports Eclipse UML2 5.0.0 format with eAnnotations for layout.
 */

import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type {
  UMLAbstractClass,
  UMLAttribute,
  UMLClass,
  UMLElement,
  UMLEnumeration,
  UMLEnumLiteral,
  UMLInterface,
  UMLOperation,
  UMLParameter,
  UMLPrimitiveType,
  UMLRelationship,
  UMLRelationshipType,
  UMLType,
  Visibility,
} from '../types/uml'

// Layout information from eAnnotations
interface NodeLayout {
  elementId: string
  x: number
  y: number
  width?: number
  height?: number
}

// Import result type
export interface XmiImportResult {
  success: boolean
  diagram?: UMLDiagram
  errors: string[]
  warnings: string[]
}

/**
 * Gets an attribute value from an element
 */
const getAttr = (element: Element, name: string): string | null => {
  return element.getAttribute(name) || element.getAttribute(`xmi:${name}`)
}

/**
 * Gets the xmi:id attribute
 */
const getId = (element: Element): string => {
  return getAttr(element, 'id') || crypto.randomUUID()
}

/**
 * Gets the xmi:type attribute
 */
const getType = (element: Element): string | null => {
  return getAttr(element, 'type')
}

/**
 * Converts XMI visibility to our Visibility type
 */
const parseVisibility = (value: string | null): Visibility => {
  switch (value) {
    case 'public':
      return 'public'
    case 'private':
      return 'private'
    case 'protected':
      return 'protected'
    case 'package':
      return 'package'
    default:
      return 'public'
  }
}

/**
 * Extracts primitive type name from href
 */
const extractPrimitiveType = (href: string): UMLPrimitiveType => {
  const match = href.match(/#(\w+)$/)
  if (match) {
    const typeName = match[1]
    // Map UML primitive types to our types
    switch (typeName) {
      case 'Real':
        return 'Float'
      case 'UnlimitedNatural':
        return 'Integer'
      case 'String':
        return 'String'
      case 'Integer':
        return 'Integer'
      case 'Boolean':
        return 'Boolean'
      case 'Float':
        return 'Float'
      case 'Double':
        return 'Double'
      case 'Long':
        return 'Long'
      case 'Short':
        return 'Short'
      case 'Byte':
        return 'Byte'
      case 'Character':
        return 'Character'
      case 'Date':
        return 'Date'
      default:
        return 'String'
    }
  }
  return 'String'
}

/**
 * Parses a type element from XMI
 */
const parseType = (element: Element): UMLType => {
  const typeElement = element.querySelector('type')
  if (!typeElement) {
    return 'String'
  }

  const href = getAttr(typeElement, 'href')
  if (href) {
    return extractPrimitiveType(href)
  }

  const idref = getAttr(typeElement, 'idref')
  if (idref) {
    return {
      id: idref,
      name: '', // Will be resolved later
    }
  }

  return 'String'
}

/**
 * Parses multiplicity from lower/upper value elements
 */
const parseMultiplicityFromElements = (element: Element): string | undefined => {
  const lowerElement = element.querySelector('lowerValue')
  const upperElement = element.querySelector('upperValue')

  if (!lowerElement && !upperElement) {
    return undefined
  }

  const lower = lowerElement?.getAttribute('value') || '1'
  const upper = upperElement?.getAttribute('value') || '1'

  if (lower === upper) {
    return lower === '1' ? undefined : lower
  }

  return `${lower}..${upper}`
}

/**
 * Parses a UML parameter from XMI
 */
const parseParameter = (element: Element): UMLParameter => {
  const direction = getAttr(element, 'direction') || 'in'

  return {
    id: getId(element),
    name: getAttr(element, 'name') || 'param',
    type: parseType(element),
    direction: direction as 'in' | 'out' | 'inout' | 'return',
    multiplicity: parseMultiplicityFromElements(element),
  }
}

/**
 * Parses a UML attribute from XMI
 */
const parseAttribute = (element: Element): UMLAttribute => {
  const defaultValueElement = element.querySelector('defaultValue')

  return {
    id: getId(element),
    name: getAttr(element, 'name') || 'attribute',
    type: parseType(element),
    visibility: parseVisibility(getAttr(element, 'visibility')),
    isStatic: getAttr(element, 'isStatic') === 'true',
    isReadonly: getAttr(element, 'isReadOnly') === 'true',
    multiplicity: parseMultiplicityFromElements(element),
    defaultValue: defaultValueElement?.getAttribute('value') || undefined,
  }
}

/**
 * Parses a UML operation from XMI
 */
const parseOperation = (element: Element): UMLOperation => {
  const parameters: UMLParameter[] = []
  let returnType: UMLType | undefined

  // Parse all parameters
  const paramElements = element.querySelectorAll(':scope > ownedParameter')
  paramElements.forEach(paramEl => {
    const direction = getAttr(paramEl, 'direction')
    if (direction === 'return') {
      returnType = parseType(paramEl)
    } else {
      parameters.push(parseParameter(paramEl))
    }
  })

  return {
    id: getId(element),
    name: getAttr(element, 'name') || 'operation',
    returnType,
    visibility: parseVisibility(getAttr(element, 'visibility')),
    isStatic: getAttr(element, 'isStatic') === 'true',
    isAbstract: getAttr(element, 'isAbstract') === 'true',
    parameters,
  }
}

/**
 * Parses eAnnotations for layout information (Eclipse EMF format)
 */
const parseEAnnotations = (element: Element): NodeLayout | null => {
  const eAnnotations = element.querySelector(':scope > eAnnotations[source="nodeLayout"]')
  if (!eAnnotations) {
    return null
  }

  const elementId = getId(element)
  let x: number | null = null
  let y: number | null = null

  // Parse details key-value pairs
  const details = eAnnotations.querySelectorAll(':scope > details')
  details.forEach(detail => {
    const key = detail.getAttribute('key')
    const value = detail.getAttribute('value')
    if (key === 'x' && value) {
      x = parseFloat(value)
    } else if (key === 'y' && value) {
      y = parseFloat(value)
    }
  })

  if (x !== null && y !== null) {
    return { elementId, x, y }
  }

  return null
}

/**
 * Parses a UML class from XMI
 */
const parseClass = (element: Element): UMLClass | UMLAbstractClass => {
  const isAbstract = getAttr(element, 'isAbstract') === 'true'

  const attributes: UMLAttribute[] = []
  const operations: UMLOperation[] = []

  // Parse attributes
  element.querySelectorAll(':scope > ownedAttribute').forEach(attrEl => {
    attributes.push(parseAttribute(attrEl))
  })

  // Parse operations
  element.querySelectorAll(':scope > ownedOperation').forEach(opEl => {
    operations.push(parseOperation(opEl))
  })

  // Get documentation from comment
  const commentEl = element.querySelector('ownedComment')
  const documentation = commentEl?.getAttribute('body') || undefined

  const baseProps = {
    id: getId(element),
    name: getAttr(element, 'name') || 'Class',
    documentation,
    attributes,
    operations,
  }

  if (isAbstract) {
    return {
      ...baseProps,
      type: 'abstractClass' as const,
    }
  }

  return {
    ...baseProps,
    type: 'class' as const,
    isAbstract: false,
  }
}

/**
 * Parses a UML interface from XMI
 */
const parseInterface = (element: Element): UMLInterface => {
  const operations: UMLOperation[] = []

  // Parse operations
  element.querySelectorAll(':scope > ownedOperation').forEach(opEl => {
    operations.push(parseOperation(opEl))
  })

  // Get documentation from comment
  const commentEl = element.querySelector('ownedComment')
  const documentation = commentEl?.getAttribute('body') || undefined

  return {
    id: getId(element),
    name: getAttr(element, 'name') || 'Interface',
    type: 'interface',
    documentation,
    operations,
  }
}

/**
 * Parses a UML enumeration from XMI
 */
const parseEnumeration = (element: Element): UMLEnumeration => {
  const literals: UMLEnumLiteral[] = []

  // Parse literals
  element.querySelectorAll(':scope > ownedLiteral').forEach(literalEl => {
    literals.push({
      id: getId(literalEl),
      name: getAttr(literalEl, 'name') || 'LITERAL',
    })
  })

  // Get documentation from comment
  const commentEl = element.querySelector('ownedComment')
  const documentation = commentEl?.getAttribute('body') || undefined

  return {
    id: getId(element),
    name: getAttr(element, 'name') || 'Enumeration',
    type: 'enumeration',
    documentation,
    literals,
  }
}

/**
 * Parses an association relationship from XMI
 */
const parseAssociation = (element: Element): UMLRelationship | null => {
  const memberEnds = element.querySelectorAll(':scope > memberEnd')
  if (memberEnds.length < 2) {
    return null
  }

  const sourceEnd = memberEnds[0]
  const targetEnd = memberEnds[1]

  const sourceType = getAttr(sourceEnd, 'type')
  const targetType = getAttr(targetEnd, 'type')

  if (!sourceType || !targetType) {
    return null
  }

  // Determine relationship type from aggregation attribute
  const aggregation = getAttr(targetEnd, 'aggregation')
  let type: UMLRelationshipType = 'association'
  if (aggregation === 'shared') {
    type = 'aggregation'
  } else if (aggregation === 'composite') {
    type = 'composition'
  }

  return {
    id: getId(element),
    type,
    source: sourceType,
    target: targetType,
    sourceRole: getAttr(sourceEnd, 'name') || undefined,
    targetRole: getAttr(targetEnd, 'name') || undefined,
    sourceMultiplicity: parseMultiplicityFromElements(sourceEnd),
    targetMultiplicity: parseMultiplicityFromElements(targetEnd),
    name: getAttr(element, 'name') || undefined,
  }
}

/**
 * Recursively collects all packagedElements from a container, including nested Packages
 */
const collectAllPackagedElements = (container: Element): Element[] => {
  const allElements: Element[] = []
  const directChildren = container.querySelectorAll(':scope > packagedElement')

  directChildren.forEach(el => {
    const xmiType = getType(el)

    // If it's a Package, recursively collect its children
    if (xmiType === 'uml:Package') {
      allElements.push(...collectAllPackagedElements(el))
    } else {
      // Not a package, add the element itself
      allElements.push(el)
    }
  })

  return allElements
}

/**
 * Parses all UML elements and relationships from XMI
 * Now recursively handles nested Packages
 */
const parseModelElements = (
  modelElement: Element,
): { elements: UMLElement[]; relationships: UMLRelationship[]; layouts: Map<string, NodeLayout> } => {
  const elements: UMLElement[] = []
  const relationships: UMLRelationship[] = []
  const layouts = new Map<string, NodeLayout>()

  // Collect all elements recursively (handles nested Packages)
  const allPackagedElements = collectAllPackagedElements(modelElement)

  allPackagedElements.forEach(el => {
    const xmiType = getType(el)

    // Try to extract layout from eAnnotations
    const layout = parseEAnnotations(el)
    if (layout) {
      layouts.set(layout.elementId, layout)
    }

    switch (xmiType) {
      case 'uml:Class': {
        const classElement = parseClass(el)
        elements.push(classElement)
        // Update layout map with correct ID
        if (layout) {
          layouts.set(classElement.id, { ...layout, elementId: classElement.id })
        }
        break
      }

      case 'uml:Interface': {
        const interfaceElement = parseInterface(el)
        elements.push(interfaceElement)
        if (layout) {
          layouts.set(interfaceElement.id, { ...layout, elementId: interfaceElement.id })
        }
        break
      }

      case 'uml:Enumeration': {
        const enumElement = parseEnumeration(el)
        elements.push(enumElement)
        if (layout) {
          layouts.set(enumElement.id, { ...layout, elementId: enumElement.id })
        }
        break
      }

      case 'uml:Association': {
        const association = parseAssociation(el)
        if (association) {
          relationships.push(association)
        }
        break
      }

      case 'uml:Generalization': {
        const general = getAttr(el, 'general')
        const specific = getAttr(el, 'specific')
        if (general && specific) {
          relationships.push({
            id: getId(el),
            type: 'inheritance',
            source: specific,
            target: general,
          })
        }
        break
      }

      case 'uml:InterfaceRealization': {
        const implementingClassifier = getAttr(el, 'implementingClassifier')
        const contract = getAttr(el, 'contract')
        if (implementingClassifier && contract) {
          relationships.push({
            id: getId(el),
            type: 'realization',
            source: implementingClassifier,
            target: contract,
            name: getAttr(el, 'name') || undefined,
          })
        }
        break
      }

      case 'uml:Dependency': {
        const client = getAttr(el, 'client')
        const supplier = getAttr(el, 'supplier')
        if (client && supplier) {
          relationships.push({
            id: getId(el),
            type: 'dependency',
            source: client,
            target: supplier,
            name: getAttr(el, 'name') || undefined,
          })
        }
        break
      }
    }
  })

  return { elements, relationships, layouts }
}

/**
 * Creates UMLNode from UMLElement with layout information
 */
const createNode = (element: UMLElement, layout: NodeLayout | undefined, index: number): UMLNode => {
  // Default grid layout if no layout info
  const gridColumns = 4
  const spacing = 250
  const defaultPosition = {
    x: (index % gridColumns) * spacing + 50,
    y: Math.floor(index / gridColumns) * spacing + 50,
  }

  return {
    id: element.id,
    type: element.type,
    position: layout ? { x: layout.x, y: layout.y } : defaultPosition,
    data: {
      element,
      label: element.name,
    },
    width: layout?.width,
    height: layout?.height,
  }
}

/**
 * Creates UMLEdge from UMLRelationship
 */
const createEdge = (relationship: UMLRelationship): UMLEdge => {
  return {
    id: relationship.id,
    type: relationship.type,
    source: relationship.source,
    target: relationship.target,
    data: {
      relationship,
      label: relationship.name,
    },
  }
}

/**
 * Main import function - parses XMI content and returns a UMLDiagram
 * Supports Eclipse UML2 5.0.0 format with eAnnotations for layout
 */
export const importFromXmi = (xmiContent: string): XmiImportResult => {
  const warnings: string[] = []

  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xmiContent, 'application/xml')

    // Check for parse errors
    const parseError = doc.querySelector('parsererror')
    if (parseError) {
      return {
        success: false,
        errors: [`Invalid XML format: ${parseError.textContent}`],
        warnings: [],
      }
    }

    // Find the UML Model element
    const modelElement = doc.querySelector('uml\\:Model, Model')
    if (!modelElement) {
      return {
        success: false,
        errors: ['No UML Model found in XMI file'],
        warnings: [],
      }
    }

    // Get model name
    const modelName = getAttr(modelElement, 'name') || 'Imported Diagram'
    const modelId = getId(modelElement).replace('_model', '')

    // Parse elements, relationships, and eAnnotations layouts
    const { elements, relationships, layouts } = parseModelElements(modelElement)

    if (elements.length === 0) {
      warnings.push('No UML elements found in the model')
    }

    // Check if we have any layout info
    if (layouts.size === 0) {
      warnings.push('No layout information found, using auto-layout')
    }

    // Create nodes
    const nodes: UMLNode[] = elements.map((element, index) => {
      return createNode(element, layouts.get(element.id), index)
    })

    // Create edges (filter out relationships with missing source/target)
    const elementIds = new Set(elements.map(e => e.id))
    const edges: UMLEdge[] = relationships
      .filter(rel => {
        const hasSource = elementIds.has(rel.source)
        const hasTarget = elementIds.has(rel.target)
        if (!hasSource || !hasTarget) {
          const missingPart = !hasSource ? 'source' : 'target'
          warnings.push(`Skipping relationship ${rel.id}: missing ${missingPart} element`)
          return false
        }
        return true
      })
      .map(createEdge)

    // Create the diagram
    const diagram: UMLDiagram = {
      id: modelId,
      name: modelName,
      nodes,
      edges,
      lastModified: new Date(),
      isDirty: false,
    }

    return {
      success: true,
      diagram,
      errors: [],
      warnings,
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    return {
      success: false,
      errors: [`Failed to parse XMI: ${errorMessage}`],
      warnings: [],
    }
  }
}

/**
 * Reads a file and imports it as XMI
 */
export const importXmiFromFile = (file: File): Promise<XmiImportResult> => {
  return new Promise(resolve => {
    const reader = new FileReader()

    reader.onload = event => {
      const content = event.target?.result as string
      if (!content) {
        resolve({
          success: false,
          errors: ['Failed to read file content'],
          warnings: [],
        })
        return
      }

      resolve(importFromXmi(content))
    }

    reader.onerror = () => {
      const errorMessage = reader.error?.message || 'Unknown error'
      resolve({
        success: false,
        errors: [`Failed to read file: ${errorMessage}`],
        warnings: [],
      })
    }

    reader.readAsText(file)
  })
}
