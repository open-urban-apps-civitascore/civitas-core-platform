// BaseInfoForm.test.tsx
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import React from 'react'
import { vi } from 'vitest'

import messages from '@/messages/de.json'
import { DatasetFormData } from '@/types/datasets'

import { BaseInfoForm } from './BaseInfoForm'

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

const mockCreateDataset = vi.fn().mockResolvedValue({ id: '123' })
const mockUpdateDataset = vi.fn().mockResolvedValue({})

vi.mock('../../actions', () => ({
  createDataset: (data: DatasetFormData) => mockCreateDataset(data),
  updateDataset: (data: DatasetFormData) => mockUpdateDataset(data),
}))

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
  const setIsLoading = vi.fn()
  return render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <BaseInfoForm
        dataset={isEditMode ? datasetMock : emptyDatasetMock}
        dataspaces={dataspaces}
        isEditMode={isEditMode}
        setIsLoading={setIsLoading}
      />
    </NextIntlClientProvider>,
  )
}

describe('BaseInfoForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders not in read-only mode when creating', () => {
    setup(false)
    expect(screen.getByTestId('datasetBaseInfoForm'))
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Speichern' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Abbrechen' })).toBeInTheDocument()
    expect(screen.getByTestId('dataspaceSelectTrigger')).toBeEnabled()
    expect(screen.getByTestId('nameTextField')).toBeEnabled()
    expect(screen.getByTestId('descriptionTextField')).toBeEnabled()
    expect(screen.getByTestId('tagsInput')).toBeEnabled()
  })

  test('renders in read-only mode initially when editing', () => {
    setup()
    expect(screen.getByTestId('datasetBaseInfoForm'))
    expect(screen.getByRole('button', { name: 'Bearbeiten' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Speichern' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Abbrechen' })).not.toBeInTheDocument()
    expect(screen.getByTestId('dataspaceSelectTrigger')).toBeDisabled()
    expect(screen.getByTestId('nameTextField')).toBeDisabled()
    expect(screen.getByTestId('descriptionTextField')).toBeDisabled()
    expect(screen.getByTestId('tagsField')).toBeInTheDocument()
    expect(screen.queryByTestId('tagsInput')).not.toBeInTheDocument()
  })

  test('clicking edit button disables read-only', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Abbrechen' })).toBeInTheDocument()
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
    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.queryByText('tag1')).toBeInTheDocument()

    const input = screen.getByTestId('tagsInput')
    fireEvent.change(screen.getByTestId('tagsInput'), { target: { value: 'tag1' } })
    fireEvent.keyUp(input, { key: 'Enter' })

    expect(screen.getAllByText('tag1')).toHaveLength(1)
  })

  test('tag gets removed when clicking X on a tag', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))

    expect(screen.getByText('tag1')).toBeInTheDocument()
    const removeBtn = screen.getAllByRole('button').find(button => button.innerHTML.includes('x')) as HTMLElement

    fireEvent.click(removeBtn)
    expect(screen.queryByText('tag1')).not.toBeInTheDocument()
  })

  test('save button gets enabled after changing a form value', () => {
    setup(false)
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeDisabled()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'new Name' } })
    expect(screen.getByRole('button', { name: 'Speichern' })).toBeEnabled()
  })

  test('form validation shows an error for name field when no name provided', async () => {
    setup(false)
    fireEvent.change(screen.getByTestId('descriptionTextField'), { target: { value: 'new Description' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => {
      expect(screen.getByTestId('nameTextField')).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByText('Die Eingabe muss mindestens 2 Zeichen lang sein.')).toBeInTheDocument()
    })
  })

  test('form field error disappears when correct value provided', async () => {
    setup(false)
    fireEvent.change(screen.getByTestId('descriptionTextField'), { target: { value: 'new Description' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() => {
      expect(screen.getByTestId('nameTextField')).toHaveAttribute('aria-invalid', 'true')
    })
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'new Name' } })
    await waitFor(() => {
      expect(screen.getByTestId('nameTextField')).toHaveAttribute('aria-invalid', 'false')
    })
  })

  test('submits createDataset when not edit mode', async () => {
    setup(false)
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Name' } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => {
      expect(mockCreateDataset).toHaveBeenCalled()
    })
  })

  test('submits updateDataset when edit mode', async () => {
    setup()

    fireEvent.click(screen.getByRole('button', { name: 'Bearbeiten' }))
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Name' } })

    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => {
      expect(mockUpdateDataset).toHaveBeenCalled()
    })
  })
})
