import type { UMLElementType } from '../types/uml'

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

// Relationship palette items for drawing connections
export const RELATIONSHIP_PALETTE_ITEMS = [
  {
    id: 'inheritance',
    type: 'inheritance' as const,
    label: 'Inheritance',
    description: 'Hollow triangle arrow (extends/inherits)',
    icon: 'arrowUpRight',
  },
  {
    id: 'composition',
    type: 'composition' as const,
    label: 'Composition',
    description: 'Filled diamond (strong ownership)',
    icon: 'diamond',
  },
]
