import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetDatasinks } from '@/app/services/api/datasets/datasinks/clientRequests'
import { useGetPipelines } from '@/app/services/api/pipelines/clientRequests'
import { API_STANDARDS, NamedApi } from '@/types/namedApis'

import { ApiList } from './ApiList'

vi.mock('./ApiCard', () => ({
  ApiCard: ({ api, canEdit }: { api: { slug: string }; canEdit: boolean }) => (
    <div data-testid={`apiCard-${api.slug}`} data-can-edit={String(canEdit)} />
  ),
}))

vi.mock('@/components/guarded-link/GuardedLink', () => ({
  GuardedLink: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/datasets/datasinks/clientRequests', () => ({
  useGetDatasinks: vi.fn().mockReturnValue({
    data: { data: [{ id: 'sink-1', dataSinkType: 'POSTGIS' }] },
  }),
  useGetPipelines: vi.fn(),
}))

const makeApi = (overrides: Partial<NamedApi> = {}): NamedApi => ({
  id: 'api-1',
  name: 'My API',
  slug: 'my-api',
  standard: 'STA',
  ...overrides,
})

const frostPipelineData = {
  data: [{ id: 'p-1', styles: { nodes: [{ type: 'frost', data: {} }] } }],
}

const defaultProps = {
  datasetId: 'dataset-123',
  apis: [] as NamedApi[],
  canEdit: true,
  isPublished: false,
  isOpenDataAccess: false,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) => render(<ApiList {...defaultProps} {...props} />)

describe('ApiList', () => {
  beforeEach(() => {
    vi.mocked(useGetPipelines).mockReturnValue({
      data: { data: [{ id: 'p-1' }] },
    } as ReturnType<typeof useGetPipelines>)
  })

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
    it('disables the button when there are no pipelines', () => {
      vi.mocked(useGetPipelines).mockReturnValue({ data: undefined } as ReturnType<typeof useGetPipelines>)
      renderComponent()
      expect(screen.getByText('addButton').closest('button')).toBeDisabled()
    })

    it('opens the add-api dropdown when a pipeline exists', async () => {
      renderComponent()
      await userEvent.click(screen.getByText('addButton'))
      expect(screen.getByText('sensorThings')).toBeInTheDocument()
      expect(screen.getByText('ows')).toBeInTheDocument()
    })

    it('SensorThings item is disabled when no FROST pipeline exists', async () => {
      renderComponent()
      await userEvent.click(screen.getByText('addButton'))
      const item = screen.getByText('sensorThings').closest('[role="menuitem"]')
      expect(item).toHaveAttribute('data-disabled')
    })

    it('SensorThings link points to the sensorthings type route when a FROST pipeline exists', async () => {
      vi.mocked(useGetPipelines).mockReturnValue({ data: frostPipelineData } as ReturnType<typeof useGetPipelines>)
      renderComponent({ datasetId: 'ds-1' })
      await userEvent.click(screen.getByText('addButton'))
      const link = screen.getByText('sensorThings').closest('a')
      expect(link).toHaveAttribute('href', '/datasets/ds-1/apis?type=sensorthings')
    })

    it('OWS link points to the ows type route when no OWS api exists', async () => {
      renderComponent({ datasetId: 'ds-1' })
      await userEvent.click(screen.getByText('addButton'))
      const link = screen.getByText('ows').closest('a')
      expect(link).toHaveAttribute('href', '/datasets/ds-1/apis?type=ows')
    })

    it('WFS/WMS item is disabled when a WFS/WMS api already exists', async () => {
      renderComponent({ apis: [makeApi({ standard: API_STANDARDS.OWS })] })
      await userEvent.click(screen.getByText('addButton'))
      const item = screen.getByText('ows').closest('[data-slot="dropdown-menu-item"]')
      expect(item).toHaveAttribute('data-disabled')
      expect(screen.getByText('ows').closest('a')).toBeNull()
    })

    it('WFS/WMS item is disabled when there are no PostGIS datasinks', async () => {
      vi.mocked(useGetDatasinks).mockReturnValueOnce({ data: { data: [] } } as never)
      renderComponent()
      await userEvent.click(screen.getByText('addButton'))
      const item = screen.getByText('ows').closest('[role="menuitem"]')
      expect(item).toHaveAttribute('data-disabled')
      expect(screen.getByText('ows').closest('a')).toBeNull()
    })

    it('disables the button when the dataset is published', () => {
      renderComponent({ isPublished: true })
      expect(screen.getByText('addButton').closest('button')).toBeDisabled()
    })
  })

  describe('Published dataset', () => {
    it('passes canEdit=false to ApiCards when dataset is published', () => {
      renderComponent({ apis: [makeApi()], canEdit: true, isPublished: true })
      expect(screen.getByTestId('apiCard-my-api')).toHaveAttribute('data-can-edit', 'false')
    })

    it('passes canEdit normally to ApiCards when dataset is not published', () => {
      renderComponent({ apis: [makeApi()], canEdit: true, isPublished: false })
      expect(screen.getByTestId('apiCard-my-api')).toHaveAttribute('data-can-edit', 'true')
    })
  })
})
