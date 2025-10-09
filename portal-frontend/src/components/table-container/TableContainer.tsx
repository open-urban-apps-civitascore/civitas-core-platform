import { HTMLAttributes } from 'react'

interface GrindContainerProps extends Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'children'> {
  shouldRespectSearchHeight?: boolean
}

export const TableContainer = (props: GrindContainerProps) => {
  const { children, shouldRespectSearchHeight = true, className } = props
  const height = `calc(100%${shouldRespectSearchHeight ? ' - var(--search-height)' : ''})`
  return (
    <div className={className} style={{ height: height }}>
      {children}
    </div>
  )
}
