import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { NamedApi } from '@/types/namedApis'

import { ApiList } from './ApiList'

vi.mock('./ApiCard', () => ({
  ApiCard: ({ api }: { api: { slug: string } }) => <div data-testid={`apiCard-${api.slug}`} />,
}))

vi.mock('@/components/guarded-link/GuardedLink', () => ({
  GuardedLink: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const makeApi = (overrides: Partial<NamedApi> = {}): NamedApi => ({
  id: 'api-1',
  name: 'My API',
  slug: 'my-api',
  standard: 'STA',
  ...overrides,
})

const defaultProps = {
  datasetId: 'dataset-123',
  apis: [] as NamedApi[],
  canEdit: true,
  isOpenDataAccess: false,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) => render(<ApiList {...defaultProps} {...props} />)

describe('ApiList', () => {
  describe('Empty state', () => {
    it('shows the empty message when there are no apis', () => {
      renderComponent({ apis: [] })
      expect(screen.getByText('empty')).toBeInTheDocument()
    })

    it('does not render any ApiCard when the list is empty', () => {
      renderComponent({ apis: [] })
      expect(screen.queryByTestId(/^apiCard-/)).not.toBeInTheDocument()
    })
  })

  describe('Populated list', () => {
    it('renders one ApiCard per api', () => {
      const apis = [makeApi({ slug: 'api-one' }), makeApi({ id: 'api-2', slug: 'api-two' })]
      renderComponent({ apis })
      expect(screen.getByTestId('apiCard-api-one')).toBeInTheDocument()
      expect(screen.getByTestId('apiCard-api-two')).toBeInTheDocument()
    })

    it('does not show the empty message when there are apis', () => {
      renderComponent({ apis: [makeApi()] })
      expect(screen.queryByText('empty')).not.toBeInTheDocument()
    })
  })

  describe('Add API dropdown', () => {
    it('opens the add-api dropdown when the button is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByText('addButton'))
      expect(screen.getByText('sensorThings')).toBeInTheDocument()
      expect(screen.getByText('wfsWms')).toBeInTheDocument()
    })

    it('SensorThings link points to the sensorthings type route', async () => {
      renderComponent({ datasetId: 'ds-1' })
      await userEvent.click(screen.getByText('addButton'))
      const link = screen.getByText('sensorThings').closest('a')
      expect(link).toHaveAttribute('href', '/datasets/ds-1/apis?type=sensorthings')
    })

    it('WFS/WMS link points to the wfs-wms type route when no WFS/WMS api exists', async () => {
      renderComponent({ datasetId: 'ds-1' })
      await userEvent.click(screen.getByText('addButton'))
      const link = screen.getByText('wfsWms').closest('a')
      expect(link).toHaveAttribute('href', '/datasets/ds-1/apis?type=wfs-wms')
    })

    it('WFS/WMS item is disabled when a WFS/WMS api already exists', async () => {
      renderComponent({ apis: [makeApi({ standard: 'WFS' })] })
      await userEvent.click(screen.getByText('addButton'))
      const item = screen.getByText('wfsWms').closest('[role="menuitem"]')
      expect(item).toHaveAttribute('data-disabled')
      expect(screen.getByText('wfsWms').closest('a')).toBeNull()
    })
  })
})
