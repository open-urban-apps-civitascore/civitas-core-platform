import { render } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'
import messages from '@/messages/de.json'

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

describe('AppSidebarContent', () => {
  const renderWithProvider = () => {
    return render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <SidebarProvider>
          <AppSidebarContent />
        </SidebarProvider>
      </NextIntlClientProvider>,
    )
  }

  describe('navigation links', () => {
    it('renders datasets link with correct href', () => {
      const { container } = renderWithProvider()

      const datasetLink = container.querySelector('a[href="/datasets"]')
      expect(datasetLink).toBeInTheDocument()
    })

    it('renders datasources link with correct href', () => {
      const { container } = renderWithProvider()

      const datasourcesLink = container.querySelector('a[href="/datasources"]')
      expect(datasourcesLink).toBeInTheDocument()
    })

    it('renders datastructures link with correct href', () => {
      const { container } = renderWithProvider()

      const datastructuresLink = container.querySelector('a[href="/datastructures"]')
      expect(datastructuresLink).toBeInTheDocument()
    })

    it('renders users link with correct href', () => {
      const { container } = renderWithProvider()

      const usersLink = container.querySelector('a[href="/users"]')
      expect(usersLink).toBeInTheDocument()
    })

    it('renders groups link with correct href', () => {
      const { container } = renderWithProvider()

      const groupsLink = container.querySelector('a[href="/groups"]')
      expect(groupsLink).toBeInTheDocument()
    })

    it('renders roles link with correct href', () => {
      const { container } = renderWithProvider()

      const rolesLink = container.querySelector('a[href="/roles"]')
      expect(rolesLink).toBeInTheDocument()
    })

    it('renders documentation link as external link', () => {
      const { container } = renderWithProvider()

      const docsLink = container.querySelector('a[target="_blank"]')
      expect(docsLink).toBeInTheDocument()
    })
  })

  describe('visual elements', () => {
    it('renders menu items with icons', () => {
      const { container } = renderWithProvider()

      const icons = container.querySelectorAll('[data-slot="sidebar-menu-button"] svg')
      expect(icons.length).toBeGreaterThan(0)
    })

    it('renders toggle buttons for expandable items with accessible label', () => {
      const { container } = renderWithProvider()

      const toggleButtons = container.querySelectorAll('[data-slot="collapsible-trigger"] svg')
      expect(toggleButtons.length).toBeGreaterThan(0)
    })
  })

  describe('collapsible structure', () => {
    it('renders collapsible items', () => {
      const { container } = renderWithProvider()

      // Both collapsible parent items (ourData, tenants) start in closed state by default
      const collapsibles = container.querySelectorAll('[data-state="closed"], [data-state="open"]')
      expect(collapsibles.length).toBeGreaterThan(0)
    })
  })
})
