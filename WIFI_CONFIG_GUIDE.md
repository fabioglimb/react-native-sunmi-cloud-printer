# WiFi Configuration Guide

This guide explains how to use the WiFi configuration features to connect your Sunmi printer to a WiFi network directly from your React Native app.

## Overview

The WiFi configuration feature allows you to:
- Connect to a Sunmi printer via Bluetooth
- Enter network configuration mode
- Scan for available WiFi networks
- Configure the printer to connect to a WiFi network with SSID and password
- Monitor the configuration process with real-time status updates

## Workflow

The typical WiFi configuration flow:

```
1. Connect to printer via Bluetooth (connectBluetoothPrinter)
2. Enter network mode WITHOUT serial number (enterNetworkMode(''))  ⚠️ IMPORTANT!
3. Get printer serial number (getPrinterSerialNumber)
4. [Optional] Get WiFi list (getWiFiList)
5. Configure WiFi (configureWiFi)
6. Exit config mode (quitWiFiConfig)
```

**⚠️ CRITICAL**: You MUST call `enterNetworkMode('')` with an empty string BEFORE calling `getPrinterSerialNumber()`. The printer needs to be in network mode to return its serial number.

## API Reference

### Methods

#### `getPrinterSerialNumber(): Promise<void>`
Get the printer's serial number. The result is sent via the `printerSerialNumberListener` event.

```typescript
import { getPrinterSerialNumber, printerSerialNumberListener } from 'react-native-sunmi-cloud-printer';

// Set up listener first
const subscription = printerSerialNumberListener((event) => {
  console.log('Printer SN:', event.serialNumber);
});

// Request the serial number
await getPrinterSerialNumber();
```

#### `enterNetworkMode(serialNumber?: string): Promise<void>`
Enter network configuration mode on the printer. The printer must be connected via Bluetooth.

**IMPORTANT**: Call with empty string `''` or no parameter FIRST to enter initial network mode, then you can get the serial number.

```typescript
import { enterNetworkMode, wifiConfigStatusListener } from 'react-native-sunmi-cloud-printer';

// Set up status listener
const statusSubscription = wifiConfigStatusListener((event) => {
  if (event.status === 'entered_network_mode') {
    console.log('Successfully entered network mode');
  }
});

// Enter network mode WITHOUT serial number (first time)
await enterNetworkMode('');  // or await enterNetworkMode();

// OR if you already have the SN, you can pass it
await enterNetworkMode(serialNumber);
```

#### `getWiFiList(): Promise<void>`
Request the list of available WiFi networks from the printer. Results are sent via `wifiNetworkListener` and `wifiListCompleteListener`.

```typescript
import { getWiFiList, wifiNetworkListener, wifiListCompleteListener } from 'react-native-sunmi-cloud-printer';

const networks = [];

// Listen for individual networks
const networkSubscription = wifiNetworkListener((event) => {
  console.log('Found network:', event.network);
  networks.push(event.network);
});

// Listen for completion
const completeSubscription = wifiListCompleteListener(() => {
  console.log('WiFi list complete. Found', networks.length, 'networks');
});

// Request the list
await getWiFiList();
```

#### `configureWiFi({ ssid, password }): Promise<void>`
Configure the printer to connect to a WiFi network. Status updates are sent via `wifiConfigStatusListener`.

```typescript
import { configureWiFi, wifiConfigStatusListener } from 'react-native-sunmi-cloud-printer';

const statusSubscription = wifiConfigStatusListener((event) => {
  switch (event.status) {
    case 'will_start_config':
      console.log('Starting WiFi configuration...');
      break;
    case 'success':
      console.log('WiFi configured successfully!');
      break;
    case 'failed':
      console.log('WiFi configuration failed');
      break;
  }
});

await configureWiFi({
  ssid: 'MyWiFiNetwork',
  password: 'MyPassword123'
});
```

#### `quitWiFiConfig(): Promise<void>`
Exit the WiFi configuration mode.

```typescript
import { quitWiFiConfig } from 'react-native-sunmi-cloud-printer';

await quitWiFiConfig();
```

#### `deleteWiFiSettings(): Promise<void>`
Delete the WiFi settings from the printer.

```typescript
import { deleteWiFiSettings } from 'react-native-sunmi-cloud-printer';

await deleteWiFiSettings();
```

### Event Listeners

#### `printerSerialNumberListener(callback)`
Listens for the printer's serial number.

**Payload**: `{ serialNumber: string }`

