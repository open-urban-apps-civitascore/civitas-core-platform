'use client'

import type { Edge, IsValidConnection, Node, NodeTypes, OnConnect } from '@xyflow/react'
import { addEdge, useEdgesState, useNodesState } from '@xyflow/react'
import { useEffect, useMemo, useRef, useState } from 'react'

import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import {
  CanvasScaffold,
  createTransformNodeType,
  EditorLayout,
  InspectorShell,
  PaletteShell,
} from '@/components/node-editor'
import type { PortType, TransformNodeData } from '@/components/node-editor/types'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'

import type { MappingConfig } from './_types'
import { compileCanvas, decompileConfig, SOURCE_NODE_ID, TARGET_NODE_ID } from './compile'
import { TransformInspector } from './inspector/TransformInspector'
import { MegaNode } from './nodes/MegaNode'
import { umlDiagramToSchemaTree } from './schema/adapter'
import { flattenTree } from './schema/fieldTree'
import { computeStatus } from './status'
import { concatInputPorts, mappingRegistry } from './transforms'

export interface SchemaRef {
  datastructureId: string
  versionId: string
  name?: string
}

interface MappingEditorModalProps {
  // eslint-disable-next-line react/boolean-prop-naming -- matches the Dialog `open` API
  open: boolean
  onOpenChange: (open: boolean) => void
  name: string
  source: SchemaRef
  target: SchemaRef
  config: MappingConfig
  onSave: (config: MappingConfig) => void
}

const FULLSCREEN =
  'flex h-screen w-screen max-w-none flex-col overflow-hidden rounded-none border-0 p-0 gap-0 top-0 left-0 translate-x-0 translate-y-0 sm:max-w-none'

const ARRAY_EDGE_STYLE = { strokeWidth: 3, stroke: '#7c3aed' }

