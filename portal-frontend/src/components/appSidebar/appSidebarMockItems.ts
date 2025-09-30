import { BookOpen, Bot, Building2, Database, LucideProps, SquareTerminal, User } from 'lucide-react'
import { ForwardRefExoticComponent, ReactNode, RefAttributes } from 'react'

export interface NavItems {
  title: string
  url: string
  icon?: ReactNode | ForwardRefExoticComponent<Omit<LucideProps, 'ref'> & RefAttributes<SVGSVGElement>>
  isActive?: boolean
  items?: NavItems[]
}

export const appSidebarNavItems = [
  {
    title: 'datasets',
    url: '/datasets',
    icon: Database,
    isActive: true,
    items: [],
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
        title: 'roles',
        url: '/roles',
      },
    ],
  },
  {
    title: 'menu-item 2',
    url: '#',
    icon: SquareTerminal,
    items: [
      {
        title: 'sub-item 1',
        url: '#',
      },
      {
        title: 'sub-item 2',
        url: '#',
      },
      {
        title: 'sub-item 3',
        url: '#',
      },
    ],
  },
  {
    title: 'menu-item 3',
    url: '#',
    icon: Bot,
    items: [
      {
        title: 'sub-item 1',
        url: '#',
      },
      {
        title: 'sub-item 2',
        url: '#',
      },
      {
        title: 'sub-item 3',
        url: '#',
      },
    ],
  },
  {
    title: 'documentation',
    url: '#',
    icon: BookOpen,
  },
]

export const expampleOrganizations = [
  { organizationName: 'Organization 2', tenant: 'Tenant B', icon: Building2 },
  { organizationName: 'Organization 3', tenant: 'Tenant C', icon: Building2 },
]

export const currentOrganization = { organizationName: 'Organization 1', tenant: 'Tenant A', icon: Building2 }