#### `wifiNetworkListener(callback)`
Listens for WiFi network information. Fires for each network when calling `getWiFiList()`.

**Payload**: `{ network: any }`

#### `wifiListCompleteListener(callback)`
Listens for the completion of WiFi list retrieval.

**Payload**: None

#### `wifiConfigStatusListener(callback)`
Listens for WiFi configuration status updates.

**Payload**: `{ status: 'entered_network_mode' | 'will_start_config' | 'success' | 'failed' }`

## Complete Example

```typescript
import {
  connectBluetoothPrinter,
  getPrinterSerialNumber,
  enterNetworkMode,
  getWiFiList,
  configureWiFi,
  quitWiFiConfig,
  printerSerialNumberListener,
  wifiNetworkListener,
  wifiListCompleteListener,
  wifiConfigStatusListener,
} from 'react-native-sunmi-cloud-printer';

async function configurePrinterWiFi(printerUUID: string, targetSSID: string, password: string) {
  let serialNumber = '';
  const networks = [];

  // Set up listeners
  const snSubscription = printerSerialNumberListener((event) => {
    serialNumber = event.serialNumber;
    console.log('Got SN:', serialNumber);
  });

  const networkSubscription = wifiNetworkListener((event) => {
    networks.push(event.network);
    console.log('Found network:', event.network);
  });

  const completeSubscription = wifiListCompleteListener(() => {
    console.log('WiFi scan complete. Found', networks.length, 'networks');
  });

  const statusSubscription = wifiConfigStatusListener((event) => {
    console.log('WiFi config status:', event.status);
  });

  try {
    // Step 1: Connect to printer via Bluetooth
    console.log('Connecting to printer...');
    await connectBluetoothPrinter({ uuid: printerUUID });
    
    // Wait for connection
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Step 2: Enter network mode (WITHOUT serial number!)
    console.log('Entering network mode...');
    await enterNetworkMode('');  // ⚠️ Pass empty string to enter initial mode
    
    // Wait for network mode
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Step 3: Get printer serial number (now that we're in network mode)
    console.log('Getting printer serial number...');
    await getPrinterSerialNumber();
    
    // Wait for SN
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Step 4: Get WiFi list (optional)
    console.log('Scanning for WiFi networks...');
    await getWiFiList();
    
    // Wait for scan
    await new Promise(resolve => setTimeout(resolve, 5000));

    // Step 5: Configure WiFi
    console.log('Configuring WiFi...');
    await configureWiFi({
      ssid: targetSSID,
      password: password,
    });
    
    // Wait for configuration
    await new Promise(resolve => setTimeout(resolve, 5000));

    // Step 6: Exit config mode
    console.log('Exiting config mode...');
    await quitWiFiConfig();

    console.log('WiFi configuration complete!');
  } catch (error) {
    console.error('WiFi configuration error:', error);
  } finally {
    // Clean up listeners
    snSubscription.remove();
    networkSubscription.remove();
    completeSubscription.remove();
    statusSubscription.remove();
  }
}

// Usage
configurePrinterWiFi(
  'PRINTER-BLUETOOTH-UUID',
  'MyWiFiNetwork',
  'MyPassword123'
);
```

## Important Notes

1. **Bluetooth Connection Required**: The printer must be connected via Bluetooth before entering network configuration mode.

2. **Event-Driven**: Most operations are asynchronous and use event listeners for results. Always set up listeners before calling the methods.

3. **Timing**: Add appropriate delays between steps to allow the printer to process each command.

4. **Error Handling**: Always wrap WiFi configuration in try-catch blocks and handle errors appropriately.

5. **Platform Support**: WiFi configuration is supported on both iOS and Android platforms.

## Troubleshooting

### Printer doesn't enter network mode
- Ensure the printer is connected via Bluetooth
- Verify you're using the correct serial number
- Check that the printer supports WiFi configuration

### WiFi list is empty
- Ensure the printer is in network mode
- Wait sufficient time for the scan to complete
- Check if the printer is in range of WiFi networks

### Configuration fails
- Verify the SSID and password are correct
- Check if the WiFi network is 2.4GHz (some printers don't support 5GHz)
- Ensure the printer is within range of the WiFi network

## TypeScript Types

```typescript
export type WiFiNetwork = {
  ssid: string;
  signalStrength?: number;
  requiresPassword: boolean;
};

export type WiFiConfigStatusPayload = {
  status: 'entered_network_mode' | 'will_start_config' | 'success' | 'failed';
};

export type PrinterSerialNumberPayload = {
  serialNumber: string;
};
```