export const MappingEditorModal = ({
  open,
  onOpenChange,
  name,
  source,
  target,
  config,
  onSave,
}: MappingEditorModalProps) => {
  const nodeTypes: NodeTypes = useMemo(
    () => ({ transform: createTransformNodeType(mappingRegistry), mega: MegaNode }),
    [],
  )

  const sourceQuery = useGetDatastructureVersion({
    datastructureId: source.datastructureId,
    versionId: source.versionId,
    isEnabled: open,
  })
  const targetQuery = useGetDatastructureVersion({
    datastructureId: target.datastructureId,
    versionId: target.versionId,
    isEnabled: open,
  })

  const sourceTree = useMemo(
    () => umlDiagramToSchemaTree(sourceQuery.data?.data?.styles, source.name ?? 'source'),
    [sourceQuery.data, source.name],
  )
  const targetTree = useMemo(
    () => umlDiagramToSchemaTree(targetQuery.data?.data?.styles, target.name ?? 'target'),
    [targetQuery.data, target.name],
  )
  const sourceFields = useMemo(() => flattenTree(sourceTree), [sourceTree])
  const targetFields = useMemo(() => flattenTree(targetTree), [targetTree])

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const nextId = useRef(0)
  const initialized = useRef(false)

  const isReady = !!sourceQuery.data?.data && !!targetQuery.data?.data

  useEffect(() => {
    if (!open) {
      initialized.current = false
      return
    }
    if (initialized.current || !isReady) return
    const built = decompileConfig(config, sourceTree, targetTree)
    setNodes(built.nodes)
    setEdges(built.edges)
    setSelectedId(null)
    initialized.current = true
  }, [open, isReady, sourceTree, targetTree, config, setNodes, setEdges])

  const endpointInfo = (nodeId: string, handleId: string): { type: PortType; sub?: string } | null => {
    if (nodeId === SOURCE_NODE_ID) {
      const field = sourceFields.get(handleId)
      return field ? { type: field.portType, sub: field.type } : null
    }
    if (nodeId === TARGET_NODE_ID) {
      const field = targetFields.get(handleId)
      return field ? { type: field.portType, sub: field.type } : null
    }
    const node = nodes.find(n => n.id === nodeId)
    if (!node) return null
    const data = node.data as TransformNodeData
    const def = mappingRegistry.byType[data.defType]
    const ports = [...(data.inputs ?? def?.inputs ?? []), ...(data.outputs ?? def?.outputs ?? [])]
    const port = ports.find(p => p.id === handleId)
    return port ? { type: port.type, sub: port.dataType } : null
  }

  const isValidConnection: IsValidConnection = connection => {
    const from = endpointInfo(connection.source, connection.sourceHandle ?? '')
    const to = endpointInfo(connection.target, connection.targetHandle ?? '')
    return !!from && !!to && from.type === to.type
  }

  const growConcat = (nodeId: string, handleId: string) => {
    setNodes(nds =>
      nds.map(node => {
        if (node.id !== nodeId) return node
        const data = node.data as TransformNodeData
        if (data.defType !== 'concat') return node
        const ports = data.inputs ?? concatInputPorts(2)
        if (handleId !== ports[ports.length - 1].id) return node
        return { ...node, data: { ...data, inputs: concatInputPorts(ports.length + 1) } }
      }),
    )
  }

  const onConnect: OnConnect = connection => {
    if (!isValidConnection(connection)) return
    const from = endpointInfo(connection.source, connection.sourceHandle ?? '')
    const to = endpointInfo(connection.target, connection.targetHandle ?? '')
    const isArray = from?.type === 'array'
    const isCast = from?.type === 'scalar' && to?.type === 'scalar' && !!from.sub && !!to.sub && from.sub !== to.sub
    setEdges(eds => {
      const kept = eds.filter(
        e => !(e.target === connection.target && (e.targetHandle ?? '') === (connection.targetHandle ?? '')),
      )
      return addEdge(
        { ...connection, ...(isArray ? { style: ARRAY_EDGE_STYLE } : {}), ...(isCast ? { label: 'cast' } : {}) },
        kept,
      )
    })
    growConcat(connection.target, connection.targetHandle ?? '')
  }

  const onDropNode = (type: string, position: { x: number; y: number }) => {
    const def = mappingRegistry.byType[type]
    if (!def) return
    const nodeConfig = Object.fromEntries(def.config.map(field => [field.key, field.default ?? '']))
    const id = `${type}-${nextId.current++}`
    const data: TransformNodeData = { defType: type, config: nodeConfig, inputs: def.inputs, outputs: def.outputs }
    setNodes(nds => [...nds, { id, type: 'transform', position, data }])
  }

  const updateConfig = (key: string, value: string) => {
    if (!selectedId) return
    setNodes(nds =>
      nds.map(node => {
        if (node.id !== selectedId) return node
        const data = node.data as TransformNodeData
        return { ...node, data: { ...data, config: { ...data.config, [key]: value } } }
      }),
    )
  }

  const status = useMemo(
    () => computeStatus(edges, sourceFields, targetFields, endpointInfo),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [edges, nodes, sourceFields, targetFields],
  )

  const displayNodes = useMemo(
    () =>
      nodes.map(node =>
        node.type === 'mega'
          ? {
              ...node,
              data: {
                ...node.data,
                portStatus: node.data.role === 'source' ? status.sourcePortStatus : status.targetPortStatus,
              },
            }
          : node,
      ),
    [nodes, status],
  )

  const selectedNode = nodes.find(n => n.id === selectedId)
  const selectedData = selectedNode?.type === 'transform' ? (selectedNode.data as TransformNodeData) : undefined
  const selectedDef = selectedData ? mappingRegistry.byType[selectedData.defType] : undefined

  const handleSave = () => {
    const { fields, positions } = compileCanvas(nodes, edges)
    onSave({
      sourceDatastructureId: source.datastructureId,
      sourceVersionId: source.versionId,
      targetDatastructureId: target.datastructureId,
      targetVersionId: target.versionId,
      fields,
      positions,
    })
    onOpenChange(false)
  }

  const { mapped, unmapped, errors } = status.counts

  const toolbar = (
    <div className="flex items-center gap-3 px-4 py-2">
      <DialogTitle className="text-base">{name || 'Mapping'}</DialogTitle>
      <span className="text-xs text-muted-foreground">
        {mapped} mapped · {unmapped} unmapped · {errors} errors
      </span>
      <div className="ml-auto flex items-center gap-2">
        <Button size="sm" onClick={handleSave}>
          Save
        </Button>
        <Button size="sm" variant="outline" onClick={() => onOpenChange(false)}>
          Close
        </Button>
      </div>
    </div>
  )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent showCloseButton={false} className={FULLSCREEN} aria-describedby={undefined}>
        <EditorLayout
          toolbar={toolbar}
          palette={<PaletteShell registry={mappingRegistry} title="Transforms" />}
          inspector={
            <InspectorShell title="Inspector" isEmpty={!selectedDef} emptyMessage="Select a transform to edit it">
              {selectedDef && selectedData && (
                <TransformInspector def={selectedDef} config={selectedData.config} onChange={updateConfig} />
              )}
            </InspectorShell>
          }
        >
          <CanvasScaffold
            nodes={displayNodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            isValidConnection={isValidConnection}
            onDropNode={onDropNode}
            onSelectionChange={setSelectedId}
          />
        </EditorLayout>
      </DialogContent>
    </Dialog>
  )
}
