import { ReactNode } from 'react'

interface DatasetLayoutProps {
  children: ReactNode
}

const DatasetLayout = ({ children }: DatasetLayoutProps) => {
  return <div>{children}</div>
}

export default DatasetLayout
