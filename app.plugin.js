/**
 * Expo Config Plugin for react-native-sunmi-cloud-printer
 * 
 * This plugin adds the necessary Android permissions for:
 * - Bluetooth communication with Sunmi printers
 * - WiFi configuration of Sunmi cloud printers
 * 
 * The library uses com.sunmi:external-printerlibrary2 from Maven Central.
 */

const { withAndroidManifest } = require('@expo/config-plugins');

const withSunmiCloudPrinter = (config) => {
  // Add Android permissions for Sunmi printer operations
  config = withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults;
    
    // Ensure manifest has the android namespace
    if (!androidManifest.$) {
      androidManifest.$ = {};
    }
    if (!androidManifest.$['xmlns:android']) {
      androidManifest.$['xmlns:android'] = 'http://schemas.android.com/apk/res/android';
    }
    
    // Ensure uses-permission array exists
    if (!androidManifest['uses-permission']) {
      androidManifest['uses-permission'] = [];
    }
    
    // Define required permissions
    const requiredPermissions = [
      // Bluetooth permissions for printer communication
      'android.permission.BLUETOOTH',
      'android.permission.BLUETOOTH_ADMIN',
      'android.permission.BLUETOOTH_CONNECT',
      
      // WiFi permissions for printer WiFi configuration
      'android.permission.ACCESS_WIFI_STATE',
      'android.permission.CHANGE_WIFI_STATE',
      'android.permission.ACCESS_NETWORK_STATE',
      'android.permission.CHANGE_NETWORK_STATE',
      
      // Location permissions (required for WiFi operations on Android 6+)
      'android.permission.ACCESS_FINE_LOCATION',
      'android.permission.ACCESS_COARSE_LOCATION',
    ];
    
    // Add permissions if they don't already exist
    for (const permission of requiredPermissions) {
      const exists = androidManifest['uses-permission'].some(
        (perm) => perm.$['android:name'] === permission
      );
      
      if (!exists) {
        androidManifest['uses-permission'].push({
          $: {
            'android:name': permission,
          },
        });
      }
    }
    
    return config;
  });
  
  return config;
};

module.exports = withSunmiCloudPrinter;
