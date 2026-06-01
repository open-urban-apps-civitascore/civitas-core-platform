import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { NamedApi } from '@/types/namedApis'

import { ApiCard } from './ApiCard'

const mockPatchDataset = vi.fn().mockResolvedValue(undefined)
const mockPush = vi.fn()
const mockRefresh = vi.fn()

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  usePatchDataset: () => ({ mutateAsync: mockPatchDataset, isPending: false }),
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, refresh: mockRefresh }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}))

const makeApi = (overrides: Partial<NamedApi> = {}): NamedApi => ({
  id: 'api-1',
  name: 'My API',
  slug: 'my-api',
  standard: 'STA',
  description: 'A test API',
  ...overrides,
})

const defaultApi = makeApi()
const defaultProps = {
  api: defaultApi,
  datasetId: 'dataset-123',
  existingApis: [defaultApi],
  canEdit: true,
  isOpenDataAccess: false,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) => render(<ApiCard {...defaultProps} {...props} />)

describe('ApiCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('displays the api name and description', () => {
      renderComponent()
      expect(screen.getByText('My API')).toBeInTheDocument()
      expect(screen.getByText('A test API')).toBeInTheDocument()
    })

    it('shows no-description fallback when description is absent', () => {
      renderComponent({ api: makeApi({ description: undefined }) })
      expect(screen.getByText('noDescription')).toBeInTheDocument()
    })

    it('renders the slug in the path preview', () => {
      const slugEl = screen.queryByText('my-api')
      renderComponent()
      expect(screen.getByText('my-api')).toBeInTheDocument()
      void slugEl
    })

    it('shows openDataBadge when isOpenDataAccess is true', () => {
      renderComponent({ isOpenDataAccess: true })
      expect(screen.getByText('openDataBadge')).toBeInTheDocument()
      expect(screen.queryByText('protectedBadge')).not.toBeInTheDocument()
    })

    it('shows protectedBadge when isOpenDataAccess is false', () => {
      renderComponent({ isOpenDataAccess: false })
      expect(screen.getByText('protectedBadge')).toBeInTheDocument()
    })
  })

  describe('Permission gating', () => {
    it('shows edit and delete menu items when canEdit is true', async () => {
      renderComponent({ canEdit: true })
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      expect(screen.getByTestId('apiCardMenuEdit-my-api')).toBeInTheDocument()
      expect(screen.getByTestId('apiCardMenuDelete-my-api')).toBeInTheDocument()
    })

    it('hides edit and delete menu items when canEdit is false', async () => {
      renderComponent({ canEdit: false })
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      expect(screen.queryByTestId('apiCardMenuEdit-my-api')).not.toBeInTheDocument()
      expect(screen.queryByTestId('apiCardMenuDelete-my-api')).not.toBeInTheDocument()
    })

    it('always shows the view item regardless of canEdit', async () => {
      renderComponent({ canEdit: false })
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      expect(screen.getByTestId('apiCardMenuView-my-api')).toBeInTheDocument()
    })

    it('always shows the copy path item regardless of canEdit', async () => {
      renderComponent({ canEdit: false })
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      expect(screen.getByTestId('apiCardMenuCopy-my-api')).toBeInTheDocument()
    })
  })

  describe('Navigation', () => {
    it('pushes to the view route when "view" is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuView-my-api'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/dataset-123/apis/my-api')
    })

    it('pushes to the edit route when "edit" is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuEdit-my-api'))
      expect(mockPush).toHaveBeenCalledWith('/datasets/dataset-123/apis/my-api?mode=edit')
    })
  })

  describe('Delete flow', () => {
    it('opens the delete confirmation modal when delete is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuDelete-my-api'))
      expect(screen.getByTestId('exitWarningModal')).toBeInTheDocument()
    })

    it('closes the modal without calling patchDataset when cancel is clicked', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuDelete-my-api'))
      fireEvent.click(screen.getByTestId('discardButton'))
      expect(screen.queryByTestId('exitWarningModal')).not.toBeInTheDocument()
      expect(mockPatchDataset).not.toHaveBeenCalled()
    })

    it('calls patchDataset with the remaining apis on confirm', async () => {
      const otherApi = makeApi({ id: 'api-2', name: 'Other API', slug: 'other-api', description: 'Other' })
      renderComponent({ existingApis: [defaultApi, otherApi] })
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuDelete-my-api'))
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(mockPatchDataset).toHaveBeenCalledWith(
          expect.objectContaining({
            id: 'dataset-123',
            namedApis: expect.arrayContaining([expect.objectContaining({ slug: 'other-api', name: 'Other API' })]),
          }),
        )
      })
    })

    it('does not include the deleted api in the patchDataset payload', async () => {
      const otherApi = makeApi({ id: 'api-2', name: 'Other API', slug: 'other-api' })
      renderComponent({ existingApis: [defaultApi, otherApi] })
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuDelete-my-api'))
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        const namedApis = mockPatchDataset.mock.calls[0][0].namedApis as { slug: string }[]
        expect(namedApis.some(a => a.slug === 'my-api')).toBe(false)
      })
    })

    it('shows success toast and refreshes the page after successful delete', async () => {
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuDelete-my-api'))
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(toast.success).toHaveBeenCalled()
        expect(mockRefresh).toHaveBeenCalled()
      })
    })

    it('shows an error toast when patchDataset rejects', async () => {
      mockPatchDataset.mockRejectedValueOnce(new Error('network'))
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardMenu-my-api'))
      await userEvent.click(screen.getByTestId('apiCardMenuDelete-my-api'))
      fireEvent.click(screen.getByTestId('confirmButton'))

      await waitFor(() => {
        expect(toast.error).toHaveBeenCalled()
      })
    })
  })
})
