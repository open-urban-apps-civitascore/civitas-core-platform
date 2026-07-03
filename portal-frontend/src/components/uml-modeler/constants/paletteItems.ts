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
    id: 'association',
    type: 'association' as const,
    label: 'Association',
    description: 'Solid line with arrow (uses/knows about)',
    icon: 'arrowRight',
  },
  {
    id: 'aggregation',
    type: 'aggregation' as const,
    label: 'Aggregation',
    description: 'Hollow diamond (has-a, weak ownership)',
    icon: 'gem',
  },
  {
    id: 'composition',
    type: 'composition' as const,
    label: 'Composition',
    description: 'Filled diamond (strong ownership)',
    icon: 'diamond',
  },
  {
    id: 'dependency',
    type: 'dependency' as const,
    label: 'Dependency',
    description: 'Dashed arrow (temporary relationship)',
    icon: 'moveRight',
  },
]
