# WiFi Configuration Debugging Steps

## Check if Bluetooth is Connected

The WiFi configuration ONLY works when connected via Bluetooth. Test this first:

```typescript
import {
  connectBluetoothPrinter,
  printerConnectionListener,
  getPrinterSerialNumber,
  printerSerialNumberListener,
} from 'react-native-sunmi-cloud-printer';

// 1. Set up listeners BEFORE calling methods
const connectionSub = printerConnectionListener((event) => {
  console.log('Printer connection:', event.connected);
});

const snSub = printerSerialNumberListener((event) => {
  console.log('RECEIVED SERIAL NUMBER:', event.serialNumber);
});

// 2. Connect via Bluetooth first
async function test() {
  try {
    // Connect to Bluetooth printer
    await connectBluetoothPrinter({ uuid: 'YOUR_PRINTER_UUID' });
    
    // Wait for connection
    await new Promise(resolve => setTimeout(resolve, 3000));
    
    // Now try to get serial number
    console.log('Calling getPrinterSerialNumber...');
    await getPrinterSerialNumber();
    
    // Wait for delegate callback
    await new Promise(resolve => setTimeout(resolve, 5000));
  } catch (error) {
    console.error('Error:', error);
  }
}
```

## iOS - Check Delegate

The issue is likely that the Bluetooth manager delegate needs to be set up correctly.

Check in Xcode logs for:
- "🟢 received printer serial number: ..."
- Any delegate callback messages

## Android - CloudPrinter API Issue

The Android CloudPrinter API might NOT have WiFi configuration methods. They may only be available in a Bluetooth-specific API.

Check logcat for errors:
```bash
adb logcat | grep -i "sunmi\|printer"
```

## Possible Issues

1. **Bluetooth not connected**: WiFi config requires active Bluetooth connection
2. **Delegate not firing**: iOS delegate callbacks not being received
3. **API doesn't exist**: Android CloudPrinter may not have these methods
4. **Wrong API**: Should use Bluetooth manager instead of CloudPrinter

## Next Steps

I need to:
1. Check if Android CloudPrinter actually has these WiFi methods
2. Verify iOS delegate callbacks are wired correctly
3. May need to use a different Android API for WiFi config

