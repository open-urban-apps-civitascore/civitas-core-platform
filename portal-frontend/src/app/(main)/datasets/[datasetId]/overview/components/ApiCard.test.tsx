import { act, createEvent, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { NamedApi } from '@/types/namedApis'

import { ApiCard } from './ApiCard'

const mockPatchDataset = vi.fn().mockResolvedValue(undefined)
const mockPush = vi.fn()
const mockRefresh = vi.fn()
const mockRequestNavigation = vi.fn()

let mockHasUnsavedChanges = false

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: mockHasUnsavedChanges,
    requestNavigation: mockRequestNavigation,
    requestBack: vi.fn(),
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

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
  canView: true,
  isOpenDataAccess: false,
}

const renderComponent = (props: Partial<typeof defaultProps> = {}) => render(<ApiCard {...defaultProps} {...props} />)

describe('ApiCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockHasUnsavedChanges = false
  })

  const openMenu = () => userEvent.click(screen.getByTestId('apiCardMenu-my-api'))

  describe('Rendering', () => {
    it('displays the api name and description', () => {
      renderComponent()
      expect(screen.getByText('My API')).toBeInTheDocument()
      expect(screen.getByText('A test API')).toBeInTheDocument()
    })

    it('renders nothing when description is absent', () => {
      renderComponent({ api: makeApi({ description: undefined }) })
      expect(screen.queryByText('A test API')).not.toBeInTheDocument()
      expect(screen.queryByText('noDescription')).not.toBeInTheDocument()
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
    it('links the card to the view route', () => {
      renderComponent()
      expect(screen.getByTestId('apiCard-my-api')).toHaveAttribute('href', '/datasets/dataset-123/apis/my-api')
    })

    it('links the "view" menu item to the view route', async () => {
      renderComponent()
      await openMenu()
      expect(screen.getByTestId('apiCardMenuView-my-api')).toHaveAttribute('href', '/datasets/dataset-123/apis/my-api')
    })

    it('links the "edit" menu item to the edit route', async () => {
      renderComponent()
      await openMenu()
      expect(screen.getByTestId('apiCardMenuEdit-my-api')).toHaveAttribute(
        'href',
        '/datasets/dataset-123/apis/my-api?mode=edit',
      )
    })
  })

  describe('Unsaved-changes guard', () => {
    it('navigates directly when there are no unsaved changes', async () => {
      renderComponent()
      await openMenu()
      await userEvent.click(screen.getByTestId('apiCardMenuEdit-my-api'))
      expect(mockRequestNavigation).not.toHaveBeenCalled()
    })

    it('requests navigation instead of following the "edit" link when there are unsaved changes', async () => {
      mockHasUnsavedChanges = true
      renderComponent()
      await openMenu()
      await userEvent.click(screen.getByTestId('apiCardMenuEdit-my-api'))
      expect(mockRequestNavigation).toHaveBeenCalledWith('/datasets/dataset-123/apis/my-api?mode=edit')
    })

    it('requests navigation instead of following the card link when there are unsaved changes', async () => {
      mockHasUnsavedChanges = true
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCard-my-api'))
      expect(mockRequestNavigation).toHaveBeenCalledWith('/datasets/dataset-123/apis/my-api')
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

  describe('Public API URL (/v1 scheme)', () => {
    const mockWriteText = vi.fn()

    beforeEach(() => {
      Object.defineProperty(navigator, 'clipboard', {
        value: { writeText: mockWriteText },
        configurable: true,
      })
    })

    it('displays the API path with the /v1 data-plane scheme', () => {
      renderComponent()
      // The gateway serves named APIs at /v1/datasets/{id}/{slug} (issue #1368).
      expect(screen.getByTestId('apiCard-my-api')).toHaveTextContent('/v1/datasets/dataset-123/my-api')
    })

    it('copies the backend-provided previewUrl verbatim when present', async () => {
      renderComponent({ api: makeApi({ previewUrl: 'https://api.example.com/v1/datasets/dataset-123/my-api' }) })
      await userEvent.click(screen.getByTestId('apiCardCopy-my-api'))
      expect(mockWriteText).toHaveBeenCalledWith('https://api.example.com/v1/datasets/dataset-123/my-api')
    })

    it('falls back to the /v1 path (not the pre-#1368 /datasets scheme) when previewUrl is missing', async () => {
      renderComponent({ api: makeApi({ previewUrl: undefined }) })
      await userEvent.click(screen.getByTestId('apiCardCopy-my-api'))
      expect(mockWriteText).toHaveBeenCalledWith(expect.stringContaining('/v1/datasets/dataset-123/my-api'))
    })
  })

  describe('Copy button (issue #2206)', () => {
    const mockWriteText = vi.fn().mockResolvedValue(undefined)

    beforeEach(() => {
      Object.defineProperty(navigator, 'clipboard', {
        value: { writeText: mockWriteText },
        configurable: true,
      })
    })

    // The button sits inside the card's <a>; without preventDefault the browser follows the
    // anchor and the detail view opens on top of the copy.
    it('prevents the card link default so copying does not open the detail view', async () => {
      renderComponent()
      const copyButton = screen.getByTestId('apiCardCopy-my-api')
      const click = createEvent.click(copyButton, { bubbles: true, cancelable: true })

      await act(async () => {
        fireEvent(copyButton, click)
      })

      expect(click.defaultPrevented).toBe(true)
    })

    it('does not trigger the unsaved-changes navigation guard when copying', async () => {
      mockHasUnsavedChanges = true
      renderComponent()
      await userEvent.click(screen.getByTestId('apiCardCopy-my-api'))
      expect(mockRequestNavigation).not.toHaveBeenCalled()
    })

    it('prevents the card link default when opening the card menu', () => {
      renderComponent()
      const menuButton = screen.getByTestId('apiCardMenu-my-api')
      const click = createEvent.click(menuButton, { bubbles: true, cancelable: true })

      fireEvent(menuButton, click)

      expect(click.defaultPrevented).toBe(true)
    })

    it('shows a success toast after copying', async () => {
      renderComponent()

      await userEvent.click(screen.getByTestId('apiCardCopy-my-api'))

      await waitFor(() => expect(toast.success).toHaveBeenCalled())
    })

    it('stays silent when clipboard access fails', async () => {
      mockWriteText.mockRejectedValueOnce(new Error('insecure context'))
      renderComponent()

      await userEvent.click(screen.getByTestId('apiCardCopy-my-api'))

      await waitFor(() => expect(mockWriteText).toHaveBeenCalled())
      expect(toast.success).not.toHaveBeenCalled()
    })

    it('exposes a translated accessible name', () => {
      renderComponent()
      expect(screen.getByTestId('apiCardCopy-my-api')).toHaveAttribute('aria-label', 'actions.copyPath')
    })
  })
})
