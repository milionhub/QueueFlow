/**
 * The shape of a translation of `T` (the English messages): the same keys,
 * each with its own words. A key missing from a translation, or one it has
 * that English has not, is a type error.
 */
export type Messages<T> = { [K in keyof T]: T[K] extends string ? string : Messages<T[K]> }
