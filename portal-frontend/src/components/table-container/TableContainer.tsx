import { HTMLAttributes } from 'react'

interface GrindContainerProps extends Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'children'> {
  shouldRespectSearchHeight?: boolean
  shouldRespectTitleHeight?: boolean
}

export const TableContainer = (props: GrindContainerProps) => {
  const { children, shouldRespectSearchHeight = true, shouldRespectTitleHeight = true, className } = props
  const height = `calc(100%${shouldRespectTitleHeight ? ' - var(--title-height)' : ''}${shouldRespectSearchHeight ? ' - var(--search-height)' : ''})`
  return (
    <div className={className} style={{ height: height }}>
      {children}
    </div>
  )
}
