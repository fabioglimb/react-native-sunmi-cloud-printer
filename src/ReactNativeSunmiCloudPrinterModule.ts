import { NativeModule, requireNativeModule } from 'expo';

import { PrintersEventPayload, PrinterConnectionPayload, WiFiConfigStatusPayload, PrinterSerialNumberPayload } from './ReactNativeSunmiCloudPrinter.types';

declare class ReactNativeSunmiCloudPrinterModule extends NativeModule {
  addListener<EventName extends 'onUpdatePrinters'>(
    eventName: EventName,
    listener: (event: PrintersEventPayload) => void
  ): { remove: () => void };
  addListener<EventName extends 'onPrinterConnectionUpdate'>(
    eventName: EventName,
    listener: (event: PrinterConnectionPayload) => void
  ): { remove: () => void };
  addListener<EventName extends 'onWiFiNetworkReceived'>(
    eventName: EventName,
    listener: (event: { network: any }) => void
  ): { remove: () => void };
  addListener<EventName extends 'onWiFiListComplete'>(
    eventName: EventName,
    listener: () => void
  ): { remove: () => void };
  addListener<EventName extends 'onWiFiConfigStatus'>(
    eventName: EventName,
    listener: (event: WiFiConfigStatusPayload) => void
  ): { remove: () => void };
  addListener<EventName extends 'onPrinterSerialNumber'>(
    eventName: EventName,
    listener: (event: PrinterSerialNumberPayload) => void
  ): { remove: () => void };
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ReactNativeSunmiCloudPrinterModule>('ReactNativeSunmiCloudPrinter');
