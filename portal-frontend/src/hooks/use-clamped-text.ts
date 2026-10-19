import { RefObject, useLayoutEffect, useState } from 'react'

const fits = (element: HTMLElement) => element.scrollHeight <= element.clientHeight

export const useClampedText = (text: string, ref: RefObject<HTMLElement | null>) => {
  const [displayText, setDisplayText] = useState(text)

  useLayoutEffect(() => {
    const element = ref.current
    if (!element) return

    element.textContent = text
    if (fits(element)) {
      setDisplayText(text)
      return
    }

    let low = 0
    let high = text.length
    while (low < high) {
      const mid = Math.ceil((low + high) / 2)
      element.textContent = `${text.slice(0, mid)}…`
      if (fits(element)) {
        low = mid
      } else {
        high = mid - 1
      }
    }
    setDisplayText(`${text.slice(0, low)}…`)
  }, [text, ref])

  return displayText
}
