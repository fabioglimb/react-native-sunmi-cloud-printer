/**
 * Expo Config Plugin for react-native-sunmi-cloud-printer
 * 
 * This module now uses com.sunmi:external-printerlibrary2 from Maven Central,
 * so no custom repository setup is needed.
 * 
 * The library is automatically resolved via Gradle's standard dependency management.
 */

const withSunmiCloudPrinter = (config) => {
  console.log('[Sunmi] Using external-printerlibrary2 from Maven Central (com.sunmi:external-printerlibrary2:1.0.14)');
  return config;
};

module.exports = withSunmiCloudPrinter;
