import { RefObject, useEffect, useState } from 'react'

export const useIsTruncated = (ref: RefObject<HTMLElement | null>) => {
  const [isTruncated, setIsTruncated] = useState(false)

  useEffect(() => {
    const element = ref.current
    if (!element) return

    const observer = new ResizeObserver(() => {
      setIsTruncated(element.scrollWidth > element.clientWidth)
    })

    observer.observe(element)
    return () => observer.disconnect()
  }, [ref])

  return isTruncated
}
