// BaseInfoForm.test.tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { DatasetFormData } from '@/types/datasets'

import { BaseInfoForm } from './BaseInfoForm'

const CANCEL_BUTTON = 'actions.cancel'
const CONFIRM_BUTTON = 'actions.submit'
const EDIT_BUTTON = 'actions.editBase'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams('page=1'),
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

vi.mock('@/app/services/api/request/apiRequest', () => ({
  apiRequest: vi.fn(),
}))

const mockApiRequest = vi.mocked(apiRequest)

const datasetMock: DatasetFormData = {
  id: '1',
  name: 'Dataset 1',
  description: 'Description',
  dataspace: '1',
  tags: ['tag1'],
}

const emptyDatasetMock: DatasetFormData = {
  id: '',
  name: '',
  description: '',
  dataspace: '',
  tags: [],
}

const dataspaces = [
  { value: '1', label: 'Dataspace 1' },
  { value: '2', label: 'Dataspace 2' },
]

const setup = (isEditMode = true) => {
  const client = new QueryClient()
  return render(
    <QueryClientProvider client={client}>
      <BaseInfoForm
        dataset={isEditMode ? datasetMock : emptyDatasetMock}
        dataspaces={dataspaces}
        isEditMode={isEditMode}
      />
    </QueryClientProvider>,
  )
}

describe('BaseInfoForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders not in read-only mode when creating', () => {
    setup(false)
    expect(screen.getByTestId('datasetBaseInfoForm'))
    expect(screen.queryByRole('button', { name: EDIT_BUTTON })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: CONFIRM_BUTTON })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: CANCEL_BUTTON })).toBeInTheDocument()
    expect(screen.getByTestId('dataspaceSelectTrigger')).toBeEnabled()
    expect(screen.getByTestId('nameTextField')).toBeEnabled()
    expect(screen.getByTestId('descriptionTextField')).toBeEnabled()
    expect(screen.getByTestId('tagsInput')).toBeEnabled()
  })

  test('renders in read-only mode initially when editing', () => {
    setup()
    expect(screen.getByTestId('datasetBaseInfoForm'))
    expect(screen.getByRole('button', { name: EDIT_BUTTON })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: CONFIRM_BUTTON })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: CANCEL_BUTTON })).not.toBeInTheDocument()
    expect(screen.getByTestId('dataspaceSelectTrigger')).toBeDisabled()
    expect(screen.getByTestId('nameTextField')).toBeDisabled()
    expect(screen.getByTestId('descriptionTextField')).toBeDisabled()
    expect(screen.getByTestId('tagsField')).toBeInTheDocument()
    expect(screen.queryByTestId('tagsInput')).not.toBeInTheDocument()
  })

  test('clicking edit button disables read-only', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: EDIT_BUTTON }))
    expect(screen.queryByRole('button', { name: EDIT_BUTTON })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: CONFIRM_BUTTON })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: CANCEL_BUTTON })).toBeInTheDocument()
    expect(screen.getByTestId('dataspaceSelectTrigger')).toBeEnabled()
    expect(screen.getByTestId('nameTextField')).toBeEnabled()
    expect(screen.getByTestId('descriptionTextField')).toBeEnabled()
    expect(screen.getByTestId('tagsInput')).toBeEnabled()
  })

  test('tags input adds a tag', async () => {
    setup(false)

    const input = screen.getByTestId('tagsInput')
    fireEvent.change(screen.getByTestId('tagsInput'), { target: { value: 'newTag' } })
    fireEvent.keyUp(input, { key: 'Enter' })
    expect(screen.getByText('newTag')).toBeInTheDocument()
  })

  test('tags input does not add the same tag twice', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: EDIT_BUTTON }))

    expect(screen.queryByText('tag1')).toBeInTheDocument()

    const input = screen.getByTestId('tagsInput')
    fireEvent.change(screen.getByTestId('tagsInput'), { target: { value: 'tag1' } })
    fireEvent.keyUp(input, { key: 'Enter' })

    expect(screen.getAllByText('tag1')).toHaveLength(1)
  })

  test('tag gets removed when clicking X on a tag', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: EDIT_BUTTON }))

    expect(screen.getByText('tag1')).toBeInTheDocument()
    const removeBtn = screen.getAllByRole('button').find(button => button.innerHTML.includes('x')) as HTMLElement

    fireEvent.click(removeBtn)
    expect(screen.queryByText('tag1')).not.toBeInTheDocument()
  })

  test('save button gets enabled after changing a form value', () => {
    setup(false)
    expect(screen.getByRole('button', { name: CONFIRM_BUTTON })).toBeDisabled()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'new Name' } })
    expect(screen.getByRole('button', { name: CONFIRM_BUTTON })).toBeEnabled()
  })

  test('form validation shows an error for name field when no name provided', async () => {
    setup(false)
    fireEvent.change(screen.getByTestId('descriptionTextField'), { target: { value: 'new Description' } })
    fireEvent.click(screen.getByRole('button', { name: CONFIRM_BUTTON }))
    await waitFor(() => {
      expect(screen.getByTestId('nameTextField')).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByText('common.errors.atLeast2')).toBeInTheDocument()
    })
  })

  test('form field error disappears when correct value provided', async () => {
    setup(false)
    fireEvent.change(screen.getByTestId('descriptionTextField'), { target: { value: 'new Description' } })
    fireEvent.click(screen.getByRole('button', { name: CONFIRM_BUTTON }))
    await waitFor(() => {
      expect(screen.getByTestId('nameTextField')).toHaveAttribute('aria-invalid', 'true')
    })
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'new Name' } })
    await waitFor(() => {
      expect(screen.getByTestId('nameTextField')).toHaveAttribute('aria-invalid', 'false')
    })
  })

  test('submits createDataset when not edit mode', async () => {
    mockApiRequest.mockResolvedValueOnce({
      data: { id: '1' },
    })
    setup(false)
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Name' } })
    fireEvent.click(screen.getByRole('button', { name: CONFIRM_BUTTON }))

    await waitFor(() => {
      expect(mockApiRequest).toHaveBeenCalledWith(
        expect.objectContaining({
          endpoint: '/datasets',
          method: 'POST',
        }),
      )
    })
  })

  test('submits updateDataset when edit mode', async () => {
    mockApiRequest.mockResolvedValueOnce({
      data: { datasetMock },
    })

    setup()

    fireEvent.click(screen.getByRole('button', { name: EDIT_BUTTON }))
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Name' } })

    fireEvent.click(screen.getByRole('button', { name: CONFIRM_BUTTON }))
    // eslint-disable-next-line unused-imports/no-unused-vars
    const { id, ...responseData } = {
      ...datasetMock,
      dataspace: { id: '1', name: 'Dataspace 1' },
      name: 'New Name',
      lastUpdated: expect.any(String),
    }

    await waitFor(() => {
      expect(mockApiRequest).toHaveBeenCalledWith(
        expect.objectContaining({
          endpoint: '/datasets/1',
          method: 'PATCH',
          data: responseData,
        }),
      )
    })
  })
})
