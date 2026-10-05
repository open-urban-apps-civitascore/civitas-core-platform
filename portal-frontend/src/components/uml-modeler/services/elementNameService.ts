import { DEFAULT_NAMES } from '../constants/umlTypes'
import type { UMLNode } from '../types/diagram'
import type { UMLElementType } from '../types/uml'

const ELEMENT_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/
const FIRST_NAME_SUFFIX = 2

export type ElementNameError = 'required' | 'invalidCharacters' | 'duplicateElement'

export const getElementNameError = (name: string, otherElementNames: string[]): ElementNameError | undefined => {
  if (!name) return 'required'
  if (!ELEMENT_NAME_PATTERN.test(name)) return 'invalidCharacters'
  if (otherElementNames.includes(name)) return 'duplicateElement'
  return undefined
}

export const getOtherElementNames = (nodes: UMLNode[], nodeId: string): string[] => {
  const otherNodes = nodes.filter(node => node.id !== nodeId)
  return otherNodes.map(node => node.data.element.name)
}

export const hasInvalidNames = (nodes: UMLNode[]): boolean =>
  nodes.some(node => getElementNameError(node.data.element.name, getOtherElementNames(nodes, node.id)) !== undefined)

export const getNextElementName = (elementType: UMLElementType, takenNames: string[] = []): string => {
  const baseName = DEFAULT_NAMES[elementType]
  if (!takenNames.includes(baseName)) return baseName
  let suffix = FIRST_NAME_SUFFIX
  while (takenNames.includes(`${baseName}${suffix}`)) suffix++
  return `${baseName}${suffix}`
}
