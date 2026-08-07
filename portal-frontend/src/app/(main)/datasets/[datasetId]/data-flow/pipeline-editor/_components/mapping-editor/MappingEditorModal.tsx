'use client'

import type { Edge, IsValidConnection, Node, NodeTypes, OnConnect, OnConnectEnd, OnConnectStart } from '@xyflow/react'
import { addEdge, useEdgesState, useNodesState } from '@xyflow/react'
import { useTranslations } from 'next-intl'
import type { KeyboardEvent } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'

import { useGetDatastructureVersion } from '@/app/services/api/datastructures/versions/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import {
  CanvasScaffold,
  createTransformNodeType,
  EditorLayout,
  InspectorShell,
  PaletteShell,
} from '@/components/node-editor'
import type { PortDef, PortType, TransformNodeData } from '@/components/node-editor/types'
import { buildRegistry } from '@/components/node-editor/types'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { rootFailureMessage } from '@/components/uml-modeler/services/rootFailureMessage'
import { buildDataStructureUrn } from '@/utils/urn'

import type { StaTargetVocabulary } from '../../_constants/staTargetCatalog'
import { deriveStaMatchKeys } from '../../_constants/staTargetCatalog'
import type { MappingConfig } from './_types'
import { ARRAY_EDGE_STYLE, INVALID_EDGE_STYLE } from './_types'
import {
  compileCanvas,
  decompileConfig,
  findUnconnectedTransformNodes,
  SOURCE_NODE_ID,
  TARGET_NODE_ID,
} from './compile'
import { TransformInspector } from './inspector/TransformInspector'
import { MegaNode } from './nodes/MegaNode'
import { flattenTree, objectFieldsCompatible, requiredFieldPaths } from './schema/fieldTree'
import { versionToSchemaTree } from './schema/versionTree'
import { computeStatus, findNodeConfigErrors, portsCompatible } from './status'
import type { MappingTransformDef } from './transforms'
import { concatInputPorts, LITERAL_DEFAULT_TYPE, literalOutputPort, mappingRegistry } from './transforms'

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
  onSave: (config: MappingConfig, targetRequiredFields: string[], staMatchKeys: StaTargetVocabulary) => void
}

const FULLSCREEN =
  'flex h-screen w-screen max-w-none flex-col overflow-hidden rounded-none border-0 p-0 gap-0 top-0 left-0 translate-x-0 translate-y-0 sm:max-w-none'

