import { zodResolver } from '@hookform/resolvers/zod'
import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import { type JSX, ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { roleSchema } from '@/types/roles'

import { BaseInfoTab } from './BaseInfoTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const wrapper = ({ children }: { children: ReactNode }): JSX.Element => <div>{children}</div>

const { result } = renderHook(
  () =>
    useForm({
      resolver: zodResolver(roleSchema),
      defaultValues: {
        name: '',
        description: '',
        readonly: false,
      },
    }),
  { wrapper },
)

describe('BaseInfoTab Component Custom Roles', () => {
  const mockDeleteRole = vi.fn()

  const defaultProps = {
    form: result.current,
    isDefaultRole: false,
    isReadOnly: true,
    deleteRole: mockDeleteRole,
  }

  it('renders the component in read only mode', () => {
    render(<BaseInfoTab {...defaultProps} />)

    expect(screen.getByText('heading')).toBeDefined()

    const nameInput = screen.getByRole('textbox', { name: 'form.inputs.name' })
    const descriptionInput = screen.getByRole('textbox', { name: 'form.inputs.description' })
    const roleOriginInput = screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })

    expect(nameInput).toBeDefined()
    expect(descriptionInput).toBeDefined()
    expect(roleOriginInput).toBeDefined()

    expect(nameInput).toBeDisabled()
    expect(descriptionInput).toBeDisabled()
    expect(roleOriginInput).toBeDisabled()
  })

  it('renders fields as editable when isReadOnly is false', () => {
    render(<BaseInfoTab {...defaultProps} isReadOnly={false} />)

    const nameInput = screen.getByRole('textbox', { name: 'form.inputs.name *' })
    const descriptionInput = screen.getByRole('textbox', { name: 'form.inputs.description' })

    expect(nameInput).not.toBeDisabled()
    expect(descriptionInput).not.toBeDisabled()

    act(() => {
      fireEvent.change(nameInput, { target: { value: 'New Role Name' } })
      fireEvent.change(descriptionInput, { target: { value: 'New Role Description' } })
    })

    expect((nameInput as HTMLInputElement).value).toBe('New Role Name')
    expect((descriptionInput as HTMLInputElement).value).toBe('New Role Description')

    expect(screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })).toBeDisabled()
  })

  it('shows delete section when not read only', () => {
    render(<BaseInfoTab {...defaultProps} isReadOnly={false} />)

    const deleteButton = screen.getByRole('button', { name: 'securityArea.deleteButton' })
    fireEvent.click(deleteButton)

    expect(mockDeleteRole).toHaveBeenCalled()
  })

  it('hides delete section when read only', () => {
    render(<BaseInfoTab {...defaultProps} isReadOnly={true} />)

    const deleteButton = screen.queryByRole('button', { name: 'securityArea.deleteButton' })
    expect(deleteButton).toBeNull()
  })
})

describe('BaseInfoTab Component Default Roles', () => {
  const mockDeleteRole = vi.fn()

  const defaultProps = {
    form: result.current,
    isDefaultRole: true,
    isReadOnly: true,
    deleteRole: mockDeleteRole,
  }

  it('renders the component in read only mode', () => {
    render(<BaseInfoTab {...defaultProps} />)

    expect(screen.getByText('heading')).toBeDefined()

    const nameInput = screen.getByRole('textbox', { name: 'form.inputs.name' })
    const descriptionInput = screen.getByRole('textbox', { name: 'form.inputs.description' })
    const roleOriginInput = screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })

    expect(nameInput).toBeDefined()
    expect(descriptionInput).toBeDefined()
    expect(roleOriginInput).toBeDefined()

    expect(nameInput).toBeDisabled()
    expect(descriptionInput).toBeDisabled()
    expect(roleOriginInput).toBeDisabled()

    const deleteButton = screen.queryByRole('button', { name: 'securityArea.deleteButton' })
    expect(deleteButton).toBeNull()
  })
})
