'use client'
import '@xyflow/react/dist/style.css'

import {
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  ReactFlow,
} from '@xyflow/react'
import { useCallback, useState } from 'react'

const initialNodes: Node[] = [
  { id: 'n1', position: { x: 0, y: 0 }, data: { label: 'Node 1' } },
  { id: 'n2', position: { x: 0, y: 100 }, data: { label: 'Node 2' } },
]

const initialEdges: Edge[] = [{ id: 'n1-n2', source: 'n1', target: 'n2' }]

const UmlModelerPage = () => {
  const [nodes, setNodes] = useState<Node[]>(initialNodes)
  const [edges, setEdges] = useState<Edge[]>(initialEdges)

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => setNodes(nodesSnapshot => applyNodeChanges(changes, nodesSnapshot)),
    [],
  )
  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => setEdges(edgesSnapshot => applyEdgeChanges(changes, edgesSnapshot)),
    [],
  )
  const onConnect = useCallback((params: Connection) => setEdges(edgesSnapshot => addEdge(params, edgesSnapshot)), [])

  return (
    <div className="flex h-full w-full flex-1 flex-col gap-4 p-4">
      <div className="h-full w-full rounded-xl border bg-background">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
          className="rounded-xl"
        />
      </div>
    </div>
  )
}

export default UmlModelerPage
