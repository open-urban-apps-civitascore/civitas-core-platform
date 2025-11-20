'use client'

import React, { useCallback } from 'react'

import { createUMLNode } from '../constants/elementTemplates'
import { useUMLDiagram } from '../hooks/UMLDiagramContext'
import type { UMLEdge, UMLNode } from '../types/diagram'
import type { UMLRelationshipType } from '../types/uml'

/**
 * Test component to demonstrate UML relationship edges
 * Creates sample nodes and connects them with different relationship types
 */
export const TestEdges: React.FC = () => {
  const { diagram, dispatch } = useUMLDiagram()

  const createTestDiagram = useCallback(() => {
    console.log('Creating test diagram with UML edges...')

    // Create test nodes
    const classNode = createUMLNode('class', { x: 100, y: 100 }, 'TestClass')
    const interfaceNode = createUMLNode('interface', { x: 400, y: 100 }, 'TestInterface')
    const abstractClassNode = createUMLNode('abstractClass', { x: 700, y: 100 }, 'TestAbstractClass')
    const enumNode = createUMLNode('enumeration', { x: 100, y: 300 }, 'TestEnum')
    const childClassNode = createUMLNode('class', { x: 400, y: 300 }, 'ChildClass')
    const compositeClassNode = createUMLNode('class', { x: 700, y: 300 }, 'CompositeClass')

    const testNodes: UMLNode[] = [
      classNode,
      interfaceNode,
      abstractClassNode,
      enumNode,
      childClassNode,
      compositeClassNode,
    ]

    // Create test edges with different relationship types
    const createTestEdge = (
      source: UMLNode,
      target: UMLNode,
      relationship: UMLRelationshipType,
      name?: string,
    ): UMLEdge => ({
      id: crypto.randomUUID(),
      type: relationship,
      source: source.id,
      target: target.id,
      data: {
        relationship: {
          id: crypto.randomUUID(),
          type: relationship,
          source: source.id,
          target: target.id,
          name,
          sourceMultiplicity: relationship === 'association' ? '1' : undefined,
          targetMultiplicity: relationship === 'association' ? '*' : undefined,
        },
        label: name || '',
        isSelected: false,
        isDirty: false,
      },
    })

    const testEdges: UMLEdge[] = [
      // Inheritance: ChildClass inherits from TestClass
      createTestEdge(childClassNode, classNode, 'inheritance'),

      // Realization: TestClass implements TestInterface
      createTestEdge(classNode, interfaceNode, 'realization'),

      // Association: TestClass has association with TestEnum
      createTestEdge(classNode, enumNode, 'association', 'uses'),

      // Aggregation: CompositeClass aggregates ChildClass
      createTestEdge(compositeClassNode, childClassNode, 'aggregation', 'contains'),

      // Composition: CompositeClass composed of TestClass
      createTestEdge(compositeClassNode, classNode, 'composition', 'owns'),

      // Dependency: TestInterface depends on TestAbstractClass
      createTestEdge(interfaceNode, abstractClassNode, 'dependency', 'depends on'),
    ]

    // Update diagram with test data
    dispatch({ type: 'SET_NODES', payload: testNodes })
    dispatch({ type: 'SET_EDGES', payload: testEdges })

    console.log('Test diagram created with', testNodes.length, 'nodes and', testEdges.length, 'edges')
  }, [dispatch])

  const clearDiagram = useCallback(() => {
    dispatch({ type: 'RESET_DIAGRAM' })
  }, [dispatch])

  return (
    <div className="flex gap-2 p-4 bg-white border-b border-gray-200">
      <button
        onClick={createTestDiagram}
        className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
      >
        Create Test Relationships
      </button>

      <button
        onClick={clearDiagram}
        className="px-4 py-2 bg-gray-500 text-white rounded hover:bg-gray-600 transition-colors"
      >
        Clear Diagram
      </button>

      <div className="flex-1 px-4 text-sm text-gray-600">
        <strong>Phase 3 Test:</strong> Click &quot;Create Test Relationships&quot; to see all 6 UML edge types:
        <span className="ml-2">
          Inheritance (hollow triangle) • Realization (dashed triangle) • Association (arrow) • Aggregation (hollow
          diamond) • Composition (filled diamond) • Dependency (dashed arrow)
        </span>
      </div>

      <div className="text-sm text-gray-500">
        Nodes: {diagram.nodes.length} | Edges: {diagram.edges.length}
      </div>
    </div>
  )
}
