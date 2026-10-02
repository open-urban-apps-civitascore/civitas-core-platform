import { act, render, screen } from '@testing-library/react'
import { toast, Toaster } from 'sonner'
import { describe, expect, it, vi } from 'vitest'

import { MARKUP_NAME } from '@/test-support/markup'

import { rootFailureMessage } from './rootFailureMessage'
import type { RootResolutionFailure } from './umlContainment'

const translator = () =>
  vi.fn((key: string, values?: Record<string, string>) => (values ? `${key}:${JSON.stringify(values)}` : key))

describe('rootFailureMessage', () => {
  it('renders the noRoot key without values', () => {
    const t = translator()
    const failure: RootResolutionFailure = { code: 'noRoot' }

    expect(rootFailureMessage(t, failure)).toBe('rootValidation.noRoot')
    expect(t).toHaveBeenCalledWith('rootValidation.noRoot')
  })

  it('renders the ambiguousRoot key with the joined candidate names', () => {
    const t = translator()
    const failure: RootResolutionFailure = { code: 'ambiguousRoot', candidateNames: ['Alpha', 'Beta'] }

    rootFailureMessage(t, failure)

    expect(t).toHaveBeenCalledWith('rootValidation.ambiguousRoot', { names: 'Alpha, Beta' })
  })

  it('renders the misdirected key with both ends of the relation', () => {
    const t = translator()
    const failure: RootResolutionFailure = { code: 'misdirected', name1: 'Gamma', name2: 'Delta' }

    rootFailureMessage(t, failure)

    expect(t).toHaveBeenCalledWith('rootValidation.misdirected', { name1: 'Gamma', name2: 'Delta' })
  })

  it('shows class names with markup characters of an ambiguous root as text in the toast', async () => {
    const { container } = render(<Toaster />)

    act(() => {
      toast.error(rootFailureMessage(translator(), { code: 'ambiguousRoot', candidateNames: [MARKUP_NAME, 'Sensor'] }))
    })

    expect(await screen.findByText(MARKUP_NAME, { exact: false })).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })
})
