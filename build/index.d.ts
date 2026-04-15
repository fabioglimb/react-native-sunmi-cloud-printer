import { PrintersEventPayload, ReactNativeSunmiCloudPrinterViewProps, PrinterInterface, SunmiCloudPrinter, SunmiError, PrinterConnectionPayload, WiFiNetwork, WiFiConfigStatusPayload, PrinterSerialNumberPayload } from './ReactNativeSunmiCloudPrinter.types';
import ReactNativeSunmiCloudPrinterView from './ReactNativeSunmiCloudPrinterView';
export { PrinterInterface, SunmiCloudPrinter, SunmiError, WiFiNetwork };
export declare function setup(): void;
export declare function setTimeout(timeout: number): void;
export declare function discoverPrinters(printerInterface: PrinterInterface): Promise<void>;
export declare function disconnectPrinter(): Promise<void>;
interface ConnectLanPrinterProps {
    ipAddress: string;
    force: boolean;
}
export declare function connectLanPrinter({ ipAddress, force }: ConnectLanPrinterProps): Promise<void>;
export declare function isPrinterConnected(printer: SunmiCloudPrinter): Promise<boolean>;
export declare function checkBluetoothPermissions(): Promise<boolean>;
export declare const requestBluetoothPermissions: () => Promise<void>;
interface ConnectBluetoothPrinterProps {
    uuid: string;
}
export declare function connectBluetoothPrinter({ uuid }: ConnectBluetoothPrinterProps): Promise<void>;
interface ConnectUSBPrinterProps {
    name: string;
}
export declare function connectUSBPrinter({ name }: ConnectUSBPrinterProps): Promise<void>;
export declare function lineFeed(lines: number): Promise<void>;
export declare function setTextAlign(textAlign: 'left' | 'right' | 'center'): Promise<void>;
interface SetPrintModesProps {
    bold: boolean;
    doubleHeight: boolean;
    doubleWidth: boolean;
}
export declare function setPrintModesBold({ bold, doubleHeight, doubleWidth }: SetPrintModesProps): Promise<void>;
export declare function restoreDefaultSettings(): Promise<void>;
export declare function restoreDefaultLineSpacing(): Promise<void>;
export declare function addCut(fullCut: boolean): Promise<void>;
export declare function addText(text: string): Promise<void>;
interface AddImageProps {
    base64: string;
    width: number;
    height: number;
}
export declare function addImage({ base64, width, height }: AddImageProps): Promise<void>;
export declare function clearBuffer(): Promise<void>;
export declare function sendData(): Promise<void>;
export declare function openCashDrawer(): Promise<void>;
export type CloudPrinterStatus = 'OFFLINE' | 'UNKNOWN' | 'RUNNING' | 'NEAR_OUT_PAPER' | 'OUT_PAPER' | 'JAM_PAPER' | 'PICK_PAPER' | 'COVER' | 'OVER_HOT' | 'MOTOR_HOT';
export declare function getDeviceState(): Promise<CloudPrinterStatus>;
export declare function printersListener(listener: (event: PrintersEventPayload) => void): {
    remove: () => void;
};
export declare function printerConnectionListener(listener: (event: PrinterConnectionPayload) => void): {
    remove: () => void;
};
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
export declare function enterNetworkMode(serialNumber?: string): Promise<void>;
/**
 * Get the printer's serial number.
 * The printer must be connected via Bluetooth before calling this method.
 * Listen to the `printerSerialNumberListener` for the result.
 *
 * NOTE: This can be called independently. You don't need to call enterNetworkMode() first.
 */
export declare function getPrinterSerialNumber(): Promise<void>;
/**
 * Request the list of available WiFi networks from the printer.
 *
 * IMPORTANT: You must call enterNetworkMode() first to put the printer in WiFi configuration mode.
 *
 * Listen to the `wifiNetworkListener` for each network found.
 * Listen to the `wifiListCompleteListener` for scan completion.
 */
export declare function getWiFiList(): Promise<void>;
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
export declare function configureWiFi({ ssid, password }: ConfigureWiFiProps): Promise<void>;
/**
 * Exit the WiFi configuration mode.
 * Call this after you're done configuring WiFi to return the printer to normal mode.
 */
export declare function quitWiFiConfig(): Promise<void>;
/**
 * Delete the WiFi settings from the printer.
 * This removes any stored WiFi credentials from the printer.
 */
export declare function deleteWiFiSettings(): Promise<void>;
/**
 * Listen for WiFi network information received from the printer.
 * This event is fired for each network when calling getWiFiList().
 */
export declare function wifiNetworkListener(listener: (event: {
    network: any;
}) => void): {
    remove: () => void;
};
/**
 * Listen for the completion of the WiFi list retrieval.
 */
export declare function wifiListCompleteListener(listener: () => void): {
    remove: () => void;
};
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
export declare function wifiConfigStatusListener(listener: (event: WiFiConfigStatusPayload) => void): {
    remove: () => void;
};
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
export declare function printerSerialNumberListener(listener: (event: PrinterSerialNumberPayload) => void): {
    remove: () => void;
};
/**
 * Alignment constants for inner printer
 */
