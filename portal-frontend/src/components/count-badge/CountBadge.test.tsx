import { render, screen } from '@testing-library/react'

import { CountBadge } from './CountBadge'

describe('CountBadge', () => {
  it('renders the count', () => {
    render(<CountBadge count={3} />)
    expect(screen.getByTestId('countBadge')).toHaveTextContent(/^3$/)
  })

  it('renders zero', () => {
    render(<CountBadge count={0} />)
    expect(screen.getByTestId('countBadge')).toHaveTextContent(/^0$/)
  })

  it('uses the given test id', () => {
    render(<CountBadge count={1} testId="customCount" />)
    expect(screen.getByTestId('customCount')).toBeInTheDocument()
  })

  it('adds the given class name', () => {
    render(<CountBadge count={1} className="ml-2" />)
    expect(screen.getByTestId('countBadge')).toHaveClass('ml-2')
  })
})
