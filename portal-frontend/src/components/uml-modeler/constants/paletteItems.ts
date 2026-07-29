import type { UMLElementType, UMLRelationshipType } from '../types/uml'

// Element palette items for drag & drop
export const ELEMENT_PALETTE_ITEMS = [
  {
    id: 'class',
    type: 'class' as UMLElementType,
    label: 'Class',
    description: 'UML Class with attributes and operations',
    icon: 'box',
  },
  {
    id: 'enumeration',
    type: 'enumeration' as UMLElementType,
    label: 'Enumeration',
    description: 'UML Enumeration with literal values',
    icon: 'list',
  },
]

// Relationship palette items for drawing connections. Single source of the supported relationship
// scope: the palette (drawing) and the edge inspector (retyping) both derive what users may pick
// from here. Adding a type widens the scope for the whole editor; the export/mapping side
// additionally needs its containment category registered in umlContainment.ts.
export const RELATIONSHIP_PALETTE_ITEMS: {
  id: string
  type: UMLRelationshipType
  label: string
  description: string
  icon: string
}[] = [
  {
    id: 'inheritance',
    type: 'inheritance',
    label: 'Inheritance',
    description: 'Hollow triangle arrow (extends/inherits)',
    icon: 'arrowUpRight',
  },
  {
    id: 'composition',
    type: 'composition',
    label: 'Composition',
    description: 'Filled diamond (strong ownership)',
    icon: 'diamond',
  },
]
