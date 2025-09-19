export * from '@tanstack/react-table'

declare module '@tanstack/react-table' {
  interface ColumnMeta<TData, TValue> {
    /** CSS Styles für Header- und Zellen */
    style?: React.CSSProperties
    /** Optionaler className */
    className?: string
  }
}
