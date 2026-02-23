import { createContext, Dispatch, ReactNode, SetStateAction, useContext, useState } from 'react'

interface ReadOnlyContextType {
  isReadOnly: boolean
  setIsReadOnly: Dispatch<SetStateAction<boolean>>
}

const ReadOnlyContext = createContext<ReadOnlyContextType | null>(null)

interface ReadOnlyProviderProps {
  children: ReactNode
}

export const ReadOnlyProvider = (props: ReadOnlyProviderProps) => {
  const { children } = props
  const [isReadOnly, setIsReadOnly] = useState(false)

  return <ReadOnlyContext.Provider value={{ isReadOnly, setIsReadOnly }}>{children}</ReadOnlyContext.Provider>
}

export const useReadOnly = () => {
  const context = useContext(ReadOnlyContext)
  if (!context) {
    throw new Error('useReadOnly must be used within ReadOnlyProvider')
  }

  return context
}
