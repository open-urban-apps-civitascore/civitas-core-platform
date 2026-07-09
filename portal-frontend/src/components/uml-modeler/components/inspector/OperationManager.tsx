'use client'

import { ChevronDown, ChevronRight, Plus, Trash2 } from 'lucide-react'
import { useCallback, useState } from 'react'

import { UML_PRIMITIVE_TYPES } from '../../constants/umlTypes'
import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { useReadOnly } from '../../hooks/use-read-only'
import type { UMLElement, UMLOperation, UMLParameter, UMLPrimitiveType } from '../../types/uml'

interface OperationManagerProps {
  nodeId: string
  element: UMLElement
}

export const OperationManager: React.FC<OperationManagerProps> = ({ nodeId, element }) => {
  const { updateNode } = useActiveDiagram()
  const { isReadOnly } = useReadOnly()
  const [expandedOperations, setExpandedOperations] = useState<Set<string>>(new Set())

  const addOperation = useCallback(() => {
    if ('operations' in element) {
      const newOperation: UMLOperation = {
        id: crypto.randomUUID(),
        name: 'neueOperation',
        parameters: [],
        returnType: 'void',
      }
      updateNode(nodeId, {
        operations: [...element.operations, newOperation],
      })
    }
  }, [element, nodeId, updateNode])

  const updateOperation = useCallback(
    (operationId: string, updates: Partial<UMLOperation>) => {
      if ('operations' in element) {
        const updatedOperations = element.operations.map(op => (op.id === operationId ? { ...op, ...updates } : op))
        updateNode(nodeId, { operations: updatedOperations })
      }
    },
    [element, nodeId, updateNode],
  )

  const removeOperation = useCallback(
    (operationId: string) => {
      if ('operations' in element) {
        const updatedOperations = element.operations.filter(op => op.id !== operationId)
        updateNode(nodeId, { operations: updatedOperations })
      }
    },
    [element, nodeId, updateNode],
  )

  const addParameter = useCallback(
    (operationId: string) => {
      if ('operations' in element) {
        const newParameter: UMLParameter = {
          id: crypto.randomUUID(),
          name: 'param',
          type: 'String',
          direction: 'in',
        }

        const updatedOperations = element.operations.map(op =>
          op.id === operationId ? { ...op, parameters: [...op.parameters, newParameter] } : op,
        )
        updateNode(nodeId, { operations: updatedOperations })
      }
    },
    [element, nodeId, updateNode],
  )

  const updateParameter = useCallback(
    (operationId: string, parameterId: string, updates: Partial<UMLParameter>) => {
      if ('operations' in element) {
        const updatedOperations = element.operations.map(op =>
          op.id === operationId
            ? {
                ...op,
                parameters: op.parameters.map(param => (param.id === parameterId ? { ...param, ...updates } : param)),
              }
            : op,
        )
        updateNode(nodeId, { operations: updatedOperations })
      }
    },
    [element, nodeId, updateNode],
  )

  const removeParameter = useCallback(
    (operationId: string, parameterId: string) => {
      if ('operations' in element) {
        const updatedOperations = element.operations.map(op =>
          op.id === operationId ? { ...op, parameters: op.parameters.filter(param => param.id !== parameterId) } : op,
        )
        updateNode(nodeId, { operations: updatedOperations })
      }
    },
    [element, nodeId, updateNode],
  )

  const toggleOperationExpanded = useCallback((operationId: string) => {
    setExpandedOperations(prev => {
      const newSet = new Set(prev)
      if (newSet.has(operationId)) {
        newSet.delete(operationId)
      } else {
        newSet.add(operationId)
      }
      return newSet
    })
  }, [])

  if (!('operations' in element)) {
    return null
  }

  const typeOptions = Object.keys(UML_PRIMITIVE_TYPES) as UMLPrimitiveType[]
  const returnTypeOptions = [...typeOptions]

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h5 className="text-sm font-medium text-gray-700">Operations</h5>
        {!isReadOnly && (
          <button
            onClick={addOperation}
            className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            <Plus size={12} />
            Add Operation
          </button>
        )}
      </div>

      <div className="space-y-3">
        {element.operations.map((operation, index) => {
          const isExpanded = expandedOperations.has(operation.id)

          return (
            <div key={operation.id} className="border border-gray-200 rounded-lg">
              {/* Operation Header */}
              <div className="p-3 bg-gray-50 rounded-t-lg">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => toggleOperationExpanded(operation.id)}
                      className="text-gray-500 hover:text-gray-700"
                    >
                      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                    </button>
                    <span className="text-xs font-medium text-gray-500">Operation {index + 1}</span>
                  </div>
                  {!isReadOnly && (
                    <button
                      onClick={() => removeOperation(operation.id)}
                      className="text-red-500 hover:text-red-700"
                      title="Remove operation"
                    >
                      <Trash2 size={12} />
                    </button>
                  )}
                </div>

                {/* Basic Operation Info */}
                <div className="mt-2 space-y-2">
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Name</label>
                      <input
                        type="text"
                        value={operation.name}
                        onChange={e => updateOperation(operation.id, { name: e.target.value })}
                        className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                        placeholder="operationName"
                        disabled={isReadOnly}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">Return Type</label>
                      <select
                        value={
                          typeof operation.returnType === 'string'
                            ? operation.returnType
                            : operation.returnType?.name || 'void'
                        }
                        onChange={e =>
                          updateOperation(operation.id, { returnType: e.target.value as UMLPrimitiveType | 'void' })
                        }
                        className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                        disabled={isReadOnly}
                      >
                        {returnTypeOptions.map(type => (
                          <option key={type} value={type}>
                            {type}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>
                </div>
              </div>

              {/* Expanded Operation Details */}
              {isExpanded && (
                <div className="p-3 space-y-3">
                  <div className="space-y-2">
                    <div className="flex items-center">
                      <input
                        type="checkbox"
                        id={`static-${operation.id}`}
                        checked={operation.isStatic || false}
                        onChange={e => updateOperation(operation.id, { isStatic: e.target.checked })}
                        className="mr-2"
                        disabled={isReadOnly}
                      />
                      <label htmlFor={`static-${operation.id}`} className="text-xs text-gray-600">
                        Static
                      </label>
                    </div>
                    {element.type !== 'interface' && (
                      <div className="flex items-center">
                        <input
                          type="checkbox"
                          id={`abstract-${operation.id}`}
                          checked={operation.isAbstract || false}
                          onChange={e => updateOperation(operation.id, { isAbstract: e.target.checked })}
                          className="mr-2"
                          disabled={isReadOnly}
                        />
                        <label htmlFor={`abstract-${operation.id}`} className="text-xs text-gray-600">
                          Abstract
                        </label>
                      </div>
                    )}
                  </div>

                  {/* Parameters */}
                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <h6 className="text-xs font-medium text-gray-600">Parameters</h6>
                      {!isReadOnly && (
                        <button
                          onClick={() => addParameter(operation.id)}
                          className="inline-flex items-center gap-1 px-1 py-0.5 text-xs bg-gray-600 text-white rounded hover:bg-gray-700"
                        >
                          <Plus size={10} />
                          Add
                        </button>
                      )}
                    </div>

                    <div className="space-y-2">
                      {operation.parameters.map((parameter, paramIndex) => (
                        <div key={parameter.id} className="p-2 bg-gray-50 rounded border">
                          <div className="flex items-center justify-between mb-2">
                            <span className="text-xs text-gray-500">Param {paramIndex + 1}</span>
                            {!isReadOnly && (
                              <button
                                onClick={() => removeParameter(operation.id, parameter.id)}
                                className="text-red-500 hover:text-red-700"
                                title="Remove parameter"
                              >
                                <Trash2 size={10} />
                              </button>
                            )}
                          </div>
                          <div className="grid grid-cols-2 gap-2">
                            <input
                              type="text"
                              value={parameter.name}
                              onChange={e => updateParameter(operation.id, parameter.id, { name: e.target.value })}
                              className="px-2 py-1 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                              placeholder="parameter name"
                              disabled={isReadOnly}
                            />
                            <select
                              value={typeof parameter.type === 'string' ? parameter.type : parameter.type.name}
                              onChange={e =>
                                updateParameter(operation.id, parameter.id, {
                                  type: e.target.value as UMLPrimitiveType,
                                })
                              }
                              className="px-2 py-1 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                              disabled={isReadOnly}
                            >
                              {typeOptions.map(type => (
                                <option key={type} value={type}>
                                  {type}
                                </option>
                              ))}
                            </select>
                          </div>
                        </div>
                      ))}

                      {operation.parameters.length === 0 && (
                        <p className="text-xs text-gray-500 text-center py-2">No parameters</p>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )
        })}

        {element.operations.length === 0 && (
          <p className="text-sm text-gray-500 text-center py-4">No operations defined</p>
        )}
      </div>
    </div>
  )
}
