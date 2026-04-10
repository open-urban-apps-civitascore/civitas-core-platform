import '@testing-library/jest-dom'

import { vi } from 'vitest'

process.env.NEXT_PUBLIC_JSON_SERVER_HOST = 'http://localhost'
process.env.NEXT_PUBLIC_JSON_SERVER_PORT = '3001'

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    hasUnsavedChanges: false,
    requestNavigation: vi.fn(),
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

vi.mock('@/hooks/use-register-unsaved-changes', () => ({
  useRegisterUnsavedChanges: vi.fn(),
}))
