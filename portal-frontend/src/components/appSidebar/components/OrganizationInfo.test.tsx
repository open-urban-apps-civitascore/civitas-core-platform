import { render, screen } from '@testing-library/react'
import { Home } from 'lucide-react'
import { describe, expect, it } from 'vitest'

import { OrganizationInfo } from './OrganizationInfo'

describe('OrganizationInfo', () => {
  const defaultProps = {
    organizationName: 'Test Organization',
    tenant: 'Test Tenant',
  }

  it('renders organization name', () => {
    render(<OrganizationInfo {...defaultProps} />)

    expect(screen.getByText('Test Organization')).toBeInTheDocument()
  })

  it('renders tenant', () => {
    render(<OrganizationInfo {...defaultProps} />)

    expect(screen.getByText('Test Tenant')).toBeInTheDocument()
  })

  it('renders default Building2 icon when no icon provided', () => {
    const { container } = render(<OrganizationInfo {...defaultProps} />)

    const svgElement = container.querySelector('svg')
    expect(svgElement).toBeInTheDocument()
  })

  it('renders custom icon when provided', () => {
    render(<OrganizationInfo {...defaultProps} icon={<Home data-testid="custom-icon" />} />)

    expect(screen.getByTestId('custom-icon')).toBeInTheDocument()
  })

  it('applies correct layout structure', () => {
    const { container } = render(<OrganizationInfo {...defaultProps} />)

    const wrapper = container.firstChild as HTMLElement
    expect(wrapper).toHaveClass('flex', 'items-center', 'justify-start', 'space-x-2')
  })
})
