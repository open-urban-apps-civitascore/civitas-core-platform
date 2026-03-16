import { FileQuestion, LucideProps, SquareMenu, UserCog } from 'lucide-react'
import { ForwardRefExoticComponent, RefAttributes } from 'react'

import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

export interface NavItem {
  title: string
  url: string
  icon?: ForwardRefExoticComponent<Omit<LucideProps, 'ref'> & RefAttributes<SVGSVGElement>>
  isActive?: boolean
  external?: boolean
  requiredPermission?: PermissionName
  items?: NavItem[]
}

export interface NavSection {
  title: string
  items: NavItem[]
}

export const appSidebarNavSections: NavSection[] = [
  {
    title: 'platform',
    items: [
      {
        title: 'ourData',
        url: '/datasets',
        icon: SquareMenu,
        items: [
          { title: 'datasets', url: '/datasets', requiredPermission: PERMISSION_NAMES.DATASET_READ },
          { title: 'datasources', url: '/datasources', requiredPermission: PERMISSION_NAMES.DATASOURCE_READ },
          { title: 'datastructures', url: '/datastructures', requiredPermission: PERMISSION_NAMES.DATASTRUCTURE_READ },
        ],
      },
    ],
  },
  {
    title: 'admin',
    items: [
      {
        title: 'tenants',
        url: '',
        icon: UserCog,
        items: [
          { title: 'users', url: '/users', requiredPermission: PERMISSION_NAMES.USER_READ },
          { title: 'groups', url: '/groups', requiredPermission: PERMISSION_NAMES.GROUP_READ },
          { title: 'roles', url: '/roles', requiredPermission: PERMISSION_NAMES.ROLE_READ },
        ],
      },
    ],
  },
  {
    title: 'help',
    items: [
      {
        title: 'documentation',
        url: 'https://docs.core.civitasconnect.digital/',
        icon: FileQuestion,
        external: true,
      },
    ],
  },
]
