import { HTMLAttributes } from 'react'

interface GrindContainerProps extends Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'children'> {
  shouldRespectSearchHeight?: boolean
  shouldRespectSegmentedControlBar?: boolean
}

export const TableContainer = (props: GrindContainerProps) => {
  const { children, shouldRespectSearchHeight = true, shouldRespectSegmentedControlBar = false, className } = props
  const searchBarHeight = shouldRespectSearchHeight ? ' - var(--search-height)' : ''
  const segmentedControlBarHeight = shouldRespectSegmentedControlBar
    ? ' - var(--segmented-control-bar-height) - 16px'
    : ''
  const height = `calc(100%${searchBarHeight}${segmentedControlBarHeight})`
  return (
    <div className={className} style={{ maxHeight: height }}>
      {children}
    </div>
  )
}
