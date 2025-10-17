/**
 * Expo Config Plugin for react-native-sunmi-cloud-printer
 * 
 * This module now uses the Sunmi Printer Library from Maven Central,
 * so no custom repository setup is needed.
 * 
 * This plugin is kept for future customizations if needed.
 */

/**
 * Main plugin - currently a pass-through as Maven handles dependencies
 */
const withSunmiCloudPrinter = (config) => {
  // The library is now available via Maven Central (com.sunmi:printerlibrary:1.0.14)
  // No custom configuration needed
  console.log('[Sunmi] Using Sunmi Printer Library from Maven Central');
  return config;
};

module.exports = withSunmiCloudPrinter;
