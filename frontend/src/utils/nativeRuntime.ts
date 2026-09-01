import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { Keyboard, KeyboardResize } from '@capacitor/keyboard'
import { SplashScreen } from '@capacitor/splash-screen'
import { StatusBar, Style } from '@capacitor/status-bar'
import type { Router } from 'vue-router'

export const initializeNativeRuntime = async (router: Router) => {
  if (!Capacitor.isNativePlatform()) return
  document.documentElement.classList.add('native-app')

  const safely = async (name: string, action: () => Promise<unknown>) => {
    try {
      await action()
    } catch (error) {
      console.warn(`[MemoSpace native] ${name} failed`, error)
    }
  }

  // Hide first so one unsupported native capability can never leave the app on a blank launch screen.
  await safely('hide splash screen', () => SplashScreen.hide())
  await safely('configure status bar overlay', () => StatusBar.setOverlaysWebView({ overlay: false }))
  await safely('configure status bar style', () => StatusBar.setStyle({ style: Style.Dark }))
  await safely('configure status bar color', () => StatusBar.setBackgroundColor({ color: '#f5f2ec' }))
  await safely('configure keyboard', () => Keyboard.setResizeMode({ mode: KeyboardResize.Native }))
  await safely('listen for keyboard show', () => Keyboard.addListener('keyboardWillShow', () => document.documentElement.classList.add('keyboard-open')))
  await safely('listen for keyboard hide', () => Keyboard.addListener('keyboardWillHide', () => document.documentElement.classList.remove('keyboard-open')))
  await safely('listen for Android back button', () => App.addListener('backButton', ({ canGoBack }) => {
    if (canGoBack && router.currentRoute.value.path !== '/home') router.back()
    else App.exitApp()
  }))
}
