import { render } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'
import messages from '@/messages/de.json'
import { MeAssignment, PERMISSION_NAMES, PermissionName } from '@/types/currentUser'

vi.mock('next/navigation', () => ({
  usePathname: vi.fn(() => '/'),
}))

vi.mock('@/components/ui/collapsible', () => ({
  Collapsible: ({ children, ...props }: React.HTMLAttributes<HTMLDivElement>) => <div {...props}>{children}</div>,
  CollapsibleTrigger: ({ children, ...props }: React.HTMLAttributes<HTMLButtonElement>) => (
    <button data-slot="collapsible-trigger" {...props}>
      {children}
    </button>
  ),
  CollapsibleContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

import { AppSidebarContent } from './AppSidebarContent'

const allPermissions: PermissionName[] = Object.values(PERMISSION_NAMES)

const makeAssignments = (permissions: PermissionName[]): MeAssignment[] => [
  { scopeType: 'TENANT', scopeId: null, permissions },
]

const renderWithProvider = (assignments: MeAssignment[]) => {
  return render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <SidebarProvider>
        <AppSidebarContent assignments={assignments} />
      </SidebarProvider>
    </NextIntlClientProvider>,
  )
}

describe('AppSidebarContent', () => {
  describe('permission filtering', () => {
    it('renders all nav links when user has all permissions', () => {
      const { container } = renderWithProvider(makeAssignments(allPermissions))

      expect(container.querySelector('a[href="/datasets"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/datasources"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/datastructures"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/users"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/groups"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/roles"]')).toBeInTheDocument()
    })

    it('hides data items when data permissions are missing', () => {
      const { container } = renderWithProvider(
        makeAssignments([PERMISSION_NAMES.USER_READ, PERMISSION_NAMES.GROUP_READ, PERMISSION_NAMES.ROLE_READ]),
      )

      expect(container.querySelector('a[href="/datasets"]')).not.toBeInTheDocument()
      expect(container.querySelector('a[href="/datasources"]')).not.toBeInTheDocument()
      expect(container.querySelector('a[href="/datastructures"]')).not.toBeInTheDocument()
    })

    it('hides admin items when admin permissions are missing', () => {
      const { container } = renderWithProvider(
        makeAssignments([
          PERMISSION_NAMES.DATASET_READ,
          PERMISSION_NAMES.DATASOURCE_READ,
          PERMISSION_NAMES.DATASTRUCTURE_READ,
        ]),
      )

      expect(container.querySelector('a[href="/users"]')).not.toBeInTheDocument()
      expect(container.querySelector('a[href="/groups"]')).not.toBeInTheDocument()
      expect(container.querySelector('a[href="/roles"]')).not.toBeInTheDocument()
    })

    it('always renders documentation link', () => {
      const { container } = renderWithProvider(makeAssignments([]))

      const docsLink = container.querySelector('a[target="_blank"]')
      expect(docsLink).toBeInTheDocument()
    })

    it('renders no data or admin items when assignments are empty', () => {
      const { container } = renderWithProvider([])

      expect(container.querySelector('a[href="/datasets"]')).not.toBeInTheDocument()
      expect(container.querySelector('a[href="/users"]')).not.toBeInTheDocument()
      expect(container.querySelector('a[target="_blank"]')).toBeInTheDocument()
    })
  })
})
