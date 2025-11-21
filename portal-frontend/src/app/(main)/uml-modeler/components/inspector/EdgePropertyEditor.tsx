'use client'

import { ChangeEvent, useCallback } from 'react'

import { MULTIPLICITY_VALUES } from '../../constants/umlTypes'
import { useActiveDiagram } from '../../hooks/useActiveDiagram'
import type { UMLEdge } from '../../types/diagram'
import type { UMLRelationshipType } from '../../types/uml'

interface EdgePropertyEditorProps {
  edge: UMLEdge
}

export const EdgePropertyEditor: React.FC<EdgePropertyEditorProps> = ({ edge }) => {
  const { updateEdge } = useActiveDiagram()

  const relationship = edge.data.relationship

  const handleTypeChange = useCallback(
    (event: ChangeEvent<HTMLSelectElement>) => {
      const newType = event.target.value as UMLRelationshipType
      updateEdge(edge.id, { type: newType })
    },
    [edge.id, updateEdge],
  )

  const handleNameChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const newName = event.target.value || undefined
      updateEdge(edge.id, { name: newName })
    },
    [edge.id, updateEdge],
  )

  const handleSourceMultiplicityChange = useCallback(
    (event: ChangeEvent<HTMLSelectElement>) => {
      const newMultiplicity = event.target.value || undefined
      updateEdge(edge.id, { sourceMultiplicity: newMultiplicity })
    },
    [edge.id, updateEdge],
  )

  const handleTargetMultiplicityChange = useCallback(
    (event: ChangeEvent<HTMLSelectElement>) => {
      const newMultiplicity = event.target.value || undefined
      updateEdge(edge.id, { targetMultiplicity: newMultiplicity })
    },
    [edge.id, updateEdge],
  )

  const handleSourceRoleChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const newRole = event.target.value || undefined
      updateEdge(edge.id, { sourceRole: newRole })
    },
    [edge.id, updateEdge],
  )

  const handleTargetRoleChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      const newRole = event.target.value || undefined
      updateEdge(edge.id, { targetRole: newRole })
    },
    [edge.id, updateEdge],
  )

  const handleNavigableChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      updateEdge(edge.id, { isNavigable: event.target.checked })
    },
    [edge.id, updateEdge],
  )

  const handleBidirectionalChange = useCallback(
    (event: ChangeEvent<HTMLInputElement>) => {
      updateEdge(edge.id, { isBidirectional: event.target.checked })
    },
    [edge.id, updateEdge],
  )

  const relationshipTypes: { value: UMLRelationshipType; label: string }[] = [
    { value: 'association', label: 'Association' },
    { value: 'aggregation', label: 'Aggregation' },
    { value: 'composition', label: 'Composition' },
    { value: 'inheritance', label: 'Inheritance' },
    { value: 'realization', label: 'Realization' },
    { value: 'dependency', label: 'Dependency' },
  ]

  return (
    <div className="space-y-4">
      {/* Relationship Type */}
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Relationship Type</label>
        <select
          value={relationship.type}
          onChange={handleTypeChange}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          {relationshipTypes.map(type => (
            <option key={type.value} value={type.value}>
              {type.label}
            </option>
          ))}
        </select>
      </div>

      {/* Relationship Name */}
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
        <input
          type="text"
          value={relationship.name || ''}
          onChange={handleNameChange}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          placeholder="Optional relationship name"
        />
      </div>

      {/* Multiplicities */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Source Multiplicity</label>
          <select
            value={relationship.sourceMultiplicity || ''}
            onChange={handleSourceMultiplicityChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="">None</option>
            {MULTIPLICITY_VALUES.filter(v => v !== '').map(value => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Target Multiplicity</label>
          <select
            value={relationship.targetMultiplicity || ''}
            onChange={handleTargetMultiplicityChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="">None</option>
            {MULTIPLICITY_VALUES.filter(v => v !== '').map(value => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Roles */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Source Role</label>
          <input
            type="text"
            value={relationship.sourceRole || ''}
            onChange={handleSourceRoleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="Optional source role"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Target Role</label>
          <input
            type="text"
            value={relationship.targetRole || ''}
            onChange={handleTargetRoleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            placeholder="Optional target role"
          />
        </div>
      </div>

      {/* Navigation Properties */}
      <div className="space-y-3">
        <h4 className="text-sm font-medium text-gray-700">Navigation</h4>

        <div className="space-y-2">
          <div className="flex items-center">
            <input
              type="checkbox"
              id={`navigable-${edge.id}`}
              checked={relationship.isNavigable || false}
              onChange={handleNavigableChange}
              className="mr-2"
            />
            <label htmlFor={`navigable-${edge.id}`} className="text-sm text-gray-600">
              Navigable relationship
            </label>
          </div>

          <div className="flex items-center">
            <input
              type="checkbox"
              id={`bidirectional-${edge.id}`}
              checked={relationship.isBidirectional || false}
              onChange={handleBidirectionalChange}
              className="mr-2"
            />
            <label htmlFor={`bidirectional-${edge.id}`} className="text-sm text-gray-600">
              Bidirectional navigation
            </label>
          </div>
        </div>
      </div>

      {/* Relationship Info */}
      <div className="bg-gray-50 p-3 rounded-md">
        <h4 className="text-sm font-medium text-gray-700 mb-2">Relationship Info</h4>
        <div className="text-xs text-gray-600 space-y-1">
          <p>
            <strong>Type:</strong> {relationship.type}
          </p>
          <p>
            <strong>From:</strong> {relationship.source}
          </p>
          <p>
            <strong>To:</strong> {relationship.target}
          </p>
          {relationship.name && (
            <p>
              <strong>Name:</strong> {relationship.name}
            </p>
          )}
          {relationship.sourceMultiplicity && (
            <p>
              <strong>Source Multiplicity:</strong> {relationship.sourceMultiplicity}
            </p>
          )}
          {relationship.targetMultiplicity && (
            <p>
              <strong>Target Multiplicity:</strong> {relationship.targetMultiplicity}
            </p>
          )}
          {relationship.sourceRole && (
            <p>
              <strong>Source Role:</strong> {relationship.sourceRole}
            </p>
          )}
          {relationship.targetRole && (
            <p>
              <strong>Target Role:</strong> {relationship.targetRole}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
