import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'

vi.mock('@/hooks/use-is-truncated', () => ({
  useIsTruncated: () => false,
}))

import { ContactCell } from './ContactCell'

describe('ContactCell', () => {
  it('renders nothing when user is null', () => {
    const { container } = render(<ContactCell user={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when user is undefined', () => {
    const { container } = render(<ContactCell user={undefined} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders a link to the user page', () => {
    render(<ContactCell user={{ id: '42', name: 'Ada Lovelace' }} />)
    expect(screen.getByRole('link')).toHaveAttribute('href', '/users/42')
  })

  it('renders the user name', () => {
    render(<ContactCell user={{ id: '1', name: 'Ada Lovelace' }} />)
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
  })

  it('renders avatar initials from first and last name', () => {
    render(<ContactCell user={{ id: '1', name: 'Ada Lovelace' }} />)
    expect(screen.getByText('AL')).toBeInTheDocument()
  })

  it('renders avatar with same initial twice for single-word name', () => {
    render(<ContactCell user={{ id: '1', name: 'Ada' }} />)
    expect(screen.getByText('AA')).toBeInTheDocument()
  })

  it('uses only first and last name initial when name has more than two parts', () => {
    render(<ContactCell user={{ id: '1', name: 'Ada van Lovelace' }} />)
    expect(screen.getByText('AL')).toBeInTheDocument()
  })
})
