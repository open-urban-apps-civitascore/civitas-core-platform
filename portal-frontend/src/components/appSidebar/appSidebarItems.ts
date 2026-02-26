import { FileQuestion, LucideProps, SquareMenu, UserCog } from 'lucide-react'
import { ForwardRefExoticComponent, ReactNode, RefAttributes } from 'react'

export interface NavItem {
  title: string
  url: string
  icon?: ReactNode | ForwardRefExoticComponent<Omit<LucideProps, 'ref'> & RefAttributes<SVGSVGElement>>
  isActive?: boolean
  external?: boolean
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
          { title: 'datasets', url: '/datasets' },
          { title: 'datasources', url: '/datasources' },
          { title: 'datastructures', url: '/datastructures' },
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
          { title: 'users', url: '/users' },
          { title: 'groups', url: '/groups' },
          { title: 'roles', url: '/roles' },
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
