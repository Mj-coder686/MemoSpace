import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.memospace.app',
  appName: '拾光空间',
  webDir: 'dist',
  backgroundColor: '#f5f2ec',
  loggingBehavior: 'debug',
  server: {
    androidScheme: 'https',
    cleartext: true,
  },
  android: {
    allowMixedContent: true,
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
