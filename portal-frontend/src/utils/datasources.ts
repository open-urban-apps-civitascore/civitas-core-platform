import { RowSelectionState } from '@tanstack/react-table'

export const getSelectedDatastructureVersion = (selection: RowSelectionState) => {
  const selectedEntries = Object.entries(selection).filter(([, isSelected]) => isSelected)

  if (selectedEntries.length !== 1) {
    if (selectedEntries.length > 1) {
      console.error('Invalid datastructure version selection: expected exactly one selected row.', {
        selection,
      })
    }
    return { datastructureId: null, versionId: null }
  }

  const [selectedId] = selectedEntries[0]
  const [datastructureId, versionId, ...rest] = selectedId.split('/')

  if (!datastructureId || !versionId || rest.length > 0) {
    console.error('Invalid datastructure version row id format.', {
      selectedId,
      selection,
    })
    return { datastructureId: null, versionId: null }
  }

  return { datastructureId, versionId }
}
