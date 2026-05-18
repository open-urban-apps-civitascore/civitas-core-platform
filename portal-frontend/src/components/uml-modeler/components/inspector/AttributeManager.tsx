'use client'

import { Plus, Trash2 } from 'lucide-react'
import { useCallback } from 'react'

import { BasicSelect } from '@/components/basicSelect/BasicSelect'
import { GroupedOption, GroupedSelect } from '@/components/select/grouped-select/GroupedSelect'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'

import { UML_GEOMETRY_TYPES, UML_PRIMITIVE_TYPES } from '../../constants/umlTypes'
import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { useReadOnly } from '../../hooks/use-read-only'
import {
  type UMLAttribute,
  type UMLElement,
  type UMLGeometryType,
  type UMLPrimitiveType,
  type Visibility,
} from '../../types/uml'

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
        isId: false,
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

  const visibilityOptions: SelectOption<Visibility>[] = [
    { value: 'public', label: '+' },
    { value: 'private', label: '-' },
    { value: 'protected', label: '#' },
    { value: 'package', label: '~' },
  ]
  const primitiveTypeOptions = Object.keys(UML_PRIMITIVE_TYPES) as UMLPrimitiveType[]
  const geometryTypeOptions = Object.keys(UML_GEOMETRY_TYPES) as UMLGeometryType[]

  const typeOptions: GroupedOption[] = [
    { label: 'Primitive Types', options: primitiveTypeOptions.map(type => ({ label: type, value: type })) },
    { label: 'Geometry Types', options: geometryTypeOptions.map(type => ({ label: type, value: type })) },
  ]

  const crsOptions = [
    {
      label: 'EPSG:4326  – WGS84 (Standard)',
      value: 'EPSG:4326',
    },
    {
      label: 'EPSG:3857  – Web Mercator',
      value: 'EPSG:3857',
    },
    {
      label: 'EPSG:25832 – UTM Zone 32N',
      value: 'EPSG:25832',
    },
    {
      label: 'EPSG:25833 – UTM Zone 33N',
      value: 'EPSG:25833',
    },
    {
      label: 'EPSG:4258  – ETRS89',
      value: 'EPSG:4258',
    },
  ]

  const isGeometryType = (type: string) => !!UML_GEOMETRY_TYPES[type as UMLGeometryType]

  const allGeomAttributes = element.attributes.filter(attr => isGeometryType(attr.type as string))

  const firstGeomAttr = allGeomAttributes[0]

  const isFirstGeomAttr = (attributeId: string) => firstGeomAttr?.id === attributeId

  const getMetaForTypeChange = (attribute: UMLAttribute, newType: string): UMLAttribute['meta'] => {
    if (!isGeometryType(newType)) {
      if (!attribute.meta?.gisInfo) return attribute.meta
      const { gisInfo: _, ...metaWithoutGisinfo } = attribute.meta
      return metaWithoutGisinfo
    }
    const crs = firstGeomAttr?.meta?.gisInfo?.crs ?? crsOptions[0].value
    return { ...attribute.meta, gisInfo: { ...attribute.meta?.gisInfo, crs } }
  }

  const handleTypeChange = (e: string, attribute: UMLAttribute) =>
    updateAttribute(attribute.id, {
      type: e as UMLPrimitiveType | UMLGeometryType,
      meta: getMetaForTypeChange(attribute, e),
    })

  const handleCrsChange = (e: string, attributeId: string) => {
    if (!isFirstGeomAttr(attributeId)) return
    const updatedAttributes = element.attributes.map(attr =>
      isGeometryType(attr.type as string)
        ? { ...attr, meta: { ...attr.meta, gisInfo: { ...attr.meta?.gisInfo, crs: e } } }
        : attr,
    )
    updateNode(nodeId, { attributes: updatedAttributes })
  }

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
              <GroupedSelect
                groupedOptions={typeOptions}
                onValueChange={e => handleTypeChange(e, attribute)}
                disabled={isReadOnly}
                value={typeof attribute.type === 'string' ? attribute.type : attribute.type.name}
                size="sm"
                triggerClassName="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>

            {/* meta info - CRS */}
            <div className={cn(!isGeometryType(attribute.type as string) && 'hidden')}>
              <label className="block text-xs font-medium text-gray-600 mb-1">CRS</label>
              <BasicSelect
                options={crsOptions}
                onValueChange={e => handleCrsChange(e, attribute.id)}
                value={attribute.meta?.gisInfo?.crs}
                disabled={!isFirstGeomAttr(attribute.id)}
                size="sm"
                triggerClassName="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
            </div>

            {/* Visibility */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Visibility</label>
              <BasicSelect
                options={visibilityOptions}
                onValueChange={e => updateAttribute(attribute.id, { visibility: e as Visibility })}
                value={attribute.visibility}
                triggerClassName="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                size="sm"
                disabled={isReadOnly}
              />
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

            {/* Primary key checkbox */}
            <div className="flex items-center">
              <input
                type="checkbox"
                id={`static-${attribute.id}`}
                checked={attribute.isId}
                onChange={e => updateAttribute(attribute.id, { isId: e.target.checked })}
                className="mr-2"
                disabled={isReadOnly}
              />
              <label htmlFor={`static-${attribute.id}`} className="text-xs text-gray-600">
                {`Primary Key {id}`}
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
