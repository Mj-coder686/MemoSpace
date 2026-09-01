import { Camera, MediaTypeSelection } from '@capacitor/camera'
import { Capacitor } from '@capacitor/core'

const canceled = (error: unknown) => {
  const value = error as { code?: string; message?: string }
  return value?.code === 'OS-PLUG-CAMR-0006' || /cancel/i.test(value?.message || '')
}

export const chooseNativeImage = async (prefix: string) => {
  if (!Capacitor.isNativePlatform()) return null

  try {
    const { results } = await Camera.chooseFromGallery({
      mediaType: MediaTypeSelection.Photo,
      allowMultipleSelection: false,
      quality: 90,
      targetWidth: 2048,
      targetHeight: 2048,
      correctOrientation: true,
      includeMetadata: true,
    })
    const image = results[0]
    if (!image?.webPath) return null

    const response = await fetch(image.webPath)
    if (!response.ok) throw new Error('无法读取刚刚选择的图片')
    const blob = await response.blob()
    const format = (image.metadata?.format || blob.type.split('/')[1] || 'jpg').replace('jpeg', 'jpg')
    const mimeType = blob.type || (format === 'jpg' ? 'image/jpeg' : `image/${format}`)
    return new File([blob], `${prefix}-${Date.now()}.${format}`, { type: mimeType })
  } catch (error) {
    if (canceled(error)) return null
    throw error
  }
}
