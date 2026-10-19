import type { UMLDiagram } from '../types/diagram'
import type { UMLAttribute, UMLElement, UMLOperation, UMLType } from '../types/uml'

// Types the editor once offered and no longer does, with the type a stored diagram reads them as.
// Json stood for any value the structure did not describe. It reads as String, the same
// degradation the JSON Schema import gives such a value. The stored diagram keeps the old name
// until it is saved again.
const RETIRED_TYPES: Record<string, UMLType> = {
  Json: 'String',
}

/** The type a stored diagram means today: a retired type reads as its replacement. */
export const readStoredType = <T>(type: T): T =>
  typeof type === 'string' && Object.hasOwn(RETIRED_TYPES, type) ? (RETIRED_TYPES[type] as T) : type

const attributeWithCurrentType = (attribute: UMLAttribute): UMLAttribute => ({
  ...attribute,
  type: readStoredType(attribute.type),
})

const operationWithCurrentTypes = (operation: UMLOperation): UMLOperation => ({
  ...operation,
  returnType: readStoredType(operation.returnType),
  parameters: operation.parameters?.map(parameter => ({ ...parameter, type: readStoredType(parameter.type) })),
})

const elementWithCurrentTypes = (element: UMLElement): UMLElement => {
  if (element.type === 'enumeration') return element
  const operations = element.operations?.map(operationWithCurrentTypes)
  if (element.type === 'interface') return { ...element, operations }
  return { ...element, attributes: element.attributes?.map(attributeWithCurrentType), operations }
}

/**
 * A stored diagram as the editor reads it today.
 *
 * A saved version's diagram comes back from the platform as it was written, without passing the
 * diagram schema, so a type the editor has since retired would reach the type pickers and the
 * export unchanged.
 */
export const withCurrentTypes = (diagram: UMLDiagram): UMLDiagram => ({
  ...diagram,
  nodes: diagram.nodes?.map(node =>
    node.data?.element
      ? { ...node, data: { ...node.data, element: elementWithCurrentTypes(node.data.element) } }
      : node,
  ),
})
