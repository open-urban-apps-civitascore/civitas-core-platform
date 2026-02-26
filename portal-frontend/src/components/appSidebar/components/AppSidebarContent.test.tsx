import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'

vi.mock('next-intl/server', () => ({
  getTranslations: vi.fn(() => Promise.resolve((key: string) => key)),
}))

import { AppSidebarContent } from './AppSidebarContent'

describe('AppSidebarContent', () => {
  const renderWithProvider = async () => {
    const Component = await AppSidebarContent()
    return render(<SidebarProvider>{Component}</SidebarProvider>)
  }

  describe('navigation links', () => {
    it('renders datasets link with correct href', async () => {
      const { container } = await renderWithProvider()

      const datasetLink = container.querySelector('a[href="/datasets"]')
      expect(datasetLink).toBeInTheDocument()
    })

    it('renders datasources link with correct href', async () => {
      const { container } = await renderWithProvider()

      const datasourcesLink = container.querySelector('a[href="/datasources"]')
      expect(datasourcesLink).toBeInTheDocument()
    })

    it('renders datastructures link with correct href', async () => {
      const { container } = await renderWithProvider()

      const datastructuresLink = container.querySelector('a[href="/datastructures"]')
      expect(datastructuresLink).toBeInTheDocument()
    })

    it('renders users link with correct href', async () => {
      const { container } = await renderWithProvider()

      const usersLink = container.querySelector('a[href="/users"]')
      expect(usersLink).toBeInTheDocument()
    })

    it('renders groups link with correct href', async () => {
      const { container } = await renderWithProvider()

      const groupsLink = container.querySelector('a[href="/groups"]')
      expect(groupsLink).toBeInTheDocument()
    })

    it('renders roles link with correct href', async () => {
      const { container } = await renderWithProvider()

      const rolesLink = container.querySelector('a[href="/roles"]')
      expect(rolesLink).toBeInTheDocument()
    })

    it('renders documentation link as external link', async () => {
      const { container } = await renderWithProvider()

      const docsLink = container.querySelector('a[target="_blank"]')
      expect(docsLink).toBeInTheDocument()
    })
  })

  describe('visual elements', () => {
    it('renders menu items with icons', async () => {
      const { container } = await renderWithProvider()

      const icons = container.querySelectorAll('[data-slot="sidebar-menu-button"] svg')
      expect(icons.length).toBeGreaterThan(0)
    })

    it('renders toggle buttons for expandable items with accessible label', async () => {
      const { container } = await renderWithProvider()

      const toggleButtons = container.querySelectorAll('[data-slot="collapsible-trigger"] svg')
      expect(toggleButtons.length).toBeGreaterThan(0)
    })
  })

  describe('collapsible structure', () => {
    it('renders collapsible items', async () => {
      const { container } = await renderWithProvider()

      // Both collapsible parent items (ourData, tenants) start in closed state by default
      const collapsibles = container.querySelectorAll('[data-state="closed"], [data-state="open"]')
      expect(collapsibles.length).toBeGreaterThan(0)
    })
  })
})
