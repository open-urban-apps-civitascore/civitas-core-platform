// DynamicFormField.spec.tsx
import { zodResolver } from '@hookform/resolvers/zod'
import { render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { describe, expect, it } from 'vitest'
import z from 'zod'

import { Form } from '@/components/ui/form'

import { DynamicFormField, type DynamicFormFieldProps } from './DynamicFormField'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

export type TestForm = {
  name: string
  description: string
  isActive: boolean
  category: string
}

export const TestFormSchema = z.object({
  name: z.string().min(1),
  description: z.string().min(1),
  isActive: z.boolean(),
  category: z.string().min(1),
})

const defaultFormValues: TestForm = {
  name: 'Test Name',
  description: 'Test Description',
  isActive: false,
  category: 'a',
}

type RenderWithFormProps = Omit<DynamicFormFieldProps<TestForm>, 'form'>

const renderWithForm = (props: RenderWithFormProps) => {
  const Wrapper = () => {
    const form = useForm<TestForm>({
      resolver: zodResolver(TestFormSchema),
      defaultValues: { ...defaultFormValues },
    })

    return (
      <Form {...form}>
        <DynamicFormField {...props} form={form} />
      </Form>
    )
  }

  return render(<Wrapper />)
}
describe('DynamicFormField (integration)', () => {
  it('renders input for type=input', () => {
    renderWithForm({
      type: 'input',
      name: 'name',
      label: 'Input',
      placeholder: 'Max Mustermann',
      shouldShowErrors: false,
    })
    expect(screen.getByLabelText('Input')).toBeInTheDocument()
  })

  it('renders textarea for type=textArea', () => {
    renderWithForm({
      type: 'textArea',
      name: 'description',
      label: 'Textarea',
      placeholder: 'text',
      shouldShowErrors: false,
    })

    expect(screen.getByLabelText('Textarea')).toBeInTheDocument()
  })

  it('renders checkbox for type=checkbox', () => {
    renderWithForm({
      type: 'checkbox',
      name: 'isActive',
      label: 'Checkbox',
      placeholder: 'text',
      shouldShowErrors: false,
    })

    expect(screen.getByRole('checkbox')).toBeInTheDocument()
  })

  it('renders select for type=select', () => {
    renderWithForm({
      type: 'select',
      name: 'category',
      label: 'Select',
      placeholder: 'select',
      shouldShowErrors: false,
      options: [
        { label: 'A', value: 'a' },
        { label: 'B', value: 'b' },
      ],
    })

    expect(screen.getByLabelText('Select')).toBeInTheDocument()
  })

  describe('Disabled state', () => {
    it('renders disabled input for type=input', () => {
      renderWithForm({
        type: 'input',
        name: 'name',
        label: 'Input',
        placeholder: 'Max Mustermann',
        shouldShowErrors: false,
        disabled: true,
      })
      expect(screen.getByLabelText('Input')).toBeDisabled()
    })

    it('renders disabled textarea for type=textArea', () => {
      renderWithForm({
        type: 'textArea',
        name: 'description',
        label: 'Textarea',
        placeholder: 'text',
        shouldShowErrors: false,
        disabled: true,
      })
      expect(screen.getByLabelText('Textarea')).toBeDisabled()
    })

    it('renders disabled checkbox for type=checkbox', () => {
      renderWithForm({
        type: 'checkbox',
        name: 'isActive',
        label: 'Checkbox',
        placeholder: 'text',
        shouldShowErrors: false,
        disabled: true,
      })
      expect(screen.getByRole('checkbox')).toBeDisabled()
    })

    it('renders disabled select for type=select', () => {
      renderWithForm({
        type: 'select',
        name: 'category',
        label: 'Select',
        placeholder: 'select',
        shouldShowErrors: false,
        disabled: true,
        options: [
          { label: 'A', value: 'a' },
          { label: 'B', value: 'b' },
        ],
      })
      expect(screen.getByTestId('categorySelectTrigger')).toBeDisabled()
    })
  })
})
