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
    StatusBar: { style: 'DARK', backgroundColor: '#f5f2ec', overlaysWebView: false },
    Keyboard: { resize: 'native' },
    SplashScreen: { launchAutoHide: true, launchShowDuration: 1200, backgroundColor: '#f5f2ec' },
  },
}

export default config
