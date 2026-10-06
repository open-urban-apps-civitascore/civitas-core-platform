import { createContext, ReactNode, useContext, useMemo } from 'react'

import { getInvalidNodeIds, getNameSignature } from '../services/elementNameService'
import type { UMLNode } from '../types/diagram'

const InvalidNodeIdsContext = createContext<ReadonlySet<string>>(new Set())

interface InvalidNodeIdsProviderProps {
  children: ReactNode
  nodes: UMLNode[]
}

export const InvalidNodeIdsProvider = (props: InvalidNodeIdsProviderProps) => {
  const { children, nodes } = props
  const nameSignature = getNameSignature(nodes)

  // Only a name change gives a new set, a position change does not.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const invalidNodeIds = useMemo(() => getInvalidNodeIds(nodes), [nameSignature])

  return <InvalidNodeIdsContext.Provider value={invalidNodeIds}>{children}</InvalidNodeIdsContext.Provider>
}

export const useInvalidNodeIds = () => useContext(InvalidNodeIdsContext)
