const { withDangerousMod, withAppBuildGradle } = require('@expo/config-plugins');
const path = require('path');
const fs = require('fs');

/**
 * Modify settings.gradle to add flatDir repository
 * This is required for Gradle 8.13+ which enforces dependencyResolutionManagement
 */
const withSunmiSettingsGradle = (config) => {
  return withDangerousMod(config, [
    'android',
    async (config) => {
      const settingsGradlePath = path.join(
        config.modRequest.platformProjectRoot,
        'settings.gradle'
      );
      
      if (!fs.existsSync(settingsGradlePath)) {
        console.warn('[Sunmi] settings.gradle not found, skipping repository setup');
        return config;
      }
      
      let contents = fs.readFileSync(settingsGradlePath, 'utf-8');
      
      // Check if already added
      if (contents.includes('react-native-sunmi-cloud-printer/android/libs')) {
        return config;
      }
      
      // Add to dependencyResolutionManagement > repositories
      const drmRepoRegex = /(dependencyResolutionManagement\s*\{[\s\S]*?repositories\s*\{)/;
      
      if (drmRepoRegex.test(contents)) {
        contents = contents.replace(
          drmRepoRegex,
          `$1
        flatDir {
            dirs "../node_modules/react-native-sunmi-cloud-printer/android/libs"
        }`
        );
        
        fs.writeFileSync(settingsGradlePath, contents, 'utf-8');
        console.log('[Sunmi] ✅ Added AAR flatDir repository to settings.gradle');
      } else {
        console.warn('[Sunmi] ⚠️  Could not find dependencyResolutionManagement in settings.gradle');
      }
      
      return config;
    },
  ]);
};

/**
 * Add AAR dependency to app/build.gradle
 */
const withSunmiAppBuildGradle = (config) => {
  return withAppBuildGradle(config, (config) => {
    if (config.modResults.language === 'groovy') {
      let contents = config.modResults.contents;
      
      // Check if already added
      if (contents.includes('externalprinterlibrary2-1.0.13-release')) {
        return config;
      }
      
      // Add AAR implementation dependency
      const dependenciesRegex = /dependencies\s*\{/;
      if (dependenciesRegex.test(contents)) {
        contents = contents.replace(
          dependenciesRegex,
          `dependencies {
    implementation(name: 'externalprinterlibrary2-1.0.13-release', ext: 'aar')`
        );
      }
      
      config.modResults.contents = contents;
    }
    return config;
  });
};

/**
 * Main plugin that combines settings.gradle and app/build.gradle modifications
 */
const withSunmiCloudPrinterAAR = (config) => {
  config = withSunmiSettingsGradle(config);
  config = withSunmiAppBuildGradle(config);
  return config;
};

module.exports = withSunmiCloudPrinterAAR;
