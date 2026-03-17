import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { PermissionsList } from './PermissionsList'

vi.mock('next-intl', () => ({
  useTranslations: () => {
    const translations: Record<string, string> = {
      'permissions.values.DATASET': 'Dataset',
      'permissions.values.USER': 'User',
      'permissions.actions.read': 'Read',
      'permissions.actions.create': 'Create',
      'permissions.systemPermissions.entry': '{action} {permission}',
      'common.noResults': 'No results found.',
    }
    const t = (key: string, params?: Record<string, string>) => {
      const value = translations[key] ?? key
      if (params) return Object.entries(params).reduce((acc, [k, v]) => acc.replace(`{${k}}`, v), value)
      return value
    }
    t.has = (key: string) => key in translations
    return t
  },
}))

describe('PermissionsList', () => {
  test('renders header text', () => {
    render(<PermissionsList header="Test Header" items={[]} />)

    expect(screen.getByText('Test Header')).toBeInTheDocument()
  })

  test('renders translated items', () => {
    render(<PermissionsList header="Permissions" items={['DATASET_READ', 'USER_CREATE']} />)

    expect(screen.getByText('Read Dataset')).toBeInTheDocument()
    expect(screen.getByText('Create User')).toBeInTheDocument()
  })

  test('falls back to raw item when permission translation is missing', () => {
    render(<PermissionsList header="Permissions" items={['UNKNOWN_READ']} />)

    expect(screen.getByText('UNKNOWN_READ')).toBeInTheDocument()
  })

  test('falls back to raw item when action translation is missing', () => {
    render(<PermissionsList header="Permissions" items={['DATASET_PUBLISH']} />)

    expect(screen.getByText('DATASET_PUBLISH')).toBeInTheDocument()
  })

  test('falls back to raw item when there is no underscore', () => {
    render(<PermissionsList header="Permissions" items={['NOUNDERSCORE']} />)

    expect(screen.getByText('NOUNDERSCORE')).toBeInTheDocument()
  })

  test('renders no results message when items array is empty', () => {
    render(<PermissionsList header="Empty" items={[]} />)

    expect(screen.getByText('No results found.')).toBeInTheDocument()
  })

  test('renders loading skeleton when isLoading is true', () => {
    render(<PermissionsList header="Loading" items={[]} isLoading={true} />)

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
