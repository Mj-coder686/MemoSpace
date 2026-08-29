import http from '../api/http'

export type Appearance = {
  background_color?: string
  background_file_id?: number | null
  background_brightness?: number
  background_overlay?: number
}

let activeImageUrl = ''

export const applyAppearance = (appearance:Appearance, imageUrl = '') => {
  const root = document.documentElement
  root.style.setProperty('--user-background', appearance.background_color || '#f5f2ec')
  root.style.setProperty('--user-bg-brightness', `${appearance.background_brightness ?? 100}%`)
  root.style.setProperty('--user-bg-overlay', `${(appearance.background_overlay ?? 0) / 100}`)
  root.style.setProperty('--user-bg-image', imageUrl ? `url("${imageUrl}")` : 'none')
  localStorage.setItem('memospace_appearance', JSON.stringify(appearance))
}

export const loadAppearance = async () => {
  const { data } = await http.get('/users/me/appearance')
  if (activeImageUrl) URL.revokeObjectURL(activeImageUrl)
  activeImageUrl = ''
  if (data.background_file_id) {
    const response = await http.get(`/files/${data.background_file_id}/content`, { responseType:'blob' })
    activeImageUrl = URL.createObjectURL(response.data)
  }
  applyAppearance(data, activeImageUrl)
  return data as Appearance
}

export const applyCachedAppearance = () => {
  try {
    const cached = JSON.parse(localStorage.getItem('memospace_appearance') || '{}')
    applyAppearance(cached)
  } catch { applyAppearance({}) }
}

export const imageLuminance = async (file:File) => {
  const bitmap = await createImageBitmap(file)
  const canvas = document.createElement('canvas'); canvas.width=32; canvas.height=32
  const context = canvas.getContext('2d', { willReadFrequently:true })
  if (!context) return .5
  context.drawImage(bitmap,0,0,32,32);bitmap.close()
  const pixels=context.getImageData(0,0,32,32).data;let total=0;let count=0
  for(let i=0;i<pixels.length;i+=4){if(pixels[i+3]<20)continue;total+=(.2126*pixels[i]+.7152*pixels[i+1]+.0722*pixels[i+2])/255;count++}
  return count ? total/count : .5
}
