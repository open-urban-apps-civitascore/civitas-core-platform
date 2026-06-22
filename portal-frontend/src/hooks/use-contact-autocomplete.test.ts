import { act, renderHook } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetUsers } from '@/app/services/api/users/clientRequests'

import { useContactAutocomplete } from './use-contact-autocomplete'

vi.mock('@/app/services/api/users/clientRequests')

const mockUsers = [
  { id: 'u1', firstName: 'Anna', lastName: 'Müller', email: 'anna@example.com' },
  { id: 'u2', firstName: 'Ben', lastName: 'Schmidt', email: 'ben@example.com' },
]

const mockGetUsers = (users = mockUsers) => {
  vi.mocked(useGetUsers).mockImplementation(
    ({ isEnabled } = {}) =>
      ({
        data: isEnabled ? { data: users } : undefined,
        isLoading: false,
      }) as ReturnType<typeof useGetUsers>,
  )
}

type TestForm = { contactId: string | null }

const renderContactHook = (initialContact?: { id: string; name: string } | null) => {
  return renderHook(
    ({ contact }: { contact?: { id: string; name: string } | null }) => {
      const form = useForm<TestForm>({ defaultValues: { contactId: initialContact?.id ?? null } })
      const hook = useContactAutocomplete({
        form,
        fieldName: 'contactId',
        initialContact: contact,
        emptyValue: null,
      })
      return { form, ...hook }
    },
    { initialProps: { contact: initialContact } },
  )
}

describe('useContactAutocomplete', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockGetUsers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  describe('initialization', () => {
    it('initializes contactInput with initialContact name', () => {
      const { result } = renderContactHook({ id: 'u1', name: 'Anna Müller' })
      expect(result.current.contactInput).toBe('Anna Müller')
    })

    it('initializes contactInput as empty string when no initialContact', () => {
      const { result } = renderContactHook(null)
      expect(result.current.contactInput).toBe('')
    })

    it('initializes isContactListOpen as false', () => {
      const { result } = renderContactHook()
      expect(result.current.isContactListOpen).toBe(false)
    })
  })

  describe('handleContactInputChange', () => {
    it('updates contactInput', () => {
      const { result } = renderContactHook()
      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      expect(result.current.contactInput).toBe('Ann')
    })

    it('opens the contact list', () => {
      const { result } = renderContactHook()
      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      expect(result.current.isContactListOpen).toBe(true)
    })
  })

  describe('contact search and selection', () => {
    it('populates contacts when API returns results after debounce', async () => {
      const { result } = renderContactHook()

      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      await act(async () => {
        vi.advanceTimersByTime(300)
      })

      expect(result.current.contacts).toEqual([
        { id: 'u1', displayName: 'Anna Müller', email: 'anna@example.com' },
        { id: 'u2', displayName: 'Ben Schmidt', email: 'ben@example.com' },
      ])
    })

    it('sets form value and updates contactInput on contact selection', () => {
      const { result } = renderContactHook()

      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      act(() => {
        vi.advanceTimersByTime(300)
      })
      act(() => {
        result.current.handleSelectContact('u1')
      })

      expect(result.current.contactInput).toBe('Anna Müller')
      expect(result.current.form.getValues('contactId')).toBe('u1')
      expect(result.current.form.formState.isDirty).toBe(true)
    })

    it('clears contacts and resets form value when input drops below MIN_LENGTH after user interaction', async () => {
      const { result } = renderContactHook()

      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      await act(async () => {
        vi.advanceTimersByTime(300)
      })

      act(() => {
        result.current.handleContactInputChange('An')
      })
      await act(async () => {
        vi.advanceTimersByTime(300)
      })

      expect(result.current.contacts).toEqual([])
      expect(result.current.form.getValues('contactId')).toBeNull()
    })

    it('does not set form value dirty whenuser has not interacted', async () => {
      const { result } = renderContactHook({ id: 'u1', name: 'Anna Müller' })

      await act(async () => {
        vi.advanceTimersByTime(300)
      })

      expect(result.current.form.formState.isDirty).toBe(false)
    })
  })

  describe('handleAutocompleteBlur', () => {
    it('clears form value and contactInput when field is empty on blur', () => {
      const { result } = renderContactHook({ id: 'u1', name: 'Anna Müller' })

      act(() => {
        result.current.handleContactInputChange('')
      })
      act(() => {
        result.current.handleAutocompleteBlur()
      })

      expect(result.current.contactInput).toBe('')
      expect(result.current.form.getValues('contactId')).toBeNull()
      expect(result.current.form.formState.isDirty).toBe(true)
    })

    it('restores selectedContact displayName when user typed but did not select on blur', () => {
      const { result } = renderContactHook()

      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      act(() => {
        vi.advanceTimersByTime(300)
      })
      act(() => {
        result.current.handleSelectContact('u1')
      })

      act(() => {
        result.current.handleContactInputChange('something else')
      })
      act(() => {
        result.current.handleAutocompleteBlur()
      })

      expect(result.current.contactInput).toBe('Anna Müller')
    })
  })

  describe('reset behaviour', () => {
    it('restores contactInput to initialContact name after form.reset when value was cleared', async () => {
      const { result } = renderContactHook({ id: 'u1', name: 'Anna Müller' })

      act(() => {
        result.current.handleContactInputChange('')
      })
      act(() => {
        result.current.handleAutocompleteBlur()
      })
      expect(result.current.contactInput).toBe('')

      act(() => {
        result.current.form.reset({ contactId: 'u1' })
      })

      expect(result.current.contactInput).toBe('Anna Müller')
    })

    it('restores contactInput after form.reset even when initialContact id did not change', async () => {
      const { result } = renderContactHook({ id: 'u1', name: 'Anna Müller' })

      act(() => {
        result.current.handleContactInputChange('Ann')
      })
      act(() => {
        vi.advanceTimersByTime(300)
      })
      act(() => {
        result.current.handleSelectContact('u2')
      })
      expect(result.current.contactInput).toBe('Ben Schmidt')

      act(() => {
        result.current.form.reset({ contactId: 'u1' })
      })

      expect(result.current.contactInput).toBe('Anna Müller')
    })

    it('updates contactInput when initialContact id changes (different entity loaded)', () => {
      const { result, rerender } = renderContactHook({ id: 'u1', name: 'Anna Müller' })
      expect(result.current.contactInput).toBe('Anna Müller')

      rerender({ contact: { id: 'u2', name: 'Ben Schmidt' } })

      expect(result.current.contactInput).toBe('Ben Schmidt')
    })

    it('clears contactInput when initialContact changes to null', () => {
      const { result, rerender } = renderContactHook({ id: 'u1', name: 'Anna Müller' })

      rerender({ contact: null })

      expect(result.current.contactInput).toBe('')
    })
  })
})
