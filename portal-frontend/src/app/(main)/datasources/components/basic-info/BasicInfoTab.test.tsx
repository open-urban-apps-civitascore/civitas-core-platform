import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { Form } from '@/components/ui/form'
import { DatasourceBaseFormData, DatasourceBaseFormSchema } from '@/types/datasources'

import { BasicInfoTab } from './BasicInfoTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

vi.mock('./TagsMultiSelect', () => ({
  TagsMultiSelect: ({
    selectedTags,
    onTagsChange,
    isDisabled,
  }: {
    selectedTags: string[]
    onTagsChange: (tags: string[]) => void
    isDisabled?: boolean
  }) => (
    <div data-testid="mockedTagsMultiSelect" data-disabled={isDisabled}>
      <span data-testid="selectedTags">{selectedTags.join(',')}</span>
      <button data-testid="addTagButton" onClick={() => onTagsChange([...selectedTags, 'New Tag'])}>
        Add Tag
      </button>
    </div>
  ),
}))

const defaultFormValues: DatasourceBaseFormData = {
  id: 1,
  name: '',
  description: '',
  tags: [],
  status: 'draft',
}

interface WrapperProps {
  isReadOnly?: boolean
  isDraftMode?: boolean
  initialValues?: Partial<DatasourceBaseFormData>
}

const TestWrapper = ({ isReadOnly = false, isDraftMode = false, initialValues = {} }: WrapperProps) => {
  const form = useForm<DatasourceBaseFormData>({
    resolver: zodResolver(DatasourceBaseFormSchema),
    defaultValues: { ...defaultFormValues, ...initialValues },
  })

  return (
    <Form {...form}>
      <BasicInfoTab form={form} isReadOnly={isReadOnly} isDraftMode={isDraftMode} />
    </Form>
  )
}

const setup = (props: WrapperProps = {}) => {
  return render(<TestWrapper {...props} />)
}

