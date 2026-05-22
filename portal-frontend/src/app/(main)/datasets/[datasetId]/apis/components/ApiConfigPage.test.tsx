import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UseFormReturn } from 'react-hook-form'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Dataset } from '@/types/datasets'
import { API_TYPE_QUERY, NamedApi, StaApiFormData } from '@/types/namedApis'

import { ApiConfigPage } from './ApiConfigPage'

// --- mocks ---

const mockPatchDataset = vi.fn().mockResolvedValue(undefined)
const mockCreateNamedApi = vi.fn().mockResolvedValue(undefined)
const mockPush = vi.fn()
const mockReplace = vi.fn()
const mockRefresh = vi.fn()
const mockGetSearchParam = vi.fn().mockReturnValue(null)

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  usePatchDataset: () => ({ mutateAsync: mockPatchDataset, isPending: false }),
  useCreateNamedApi: () => ({ mutateAsync: mockCreateNamedApi, isPending: false }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, refresh: mockRefresh }),
  usePathname: () => '/datasets/test-id/apis/existing-slug',
  useSearchParams: () => ({ get: mockGetSearchParam, toString: () => '' }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('./ApiConfigForm', () => ({
  ApiConfigForm: ({ form }: { form: UseFormReturn<StaApiFormData> }) => (
    <div>
      <input data-testid="nameInput" {...form.register('baseInfo.name')} />
      <input data-testid="slugInput" {...form.register('baseInfo.slug')} />
    </div>
  ),
}))

vi.mock('@/components/page-header/PageHeader', () => ({
  PageHeader: ({ customElement }: { customElement?: React.ReactNode }) => (
    <div data-testid="pageHeader">{customElement}</div>
  ),
}))

vi.mock('@/components/page-container/PageContainer', () => ({
  PageContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/page-background/PageBackground', () => ({
  PageBackground: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/content-card/ContentCard', () => ({
  ContentCard: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

// --- helpers ---

const makeDataset = (overrides: Partial<Dataset> = {}): Dataset => ({
  id: 'test-id',
  name: 'Test Dataset',
  description: 'A test dataset',
  dataSetStatus: 'DRAFT',
  pipelines: [],
  createdAt: '2024-01-01',
  modifiedAt: '2024-01-01',
  openDataAccess: false,
  createdBy: { id: 'user-1', name: 'User One' },
  ...overrides,
})

const makeExistingApi = (overrides: Partial<NamedApi> = {}): NamedApi => ({
  id: 'api-1',
  name: 'Existing API',
  slug: 'existing-slug',
  standard: 'STA',
  description: 'An existing API',
  ...overrides,
})

interface RenderProps {
  dataset?: Dataset
  apiType?: (typeof API_TYPE_QUERY)[keyof typeof API_TYPE_QUERY]
  existingApi?: NamedApi
}

const renderComponent = ({ dataset, apiType, existingApi }: RenderProps = {}) =>
  render(
    <ApiConfigPage
      dataset={dataset ?? makeDataset()}
      apiType={apiType ?? API_TYPE_QUERY.SENSORTHINGS}
      existingApi={existingApi}
    />,
  )

// --- tests ---

describe('ApiConfigPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSearchParam.mockReturnValue(null)
  })

  describe('Mode detection', () => {
    it('renders in edit mode (no editButton) when there is no existing api (create)', () => {
      renderComponent()
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })

    it('renders in view mode (editButton shown) when existing api has no mode=edit param', () => {
      mockGetSearchParam.mockReturnValue(null)
      renderComponent({ existingApi: makeExistingApi() })
      expect(screen.getByTestId('editButton')).toBeInTheDocument()
      expect(screen.queryByTestId('cancelButton')).not.toBeInTheDocument()
    })

    it('renders in edit mode when existing api and mode=edit param is present', () => {
      mockGetSearchParam.mockReturnValue('edit')
      renderComponent({ existingApi: makeExistingApi() })
      expect(screen.queryByTestId('editButton')).not.toBeInTheDocument()
      expect(screen.getByTestId('cancelButton')).toBeInTheDocument()
    })
  })

  describe('Edit button', () => {
    it('switches to edit mode and calls router.replace with mode=edit on click', async () => {
      mockGetSearchParam.mockReturnValue(null)
      renderComponent({ existingApi: makeExistingApi() })
      await userEvent.click(screen.getByTestId('editButton'))
      expect(mockReplace).toHaveBeenCalledWith(
        expect.stringContaining('mode=edit'),
        expect.objectContaining({ scroll: false }),
      )
    })
  })

  describe('Cancel / exit without unsaved changes', () => {
    it('navigates to dataset overview when cancel is clicked in create mode with no changes', async () => {
      renderComponent()
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id')
    })

    it('switches to view mode when cancel is clicked in edit mode with no changes', async () => {
      mockGetSearchParam.mockReturnValue('edit')
      renderComponent({ existingApi: makeExistingApi() })
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(mockReplace).toHaveBeenCalledWith('/datasets/test-id/apis/existing-slug', { scroll: false })
    })
  })

  describe('Exit warning modal', () => {
    it('opens the exit modal when cancel is clicked with unsaved changes', async () => {
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'New Name')
      fireEvent.click(screen.getByTestId('cancelButton'))
      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('resets form and exits when discard is clicked in the modal (create mode)', async () => {
      renderComponent()
      await userEvent.type(screen.getByTestId('nameInput'), 'New Name')
      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id')
    })

    it('resets form and exits when discard is clicked in the modal (edit mode)', async () => {
      mockGetSearchParam.mockReturnValue('edit')
      renderComponent({ existingApi: makeExistingApi() })
      await userEvent.clear(screen.getByTestId('nameInput'))
      await userEvent.type(screen.getByTestId('nameInput'), 'Changed Name')
      fireEvent.click(screen.getByTestId('cancelButton'))
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(mockReplace).toHaveBeenCalledWith('/datasets/test-id/apis/existing-slug', { scroll: false })
    })
  })

  describe('Save — create flow', () => {
    it('calls createNamedApi.mutateAsync with correct payload and shows success toast', async () => {
      renderComponent({ apiType: API_TYPE_QUERY.SENSORTHINGS })
      await userEvent.type(screen.getByTestId('nameInput'), 'My New API')
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        expect(mockCreateNamedApi).toHaveBeenCalledWith(
          expect.objectContaining({
            datasetId: 'test-id',
            api: expect.objectContaining({
              name: 'My New API',
              slug: 'sta',
              standard: 'STA',
            }),
            existingApis: [],
          }),
        )
        expect(toast.success).toHaveBeenCalled()
      })
    })

    it('navigates to the new api slug with mode=edit after successful create', async () => {
      renderComponent({ apiType: API_TYPE_QUERY.SENSORTHINGS })
      await userEvent.type(screen.getByTestId('nameInput'), 'My New API')
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        expect(mockPush).toHaveBeenCalledWith('/datasets/test-id/apis/sta?mode=edit')
      })
    })

    it('shows error toast when createNamedApi rejects', async () => {
      mockCreateNamedApi.mockRejectedValueOnce(new Error('network'))
      renderComponent({ apiType: API_TYPE_QUERY.SENSORTHINGS })
      await userEvent.type(screen.getByTestId('nameInput'), 'My New API')
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
    })
  })

  describe('Save — update flow', () => {
    it('calls patchDataset.mutateAsync with merged namedApis and shows success toast', async () => {
      mockGetSearchParam.mockReturnValue('edit')
      const existingApi = makeExistingApi()
      renderComponent({ existingApi })
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        expect(mockPatchDataset).toHaveBeenCalledWith(
          expect.objectContaining({
            id: 'test-id',
            namedApis: expect.arrayContaining([expect.objectContaining({ slug: 'existing-slug' })]),
          }),
        )
        expect(toast.success).toHaveBeenCalled()
      })
    })

    it('calls router.refresh and switches to view mode after successful update', async () => {
      mockGetSearchParam.mockReturnValue('edit')
      renderComponent({ existingApi: makeExistingApi() })
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        expect(mockRefresh).toHaveBeenCalled()
        expect(mockReplace).toHaveBeenCalledWith('/datasets/test-id/apis/existing-slug', { scroll: false })
      })
    })

    it('trims whitespace from name and description before saving', async () => {
      mockGetSearchParam.mockReturnValue('edit')
      renderComponent({ existingApi: makeExistingApi() })
      await userEvent.clear(screen.getByTestId('nameInput'))
      await userEvent.type(screen.getByTestId('nameInput'), '  Trimmed Name  ')
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        expect(mockPatchDataset).toHaveBeenCalledWith(
          expect.objectContaining({
            namedApis: expect.arrayContaining([expect.objectContaining({ name: 'Trimmed Name' })]),
          }),
        )
      })
    })

    it('excludes other apis from the update payload when the dataset has multiple apis', async () => {
      mockGetSearchParam.mockReturnValue('edit')
      const otherApi = makeExistingApi({ id: 'api-2', slug: 'other-api', name: 'Other' })
      const existingApi = makeExistingApi()
      const dataset = makeDataset({ namedApis: [existingApi, otherApi] })
      renderComponent({ existingApi, dataset })
      fireEvent.submit(screen.getByTestId('apiConfigForm'))

      await waitFor(() => {
        const namedApis = mockPatchDataset.mock.calls[0][0].namedApis as { slug: string }[]
        expect(namedApis.some(a => a.slug === 'other-api')).toBe(true)
        expect(namedApis.some(a => a.slug === 'existing-slug')).toBe(true)
      })
    })
  })
})
