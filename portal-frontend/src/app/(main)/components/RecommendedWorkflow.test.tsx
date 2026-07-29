import { render, screen } from '@testing-library/react'

import { RecommendedWorkflow } from './RecommendedWorkflow'

vi.mock('next-intl', () => ({
  useTranslations: () =>
    Object.assign((key: string) => key, {
      rich: (key: string) => key,
    }),
}))

vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text
  default: (props: React.ImgHTMLAttributes<HTMLImageElement>) => <img {...props} />,
}))

describe('RecommendedWorkflow', () => {
  it('renders the section title', () => {
    render(<RecommendedWorkflow />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('title')
  })

  it('renders each workflow step title and subtitle', () => {
    render(<RecommendedWorkflow />)
    expect(screen.getByText('steps.datastructure.title')).toBeInTheDocument()
    expect(screen.getByText('steps.datastructure.subtitle')).toBeInTheDocument()
    expect(screen.getByText('steps.datasource.title')).toBeInTheDocument()
    expect(screen.getByText('steps.datasource.subtitle')).toBeInTheDocument()
    expect(screen.getByText('steps.pipeline.title')).toBeInTheDocument()
    expect(screen.getByText('steps.pipeline.subtitle')).toBeInTheDocument()
  })

  it('renders the workflow illustrations in order with correct sources', () => {
    const { container } = render(<RecommendedWorkflow />)
    const images = container.querySelectorAll('img[src^="/svg/workflow/"]')
    expect(Array.from(images).map(img => img.getAttribute('src'))).toEqual([
      '/svg/workflow/datastructure.svg',
      '/svg/workflow/datasource.svg',
      '/svg/workflow/pipeline.svg',
    ])
  })

  it('numbers the steps sequentially starting at 1', () => {
    render(<RecommendedWorkflow />)
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })
})
