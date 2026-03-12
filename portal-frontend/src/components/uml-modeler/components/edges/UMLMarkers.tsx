'use client'

import React from 'react'

/**
 * SVG marker definitions for UML relationship arrows
 * Following UML 2.5 specification for visual representation
 */
export const UMLMarkers: React.FC = () => (
  <svg style={{ position: 'absolute', top: 0, left: 0 }} width="0" height="0">
    <defs>
      {/* Inheritance - Hollow triangle */}
      <marker
        id="inheritance"
        viewBox="0 0 10 10"
        refX="9"
        refY="3"
        markerUnits="strokeWidth"
        markerWidth="10"
        markerHeight="10"
        orient="auto"
      >
        <path d="M0,0 L0,6 L9,3 z" fill="white" stroke="#333333" strokeWidth="1" />
      </marker>

      {/* Realization - Hollow triangle (same as inheritance but for dashed lines) */}
      <marker
        id="realization"
        viewBox="0 0 10 10"
        refX="9"
        refY="3"
        markerUnits="strokeWidth"
        markerWidth="10"
        markerHeight="10"
        orient="auto"
      >
        <path d="M0,0 L0,6 L9,3 z" fill="white" stroke="#333333" strokeWidth="1" />
      </marker>

      {/* Association - Simple arrow */}
      <marker
        id="association"
        viewBox="0 0 10 10"
        refX="9"
        refY="3"
        markerUnits="strokeWidth"
        markerWidth="10"
        markerHeight="10"
        orient="auto"
      >
        <path d="M0,0 L0,6 L9,3 z" fill="#333333" stroke="#333333" strokeWidth="1" />
      </marker>

      {/* Aggregation - Hollow diamond */}
      <marker
        id="aggregation"
        viewBox="0 0 10 10"
        refX="9"
        refY="3"
        markerUnits="strokeWidth"
        markerWidth="12"
        markerHeight="10"
        orient="auto"
      >
        <path d="M0,3 L3,0 L9,3 L3,6 z" fill="white" stroke="#333333" strokeWidth="1" />
      </marker>

      {/* Composition - Filled diamond */}
      <marker
        id="composition"
        viewBox="0 0 10 10"
        refX="9"
        refY="3"
        markerUnits="strokeWidth"
        markerWidth="12"
        markerHeight="10"
        orient="auto"
      >
        <path d="M0,3 L3,0 L9,3 L3,6 z" fill="#333333" stroke="#333333" strokeWidth="1" />
      </marker>

      {/* Dependency - Simple arrow (same as association) */}
      <marker
        id="dependency"
        viewBox="0 0 10 10"
        refX="9"
        refY="3"
        markerUnits="strokeWidth"
        markerWidth="10"
        markerHeight="10"
        orient="auto"
      >
        <path d="M0,0 L0,6 L9,3 z" fill="#333333" stroke="#333333" strokeWidth="1" />
      </marker>
    </defs>
  </svg>
)
