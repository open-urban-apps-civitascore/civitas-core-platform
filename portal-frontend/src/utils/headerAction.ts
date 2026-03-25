import { JSX } from 'react'

export const getHeaderAction = (opts: {
  isReadOnly: boolean
  canUpdate: boolean
  editButton: JSX.Element
  saveExitButtons: JSX.Element
}): JSX.Element | undefined => {
  if (!opts.isReadOnly) return opts.saveExitButtons
  if (opts.canUpdate) return opts.editButton
  return undefined
}
