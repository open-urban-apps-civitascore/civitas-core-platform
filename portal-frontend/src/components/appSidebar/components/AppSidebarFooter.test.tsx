import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it, vi } from 'vitest'

import { SidebarProvider } from '@/components/ui/sidebar'
import messages from '@/messages/de.json'
import { CurrentUser } from '@/types/currentUser'

import { AppSidebarFooter } from './AppSidebarFooter'

vi.mock('@/auth', () => ({
  signOut: vi.fn(),
}))

const mockCurrentUser: CurrentUser = {
  username: 'jdoe',
  email: 'john.doe@example.com',
  title: 'MR',
  firstName: 'John',
  lastName: 'Doe',
  assignments: [],
}

describe('AppSidebarFooter', () => {
  const renderWithProviders = (currentUser: CurrentUser) => {
    return render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <SidebarProvider>
          <AppSidebarFooter currentUser={currentUser} />
        </SidebarProvider>
      </NextIntlClientProvider>,
    )
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('user info display', () => {
    it('renders full name from firstName and lastName', () => {
      renderWithProviders(mockCurrentUser)

      expect(screen.getByText('John Doe')).toBeInTheDocument()
    })

    it('renders email', () => {
      renderWithProviders(mockCurrentUser)

      expect(screen.getByText('john.doe@example.com')).toBeInTheDocument()
    })
  })

  describe('avatar display', () => {
    it('renders avatar fallback with first letter of firstName', () => {
      renderWithProviders(mockCurrentUser)

      expect(screen.getAllByText('J')[0]).toBeInTheDocument()
    })
  })

  describe('dropdown trigger', () => {
    it('renders dropdown trigger button', () => {
      renderWithProviders(mockCurrentUser)

      const button = screen.getByRole('button')
      expect(button).toBeInTheDocument()
    })

    it('displays user info in trigger button', () => {
      renderWithProviders(mockCurrentUser)

      const button = screen.getByRole('button')
      expect(button).toHaveTextContent('John Doe')
      expect(button).toHaveTextContent('john.doe@example.com')
    })
  })
})
