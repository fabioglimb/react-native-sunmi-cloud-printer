/**
 * Expo Config Plugin for react-native-sunmi-cloud-printer
 * 
 * This library uses com.sunmi:external-printerlibrary2 and com.sunmi:printerlibrary from Maven Central.
 * No custom configuration is needed - dependencies are resolved automatically via Gradle.
 * 
 * Required Android Permissions (add these to your app.json/app.config.json):
 * - android.permission.BLUETOOTH
 * - android.permission.BLUETOOTH_ADMIN
 * - android.permission.BLUETOOTH_CONNECT
 * - android.permission.ACCESS_WIFI_STATE
 * - android.permission.CHANGE_WIFI_STATE
 * - android.permission.ACCESS_NETWORK_STATE
 * - android.permission.CHANGE_NETWORK_STATE
 * - android.permission.ACCESS_FINE_LOCATION
 * - android.permission.ACCESS_COARSE_LOCATION
 */

const withSunmiCloudPrinter = (config) => {
  return config;
};

module.exports = withSunmiCloudPrinter;
