import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { SubHeader } from './SubHeader'

describe('SubHeader', () => {
  const defaultProps = {
    title: 'Test Title',
  }

  const renderComponent = (props = {}) => {
    return render(<SubHeader {...defaultProps} {...props} />)
  }

  describe('rendering', () => {
    it('renders title as h2 heading', () => {
      renderComponent()

      const heading = screen.getByRole('heading', { level: 2 })
      expect(heading).toBeInTheDocument()
      expect(heading).toHaveTextContent('Test Title')
    })

    it('renders subtitle when provided', () => {
      renderComponent({ subtitle: 'Test Subtitle' })

      expect(screen.getByText('Test Subtitle')).toBeInTheDocument()
    })

    it('renders custom element when provided', () => {
      renderComponent({ customElement: <button data-testid="custom-button">Click me</button> })

      expect(screen.getByTestId('custom-button')).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument()
    })

    it('merges custom className with default classes', () => {
      const { container } = renderComponent({ className: 'custom-class' })

      const wrapper = container.firstChild as HTMLElement
      expect(wrapper).toHaveClass('custom-class')
    })
  })
})
