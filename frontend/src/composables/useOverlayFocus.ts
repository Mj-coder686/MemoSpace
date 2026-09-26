import { nextTick, onBeforeUnmount, ref, type Ref, watch } from 'vue'
import { registerOverlay } from './useOverlayStack'

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function useOverlayFocus(open: Ref<boolean>, requestClose: () => void, canEscape: () => boolean) {
  const surfaceRef = ref<HTMLElement | null>(null)
  let previousFocus: HTMLElement | null = null
  let previousOverflow = ''
  let unregisterOverlay: (() => void) | undefined

  const focusable = () => Array.from(surfaceRef.value?.querySelectorAll<HTMLElement>(focusableSelector) || [])

  const onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && canEscape()) {
      event.preventDefault()
      requestClose()
      return
    }
    if (event.key !== 'Tab') return
    const items = focusable()
    if (!items.length) {
      event.preventDefault()
      surfaceRef.value?.focus()
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  const deactivate = () => {
    document.removeEventListener('keydown', onKeydown)
    unregisterOverlay?.()
    unregisterOverlay = undefined
    document.body.style.overflow = previousOverflow
    previousFocus?.focus()
    previousFocus = null
  }

  watch(open, async (active) => {
    if (!active) {
      deactivate()
      return
    }
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    unregisterOverlay = registerOverlay(requestClose)
    document.addEventListener('keydown', onKeydown)
    await nextTick()
    const preferred = surfaceRef.value?.querySelector<HTMLElement>('[autofocus]')
    ;(preferred || focusable()[0] || surfaceRef.value)?.focus()
  }, { flush: 'post' })

  onBeforeUnmount(() => {
    if (open.value) deactivate()
  })

  return { surfaceRef }
}
