import { Platform, PermissionsAndroid } from 'react-native';

// Import the native module. On web, it will be resolved to ReactNativeSunmiCloudPrinter.web.ts
// and on native platforms to ReactNativeSunmiCloudPrinter.ts
import {
  PrintersEventPayload,
  ReactNativeSunmiCloudPrinterViewProps,
  PrinterInterface,
  SunmiCloudPrinter,
  SunmiError,
  PrinterConnectionPayload,
  WiFiNetwork,
  WiFiConfigStatusPayload,
  PrinterSerialNumberPayload,
} from './ReactNativeSunmiCloudPrinter.types';
import ReactNativeSunmiCloudPrinterModule from './ReactNativeSunmiCloudPrinterModule';
import ReactNativeSunmiCloudPrinterView from './ReactNativeSunmiCloudPrinterView';

export { PrinterInterface, SunmiCloudPrinter, SunmiError, WiFiNetwork };

export function setup() {
  if (Platform.OS === 'ios') {
    // Native setup is only required on iOS
    ReactNativeSunmiCloudPrinterModule.setup();
  }
}

export function setTimeout(timeout: number) {
  ReactNativeSunmiCloudPrinterModule.setTimeout(timeout);
}

export async function discoverPrinters(printerInterface: PrinterInterface): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.discoverPrinters(printerInterface);
}

export async function disconnectPrinter(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.disconnectPrinter();
}

interface ConnectLanPrinterProps {
  // The IP address of the printer
  ipAddress: string;
  // If set to true, it will force the connection even if the printer is not in the list of discovered printers
  force: boolean;
}
export async function connectLanPrinter({ ipAddress, force }: ConnectLanPrinterProps): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.connectLanPrinter(ipAddress, force);
}

export function isPrinterConnected(printer: SunmiCloudPrinter): Promise<boolean> {
  switch (printer.interface) {
    case 'BLUETOOTH':
      return ReactNativeSunmiCloudPrinterModule.isBluetoothPrinterConnected(printer.uuid);
    case 'LAN':
      return ReactNativeSunmiCloudPrinterModule.isLanPrinterConnected(printer.ip);
    case 'USB':
      return ReactNativeSunmiCloudPrinterModule.isUSBPrinterConnected(printer.name);
  }
}

export function checkBluetoothPermissions(): Promise<boolean> {
  if (Platform.OS === 'android') {
    return ReactNativeSunmiCloudPrinterModule.checkBluetoothPermissions();
  } else {
    return Promise.resolve(true);
  }
}

export const requestBluetoothPermissions = async (): Promise<void> => {
  try {
    if (Platform.OS === 'android') {
      // 1) Request Location permission
      const grantedLocation = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION, {
        title: 'Access Fine Location',
        message: 'Sunmi Cloud Printer needs access to your location for bluetooth connection',
        buttonPositive: 'OK',
      });
      if (grantedLocation !== PermissionsAndroid.RESULTS.GRANTED) {
        // If never asked again... we must inform the customer that this permission is required to run the app
        throw new SunmiError(
          'ERROR_INVALID_PERMISSIONS',
          'Access Fine Location permission denied. Please, go to Android settings and enable it.'
        );
      }

      // 2) Request extra Bluetooth permissions (required for Android API 31+)
      if (Platform.Version >= 31) {
        // BLUETOOTH_SCAN
        const grantedBluetoothScan = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN, {
          title: 'Access Bluetooth Scan',
          message: 'Sunmi Cloud Printer needs access to your bluetooth for printing',
          buttonPositive: 'OK',
        });
        if (grantedBluetoothScan !== PermissionsAndroid.RESULTS.GRANTED) {
          // If never asked again... we must inform the customer that this permission is required to run the app
          throw new SunmiError(
            'ERROR_INVALID_PERMISSIONS',
            'Bluetooth permission denied. Please, go to Android settings and enable it.'
          );
        }

        // BLUETOOTH_CONNECT
        const grantedBluetoothConnect = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          {
            title: 'Access Bluetooth',
            message: 'Sunmi Cloud Printer needs access to your bluetooth for printing',
            buttonPositive: 'OK',
          }
        );
        if (grantedBluetoothConnect !== PermissionsAndroid.RESULTS.GRANTED) {
          // If never asked again... we must inform the customer that this permission is required to run the app
          throw new SunmiError(
            'ERROR_INVALID_PERMISSIONS',
            'Bluetooth permission denied. Please, go to Android settings and enable it.'
          );
        }
      }
    }
    return Promise.resolve();
  } catch (e) {
    if (__DEV__) {
      console.error('Error requesting Bluetooth permissions', e);
    }
    return Promise.reject(e);
  }
};

