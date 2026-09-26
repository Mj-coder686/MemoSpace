import { readonly, ref } from 'vue'

export type ToastTone = 'info' | 'success' | 'warning' | 'danger'
export type ToastInput = {
  title: string
  message?: string
  tone?: ToastTone
  duration?: number
  actionLabel?: string
  onAction?: () => void | Promise<void>
}
export type ToastItem = Required<Pick<ToastInput, 'title' | 'tone' | 'duration'>> & Omit<ToastInput, 'title' | 'tone' | 'duration'> & { id: number }

const visible = ref<ToastItem[]>([])
const queued: ToastItem[] = []
const timers = new Map<number, ReturnType<typeof setTimeout>>()
let nextId = 1

const schedule = (toast: ToastItem) => {
  if (toast.duration <= 0) return
  timers.set(toast.id, setTimeout(() => dismissToast(toast.id), toast.duration))
}

const revealNext = () => {
  const next = queued.shift()
  if (!next) return
  visible.value.push(next)
  schedule(next)
}

export const dismissToast = (id: number) => {
  const timer = timers.get(id)
  if (timer) clearTimeout(timer)
  timers.delete(id)
  visible.value = visible.value.filter((item) => item.id !== id)
  revealNext()
}

export const pushToast = (input: ToastInput) => {
  const toast: ToastItem = {
    id: nextId++,
    title: input.title,
    message: input.message,
    tone: input.tone || 'info',
    duration: input.duration ?? 4000,
    actionLabel: input.actionLabel,
    onAction: input.onAction,
  }
  if (visible.value.length < 3) {
    visible.value.push(toast)
    schedule(toast)
  } else {
    queued.push(toast)
  }
  return toast.id
}

export function useToast() {
  return { toasts: readonly(visible), pushToast, dismissToast }
}
