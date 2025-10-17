const { withDangerousMod } = require('@expo/config-plugins');
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
      
      // Find the package location - try multiple paths for monorepo support
      const androidRoot = config.modRequest.platformProjectRoot;
      const possiblePaths = [
        '../node_modules/react-native-sunmi-cloud-printer/android/libs',  // Standard
        '../../node_modules/react-native-sunmi-cloud-printer/android/libs',  // Apps folder in monorepo
        '../../../node_modules/react-native-sunmi-cloud-printer/android/libs',  // Deeper monorepo
      ];
      
      let packagePath = possiblePaths[0]; // Default to standard path
      
      // Try to find which path actually exists
      for (const testPath of possiblePaths) {
        const absolutePath = path.resolve(androidRoot, testPath);
        if (fs.existsSync(absolutePath)) {
          packagePath = testPath;
          console.log(`[Sunmi] Found package at: ${packagePath}`);
          break;
        }
      }
      
      // Add to dependencyResolutionManagement > repositories
      const drmRepoRegex = /(dependencyResolutionManagement\s*\{[\s\S]*?repositories\s*\{)/;
      
      if (drmRepoRegex.test(contents)) {
        // Found existing dependencyResolutionManagement, add to it
        contents = contents.replace(
          drmRepoRegex,
          `$1
        flatDir {
            dirs "${packagePath}"
        }`
        );
        
        fs.writeFileSync(settingsGradlePath, contents, 'utf-8');
        console.log('[Sunmi] ✅ Added AAR flatDir repository to existing dependencyResolutionManagement');
      } else {
        // No dependencyResolutionManagement found, create it
        // Insert after pluginManagement and before plugins
        const pluginsRegex = /(pluginManagement\s*\{[\s\S]*?\}\s*\n)/;
        
        if (pluginsRegex.test(contents)) {
          const drmBlock = `
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs "${packagePath}"
        }
    }
}

`;
          contents = contents.replace(pluginsRegex, `$1${drmBlock}`);
          fs.writeFileSync(settingsGradlePath, contents, 'utf-8');
          console.log('[Sunmi] ✅ Created dependencyResolutionManagement block with AAR flatDir repository');
        } else {
          console.warn('[Sunmi] ⚠️  Could not find suitable location to add dependencyResolutionManagement');
        }
      }
      
      return config;
    },
  ]);
};

/**
 * Main plugin that adds flatDir repository to settings.gradle
 */
const withSunmiCloudPrinterAAR = (config) => {
  config = withSunmiSettingsGradle(config);
  return config;
};

module.exports = withSunmiCloudPrinterAAR;
