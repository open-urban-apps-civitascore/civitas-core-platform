import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import Page from './page'

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

describe('Page', () => {
  beforeEach(() => {
    render(<Page />)
  })

  it('renders the civitas logo', () => {
    const logo = screen.getByAltText('Civitas Logo')
    expect(logo).toBeInTheDocument()
    expect(logo).toHaveAttribute('src', '/images/only_logo_civitas.svg')
  })

  it('renders the welcome title', () => {
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('title')
  })

  it('renders the subtitle', () => {
    expect(screen.getByText('subtitle')).toBeInTheDocument()
  })

  it('renders the documentation button with correct link', () => {
    const link = screen.getByRole('link', { name: /docsButton/i })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', 'https://docs.core.civitasconnect.digital/')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('renders the file icon inside the documentation button', () => {
    const link = screen.getByRole('link', { name: /docsButton/i })
    expect(link.querySelector('svg')).toBeInTheDocument()
  })

  it('renders the recommended workflow section title', () => {
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('title')
  })

  it('renders each workflow step title and subtitle', () => {
    expect(screen.getByText('steps.datastructure.title')).toBeInTheDocument()
    expect(screen.getByText('steps.datastructure.subtitle')).toBeInTheDocument()
    expect(screen.getByText('steps.datasource.title')).toBeInTheDocument()
    expect(screen.getByText('steps.datasource.subtitle')).toBeInTheDocument()
    expect(screen.getByText('steps.pipeline.title')).toBeInTheDocument()
    expect(screen.getByText('steps.pipeline.subtitle')).toBeInTheDocument()
  })

  it('renders the three workflow illustrations with correct sources', () => {
    const images = document.querySelectorAll('img[src^="/svg/workflow/"]')
    expect(Array.from(images).map(img => img.getAttribute('src'))).toEqual([
      '/svg/workflow/datastructure.svg',
      '/svg/workflow/datasource.svg',
      '/svg/workflow/pipeline.svg',
    ])
  })
})
