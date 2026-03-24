import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { signOut, update, getMockSession, setMockSession } = vi.hoisted(() => {
  let mockSession: object | null = null
  return {
    signOut: vi.fn(),
    update: vi.fn(),
    getMockSession: () => mockSession,
    setMockSession: (s: object | null) => {
      mockSession = s
    },
  }
})

vi.mock('next-auth/react', () => ({
  signOut,
  useSession: () => ({ data: getMockSession(), update }),
}))

import { useActivityRefresh } from './use-activity-refresh'

const ACTIVITY_DEBOUNCE_MS = 2 * 60 * 1000 // 2 minutes
const INACTIVITY_TIMEOUT_MS = 55 * 60 * 1000 // 55 minutes (default)

describe('useActivityRefresh', () => {
  let addEventListenerSpy: ReturnType<typeof vi.spyOn>
  let removeEventListenerSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: false })
    vi.setSystemTime(0)

    signOut.mockClear()
    update.mockClear()
    setMockSession(null)

    addEventListenerSpy = vi.spyOn(document, 'addEventListener')
    removeEventListenerSpy = vi.spyOn(document, 'removeEventListener')
  })

  afterEach(() => {
    vi.useRealTimers()
    addEventListenerSpy.mockRestore()
    removeEventListenerSpy.mockRestore()
  })

  it('does not register event listeners when there is no session', () => {
    setMockSession(null)
    renderHook(() => useActivityRefresh())

    const activityEvents = ['mousedown', 'keydown', 'scroll', 'touchstart']
    const registeredEvents = addEventListenerSpy.mock.calls.map(([event]) => event)
    const hasActivityListener = activityEvents.some(e => registeredEvents.includes(e))

    expect(hasActivityListener).toBe(false)
  })

  it('calls signOut after INACTIVITY_TIMEOUT_MS of inactivity', () => {
    setMockSession({ user: { name: 'test' } })
    renderHook(() => useActivityRefresh())

    expect(signOut).not.toHaveBeenCalled()

    vi.advanceTimersByTime(INACTIVITY_TIMEOUT_MS - 1)
    expect(signOut).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(signOut).toHaveBeenCalledWith({ redirectTo: '/login' })
  })

  it.each(['mousedown', 'keydown', 'scroll', 'touchstart'])('resets the inactivity timer on "%s" event', eventName => {
    setMockSession({ user: { name: 'test' } })
    renderHook(() => useActivityRefresh())

    // Advance past the debounce window so the event actually resets the timer
    vi.advanceTimersByTime(ACTIVITY_DEBOUNCE_MS + 1)
    vi.setSystemTime(ACTIVITY_DEBOUNCE_MS + 1)

    document.dispatchEvent(new Event(eventName))

    // After the event, the timer should have been reset.
    // Advance to just before a full timeout from the event — should NOT sign out.
    vi.advanceTimersByTime(INACTIVITY_TIMEOUT_MS - 1)
    expect(signOut).not.toHaveBeenCalled()

    // One more ms completes the new timeout — should sign out now.
    vi.advanceTimersByTime(1)
    expect(signOut).toHaveBeenCalledTimes(1)
  })

  it('does not call update function for events within the debounce window', () => {
    setMockSession({ user: { name: 'test' } })
    renderHook(() => useActivityRefresh())

    // Fire events immediately
    document.dispatchEvent(new Event('mousedown'))
    document.dispatchEvent(new Event('keydown'))
    document.dispatchEvent(new Event('scroll'))

    expect(update).not.toHaveBeenCalled()
  })

  it('calls update function and resets the timer when an event occurs beyond the debounce window', () => {
    setMockSession({ user: { name: 'test' } })
    renderHook(() => useActivityRefresh())

    // Advance time past the debounce window
    const timeAfterDebounce = ACTIVITY_DEBOUNCE_MS + 1
    vi.advanceTimersByTime(timeAfterDebounce)
    vi.setSystemTime(timeAfterDebounce)

    document.dispatchEvent(new Event('mousedown'))

    expect(update).toHaveBeenCalledTimes(1)

    // A second event immediately after should be debounced again
    document.dispatchEvent(new Event('keydown'))
    expect(update).toHaveBeenCalledTimes(1)

    // Advance past another debounce window — next event should trigger update again
    vi.advanceTimersByTime(ACTIVITY_DEBOUNCE_MS + 1)
    vi.setSystemTime(timeAfterDebounce + ACTIVITY_DEBOUNCE_MS + 1)

    document.dispatchEvent(new Event('scroll'))
    expect(update).toHaveBeenCalledTimes(2)
  })

  it('removes event listeners and clears the timeout on unmount', () => {
    setMockSession({ user: { name: 'test' } })
    const { unmount } = renderHook(() => useActivityRefresh())

    const activityEvents = ['mousedown', 'keydown', 'scroll', 'touchstart']

    // Verify listeners were registered
    const registeredEvents = addEventListenerSpy.mock.calls.map(([event]) => event)
    activityEvents.forEach(event => {
      expect(registeredEvents).toContain(event)
    })

    unmount()

    // Verify listeners were removed
    const removedEvents = removeEventListenerSpy.mock.calls.map(([event]) => event)
    activityEvents.forEach(event => {
      expect(removedEvents).toContain(event)
    })

    // The timeout should have been cleared — advancing time must NOT trigger signOut
    vi.advanceTimersByTime(INACTIVITY_TIMEOUT_MS + 1)
    expect(signOut).not.toHaveBeenCalled()
  })
})
