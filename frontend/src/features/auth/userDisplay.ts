import { i18n } from '../../i18n'
import type { UserRole } from './types'

/**
 * How a role is named in the interface language; the role itself (ADMIN,
 * MEMBER) is never changed. The component showing it uses useTranslation,
 * so it renders again when the language changes.
 */
export function roleLabel(role: UserRole): string {
  return i18n.t(`common:roles.${role}`)
}

const LETTER_OR_NUMBER = /[\p{L}\p{N}]/u
const PICTOGRAPH = /\p{Extended_Pictographic}/u

/** The first grapheme of `text` (an emoji with modifiers stays whole), or "". */
function firstGrapheme(text: string): string {
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    const segment = new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text)[Symbol.iterator]().next()
    return segment.done ? '' : segment.value.segment
  }
  return [...text][0] ?? ''
}

/**
 * Up to two initials from a real name, for an avatar: the first letter or
 * digit of the first and of the last word that contain one, so quotes,
 * brackets and symbols are skipped (`"Quoted" & 'amp'` → "QA",
 * `<img src=x>` → "IX"). A name with no letter or digit at all shows its
 * first emoji, if it starts with one; anything else (symbols only, empty)
 * shows "?".
 */
export function initials(name: string): string {
  const words = name
    .trim()
    .split(/\s+/)
    .filter((word) => LETTER_OR_NUMBER.test(word))
  if (words.length === 0) {
    const first = firstGrapheme(name.trim())
    return PICTOGRAPH.test(first) ? first : '?'
  }
  const picked = words.length > 1 ? [words[0], words[words.length - 1]] : [words[0]]
  return picked.map((word) => (word.match(LETTER_OR_NUMBER)?.[0] ?? '').toLocaleUpperCase()).join('')
}

/**
 * Eight restrained tints (background and text), each above 4.5:1. A person
 * keeps the same one everywhere: it is picked from their stable user id,
 * never from their name, which can repeat.
 */
const AVATAR_TINTS = [
  'bg-[#e8ebfb] text-[#3341b3]',
  'bg-[#e3f1ea] text-[#16603a]',
  'bg-[#f6ecdc] text-[#8a4a0b]',
  'bg-[#efe9fb] text-[#5b2bb5]',
  'bg-[#e2eff4] text-[#155e75]',
  'bg-[#f7e6ea] text-[#9f1239]',
  'bg-[#ebeef2] text-[#3f4a5a]',
  'bg-[#eaf0dc] text-[#3f5f12]',
] as const

/** A small stable hash (FNV-1a) of any string. */
export function stableHash(value: string): number {
  let hash = 0x811c9dc5
  for (let index = 0; index < value.length; index++) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }
  return hash >>> 0
}

export function avatarTint(seed: string): string {
  return AVATAR_TINTS[stableHash(seed) % AVATAR_TINTS.length]
}
