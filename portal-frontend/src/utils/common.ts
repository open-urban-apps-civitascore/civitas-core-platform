export const setFocus = (id: string) => {
  requestAnimationFrame(() => {
    const element = document.getElementById(id)
    element?.focus()
  })
}
