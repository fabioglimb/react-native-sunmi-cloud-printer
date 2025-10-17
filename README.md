# react-native-sunmi-cloud-printer

React Native Sunmi Cloud Printer SDK:
This SDK wraps the Sunmi native SDKs and expose them to React Native.

- [iOS - SMPrinterSDKProject V1.6.6](https://developer.sunmi.com/docs/en-US/xeghjk491/fdfeghjk535)
- [Android - externalprinterlibrary2-1.0.13](https://developer.sunmi.com/docs/en-US/xeghjk491/rxceghjk502)

# API documentation

- [Documentation for the main branch](https://github.com/expo/expo/blob/main/docs/pages/versions/unversioned/sdk/react-native-sunmi-cloud-printer.md)
- [Documentation for the latest stable release](https://docs.expo.dev/versions/latest/sdk/react-native-sunmi-cloud-printer/)

# Installation in managed Expo projects

For [managed](https://docs.expo.dev/archive/managed-vs-bare/) Expo projects, please follow the installation instructions in the [API documentation for the latest stable release](#api-documentation). If you follow the link and there is no documentation available then this library is not yet usable within managed projects &mdash; it is likely to be included in an upcoming Expo SDK release.

# Installation in bare React Native projects

For bare React Native projects, you must ensure that you have [installed and configured the `expo` package](https://docs.expo.dev/bare/installing-expo-modules/) before continuing.

### Add the package to your npm dependencies

```bash
npm install react-native-sunmi-cloud-printer
```

### Configure for iOS

Run `npx pod-install` after installing the npm package.

```bash
npx pod-install
```

### Configure for Android

**⚠️ IMPORTANT:** You must add the Expo config plugin to your `app.json` or `app.config.js`. This is required to automatically configure the Android Gradle files to include the Sunmi AAR library.

Add to your `app.json`:

```json
{
  "expo": {
    "plugins": [
      "react-native-sunmi-cloud-printer"
    ]
  }
}
```

Or in `app.config.js`:

```js
export default {
  plugins: [
    'react-native-sunmi-cloud-printer'
  ]
};
```

The plugin will automatically:
- ✅ Add the AAR library repository to `settings.gradle`
- ✅ Add the AAR dependency to `app/build.gradle`
- ✅ Work with both local builds and EAS builds
- ✅ Support Gradle 8.13+ (required for Expo SDK 52+)

After adding the plugin, run:

```bash
npx expo prebuild --clean
```

### Troubleshooting

If you encounter build errors:

1. **"Could not find :externalprinterlibrary2-1.0.13-release:"**
   - Make sure the plugin is added to your `app.json`
   - Run `npx expo prebuild --clean` to regenerate native files
   - Check that `settings.gradle` contains the flatDir entry

2. **"Failed to resolve plugin"**
   - Ensure you have version `0.4.3` or later
   - Run `npm install react-native-sunmi-cloud-printer@latest`
   - Clear your `node_modules` and reinstall: `rm -rf node_modules && npm install`

# Contributing

Contributions are very welcome! Please refer to guidelines described in the [contributing guide](https://github.com/expo/expo#contributing).
