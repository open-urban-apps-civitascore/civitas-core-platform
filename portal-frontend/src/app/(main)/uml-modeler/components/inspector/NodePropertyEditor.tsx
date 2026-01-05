'use client'

import { Plus, Trash2 } from 'lucide-react'
import { ChangeEvent, useCallback, useState } from 'react'

import { UML_STEREOTYPES } from '../../constants/umlTypes'
import { useActiveDiagram } from '../../hooks/use-active-diagram'
import type { UMLNode } from '../../types/diagram'
import { hasAttributes, hasOperations } from '../../types/uml'
import { AttributeManager } from './AttributeManager'
import { OperationManager } from './OperationManager'

interface NodePropertyEditorProps {
  node: UMLNode
}

export const NodePropertyEditor: React.FC<NodePropertyEditorProps> = ({ node }) => {
  const { updateNode } = useActiveDiagram()
  const [activeSection, setActiveSection] = useState<'basic' | 'attributes' | 'operations' | 'literals'>('basic')

  const element = node.data.element

  const handleNameChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const newName = event.target.value
      updateNode(node.id, { name: newName })
    },
    [node.id, updateNode],
  )

  const handleStereotypeChange = useCallback(
    (event: ChangeEvent<HTMLSelectElement>) => {
      const newStereotype = event.target.value || undefined
      updateNode(node.id, { stereotype: newStereotype })
    },
    [node.id, updateNode],
  )

  const addEnumLiteral = useCallback(() => {
    if (element.type === 'enumeration' && 'literals' in element) {
      const newLiteral = {
        id: crypto.randomUUID(),
        name: `WERT${element.literals.length + 1}`,
      }
      updateNode(node.id, {
        literals: [...element.literals, newLiteral],
      })
    }
  }, [element, node.id, updateNode])

  const updateEnumLiteral = useCallback(
    (literalId: string, name: string) => {
      if (element.type === 'enumeration' && 'literals' in element) {
        const updatedLiterals = element.literals.map(literal =>
          literal.id === literalId ? { ...literal, name } : literal,
        )
        updateNode(node.id, { literals: updatedLiterals })
      }
    },
    [element, node.id, updateNode],
  )

  const removeEnumLiteral = useCallback(
    (literalId: string) => {
      if (element.type === 'enumeration' && 'literals' in element) {
        const updatedLiterals = element.literals.filter(literal => literal.id !== literalId)
        updateNode(node.id, { literals: updatedLiterals })
      }
    },
    [element, node.id, updateNode],
  )

  const availableStereotypes = UML_STEREOTYPES[element.type] || []

  return (
    <div className="space-y-4">
      {/* Tab Navigation */}
      <div className="flex border-b border-gray-200">
        <button
          onClick={() => setActiveSection('basic')}
          className={`px-3 py-2 text-sm font-medium border-b-2 ${
            activeSection === 'basic'
              ? 'border-blue-500 text-blue-600'
              : 'border-transparent text-gray-500 hover:text-gray-700'
          }`}
        >
          Basic
        </button>
        {hasAttributes(element) && (
          <button
            onClick={() => setActiveSection('attributes')}
            className={`px-3 py-2 text-sm font-medium border-b-2 ${
              activeSection === 'attributes'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Attributes
          </button>
        )}
        {hasOperations(element) && (
          <button
            onClick={() => setActiveSection('operations')}
            className={`px-3 py-2 text-sm font-medium border-b-2 ${
              activeSection === 'operations'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Operations
          </button>
        )}
        {element.type === 'enumeration' && (
          <button
            onClick={() => setActiveSection('literals')}
            className={`px-3 py-2 text-sm font-medium border-b-2 ${
              activeSection === 'literals'
                ? 'border-blue-500 text-blue-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Values
          </button>
        )}
      </div>

      {/* Tab Content */}
      <div className="mt-4">
        {activeSection === 'basic' && (
          <div className="space-y-4">
            {/* Name */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
              <input
                type="text"
                value={element.name}
                onChange={handleNameChange}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Element name"
              />
            </div>

            {/* Stereotype */}
            {availableStereotypes.length > 0 && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Stereotype</label>
                <select
                  value={element.stereotype || ''}
                  onChange={handleStereotypeChange}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">None</option>
                  {availableStereotypes.map(stereotype => (
                    <option key={stereotype} value={stereotype}>
                      {stereotype}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
        )}

        {activeSection === 'attributes' && hasAttributes(element) && (
          <AttributeManager nodeId={node.id} element={element} />
        )}

        {activeSection === 'operations' && hasOperations(element) && (
          <OperationManager nodeId={node.id} element={element} />
        )}

        {activeSection === 'literals' && element.type === 'enumeration' && 'literals' in element && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h5 className="text-sm font-medium text-gray-700">Enumeration Values</h5>
              <button
                onClick={addEnumLiteral}
                className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                <Plus size={12} />
                Add Value
              </button>
            </div>

            <div className="space-y-2">
              {element.literals.map((literal, index) => (
                <div key={literal.id} className="flex items-center gap-2">
                  <span className="text-xs text-gray-500 w-6">{index + 1}.</span>
                  <input
                    type="text"
                    value={literal.name}
                    onChange={e => updateEnumLiteral(literal.id, e.target.value)}
                    className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="VALUE_NAME"
                  />
                  <button
                    onClick={() => removeEnumLiteral(literal.id)}
                    className="p-1 text-red-500 hover:text-red-700"
                    title="Remove value"
                  >
                    <Trash2 size={12} />
                  </button>
                </div>
              ))}

              {element.literals.length === 0 && (
                <p className="text-sm text-gray-500 text-center py-4">No enumeration values defined</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
