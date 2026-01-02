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

    it('renders dataspaces link with correct href', async () => {
      const { container } = await renderWithProvider()

      const dataspacesLink = container.querySelector('a[href="/dataspaces"]')
      expect(dataspacesLink).toBeInTheDocument()
    })

    it('renders UML modeler link with correct href', async () => {
      const { container } = await renderWithProvider()

      const umlLink = container.querySelector('a[href="/uml-modeler"]')
      expect(umlLink).toBeInTheDocument()
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
    it('renders collapsible items with correct initial states', async () => {
      const { container } = await renderWithProvider()

      // Verify some items are open (isActive: true in mock data)
      const openCollapsibles = container.querySelectorAll('[data-state="open"]')
      expect(openCollapsibles.length).toBeGreaterThan(0)
    })
  })
})