interface ConnectBluetoothPrinterProps {
  uuid: string;
}
export async function connectBluetoothPrinter({ uuid }: ConnectBluetoothPrinterProps): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.connectBluetoothPrinter(uuid);
}

interface ConnectUSBPrinterProps {
  name: string;
}
export async function connectUSBPrinter({ name }: ConnectUSBPrinterProps): Promise<void> {
  if (Platform.OS === 'android') {
    return ReactNativeSunmiCloudPrinterModule.connectUSBPrinter(name);
  } else {
    return Promise.reject(
      new SunmiError('ERROR_UNSUPPORTED_PLATFORM', 'USB connection is not supported on this platform')
    );
  }
}

// ---------------
// Low level APIs
// ---------------

export function lineFeed(lines: number): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.lineFeed(lines);
}

export function setTextAlign(textAlign: 'left' | 'right' | 'center'): Promise<void> {
  const align = textAlign === 'left' ? 0 : textAlign === 'right' ? 2 : 1;
  return ReactNativeSunmiCloudPrinterModule.setTextAlign(align);
}

interface SetPrintModesProps {
  bold: boolean;
  doubleHeight: boolean;
  doubleWidth: boolean;
}
export function setPrintModesBold({ bold, doubleHeight, doubleWidth }: SetPrintModesProps): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.setPrintModesBold(bold, doubleHeight, doubleWidth);
}

export function restoreDefaultSettings(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.restoreDefaultSettings();
}

export function restoreDefaultLineSpacing(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.restoreDefaultLineSpacing();
}

export function addCut(fullCut: boolean): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.addCut(fullCut);
}

export function addText(text: string): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.addText(text);
}

interface AddImageProps {
  base64: string;
  width: number;
  height: number;
}
export function addImage({ base64, width, height }: AddImageProps): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.addImage(base64, Math.floor(width), Math.floor(height));
}

export function clearBuffer(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.clearBuffer();
}

export function sendData(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.sendData();
}

export function openCashDrawer(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.openCashDrawer();
}

export type CloudPrinterStatus =
  | 'OFFLINE'
  | 'UNKNOWN'
  | 'RUNNING'
  | 'NEAR_OUT_PAPER'
  | 'OUT_PAPER'
  | 'JAM_PAPER'
  | 'PICK_PAPER'
  | 'COVER'
  | 'OVER_HOT'
  | 'MOTOR_HOT';

export async function getDeviceState(): Promise<CloudPrinterStatus> {
  return ReactNativeSunmiCloudPrinterModule.getDeviceState();
}

export function printersListener(listener: (event: PrintersEventPayload) => void) {
  return ReactNativeSunmiCloudPrinterModule.addListener('onUpdatePrinters', listener);
}

export function printerConnectionListener(listener: (event: PrinterConnectionPayload) => void) {
  return ReactNativeSunmiCloudPrinterModule.addListener('onPrinterConnectionUpdate', listener);
}

// ---------------
// WiFi Configuration APIs
// ---------------

/**
 * Enter network configuration mode on the printer.
 * The printer must be connected via Bluetooth before calling this method.
 * 
 * @param serialNumber - Optional: The printer's serial number.
 *   - If you have the serial number, pass it here
 *   - If not provided or empty, it will try to work without it (may work if printer is physically paired)
 * 
 * NOTE: Some printers may work without a serial number if they are physically paired via Bluetooth.
 */
export async function enterNetworkMode(serialNumber?: string): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.enterNetworkMode(serialNumber || '');
}

