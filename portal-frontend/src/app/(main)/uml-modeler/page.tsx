'use client'
import '@xyflow/react/dist/style.css'

import { UMLModelerLayout } from './components/layout/UMLModelerLayout'

const UmlModelerPage = () => {
  return (
    <div className="flex h-full w-full flex-1 flex-col gap-4 p-4">
      <div className="h-full w-full rounded-xl border bg-background overflow-hidden">
        {/* <UMLCanvasProvider className="rounded-xl" /> */}
        <UMLModelerLayout className="rounded-xl" />
      </div>
    </div>
  )
}

export default UmlModelerPage
