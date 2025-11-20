'use client'

import { useEffect } from 'react'

import { useUMLDiagram } from '../hooks/UMLDiagramContext'

export const TestNodes: React.FC = () => {
  const { addNode, diagram } = useUMLDiagram()

  useEffect(() => {
    // Only add nodes if the diagram is empty
    if (diagram.nodes.length === 0) {
      // Add test nodes to demonstrate all UML node types
      setTimeout(() => {
        // Class Node
        addNode({
          elementType: 'class',
          position: { x: 100, y: 100 },
          name: 'Person',
        })

        // Interface Node
        addNode({
          elementType: 'interface',
          position: { x: 350, y: 100 },
          name: 'Drawable',
        })

        // Abstract Class Node
        addNode({
          elementType: 'abstractClass',
          position: { x: 100, y: 300 },
          name: 'Animal',
        })

        // Enum Node
        addNode({
          elementType: 'enumeration',
          position: { x: 350, y: 300 },
          name: 'Color',
        })
      }, 100) // Small delay to ensure canvas is ready
    }
  }, [addNode, diagram.nodes.length])

  return null // This is just a utility component, renders nothing
}