/**
 * Get the printer's serial number.
 * The printer must be connected via Bluetooth before calling this method.
 * Listen to the `printerSerialNumberListener` for the result.
 * 
 * NOTE: This can be called independently. You don't need to call enterNetworkMode() first.
 */
export async function getPrinterSerialNumber(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.getPrinterSerialNumber();
}

/**
 * Request the list of available WiFi networks from the printer.
 * 
 * IMPORTANT: You must call enterNetworkMode() first to put the printer in WiFi configuration mode.
 * 
 * Listen to the `wifiNetworkListener` for each network found.
 * Listen to the `wifiListCompleteListener` for scan completion.
 */
export async function getWiFiList(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.getWiFiList();
}

interface ConfigureWiFiProps {
  ssid: string;
  password: string;
}

/**
 * Configure the printer to connect to a WiFi network.
 * 
 * IMPORTANT: You must call enterNetworkMode() first to put the printer in WiFi configuration mode.
 * 
 * Listen to the `wifiConfigStatusListener` for status updates:
 * - 'will_start_config': Configuration is starting
 * - 'saved': WiFi settings saved to printer
 * - 'success': Printer successfully connected to WiFi
 * - 'failed': Connection failed
 * 
 * @param ssid - The WiFi network SSID (network name)
 * @param password - The WiFi network password
 */
export async function configureWiFi({ ssid, password }: ConfigureWiFiProps): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.configureWiFi(ssid, password);
}

/**
 * Exit the WiFi configuration mode.
 * Call this after you're done configuring WiFi to return the printer to normal mode.
 */
export async function quitWiFiConfig(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.quitWiFiConfig();
}

/**
 * Delete the WiFi settings from the printer.
 * This removes any stored WiFi credentials from the printer.
 */
export async function deleteWiFiSettings(): Promise<void> {
  return ReactNativeSunmiCloudPrinterModule.deleteWiFiSettings();
}

/**
 * Listen for WiFi network information received from the printer.
 * This event is fired for each network when calling getWiFiList().
 */
export function wifiNetworkListener(listener: (event: { network: any }) => void) {
  return ReactNativeSunmiCloudPrinterModule.addListener('onWiFiNetworkReceived', listener);
}

/**
 * Listen for the completion of the WiFi list retrieval.
 */
export function wifiListCompleteListener(listener: () => void) {
  return ReactNativeSunmiCloudPrinterModule.addListener('onWiFiListComplete', listener);
}

/**
 * Listen for WiFi configuration status updates.
 * 
 * Status values:
 * - 'fetching_serial_number': Attempting to fetch printer serial number
 * - 'entered_network_mode': Printer entered network configuration mode successfully
 * - 'will_start_config': WiFi configuration is about to start
 * - 'saved': WiFi settings saved to printer
 * - 'success': Printer successfully connected to WiFi network
 * - 'failed': WiFi configuration or connection failed
 * - 'error_no_serial_number': No serial number available (deprecated - now continues anyway)
 * - 'error_empty_serial_number': Fetched serial number was empty (deprecated - now continues anyway)
 * - 'error_fetching_serial_number': Failed to fetch serial number (deprecated - now continues anyway)
 */
export function wifiConfigStatusListener(listener: (event: WiFiConfigStatusPayload) => void) {
  return ReactNativeSunmiCloudPrinterModule.addListener('onWiFiConfigStatus', listener);
}

/**
 * Listen for the printer's serial number.
 * This event is fired when you call getPrinterSerialNumber().
 * 
 * @example
 * const subscription = printerSerialNumberListener((event) => {
 *   console.log('Printer SN:', event.serialNumber);
 * });
 * 
 * await getPrinterSerialNumber();
 * // ... wait for event
 * subscription.remove(); // Clean up when done
 */
export function printerSerialNumberListener(listener: (event: PrinterSerialNumberPayload) => void) {
  return ReactNativeSunmiCloudPrinterModule.addListener('onPrinterSerialNumber', listener);
}

export { ReactNativeSunmiCloudPrinterView, ReactNativeSunmiCloudPrinterViewProps, PrintersEventPayload };