describe('BasicInfoTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Basic Rendering', () => {
    test('renders the basicInfoTab container', () => {
      setup()
      expect(screen.getByTestId('basicInfoTab')).toBeInTheDocument()
    })

    test('renders SubHeader with title and subtitle', () => {
      setup()
      expect(screen.getByText('edit.basicInfo.title')).toBeInTheDocument()
      expect(screen.getByText('edit.basicInfo.subtitle')).toBeInTheDocument()
    })

    test('renders name input field with correct placeholder', () => {
      setup()
      expect(screen.getByPlaceholderText('form.namePlaceholder')).toBeInTheDocument()
    })

    test('renders description textarea', () => {
      setup()
      expect(screen.getByTestId('descriptionTextArea')).toBeInTheDocument()
    })

    test('renders tags field container', () => {
      setup()
      expect(screen.getByTestId('tagsField')).toBeInTheDocument()
      expect(screen.getByTestId('mockedTagsMultiSelect')).toBeInTheDocument()
    })

    test('renders name label', () => {
      setup()
      expect(screen.getByText('form.name')).toBeInTheDocument()
    })

    test('renders description label', () => {
      setup()
      expect(screen.getByText('form.description')).toBeInTheDocument()
    })

    test('renders tags label with optional text', () => {
      setup()
      expect(screen.getByText('form.tags')).toBeInTheDocument()
      expect(screen.getByText('form.tagsOptional')).toBeInTheDocument()
    })
  })

  describe('Character Counter', () => {
    test('shows initial character count as 0/150', () => {
      setup()
      expect(screen.getByText('0/150')).toBeInTheDocument()
    })

    test('updates character count when description changes', () => {
      setup()
      const textarea = screen.getByTestId('descriptionTextArea')
      fireEvent.change(textarea, { target: { value: 'Test description' } })
      expect(screen.getByText('16/150')).toBeInTheDocument()
    })

    test('shows correct character count with initial description value', () => {
      setup({ initialValues: { description: 'Initial description text' } })
      expect(screen.getByText('24/150')).toBeInTheDocument()
    })
  })

  describe('Read-Only Mode', () => {
    test('name field is disabled when isReadOnly is true', () => {
      setup({ isReadOnly: true })
      const nameInput = screen.getByPlaceholderText('form.namePlaceholder')
      expect(nameInput).toBeDisabled()
    })

    test('description textarea is disabled when isReadOnly is true', () => {
      setup({ isReadOnly: true })
      const textarea = screen.getByTestId('descriptionTextArea')
      expect(textarea).toBeDisabled()
    })

    test('tags component receives isDisabled true', () => {
      setup({ isReadOnly: true })
      const tagsComponent = screen.getByTestId('mockedTagsMultiSelect')
      expect(tagsComponent).toHaveAttribute('data-disabled', 'true')
    })
  })

  describe('Editable Mode', () => {
    test('name field is enabled when isReadOnly is false', () => {
      setup({ isReadOnly: false })
      const nameInput = screen.getByPlaceholderText('form.namePlaceholder')
      expect(nameInput).not.toBeDisabled()
    })

    test('description textarea is enabled when isReadOnly is false', () => {
      setup({ isReadOnly: false })
      const textarea = screen.getByTestId('descriptionTextArea')
      expect(textarea).not.toBeDisabled()
    })

    test('tags component receives isDisabled false', () => {
      setup({ isReadOnly: false })
      const tagsComponent = screen.getByTestId('mockedTagsMultiSelect')
      expect(tagsComponent).toHaveAttribute('data-disabled', 'false')
    })
  })

  describe('Draft Mode', () => {
    test('description required asterisk is hidden in draft mode', () => {
      setup({ isDraftMode: true })
      const descriptionLabel = screen.getByText('form.description')
      const asterisk = descriptionLabel.parentElement?.querySelector('.text-red-500')
      expect(asterisk).not.toBeInTheDocument()
    })

    test('description required asterisk is visible when not in draft mode', () => {
      setup({ isDraftMode: false })
      const descriptionLabel = screen.getByText('form.description')
      const asterisk = descriptionLabel.parentElement?.querySelector('.text-red-500')
      expect(asterisk).toBeInTheDocument()
    })
  })

  describe('Form Interaction', () => {
    test('can change name field value', () => {
      setup()
      const nameInput = screen.getByPlaceholderText('form.namePlaceholder')
      fireEvent.change(nameInput, { target: { value: 'New Datasource Name' } })
      expect(nameInput).toHaveValue('New Datasource Name')
    })

    test('can change description field value', () => {
      setup()
      const textarea = screen.getByTestId('descriptionTextArea')
      fireEvent.change(textarea, { target: { value: 'New description' } })
      expect(textarea).toHaveValue('New description')
    })

    test('tags change updates selected tags', () => {
      setup({ initialValues: { tags: ['Tag1'] } })
      expect(screen.getByTestId('selectedTags')).toHaveTextContent('Tag1')

      fireEvent.click(screen.getByTestId('addTagButton'))
      expect(screen.getByTestId('selectedTags')).toHaveTextContent('Tag1,New Tag')
    })
  })

  describe('Initial Values', () => {
    test('displays initial name value', () => {
      setup({ initialValues: { name: 'Existing Datasource' } })
      const nameInput = screen.getByPlaceholderText('form.namePlaceholder')
      expect(nameInput).toHaveValue('Existing Datasource')
    })

    test('displays initial description value', () => {
      setup({ initialValues: { description: 'Existing description' } })
      const textarea = screen.getByTestId('descriptionTextArea')
      expect(textarea).toHaveValue('Existing description')
    })

    test('displays initial tags', () => {
      setup({ initialValues: { tags: ['Tag1', 'Tag2'] } })
      expect(screen.getByTestId('selectedTags')).toHaveTextContent('Tag1,Tag2')
    })
  })
})
