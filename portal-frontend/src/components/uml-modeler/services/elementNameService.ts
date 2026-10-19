import { DEFAULT_NAMES } from '../constants/umlTypes'
import type { UMLNode } from '../types/diagram'
import { hasAttributes, type UMLElementType } from '../types/uml'

const ELEMENT_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/
const FIRST_NAME_SUFFIX = 2
const FIRST_ENUM_VALUE_NUMBER = 1

export type ElementNameError = 'required' | 'invalidCharacters' | 'duplicateElement'
export type AttributeNameError = 'duplicateAttribute'
export type EnumValueNameError = 'duplicateEnumValue'

interface NamedMember {
  id: string
  name: string
}

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

export const getAttributeNameError = (name: string, otherAttributeNames: string[]): AttributeNameError | undefined =>
  otherAttributeNames.includes(name) ? 'duplicateAttribute' : undefined

export const getEnumValueNameError = (name: string, otherValueNames: string[]): EnumValueNameError | undefined =>
  otherValueNames.includes(name) ? 'duplicateEnumValue' : undefined

export const getOtherMemberNames = (members: NamedMember[], memberId: string): string[] => {
  const otherMembers = members.filter(member => member.id !== memberId)
  return otherMembers.map(member => member.name)
}

export const hasDuplicateMemberNames = (members: NamedMember[]): boolean =>
  members.some(member => getOtherMemberNames(members, member.id).includes(member.name))

export const hasInvalidNamesInNode = (node: UMLNode, nodes: UMLNode[]): boolean => {
  const element = node.data.element
  if (getElementNameError(element.name, getOtherElementNames(nodes, node.id)) !== undefined) return true
  if (hasAttributes(element)) return hasDuplicateMemberNames(element.attributes)
  if (element.type === 'enumeration') return hasDuplicateMemberNames(element.literals)
  return false
}

export const hasInvalidNamesInDiagram = (nodes: UMLNode[]): boolean =>
  nodes.some(node => hasInvalidNamesInNode(node, nodes))

export const getInvalidNodeIds = (nodes: UMLNode[]): Set<string> =>
  new Set(nodes.filter(node => hasInvalidNamesInNode(node, nodes)).map(node => node.id))

const getMemberNames = (node: UMLNode): string[] => {
  const element = node.data.element
  if (hasAttributes(element)) return element.attributes.map(attribute => attribute.name)
  if (element.type === 'enumeration') return element.literals.map(literal => literal.name)
  return []
}

export const getNameSignature = (nodes: UMLNode[]): string =>
  JSON.stringify(nodes.map(node => [node.id, node.data.element.name, getMemberNames(node)]))

const getNextFreeName = (baseName: string, takenNames: string[]): string => {
  if (!takenNames.includes(baseName)) return baseName
  let suffix = FIRST_NAME_SUFFIX
  while (takenNames.includes(`${baseName}${suffix}`)) suffix++
  return `${baseName}${suffix}`
}

export const getNextElementName = (elementType: UMLElementType, takenNames: string[] = []): string =>
  getNextFreeName(DEFAULT_NAMES[elementType], takenNames)

export const getNextAttributeName = (takenNames: string[]): string =>
  getNextFreeName(DEFAULT_NAMES.attribute, takenNames)

export const getNextEnumValueName = (takenNames: string[]): string => {
  let number = FIRST_ENUM_VALUE_NUMBER
  while (takenNames.includes(`${DEFAULT_NAMES.enumLiteral}${number}`)) number++
  return `${DEFAULT_NAMES.enumLiteral}${number}`
}
