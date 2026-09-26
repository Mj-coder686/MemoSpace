import { App } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { Keyboard, KeyboardResize } from '@capacitor/keyboard'
import { SplashScreen } from '@capacitor/splash-screen'
import { StatusBar, Style } from '@capacitor/status-bar'
import type { Router } from 'vue-router'
import { closeTopOverlay } from '../composables/useOverlayStack'

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
  const systemDark = window.matchMedia('(prefers-color-scheme: dark)')
  const applyNativeTheme = async () => {
    const explicitMode = document.documentElement.dataset.mode
    const dark = explicitMode === 'dark' || (!explicitMode && systemDark.matches)
    document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')?.setAttribute('content', dark ? '#181716' : '#faf7f2')
    await safely('configure status bar style', () => StatusBar.setStyle({ style: dark ? Style.Light : Style.Dark }))
    await safely('configure status bar color', () => StatusBar.setBackgroundColor({ color: dark ? '#181716' : '#faf7f2' }))
  }
  await applyNativeTheme()
  const themeObserver = new MutationObserver(() => { void applyNativeTheme() })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-mode'] })
  systemDark.addEventListener('change', () => { void applyNativeTheme() })
  await safely('configure keyboard', () => Keyboard.setResizeMode({ mode: KeyboardResize.Native }))
  await safely('listen for keyboard show', () => Keyboard.addListener('keyboardWillShow', ({ keyboardHeight }) => {
    document.documentElement.classList.add('keyboard-open')
    document.documentElement.style.setProperty('--keyboard-offset', `${keyboardHeight}px`)
  }))
  await safely('listen for keyboard hide', () => Keyboard.addListener('keyboardWillHide', () => {
    document.documentElement.classList.remove('keyboard-open')
    document.documentElement.style.setProperty('--keyboard-offset', '0px')
  }))
  await safely('listen for Android back button', () => App.addListener('backButton', ({ canGoBack }) => {
    if (closeTopOverlay()) return
    if (canGoBack && router.currentRoute.value.path !== '/home') router.back()
    else App.exitApp()
  }))
}
