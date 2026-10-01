const MAX_EDGE = 2560
const COMPRESS_FROM_BYTES = 900 * 1024
const JPEG_QUALITY = 0.84

const canvasBlob = (canvas: HTMLCanvasElement) => new Promise<Blob | null>((resolve) => {
  canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY)
})

export const optimizeUploadImage = async (file: File): Promise<File> => {
  if (!file.type.startsWith('image/') || file.type === 'image/gif' || file.type === 'image/webp') return file
  const objectUrl = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.decoding = 'async'
    image.src = objectUrl
    await image.decode()
    const longest = Math.max(image.naturalWidth, image.naturalHeight)
    if (file.size < COMPRESS_FROM_BYTES && longest <= MAX_EDGE) return file
    const scale = Math.min(1, MAX_EDGE / longest)
    const width = Math.max(1, Math.round(image.naturalWidth * scale))
    const height = Math.max(1, Math.round(image.naturalHeight * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d', { alpha: false })
    if (!context) return file
    context.fillStyle = '#f7f4ef'
    context.fillRect(0, 0, width, height)
    context.drawImage(image, 0, 0, width, height)
    const compressed = await canvasBlob(canvas)
    if (!compressed || compressed.size >= file.size) return file
    const base = file.name.replace(/\.[^.]+$/, '') || 'memory'
    return new File([compressed], `${base}.jpg`, { type: 'image/jpeg', lastModified: file.lastModified })
  } catch {
    return file
  } finally {
    URL.revokeObjectURL(objectUrl)
  }
}
