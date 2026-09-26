type OverlayEntry = { id: symbol; close: () => void }

const stack: OverlayEntry[] = []

export const registerOverlay = (close: () => void) => {
  const entry = { id: Symbol('overlay'), close }
  stack.push(entry)
  return () => {
    const index = stack.findIndex((item) => item.id === entry.id)
    if (index >= 0) stack.splice(index, 1)
  }
}

export const closeTopOverlay = () => {
  const entry = stack[stack.length - 1]
  if (!entry) return false
  entry.close()
  return true
}

export const hasOpenOverlay = () => stack.length > 0
