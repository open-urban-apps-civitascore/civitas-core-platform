import { BookOpen, Building2, LucideProps, SquareMenu, SquareTerminal, User } from 'lucide-react'
import { ForwardRefExoticComponent, ReactNode, RefAttributes } from 'react'

export interface NavItem {
  title: string
  url: string
  icon?: ReactNode | ForwardRefExoticComponent<Omit<LucideProps, 'ref'> & RefAttributes<SVGSVGElement>>
  isActive?: boolean
  items?: NavItem[]
}

export const appSidebarNavItems = [
  {
    title: 'ourData',
    url: '/datasets',
    icon: SquareMenu,
    isActive: true,
  },
  {
    title: 'tenants',
    url: '',
    icon: User,
    isActive: true,
    items: [
      {
        title: 'users',
        url: '/users',
      },
      {
        title: 'groups',
        url: '/groups',
      },
      {
        title: 'roles',
        url: '/roles',
      },
      {
        title: 'permissions',
        url: '/permissions',
      },
      {
        title: 'dataspaces',
        url: '/dataspaces',
      },
    ],
  },
  {
    title: 'documentation',
    url: '#',
    icon: BookOpen,
  },
  {
    title: 'uml-modeler',
    url: '/uml-modeler',
    icon: SquareTerminal,
  },
]

export const expampleOrganizations = [
  { organizationName: 'Organization 2', tenant: 'Tenant B', icon: Building2 },
  { organizationName: 'Organization 3', tenant: 'Tenant C', icon: Building2 },
]

export const currentOrganization = { organizationName: 'Organization 1', tenant: 'Tenant A', icon: Building2 }
