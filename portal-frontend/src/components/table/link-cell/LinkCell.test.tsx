import { render, screen } from '@testing-library/react'

import { LinkCell } from './LinkCell'

describe('LinkCell', () => {
  beforeEach(() => {
    render(<LinkCell href="/test-link">Test Link</LinkCell>)
  })
  it('renders a link with correct href and children', () => {
    const link = screen.getByRole('link')
    expect(link).toHaveTextContent('Test Link')
    expect(link).toHaveAttribute('href', '/test-link')
  })

  it('has underline on hover', () => {
    const link = screen.getByRole('link')
    expect(link).toHaveClass('hover:underline')
  })

  it('renders JSX children correctly', () => {
    render(
      <LinkCell href="/test">
        <span data-testid="custom-child">Custom Content</span>
      </LinkCell>,
    )
    expect(screen.getByTestId('custom-child')).toBeInTheDocument()
  })
})
