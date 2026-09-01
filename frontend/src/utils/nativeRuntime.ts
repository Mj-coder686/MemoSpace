import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { Keyboard, KeyboardResize } from '@capacitor/keyboard'
import { SplashScreen } from '@capacitor/splash-screen'
import { StatusBar, Style } from '@capacitor/status-bar'
import type { Router } from 'vue-router'

export const initializeNativeRuntime = async (router: Router) => {
  if (!Capacitor.isNativePlatform()) return
  document.documentElement.classList.add('native-app')
  await StatusBar.setOverlaysWebView({ overlay: false })
  await StatusBar.setStyle({ style: Style.Dark })
  await StatusBar.setBackgroundColor({ color: '#f5f2ec' })
  await Keyboard.setResizeMode({ mode: KeyboardResize.Native })
  await Keyboard.addListener('keyboardWillShow', () => document.documentElement.classList.add('keyboard-open'))
  await Keyboard.addListener('keyboardWillHide', () => document.documentElement.classList.remove('keyboard-open'))
  await App.addListener('backButton', ({ canGoBack }) => {
    if (canGoBack && router.currentRoute.value.path !== '/home') router.back()
    else App.exitApp()
  })
  await SplashScreen.hide()
}