export const MappingEditorModal = ({
  open,
  onOpenChange,
  name,
  source,
  target,
  config,
  onSave,
}: MappingEditorModalProps) => {
  const t = useTranslations('pipelineEditor.mappingEditor')
  const tCommon = useTranslations('common')
  const tUmlModeler = useTranslations('umlModeler')

  /**
   * Build a translated copy of the registry so PaletteShell renders
   * localised category names and descriptions instead of raw i18n keys.
   */
  const translatedRegistry = useMemo(() => {
    const translatedDefs = mappingRegistry.list.map((def: MappingTransformDef) => ({
      ...def,
      category: t(def.category as Parameters<typeof t>[0]),
      description: def.description ? t(def.description as Parameters<typeof t>[0]) : def.description,
      config: def.config.map(field => ({
        ...field,
        label: t(field.label as Parameters<typeof t>[0]),
        ...(field.placeholder ? { placeholder: t(field.placeholder as Parameters<typeof t>[0]) } : {}),
      })),
    }))
    return buildRegistry<MappingTransformDef>(translatedDefs)
  }, [t])

  const nodeTypes: NodeTypes = useMemo(
    () => ({ transform: createTransformNodeType(translatedRegistry), mega: MegaNode }),
    [translatedRegistry],
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

  const sourceResolution = useMemo(
    () => versionToSchemaTree(sourceQuery.data?.data, source.name ?? 'source'),
    [sourceQuery.data, source.name],
  )
  const targetResolution = useMemo(
    () => versionToSchemaTree(targetQuery.data?.data, target.name ?? 'target'),
    [targetQuery.data, target.name],
  )
  const sourceTree = sourceResolution.tree
  const targetTree = targetResolution.tree
  const sourceFields = useMemo(() => flattenTree(sourceTree), [sourceTree])
  const targetFields = useMemo(() => flattenTree(targetTree), [targetTree])

  // The diagram-derived fallback tree is not the artifact the engine reads — the user must know
  // before drawing mappings against it — and a rootless diagram yields an empty tree whose reason
  // would otherwise be invisible. The stable role-keyed toast ids absorb re-renders (StrictMode,
  // refetches) into one visible warning per side, even when both structures share a name.
  useEffect(() => {
    if (!open) return
    for (const [role, resolution] of [
      ['source', sourceResolution],
      ['target', targetResolution],
    ] as const) {
      if (resolution.isModelBroken) {
        toast.warning(t('modelUnresolvable', { name: resolution.tree.name }), {
          id: `model-broken-${role}`,
        })
      }
      if (resolution.diagramFailure) {
        toast.warning(
          t('diagramUnresolvable', {
            name: resolution.tree.name,
            reason: rootFailureMessage(tUmlModeler, resolution.diagramFailure),
          }),
          { id: `diagram-unresolvable-${role}` },
        )
      }
    }
  }, [open, sourceResolution, targetResolution, t, tUmlModeler])

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  // Which exit action is awaiting confirmation because unconnected transforms would be dropped.
  const [pendingExit, setPendingExit] = useState<'apply' | 'close' | null>(null)
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
      if (!field) return null
      // `sub` is the concrete field type (incl. geometry names like 'Point') so the
      // exact subtype check matches Point↔Point and blocks Point↔Polygon.
      return { type: field.portType, sub: field.type }
    }
    if (nodeId === TARGET_NODE_ID) {
      const field = targetFields.get(handleId)
      if (!field) return null
      return { type: field.portType, sub: field.type }
    }

    const node = nodes.find(n => n.id === nodeId)
    if (!node) return null
    const data = node.data as TransformNodeData
    const def = mappingRegistry.byType[data.defType]
    const ports = [...(data.inputs ?? def?.inputs ?? []), ...(data.outputs ?? def?.outputs ?? [])]
    const port = ports.find(p => p.id === handleId)
    return port ? { type: port.type, sub: port.dataType } : null
  }

  const isValidConnection: IsValidConnection = useCallback(
    connection => {
      const from = endpointInfo(connection.source, connection.sourceHandle ?? '')
      const to = endpointInfo(connection.target, connection.targetHandle ?? '')
      if (!portsCompatible(from, to)) return false
      // Object/array source→target links auto-map their children, so the two
      // subtrees must line up by exact field name + matching type.
      if (
        connection.source === SOURCE_NODE_ID &&
        connection.target === TARGET_NODE_ID &&
        (from?.type === 'object' || from?.type === 'array')
      ) {
        const sourceField = sourceFields.get(connection.sourceHandle ?? '')
        const targetField = targetFields.get(connection.targetHandle ?? '')
        if (sourceField && targetField) return objectFieldsCompatible(sourceField, targetField)
      }
      return true
    },
    // endpointInfo reads nodes/sourceFields/targetFields via closure — include them
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [nodes, sourceFields, targetFields],
  )

  /** Tracks the source endpoint of an in-progress drag so we can show a toast on failure. */
  const pendingConnection = useRef<{ nodeId: string; handleId: string } | null>(null)

  const onConnectStart: OnConnectStart = useCallback((_event, params) => {
    pendingConnection.current = params.nodeId ? { nodeId: params.nodeId, handleId: params.handleId ?? '' } : null
  }, [])

  const onConnectEnd: OnConnectEnd = useCallback(
    (_event, connectionState) => {
      // connectionState.isValid is false when the drag ended on an incompatible handle
      if (connectionState && !connectionState.isValid && pendingConnection.current) {
        const fromDir = connectionState.fromHandle?.type
        const toDir = connectionState.toHandle?.type
        // Same-direction drop (output-output or input-input)
        if (toDir && fromDir === toDir) {
          toast.error(fromDir === 'source' ? t('errors.outputToOutput') : t('errors.inputToInput'))
        } else {
          const from = endpointInfo(pendingConnection.current.nodeId, pendingConnection.current.handleId)
          // Only show the message when we can identify the source type (not a missed drop into empty space)
          if (from?.sub) {
            toast.error(t('errors.typeMismatchWithType', { type: from.sub }))
          } else if (from) {
            toast.error(t('errors.typeMismatch'))
          }
        }
      }
      pendingConnection.current = null
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [nodes, sourceFields, targetFields, t],
  )

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
    const isArray = from?.type === 'array'
    setEdges(eds => {
      const kept = eds.filter(
        e => !(e.target === connection.target && (e.targetHandle ?? '') === (connection.targetHandle ?? '')),
      )
      return addEdge({ ...connection, ...(isArray ? { style: ARRAY_EDGE_STYLE } : {}) }, kept)
    })
    growConcat(connection.target, connection.targetHandle ?? '')
  }

  const onDropNode = (type: string, position: { x: number; y: number }) => {
    const def = mappingRegistry.byType[type]
    if (!def) return
    const nodeConfig = Object.fromEntries(def.config.map(field => [field.key, field.default ?? '']))
    const id = `${type}-${nextId.current++}`
    // For literal nodes: initialize the output port with the default type so connections work on drop.
    const outputs = type === 'const' ? [literalOutputPort(LITERAL_DEFAULT_TYPE)] : def.outputs
    const data: TransformNodeData = { defType: type, config: nodeConfig, inputs: def.inputs, outputs }
    setNodes(nds => [...nds, { id, type: 'transform', position, data }])
  }

  /** Outgoing edges of `nodeId` that the rewritten output port no longer type-matches. */
  const edgesBrokenBy = (nodeId: string, port: PortDef): Edge[] =>
    edges.filter(
      e =>
        e.source === nodeId &&
        (e.sourceHandle ?? '') === port.id &&
        !portsCompatible({ type: port.type, sub: port.dataType }, endpointInfo(e.target, e.targetHandle ?? '')),
    )

  const updateConfig = (key: string, value: string) => {
    if (!selectedId) return

    const selectedDefType = (nodes.find(n => n.id === selectedId)?.data as TransformNodeData | undefined)?.defType
    if (key === 'type' && selectedDefType === 'const') {
      const newPort = literalOutputPort(value || LITERAL_DEFAULT_TYPE)
      const broken = edgesBrokenBy(selectedId, newPort)
      if (broken.length > 0) {
        toast.error(t('errors.connectionsBroken', { count: broken.length, type: newPort.dataType ?? '' }))
      }
    }

    setNodes(nds =>
      nds.map(node => {
        if (node.id !== selectedId) return node
        const data = node.data as TransformNodeData
        const newConfig = { ...data.config, [key]: value }
        // When the literal node's type changes, update the output port's dataType so
        // connection validation reflects the chosen type.
        if (data.defType === 'const' && key === 'type') {
          const newOutputs = [literalOutputPort(value || LITERAL_DEFAULT_TYPE)]
          return { ...node, data: { ...data, config: newConfig, outputs: newOutputs } }
        }
        return { ...node, data: { ...data, config: newConfig } }
      }),
    )
  }

  const status = useMemo(
    () => computeStatus(edges, sourceFields, targetFields, endpointInfo),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [edges, nodes, sourceFields, targetFields],
  )

  const configErrors = useMemo(() => findNodeConfigErrors(nodes, edges), [nodes, edges])

  // Port statuses and error flags are injected for rendering only — never into the `nodes`
  // state, so compileCanvas keeps seeing the persisted node data.
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
          : {
              ...node,
              data: {
                ...node.data,
                portStatus: status.transformPortStatus[node.id],
                hasError: !!configErrors.byNode[node.id],
              },
            },
      ),
    [nodes, status, configErrors],
  )

  const displayEdges = useMemo(() => {
    const invalid = new Set(status.invalidEdgeIds)
    return edges.map(edge =>
      invalid.has(edge.id) ? { ...edge, style: { ...edge.style, ...INVALID_EDGE_STYLE } } : edge,
    )
  }, [edges, status.invalidEdgeIds])

  const selectedNode = nodes.find(n => n.id === selectedId)
  const selectedData = selectedNode?.type === 'transform' ? (selectedNode.data as TransformNodeData) : undefined
  // Look up the def from the translated registry so the inspector receives translated labels
  const selectedDef = selectedData ? translatedRegistry.byType[selectedData.defType] : undefined
  // validate() returns i18n keys; translate here so the inspector just renders strings.
  const selectedErrors = useMemo(() => {
    const keys = selectedId ? configErrors.byNode[selectedId] : undefined
    if (!keys) return undefined
    return Object.fromEntries(
      Object.entries(keys).map(([fieldKey, messageKey]) => [fieldKey, t(messageKey as Parameters<typeof t>[0])]),
    )
  }, [selectedId, configErrors, t])

  // Transform nodes that aren't wired to the output — compileCanvas drops these on save.
  const unconnectedTransforms = useMemo(() => findUnconnectedTransformNodes(nodes, edges), [nodes, edges])

  const doSave = () => {
    const { fields, positions } = compileCanvas(nodes, edges)
    // A selectable source/target is always a released version, so name and
    // version are present. If buildDataStructureUrn throws here, the platform is
    // in an unexpected state (a released DataStructure without name/version) —
    // let it surface rather than silently emitting a malformed URN.
    onSave(
      {
        $schema: 'https://civitasconnect.digital/core/mapping/v1',
        source: buildDataStructureUrn(source.name ?? '', source.datastructureId, sourceQuery.data?.data?.version ?? ''),
        target: buildDataStructureUrn(target.name ?? '', target.datastructureId, targetQuery.data?.data?.version ?? ''),
        fields,
        positions,
      },
      requiredFieldPaths(targetTree),
      deriveStaMatchKeys(targetTree),
    )
  }

  const performExit = (action: 'apply' | 'close') => {
    setPendingExit(null)
    if (action === 'apply') doSave()
    onOpenChange(false)
  }

  // Warn before leaving if unconnected transforms would be discarded; otherwise exit directly.
  const requestExit = (action: 'apply' | 'close') => {
    if (unconnectedTransforms.length > 0) {
      setPendingExit(action)
      return
    }
    performExit(action)
  }

  const { mapped, unmapped } = status.counts
  const errorCount = status.counts.errors + configErrors.blockingCount

  const toolbar = (
    <div className="grid grid-cols-3 items-center px-4 py-2">
      <DialogTitle className="text-base">{name || t('toolbar.title')}</DialogTitle>
      <span className="text-center text-xs text-muted-foreground">
        {t('toolbar.status', { mapped, unmapped })}
        {errorCount > 0 && (
          <span className="ml-2 font-medium text-destructive">{t('toolbar.errors', { count: errorCount })}</span>
        )}
      </span>
      <div className="flex items-center justify-end gap-2">
        <Button size="sm" onClick={() => requestExit('apply')} disabled={!isReady || errorCount > 0}>
          {tCommon('actions.apply')}
        </Button>
        <Button size="sm" variant="outline" onClick={() => requestExit('close')}>
          {tCommon('actions.close')}
        </Button>
      </div>
    </div>
  )

  /**
   * Prevent Delete/Backspace from bubbling out of the modal to the parent
   * PipelineCanvas.
   */
  const stopDeletePropagation = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Delete' || e.key === 'Backspace') {
      e.stopPropagation()
    }
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent
          showCloseButton={false}
          className={FULLSCREEN}
          aria-describedby={undefined}
          onKeyDown={stopDeletePropagation}
        >
          <EditorLayout
            toolbar={toolbar}
            palette={<PaletteShell registry={translatedRegistry} title={t('palette.title')} />}
            inspector={
              <InspectorShell
                title={t('inspector.title')}
                isEmpty={!selectedDef}
                emptyMessage={t('inspector.emptyMessage')}
              >
                {selectedDef && selectedData && (
                  <TransformInspector
                    def={selectedDef}
                    config={selectedData.config}
                    onChange={updateConfig}
                    errors={selectedErrors}
                  />
                )}
              </InspectorShell>
            }
          >
            <CanvasScaffold
              nodes={displayNodes}
              edges={displayEdges}
              nodeTypes={nodeTypes}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onConnectStart={onConnectStart}
              onConnectEnd={onConnectEnd}
              isValidConnection={isValidConnection}
              onDropNode={onDropNode}
              onSelectionChange={setSelectedId}
              deleteKeyCode={['Delete', 'Backspace']}
            />
          </EditorLayout>
        </DialogContent>
      </Dialog>

      <WarningModal
        open={pendingExit !== null}
        title={t('unconnectedWarning.title')}
        description={t('unconnectedWarning.description', { count: unconnectedTransforms.length })}
        confirmButtonTitle={t('unconnectedWarning.confirm')}
        onOpenChange={isOpen => {
          if (!isOpen) setPendingExit(null)
        }}
        onDiscard={() => setPendingExit(null)}
        onConfirm={() => pendingExit && performExit(pendingExit)}
      />
    </>
  )
}
