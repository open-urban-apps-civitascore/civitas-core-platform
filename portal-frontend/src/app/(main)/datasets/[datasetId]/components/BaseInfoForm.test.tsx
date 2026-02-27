// BaseInfoForm.test.tsx
import { render, screen } from '@testing-library/react'
import { FormProvider, useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { DatasetFormDraft } from '@/types/datasets'

import { BaseInfoForm } from './BaseInfoForm'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const TestWrapper = ({ isReadOnly }: { isReadOnly: boolean }) => {
  const form = useForm<DatasetFormDraft>({
    defaultValues: {
      id: '1',
      name: 'Dataset 1',
      description: 'Description',
      openDataAccess: false,
    },
  })

  return (
    <FormProvider {...form}>
      <BaseInfoForm form={form} isReadOnly={isReadOnly} isLoading={false} />
    </FormProvider>
  )
}

const setup = (isReadOnly = true) => {
  return render(<TestWrapper isReadOnly={isReadOnly} />)
}

describe('BaseInfoForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders form with correct title', () => {
    setup()
    expect(screen.getByText('overview.info.title')).toBeInTheDocument()
  })

  test('renders form in edit mode when not read-only', () => {
    setup(false)
    expect(screen.getByTestId('nameTextField')).toBeEnabled()
    expect(screen.getByTestId('descriptionTextArea')).toBeEnabled()
  })

  test('renders form in read-only mode', () => {
    setup(true)
    expect(screen.getByTestId('nameTextField')).toBeDisabled()
    expect(screen.getByTestId('descriptionTextArea')).toBeDisabled()
  })

  test('renders name field with correct value', () => {
    setup(true)
    expect(screen.getByTestId('nameTextField')).toHaveValue('Dataset 1')
  })

  test('renders description field with correct value', () => {
    setup(true)
    expect(screen.getByTestId('descriptionTextArea')).toHaveValue('Description')
  })

  test('shows description hint', () => {
    setup(true)
    expect(screen.getByText('overview.info.descriptionHint')).toBeInTheDocument()
  })
})
