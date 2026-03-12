'use client'

import { Plus, Trash2 } from 'lucide-react'
import { useCallback } from 'react'

import { UML_PRIMITIVE_TYPES } from '../../constants/umlTypes'
import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { useReadOnly } from '../../hooks/use-read-only'
import type { UMLAttribute, UMLElement, UMLPrimitiveType, Visibility } from '../../types/uml'

interface AttributeManagerProps {
  nodeId: string
  element: UMLElement
}

export const AttributeManager: React.FC<AttributeManagerProps> = props => {
  const { nodeId, element } = props
  const { isReadOnly } = useReadOnly()

  const { updateNode } = useActiveDiagram()

  const addAttribute = useCallback(() => {
    if ('attributes' in element) {
      const newAttribute: UMLAttribute = {
        id: crypto.randomUUID(),
        name: 'neuesAttribut',
        type: 'String',
        visibility: 'private',
      }
      updateNode(nodeId, {
        attributes: [...element.attributes, newAttribute],
      })
    }
  }, [element, nodeId, updateNode])

  const updateAttribute = useCallback(
    (attributeId: string, updates: Partial<UMLAttribute>) => {
      if ('attributes' in element) {
        const updatedAttributes = element.attributes.map(attr =>
          attr.id === attributeId ? { ...attr, ...updates } : attr,
        )
        updateNode(nodeId, { attributes: updatedAttributes })
      }
    },
    [element, nodeId, updateNode],
  )

  const removeAttribute = useCallback(
    (attributeId: string) => {
      if ('attributes' in element) {
        const updatedAttributes = element.attributes.filter(attr => attr.id !== attributeId)
        updateNode(nodeId, { attributes: updatedAttributes })
      }
    },
    [element, nodeId, updateNode],
  )

  if (!('attributes' in element)) {
    return null
  }

  const visibilityOptions: Visibility[] = ['public', 'private', 'protected', 'package']
  const typeOptions = Object.keys(UML_PRIMITIVE_TYPES) as UMLPrimitiveType[]

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h5 className="text-sm font-medium text-gray-700">Attributes</h5>
        {!isReadOnly && (
          <button
            onClick={addAttribute}
            className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            <Plus size={12} />
            Add Attribute
          </button>
        )}
      </div>

      <div className="space-y-3">
        {element.attributes.map((attribute, index) => (
          <div key={attribute.id} className="p-3 border border-gray-200 rounded-lg space-y-2">
            {!isReadOnly && (
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-gray-500">Attribute {index + 1}</span>
                <button
                  onClick={() => removeAttribute(attribute.id)}
                  className="text-red-500 hover:text-red-700"
                  title="Remove attribute"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            )}

            {/* Name */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Name</label>
              <input
                type="text"
                value={attribute.name}
                onChange={e => updateAttribute(attribute.id, { name: e.target.value })}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="attributeName"
                disabled={isReadOnly}
              />
            </div>

            {/* Type */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Type</label>
              <select
                value={typeof attribute.type === 'string' ? attribute.type : attribute.type.name}
                onChange={e => updateAttribute(attribute.id, { type: e.target.value as UMLPrimitiveType })}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                disabled={isReadOnly}
              >
                {typeOptions.map(type => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>

            {/* Visibility */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Visibility</label>
              <select
                value={attribute.visibility}
                onChange={e => updateAttribute(attribute.id, { visibility: e.target.value as Visibility })}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                disabled={isReadOnly}
              >
                {visibilityOptions.map(visibility => (
                  <option key={visibility} value={visibility}>
                    {visibility} (
                    {visibility === 'public'
                      ? '+'
                      : visibility === 'private'
                        ? '-'
                        : visibility === 'protected'
                          ? '#'
                          : '~'}
                    )
                  </option>
                ))}
              </select>
            </div>

            {/* Default Value */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Default Value</label>
              <input
                type="text"
                value={attribute.defaultValue || ''}
                onChange={e => updateAttribute(attribute.id, { defaultValue: e.target.value || undefined })}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Optional default value"
                disabled={isReadOnly}
              />
            </div>

            {/* Static checkbox */}
            <div className="flex items-center">
              <input
                type="checkbox"
                id={`static-${attribute.id}`}
                checked={attribute.isStatic || false}
                onChange={e => updateAttribute(attribute.id, { isStatic: e.target.checked })}
                className="mr-2"
                disabled={isReadOnly}
              />
              <label htmlFor={`static-${attribute.id}`} className="text-xs text-gray-600">
                Static (underlined)
              </label>
            </div>
          </div>
        ))}

        {element.attributes.length === 0 && (
          <p className="text-sm text-gray-500 text-center py-4">No attributes defined</p>
        )}
      </div>
    </div>
  )
}
