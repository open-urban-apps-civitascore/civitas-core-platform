export * from '@tanstack/react-table'

declare module '@tanstack/react-table' {
  interface ColumnMeta {
    /** CSS Styles für Header- und Zellen */
    style?: React.CSSProperties
    /** Optionaler className */
    className?: string
  }
}
