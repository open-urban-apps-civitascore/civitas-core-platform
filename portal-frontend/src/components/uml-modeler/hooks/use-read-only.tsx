import { createContext, ReactNode, useContext } from 'react'

interface ReadOnlyContextType {
  isReadOnly: boolean
}

const ReadOnlyContext = createContext<ReadOnlyContextType | null>(null)

interface ReadOnlyProviderProps {
  children: ReactNode
  isReadOnly: boolean
}

export const ReadOnlyProvider = (props: ReadOnlyProviderProps) => {
  const { children, isReadOnly } = props

  return <ReadOnlyContext.Provider value={{ isReadOnly }}>{children}</ReadOnlyContext.Provider>
}

export const useReadOnly = () => {
  const context = useContext(ReadOnlyContext)
  if (!context) {
    throw new Error('useReadOnly must be used within ReadOnlyProvider')
  }

  return context
}
