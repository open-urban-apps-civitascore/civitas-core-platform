import { render, screen } from '@testing-library/react'

import { WorkflowStepCard } from './WorkflowStepCard'

vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
  default: (props: React.ImgHTMLAttributes<HTMLImageElement>) => <img {...props} />,
}))

const defaultProps = {
  stepNumber: 1,
  title: 'Create a Data structure',
  subtitle: 'Describes the Data',
  illustrationSrc: '/svg/workflow/datastructure.svg',
  illustrationWidth: 129,
  illustrationHeight: 40,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) =>
  render(<WorkflowStepCard {...defaultProps} {...props} />)

describe('WorkflowStepCard', () => {
  it('renders the step number', () => {
    renderComponent({ stepNumber: 3 })
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('renders the title and subtitle', () => {
    renderComponent()
    expect(screen.getByText('Create a Data structure')).toBeInTheDocument()
    expect(screen.getByText('Describes the Data')).toBeInTheDocument()
  })

  it('renders a rich (ReactNode) title', () => {
    renderComponent({ title: <span>Rich title</span> })
    expect(screen.getByText('Rich title')).toBeInTheDocument()
  })

  it('renders the illustration with the given source and dimensions', () => {
    const { container } = renderComponent()
    const image = container.querySelector('img')
    expect(image).toHaveAttribute('src', '/svg/workflow/datastructure.svg')
    expect(image).toHaveAttribute('width', '129')
    expect(image).toHaveAttribute('height', '40')
  })
})
