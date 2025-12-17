import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'

import { AppSidebarHeader } from './AppSidebarHeader'

describe('AppSidebarHeader', () => {
  const currentOrganization = {
    organizationName: 'Current Org',
    tenant: 'Current Tenant',
  }

  const organizations = [
    { organizationName: 'Organization 2', tenant: 'Tenant B' },
    { organizationName: 'Organization 3', tenant: 'Tenant C' },
  ]

  const renderWithProvider = (
    props: { currentOrganization: typeof currentOrganization; organizations: typeof organizations } = {
      currentOrganization,
      organizations,
    },
  ) => {
    return render(
      <SidebarProvider>
        <AppSidebarHeader {...props} />
      </SidebarProvider>,
    )
  }

  describe('dropdown trigger', () => {
    it('renders dropdown trigger button', () => {
      renderWithProvider()

      const button = screen.getByRole('button')
      expect(button).toBeInTheDocument()
    })

    it('has correct accessibility attributes for dropdown menu', () => {
      renderWithProvider()

      const button = screen.getByRole('button')
      expect(button).toHaveAttribute('aria-haspopup', 'menu')
      expect(button).toHaveAttribute('data-state', 'closed')
    })

    it('displays organization info in trigger button', () => {
      renderWithProvider()

      const button = screen.getByRole('button')
      expect(button).toHaveTextContent('Current Org')
      expect(button).toHaveTextContent('Current Tenant')
    })
  })

  describe('edge cases', () => {
    it('renders correctly with empty organizations array', () => {
      renderWithProvider({ currentOrganization, organizations: [] })

      expect(screen.getByText('Current Org')).toBeInTheDocument()
      expect(screen.getByText('Current Tenant')).toBeInTheDocument()
    })
  })
})
