import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'

import { AppSidebarHeader } from './AppSidebarHeader'

describe('AppSidebarHeader', () => {
  const renderWithProvider = () => {
    return render(
      <SidebarProvider>
        <AppSidebarHeader />
      </SidebarProvider>,
    )
  }

  describe('tenant name from environment variable', () => {
    beforeEach(() => {
      process.env.NEXT_PUBLIC_TENANT_NAME = 'Test-Mandant'
    })

    afterEach(() => {
      delete process.env.NEXT_PUBLIC_TENANT_NAME
    })

    it('renders the tenant name from NEXT_PUBLIC_TENANT_NAME', () => {
      renderWithProvider()

      expect(screen.getByText('Test-Mandant')).toBeInTheDocument()
    })
  })

  describe('fallback when env variable is not set', () => {
    beforeEach(() => {
      delete process.env.NEXT_PUBLIC_TENANT_NAME
    })

    it('renders fallback tenant name when env variable is not set', () => {
      renderWithProvider()

      expect(screen.getByText('Mandanten-Name')).toBeInTheDocument()
    })
  })

  describe('static content', () => {
    it('renders CIVITAS/CORE subtitle', () => {
      renderWithProvider()

      expect(screen.getByText('CIVITAS/CORE')).toBeInTheDocument()
    })
  })
})
