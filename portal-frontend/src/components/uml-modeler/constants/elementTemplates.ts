import type { UMLNode, UMLNodeData } from '../types/diagram'
import type { UMLAbstractClass, UMLClass, UMLElement, UMLElementType, UMLEnumeration, UMLInterface } from '../types/uml'
import { DEFAULT_NAMES } from './umlTypes'

// Helper function to generate unique IDs
const generateId = () => crypto.randomUUID()

// Counter for auto-naming
let elementCounter = {
  class: 1,
  interface: 1,
  abstractClass: 1,
  enumeration: 1,
}

// Get next auto-generated name for element type
export const getNextElementName = (elementType: UMLElementType): string => {
  const baseName = DEFAULT_NAMES[elementType]
  const count = elementCounter[elementType]
  elementCounter[elementType]++

  return count === 1 ? baseName : `${baseName}${count}`
}

// Reset element counters (useful for new diagrams)
export const resetElementCounters = (): void => {
  elementCounter = {
    class: 1,
    interface: 1,
    abstractClass: 1,
    enumeration: 1,
  }
}

// Default UML Class template. Operations are unsupported for the first release and
// hidden from the editor, so a new class starts without one.
export const createClassTemplate = (name?: string): UMLClass => ({
  id: generateId(),
  name: name || getNextElementName('class'),
  type: 'class',
  attributes: [
    {
      id: generateId(),
      name: 'attribut',
      type: 'String',
    },
  ],
  operations: [],
})

// Default UML Interface template. Operations are unsupported for the first release
// and hidden from the editor; since the interface model carries no other members,
// a new interface starts empty.
export const createInterfaceTemplate = (name?: string): UMLInterface => ({
  id: generateId(),
  name: name || getNextElementName('interface'),
  type: 'interface',
  stereotype: '<<interface>>',
  operations: [],
})

// Default UML Abstract Class template. Operations are unsupported for the first
// release and hidden from the editor, so a new abstract class starts with a single
// attribute and no operation.
export const createAbstractClassTemplate = (name?: string): UMLAbstractClass => ({
  id: generateId(),
  name: name || getNextElementName('abstractClass'),
  type: 'abstractClass',
  attributes: [
    {
      id: generateId(),
      name: 'attribut',
      type: 'String',
    },
  ],
  operations: [],
})

// Default UML Enumeration template
export const createEnumerationTemplate = (name?: string): UMLEnumeration => ({
  id: generateId(),
  name: name || getNextElementName('enumeration'),
  type: 'enumeration',
  stereotype: '<<enumeration>>',
  literals: [
    {
      id: generateId(),
      name: 'WERT1',
    },
    {
      id: generateId(),
      name: 'WERT2',
    },
  ],
})

// Factory function to create UML elements by type
export const createElement = (elementType: UMLElementType, name?: string) => {
  switch (elementType) {
    case 'class':
      return createClassTemplate(name)
    case 'interface':
      return createInterfaceTemplate(name)
    case 'abstractClass':
      return createAbstractClassTemplate(name)
    case 'enumeration':
      return createEnumerationTemplate(name)
    default:
      throw new Error(`Unknown element type: ${elementType}`)
  }
}

// Create UML node from element template
/**
 * The canvas node of an element. Every producer goes through here — the palette, and the import of
 * a published structure — so a node a modeller draws and a node that is loaded behave the same:
 * the same drag handle, the same minimum size.
 */
export const umlNodeFor = (element: UMLElement, position: { x: number; y: number }): UMLNode => {
  const nodeData: UMLNodeData = {
    element,
    label: element.name,
    isSelected: false,
    isDirty: true,
  }

  return {
    id: element.id,
    type: element.type,
    position,
    data: nodeData,
    dragHandle: '.node-header',
    style: {
      border: '2px solid #333',
      borderRadius: '8px',
      background: '#fff',
      minWidth: 150,
      minHeight: 80,
    },
  }
}

export const createUMLNode = (
  elementType: UMLElementType,
  position: { x: number; y: number },
  name?: string,
): UMLNode => umlNodeFor(createElement(elementType, name), position)

// Sample diagram templates for quick start
export const SAMPLE_DIAGRAMS = {
  empty: {
    name: 'Empty Diagram',
    description: 'Start with a blank canvas',
    nodes: [],
    edges: [],
  },
  basicClass: {
    name: 'Basic Class Diagram',
    description: 'Simple class with attributes and methods',
    nodes: [createUMLNode('class', { x: 100, y: 100 }, 'Person')],
    edges: [],
  },
  classHierarchy: {
    name: 'Class Hierarchy',
    description: 'Example inheritance structure',
    nodes: [
      createUMLNode('abstractClass', { x: 200, y: 50 }, 'Animal'),
      createUMLNode('class', { x: 100, y: 200 }, 'Dog'),
      createUMLNode('class', { x: 300, y: 200 }, 'Cat'),
    ],
    edges: [
      {
        id: generateId(),
        type: 'inheritance',
        source: 'dog-node-id',
        target: 'animal-node-id',
        data: {
          relationship: {
            id: generateId(),
            type: 'inheritance',
            source: 'dog-element-id',
            target: 'animal-element-id',
          },
        },
      },
    ],
  },
}