export declare const InnerPrinterAlignment: {
    readonly LEFT: 0;
    readonly CENTER: 1;
    readonly RIGHT: 2;
};
/**
 * Barcode symbology constants
 */
export declare const BarcodeSymbology: {
    readonly UPC_A: 0;
    readonly UPC_E: 1;
    readonly EAN13: 2;
    readonly EAN8: 3;
    readonly CODE39: 4;
    readonly ITF: 5;
    readonly CODABAR: 6;
    readonly CODE93: 7;
    readonly CODE128: 8;
};
/**
 * Barcode text position constants
 */
export declare const BarcodeTextPosition: {
    readonly NO_TEXT: 0;
    readonly TEXT_ABOVE: 1;
    readonly TEXT_BELOW: 2;
    readonly TEXT_BOTH: 3;
};
/**
 * Check if the device has an inner printer (Sunmi embedded thermal printer).
 * Returns false on iOS and non-Sunmi Android devices.
 */
export declare function hasInnerPrinter(): boolean;
/**
 * Initialize the inner printer.
 * Resets printer logic but doesn't clear the buffer.
 * Android Sunmi devices only.
 */
export declare function innerPrinterInit(): Promise<void>;
/**
 * Get inner printer status.
 * Returns:
 * - 0: Normal
 * - 1: Preparing
 * - 2: Abnormal communication
 * - 3: Out of paper
 * - 4: Overheated
 * - 8: No printer connected
 * - 9: Firmware upgrade
 * - 505: Printer not detected
 *
 * Android Sunmi devices only.
 */
export declare function getInnerPrinterStatus(): Promise<number>;
/**
 * Get inner printer serial number.
 * Android Sunmi devices only.
 */
export declare function getInnerPrinterSerialNo(): Promise<string>;
/**
 * Get inner printer firmware version.
 * Android Sunmi devices only.
 */
export declare function getInnerPrinterVersion(): Promise<string>;
/**
 * Get inner printer model.
 * Android Sunmi devices only.
 */
export declare function getInnerPrinterModel(): Promise<string>;
/**
 * Get inner printer paper size (58mm or 80mm).
 * Android Sunmi devices only.
 */
export declare function getInnerPrinterPaper(): Promise<string>;
/**
 * Print text on the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerPrintText(text: string): Promise<void>;
/**
 * Print text with custom font on the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerPrintTextWithFont(text: string, typeface: string, fontSize: number): Promise<void>;
/**
 * Set text alignment for the inner printer.
 * Use InnerPrinterAlignment constants.
 * Android Sunmi devices only.
 */
export declare function innerSetAlignment(alignment: number): Promise<void>;
/**
 * Set font size for the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerSetFontSize(fontSize: number): Promise<void>;
/**
 * Set font weight (bold) for the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerSetFontWeight(isBold: boolean): Promise<void>;
/**
 * Print N lines (line feed) on the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerLineWrap(lines: number): Promise<void>;
interface InnerPrintBitmapProps {
    base64: string;
    width: number;
}
/**
 * Print a bitmap image on the inner printer.
 * Android Sunmi devices only.
 *
 * @param base64 - Base64 encoded image (with or without data URL prefix)
 * @param width - Width in pixels (384 for 58mm, 576 for 80mm)
 */
export declare function innerPrintBitmap({ base64, width }: InnerPrintBitmapProps): Promise<void>;
interface InnerPrintBarCodeProps {
    data: string;
    symbology: number;
    height: number;
    width: number;
    textPosition: number;
}
/**
 * Print a 1D barcode on the inner printer.
 * Android Sunmi devices only.
 *
 * @param data - Barcode data
 * @param symbology - Barcode type (use BarcodeSymbology constants)
 * @param height - Height in pixels
 * @param width - Width multiplier (2-6)
 * @param textPosition - Text position (use BarcodeTextPosition constants)
 */
export declare function innerPrintBarCode({ data, symbology, height, width, textPosition }: InnerPrintBarCodeProps): Promise<void>;
interface InnerPrintQRCodeProps {
    data: string;
    moduleSize?: number;
    errorLevel?: number;
}
/**
 * Print a QR code on the inner printer.
 * Android Sunmi devices only.
 *
 * @param data - QR code data
 * @param moduleSize - Module size (4-16, default 8)
 * @param errorLevel - Error correction level (0-3, default 1)
 */
export declare function innerPrintQRCode({ data, moduleSize, errorLevel }: InnerPrintQRCodeProps): Promise<void>;
/**
 * Cut paper on the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerCutPaper(): Promise<void>;
/**
 * Open cash drawer connected to the inner printer.
 * Android Sunmi devices only.
 */
export declare function innerOpenCashDrawer(): Promise<void>;
/**
 * Start the Sunmi barcode scanner UI and return the scanned value.
 * Supports QR, EAN, UPC, CODE128, PDF417, DataMatrix, Aztec, and more.
 * Android Sunmi devices only.
 *
 * @returns The scanned barcode/QR code value
 */
export declare function startScanner(): Promise<string>;
export { ReactNativeSunmiCloudPrinterView, ReactNativeSunmiCloudPrinterViewProps, PrintersEventPayload };
//# sourceMappingURL=index.d.ts.map