import { Geolocation } from '@capacitor/geolocation'
import { isNativeApp } from './serverConnection'

export type DevicePosition = {
  latitude: number
  longitude: number
  accuracy: number
}

const fromBrowser = () => new Promise<DevicePosition>((resolve, reject) => {
  if (!navigator.geolocation) {
    reject(new Error('当前浏览器不支持定位功能'))
    return
  }
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => resolve({ latitude: coords.latitude, longitude: coords.longitude, accuracy: coords.accuracy }),
    (error) => reject(new Error(error.code === error.PERMISSION_DENIED
      ? '定位权限已被拒绝，请在浏览器或系统设置中允许后重试'
      : error.code === error.TIMEOUT ? '获取位置超时，请到开阔位置后重试' : '暂时无法获取当前位置')),
    { enableHighAccuracy: true, timeout: 15_000, maximumAge: 30_000 },
  )
})

export const getCurrentDevicePosition = async (): Promise<DevicePosition> => {
  if (!isNativeApp()) return fromBrowser()
  const current = await Geolocation.checkPermissions()
  const permission = current.location === 'granted' ? current : await Geolocation.requestPermissions({ permissions: ['location'] })
  if (permission.location !== 'granted') throw new Error('定位权限未开启，请在手机设置中允许拾光空间访问位置')
  const { coords } = await Geolocation.getCurrentPosition({ enableHighAccuracy: true, timeout: 15_000, maximumAge: 30_000 })
  return { latitude: coords.latitude, longitude: coords.longitude, accuracy: coords.accuracy }
}

export const coordinateLabel = (position: Pick<DevicePosition, 'latitude' | 'longitude'>) =>
  `${position.latitude.toFixed(6)}, ${position.longitude.toFixed(6)}`
