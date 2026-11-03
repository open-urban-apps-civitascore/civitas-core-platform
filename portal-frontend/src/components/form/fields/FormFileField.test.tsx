import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useForm, UseFormReturn } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'

import { FormFileField } from './FormFileField'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

type FileForm = { certificate: string }

const CERTIFICATE = '-----BEGIN CERTIFICATE-----\nMIIBtest1\n-----END CERTIFICATE-----'

const renderFileField = (certificate: string, disabled = false) => {
  let form: UseFormReturn<FileForm> | undefined
  const Wrapper = () => {
    form = useForm<FileForm>({ defaultValues: { certificate } })
    return (
      <Form {...form}>
        <FormFileField form={form} name="certificate" label="Certificate" accept=".pem" disabled={disabled} />
      </Form>
    )
  }
  render(<Wrapper />)
  return { user: userEvent.setup(), getValue: () => form?.getValues('certificate') }
}

describe('FormFileField', () => {
  it('writes the text of the selected file into the form and shows the file name', async () => {
    const { user, getValue } = renderFileField('')

    await user.upload(screen.getByTestId('certificateFileInput'), new File([CERTIFICATE], 'client.pem'))

    await waitFor(() => expect(getValue()).toBe(CERTIFICATE))
    expect(screen.getByLabelText('Certificate')).toHaveValue('client.pem')
    expect(screen.queryByDisplayValue(CERTIFICATE)).not.toBeInTheDocument()
  })

  it('shows an error and keeps the value when the file cannot be read', async () => {
    const { user, getValue } = renderFileField('********')
    const file = new File([CERTIFICATE], 'client.pem')
    vi.spyOn(file, 'text').mockRejectedValue(new DOMException('The file cannot be read.', 'NotReadableError'))

    await user.upload(screen.getByTestId('certificateFileInput'), file)

    expect(await screen.findByText('common.fileField.unreadable')).toBeInTheDocument()
    expect(getValue()).toBe('********')
    expect(screen.getByLabelText('Certificate')).toHaveValue('********')
  })

  it('lets the form move the focus to the field', () => {
    let form: UseFormReturn<FileForm> | undefined
    const Wrapper = () => {
      form = useForm<FileForm>({ defaultValues: { certificate: '' } })
      return (
        <Form {...form}>
          <FormFileField form={form} name="certificate" label="Certificate" />
        </Form>
      )
    }
    render(<Wrapper />)

    act(() => form?.setFocus('certificate'))

    expect(screen.getByLabelText('Certificate')).toHaveFocus()
  })

  it('opens the file selection with the button', async () => {
    const { user } = renderFileField('')
    const fileInput = screen.getByTestId('certificateFileInput')
    const click = vi.spyOn(fileInput, 'click')

    await user.click(screen.getByRole('button', { name: 'select' }))

    expect(click).toHaveBeenCalled()
  })

  it('accepts only the given file types', () => {
    renderFileField('')

    expect(screen.getByTestId('certificateFileInput')).toHaveAttribute('accept', '.pem')
  })

  it('shows a stored value masked', () => {
    renderFileField('********')

    expect(screen.getByLabelText('Certificate')).toHaveValue('********')
  })

  it('shows a value that was not selected in this session masked', () => {
    renderFileField(CERTIFICATE)

    expect(screen.getByLabelText('Certificate')).toHaveValue('********')
  })

  it('shows that no file is selected for an empty value', () => {
    renderFileField('')

    expect(screen.getByLabelText('Certificate')).toHaveValue('noFile')
  })

  it('disables the button in read-only mode', () => {
    renderFileField('********', true)

    expect(screen.getByRole('button', { name: 'select' })).toBeDisabled()
    expect(screen.getByTestId('certificateFileInput')).toBeDisabled()
  })
})
