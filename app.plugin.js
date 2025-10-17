const { withAppBuildGradle } = require('@expo/config-plugins');

const withSunmiCloudPrinterAAR = (config) => {
  return withAppBuildGradle(config, (config) => {
    if (config.modResults.language === 'groovy') {
      let contents = config.modResults.contents;
      
      // Check if already added
      if (contents.includes('externalprinterlibrary2-1.0.13-release')) {
        return config;
      }
      
      // Add flatDir repository using $rootDir for absolute path
      const repositoriesRegex = /repositories\s*\{/;
      if (repositoriesRegex.test(contents)) {
        contents = contents.replace(
          repositoriesRegex,
          `repositories {
    flatDir {
        dirs "\${rootDir}/../node_modules/react-native-sunmi-cloud-printer/android/libs"
    }`
        );
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
    } else {
      throw new Error("Sunmi Cloud Printer plugin requires Groovy build.gradle");
    }
    return config;
  });
};

module.exports = withSunmiCloudPrinterAAR;
