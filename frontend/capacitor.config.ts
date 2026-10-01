import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.memospace.app',
  appName: '拾光空间',
  webDir: 'dist',
  backgroundColor: '#f5f2ec',
  loggingBehavior: 'none',
  server: {
    androidScheme: 'https',
    cleartext: false,
  },
  android: {
    allowMixedContent: false,
    backgroundColor: '#f5f2ec',
  },
  plugins: {
    CapacitorHttp: { enabled: true },
    // Capacitor LIGHT means dark system icons, which is the readable launch
    // state on MemoSpace's default light canvas. Runtime theming takes over
    // once the WebView has loaded.
    StatusBar: { style: 'LIGHT', backgroundColor: '#faf7f2', overlaysWebView: false },
    PushNotifications: { presentationOptions: ['sound', 'alert', 'banner', 'list'] },
    Keyboard: { resize: 'native' },
    SplashScreen: { launchAutoHide: true, launchShowDuration: 1200, backgroundColor: '#f5f2ec' },
  },
}

export default config
