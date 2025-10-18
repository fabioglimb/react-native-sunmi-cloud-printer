package expo.modules.sunmicloudprinter

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.sunmi.externalprinterlibrary2.ConnectCallback
import com.sunmi.externalprinterlibrary2.ResultCallback
import com.sunmi.externalprinterlibrary2.SearchMethod
import com.sunmi.externalprinterlibrary2.SunmiPrinterManager
import com.sunmi.externalprinterlibrary2.printer.CloudPrinter
import com.sunmi.externalprinterlibrary2.style.AlignStyle
import com.sunmi.externalprinterlibrary2.style.CloudPrinterStatus
import com.sunmi.externalprinterlibrary2.style.ImageAlgorithm
// WiFi configuration classes from Maven external-printerlibrary2:1.0.14
import com.sunmi.externalprinterlibrary2.WifiResult
import com.sunmi.cloudprinter.bean.Router
import com.sunmi.externalprinterlibrary2.SetWifiCallback
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class SunmiPrinterError(val error: String, val reason: String) {
    PRINTER_NOT_CONNECTED("ERROR_PRINTER_NOT_CONNECTED", "Printer not connected"),
    PRINTER_NOT_FOUND("ERROR_PRINTER_NOT_FOUND", "Printer not found"),
    EMPTY_BUFFER("ERROR_EMPTY_BUFFER", "Empty buffer"),
    ERROR_INVALID_PERMISSIONS("ERROR_INVALID_PERMISSIONS", "Invalid permissions")
}

class PrinterException(errorCode: SunmiPrinterError) : CodedException(errorCode.error)

enum class PrinterInterface(val interfaceName: String) {
    LAN("LAN"),
    BLUETOOTH("BLUETOOTH"),
    USB("USB");

    val method: Int
        get() = when (this) {
            LAN -> SearchMethod.LAN
            BLUETOOTH -> SearchMethod.BT
            USB -> SearchMethod.USB
        }
}

class SunmiManager {

    private var timeout: Long = 5000;
    private var cloudPrinter: CloudPrinter? = null
    private var manager: SunmiPrinterManager? = null
    private var cachedSerialNumber: String? = null // Cache for printer serial number
    private var _devices: List<CloudPrinter> = emptyList()
    private var devices: List<CloudPrinter>
        get() = _devices
        set(value) {
            _devices = value
            PrintersNotifier.onUpdatePrinters(value)
        }

    init {
        manager = SunmiPrinterManager.getInstance()
    }

    fun setTimeout(timeout: Long) {
        this.timeout = timeout
    }

    fun checkBluetoothPermissions(context: Context, promise: Promise) {
        val hasPermissions = haveBluetoothPermissions(context)
        promise.resolve(hasPermissions)
    }

    fun discoverPrinters(context: Context, printerInterface: PrinterInterface, promise: Promise) = runBlocking {
        // Every time we trigger discover, we clear the list of devices
        devices = emptyList()

        val method = printerInterface.method

        // Search for printers
        val hasPermissions: Boolean
        if (printerInterface == PrinterInterface.BLUETOOTH) {
            printDebugLog("🟢 will check bluetooth permissions")
            hasPermissions = haveBluetoothPermissions(context)
        } else {
            hasPermissions = true
        }
        if (hasPermissions) {
            launch { // launch a new coroutine and continue
                printDebugLog("🟢 will discover a cloud printer: ${printerInterface.interfaceName}")
                manager?.searchCloudPrinter(context, method
                ) { p0 ->
                    printDebugLog("🟢 did discover a cloud printer: ${p0?.cloudPrinterInfo.toString()}")
                    val incomingDeviceName = p0?.cloudPrinterInfo?.name
                    val hasDevice = devices.any { device ->
                        device.cloudPrinterInfo.name == incomingDeviceName
                    }

                    // We only include the device if we're sure the device is not already in the list
                    if (!hasDevice && p0 != null) {
                        devices = devices + p0
                    }
                }
                printDebugLog("🟢 did start to discover printers: [interface=${printerInterface.interfaceName}]")
                delay(timeout) // non-blocking delay for `timeout` ms
                manager?.stopSearch(context, method)
                printDebugLog("🟢 did stop searching for printers after timeout: [interface=${printerInterface.interfaceName}]")
                promise.resolve()
            }
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.ERROR_INVALID_PERMISSIONS)
        }
    }

    fun connectLanPrinter(context: Context, force: Boolean, ipAddress: String, promise: Promise) {
        connectToPrinter(context, force, PrinterInterface.LAN, ipAddress, promise)
    }

    fun connectUSBPrinter(context: Context, name: String, promise: Promise) {
        connectToPrinter(context, false, PrinterInterface.USB, name, promise)
    }

    fun connectBluetoothPrinter(context: Context, mac: String, promise: Promise) {
        connectToPrinter(context, false, PrinterInterface.BLUETOOTH, mac, promise)
    }

    fun disconnectPrinter(context: Context, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.release(context)
            // Clear cached serial number on disconnect
            cachedSerialNumber = null
            printDebugLog("🔵 Cleared cached serial number")
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun isBluetoothPrinterConnected(uuid: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null && printer.cloudPrinterInfo.mac == uuid) {
            promise.resolve(printer.isConnected)
        } else {
            promise.resolve(false)
        }
    }

    fun isLanPrinterConnected(ipAddress: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null && printer.cloudPrinterInfo.address == ipAddress) {
            promise.resolve(printer.isConnected)
        } else {
            promise.resolve(false)
        }
    }

    fun isUSBPrinterConnected(name: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null && printer.cloudPrinterInfo.name == name) {
            promise.resolve(printer.isConnected)
        } else {
            promise.resolve(false)
        }
    }
    // -----------------------
    // Low Level API methods
    // -----------------------

    fun lineFeed(lines: Int, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.lineFeed(lines)
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun setTextAlign(alignment: Int, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            val alignStyle: AlignStyle = when (alignment) {
                1 -> {
                    AlignStyle.CENTER
                }

                2 -> {
                    AlignStyle.RIGHT
                }

                else -> {
                    AlignStyle.LEFT
                }
            }

            printer.setAlignment(alignStyle)
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun setPrintModesBold(bold: Boolean, doubleHeight: Boolean, doubleWidth: Boolean, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.setBoldMode(bold)
            // doubleHeight and doubleWidth are ignored in Android
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun restoreDefaultSettings(promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.restoreDefaultSettings()
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun restoreDefaultLineSpacing(promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.restoreDefaultLineSpacing()
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun addCut(fullCut: Boolean, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.cutPaper(fullCut)
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun addText(text: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.printText(text)
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun addImage(bitmap: Bitmap, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.printImage(bitmap, ImageAlgorithm.DITHERING)
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun clearBuffer(promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.clearTransBuffer()
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun sendData(promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.commitTransBuffer(object : ResultCallback {
                override fun onComplete() {
                    promise.resolve()
                }

                override fun onFailed(p0: CloudPrinterStatus?) {
                    promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_FOUND)
                }
            })
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun openCashDrawer(promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.openCashBox()
            promise.resolve()
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun getDeviceState(promise:Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            printer.getDeviceState { data ->
                promise.resolve(data.name)
            }
        } else {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    // -----------------------
    // WiFi Configuration APIs
    // -----------------------

    // Helper function to fetch serial number synchronously with timeout
    private suspend fun fetchSerialNumberSync(printer: CloudPrinter): String? {
        return withTimeout(5000L) { // 5 second timeout
            suspendCancellableCoroutine { continuation ->
                try {
                    printDebugLog("🔵 Fetching serial number synchronously...")
                    printer.getDeviceSN { serialNumber ->
                        printDebugLog("🟢 Received serial number: ${if (serialNumber?.isEmpty() != false) "<empty>" else serialNumber}")
                        // Cache it
                        cachedSerialNumber = serialNumber
                        
                        // Resume the coroutine only if not already resumed
                        if (continuation.isActive) {
                            continuation.resume(serialNumber)
                        }
                    }
                } catch (e: Exception) {
                    printDebugLog("🔴 ERROR fetching SN: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    fun getPrinterSerialNumber(promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            try {
                printDebugLog("🟢 Attempting to get printer serial number...")
                
                // Check if method exists
                val method = try {
                    printer.javaClass.getMethod("getDeviceSN", kotlin.jvm.functions.Function1::class.java)
                } catch (e: NoSuchMethodException) {
                    printDebugLog("🔴 ERROR: getDeviceSN method not found on CloudPrinter")
                    promise.reject("ERROR_METHOD_NOT_FOUND", "WiFi configuration methods not available on CloudPrinter API. Must use Bluetooth manager.", e)
                    return
                }
                
                printer.getDeviceSN { serialNumber ->
                    printDebugLog("🟢 received printer serial number: $serialNumber")
                    // Cache the serial number for later use in WiFi configuration
                    cachedSerialNumber = serialNumber
                    printDebugLog("🔵 Cached serial number: ${if (serialNumber?.isEmpty() == true) "<empty>" else "present"}")
                    PrinterSerialNumberNotifier.onSerialNumberReceived(serialNumber ?: "")
                }
                promise.resolve()
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR getting SN: ${e.message}")
                promise.reject("ERROR_GET_SN", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun enterNetworkMode(context: Context, serialNumber: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer == null) {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
            return
        }
        
        // WiFi configuration only works with Bluetooth-connected printers
        val printerInfo = printer.cloudPrinterInfo
        if (printerInfo == null) {
            printDebugLog("🔴 ERROR: Printer info is null")
            promise.reject("ERROR_NO_PRINTER_INFO", "Cannot get printer information", null)
            return
        }
        
        val isBluetoothPrinter = printerInfo.mac != null && printerInfo.mac.isNotEmpty()
        
        printDebugLog("🔵 ========== ENTER NETWORK MODE ==========")
        printDebugLog("🔵 Context type: ${context.javaClass.simpleName}")
        printDebugLog("🔵 Printer info: mac=${printerInfo.mac}, ip=${printerInfo.address}, name=${printerInfo.name}")
        printDebugLog("🔵 Is Bluetooth: $isBluetoothPrinter")
        printDebugLog("🔵 Provided SN: ${if (serialNumber.isEmpty()) "<empty>" else serialNumber}")
        
        if (!isBluetoothPrinter) {
            printDebugLog("🔴 ERROR: WiFi configuration requires Bluetooth connection")
            promise.reject(
                "ERROR_BLUETOOTH_REQUIRED",
                "WiFi configuration only works with printers connected via Bluetooth. Please connect to the printer using Bluetooth first.",
                null
            )
            return
        }
        
        // Proceed to enter network mode
        // Determine serial number to use - fallback chain: provided → cached → empty
        val snToUse: String = when {
            serialNumber.isNotEmpty() -> {
                printDebugLog("🔵 Using provided serial number: $serialNumber")
                serialNumber
            }
            cachedSerialNumber != null && cachedSerialNumber!!.isNotEmpty() -> {
                printDebugLog("🔵 Using cached serial number: $cachedSerialNumber")
                cachedSerialNumber!!
            }
            else -> {
                printDebugLog("🟡 WARNING: No serial number available, trying with empty string")
                printDebugLog("🟡 This may work if printer is physically paired")
                ""
            }
        }
        
        try {
            printDebugLog("🟢 Attempting to enter network mode...")
            printDebugLog("🔵 Serial number to use: ${if (snToUse.isEmpty()) "<EMPTY STRING>" else snToUse}")
            printDebugLog("🔵 Printer object: ${printer.javaClass.simpleName}")
            printDebugLog("🔵 SunmiPrinterManager instance: ${SunmiPrinterManager.getInstance()}")
            
            // Call Sunmi SDK to enter WiFi configuration mode
            printDebugLog("🔵 Calling startPrinterWifi NOW...")
            SunmiPrinterManager.getInstance().startPrinterWifi(context, printer, snToUse)
            
            printDebugLog("🟢 🟢 🟢 startPrinterWifi call completed without exception!")
            printDebugLog("🟢 Entered network mode successfully!")
            printDebugLog("🔵 You can now scan for WiFi networks using getWiFiList()")
            WiFiConfigStatusNotifier.onStatusUpdate("entered_network_mode")
            promise.resolve(null)
        } catch (e: Exception) {
            printDebugLog("🔴 ========== EXCEPTION IN startPrinterWifi ==========")
            printDebugLog("🔴 Error message: ${e.message}")
            printDebugLog("🔴 Exception type: ${e.javaClass.name}")
            printDebugLog("🔴 Cause: ${e.cause}")
            printDebugLog("🔴 Localized message: ${e.localizedMessage}")
            printDebugLog("🔴 Stack trace:")
            e.printStackTrace()
            printDebugLog("🔴 ================================================")
            printDebugLog("🔴 Possible causes:")
            printDebugLog("🔴   1. Printer firmware doesn't support WiFi config")
            printDebugLog("🔴   2. Bluetooth connection not stable")
            printDebugLog("🔴   3. Serial number required but invalid")
            printDebugLog("🔴   4. Printer already in network mode")
            printDebugLog("🔴   5. SDK version mismatch")
            WiFiConfigStatusNotifier.onStatusUpdate("failed")
            promise.reject("ERROR_ENTER_NETWORK_MODE", "Failed to enter network mode: ${e.message}. Check logs for details.", e)
        }
    }

    fun getWiFiList(context: Context, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            try {
                printDebugLog("🔵 getWiFiList called")
                
                // WiFi configuration only works with Bluetooth-connected printers
                val printerInfo = printer.cloudPrinterInfo
                val isBluetoothPrinter = printerInfo?.mac != null && printerInfo.mac.isNotEmpty()
                
                printDebugLog("🔵 Printer: ${printerInfo?.name}")
                printDebugLog("🔵 MAC: ${printerInfo?.mac}")
                printDebugLog("🔵 Is Bluetooth: $isBluetoothPrinter")
                
                if (!isBluetoothPrinter) {
                    printDebugLog("🔴 ERROR: WiFi list search requires Bluetooth connection")
                    promise.reject(
                        "ERROR_BLUETOOTH_REQUIRED",
                        "WiFi configuration only works with printers connected via Bluetooth.",
                        null
                    )
                    return
                }
                
                printDebugLog("🟢 Starting WiFi network scan...")
                printDebugLog("🔵 NOTE: Make sure you called enterNetworkMode() first!")
                
                // Clear previous networks
                WiFiNetworkNotifier.clearNetworks()
                
                SunmiPrinterManager.getInstance().searchPrinterWifiList(context, printer, object : WifiResult {
                    override fun onRouterFound(router: Router) {
                        printDebugLog("🟢 WiFi network found!")
                        printDebugLog("🔵   SSID: ${router.essid}")
                        printDebugLog("🔵   Name: ${router.name}")
                        printDebugLog("🔵   Signal: ${router.rssi} dBm")
                        printDebugLog("🔵   Password protected: ${router.isHasPwd}")
                        
                        // Notify through event emitter
                        WiFiNetworkNotifier.onNetworkFound(router)
                    }
                    
                    override fun onFinish() {
                        printDebugLog("🟢 🟢 🟢 WiFi network scan completed!")
                        printDebugLog("🔵 All available networks have been found")
                        promise.resolve(null)
                    }
                    
                    override fun onFailed() {
                        printDebugLog("🔴 WiFi network scan failed!")
                        printDebugLog("🔴 Make sure you called enterNetworkMode() first")
                        promise.reject("ERROR_WIFI_SEARCH_FAILED", "Failed to search WiFi networks. Did you call enterNetworkMode() first?", null)
                    }
                })
                
                printDebugLog("🔵 WiFi scan initiated, waiting for results...")
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR getting WiFi list: ${e.message}")
                printDebugLog("🔴 Exception type: ${e.javaClass.name}")
                printDebugLog("🔴 Stack trace: ${e.stackTraceToString()}")
                promise.reject("ERROR_GET_WIFI_LIST", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun configureWiFi(context: Context, ssid: String, password: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer == null) {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
            return
        }
        
        val printerInfo = printer.cloudPrinterInfo
        if (printerInfo == null) {
            printDebugLog("🔴 ERROR: Printer info is null")
            promise.reject("ERROR_NO_PRINTER_INFO", "Cannot get printer information", null)
            return
        }
        
        // WiFi configuration only works with Bluetooth-connected printers
        val isBluetoothPrinter = printerInfo.mac != null && printerInfo.mac.isNotEmpty()
        if (!isBluetoothPrinter) {
            printDebugLog("🔴 ERROR: WiFi configuration requires Bluetooth connection")
            promise.reject(
                "ERROR_BLUETOOTH_REQUIRED",
                "WiFi configuration only works with printers connected via Bluetooth.",
                null
            )
                    return
                }
        
        try {
            printDebugLog("🔵 ========== CONFIGURE WIFI ==========")
            printDebugLog("🔵 SSID: $ssid")
            printDebugLog("🔵 Password length: ${password.length}")
            printDebugLog("🔵 Printer: ${printerInfo.name} (MAC: ${printerInfo.mac})")
            printDebugLog("🔵 NOTE: Printer should already be in network mode (manual setup)")
            
            WiFiConfigStatusNotifier.onStatusUpdate("will_start_config")
            
            // Convert SSID to ByteArray as required by Sunmi SDK
            val essid = ssid.toByteArray(Charsets.UTF_8)
            printDebugLog("🔵 ESSID: ${essid.size} bytes")
            
            // Variable to track if we already resolved/rejected the promise
            var promiseHandled = false
            
            // Use Handler for async operations
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            
            // Track which callbacks were received
            var onSetWifiSuccessCalled = false
            var onConnectWifiSuccessCalled = false
            var onConnectWifiFailedCalled = false
            
            // Timeout handler - 30 seconds total
            val timeoutRunnable = Runnable {
                if (!promiseHandled) {
                    promiseHandled = true
                    printDebugLog("⏰ ⏰ ⏰ TIMEOUT after 30 seconds!")
                    printDebugLog("🔴 No connection result received from WiFi configuration")
                    printDebugLog("📊 Callback Status:")
                    printDebugLog("   - onSetWifiSuccess: ${if (onSetWifiSuccessCalled) "✅ CALLED" else "❌ NOT CALLED"}")
                    printDebugLog("   - onConnectWifiSuccess: ${if (onConnectWifiSuccessCalled) "✅ CALLED" else "❌ NOT CALLED"}")
                    printDebugLog("   - onConnectWifiFailed: ${if (onConnectWifiFailedCalled) "✅ CALLED" else "❌ NOT CALLED"}")
                    printDebugLog("📊 Configuration Details:")
                    printDebugLog("   - SSID: $ssid")
                    printDebugLog("   - SSID bytes: ${essid.size}")
                    printDebugLog("   - Password length: ${password.length}")
                    printDebugLog("   - Printer name: ${printerInfo.name}")
                    printDebugLog("   - Printer MAC: ${printerInfo.mac}")
                    printDebugLog("   - Printer IP: ${printerInfo.address}")
                    
                    // Exit WiFi config mode on timeout
                    try {
                        SunmiPrinterManager.getInstance().exitPrinterWifi(context, printer)
                        printDebugLog("🟢 Exited WiFi config mode (timeout cleanup)")
                    } catch (e: Exception) {
                        printDebugLog("🟡 Failed to exit: ${e.message}")
                    }
                    
                    WiFiConfigStatusNotifier.onStatusUpdate("timeout")
                    
                    // Build detailed error message
                    val debugInfo = mapOf(
                        "ssid" to ssid,
                        "ssidBytesLength" to essid.size,
                        "passwordLength" to password.length,
                        "printerName" to printerInfo.name,
                        "printerMAC" to printerInfo.mac,
                        "printerIP" to (printerInfo.address ?: "null"),
                        "onSetWifiSuccessCalled" to onSetWifiSuccessCalled,
                        "onConnectWifiSuccessCalled" to onConnectWifiSuccessCalled,
                        "onConnectWifiFailedCalled" to onConnectWifiFailedCalled
                    )
                    
                    promise.reject(
                        "ERROR_TIMEOUT", 
                        "WiFi config timeout after 60s. Callbacks: onSetWifiSuccess=${onSetWifiSuccessCalled}, onConnectSuccess=${onConnectWifiSuccessCalled}, onConnectFailed=${onConnectWifiFailedCalled}. Debug: $debugInfo",
                        null
                    )
                }
            }
            
            // Send WiFi credentials directly (printer should already in network mode)
            printDebugLog("🔵 Sending WiFi credentials...")
            
            // Start timeout counter - 60 seconds to allow for slow WiFi connection
            printDebugLog("🔵 Setting 60-second timeout for WiFi connection attempt...")
            handler.postDelayed(timeoutRunnable, 60000)
            
            try {
                SunmiPrinterManager.getInstance().setPrinterWifi(context, printer, essid, password, object : SetWifiCallback {
                    override fun onSetWifiSuccess() {
                        onSetWifiSuccessCalled = true
                        printDebugLog("🟢 🟢 🟢 ✅ onSetWifiSuccess() called! (timestamp: ${System.currentTimeMillis()})")
                        printDebugLog("🔵 WiFi credentials saved to printer")
                        WiFiConfigStatusNotifier.onStatusUpdate("saved")
                        printDebugLog("🔵 Waiting for connection callbacks (onConnectWifiSuccess or onConnectWifiFailed)...")
                        printDebugLog("🔵 The printer will attempt to connect to the WiFi network")
                        printDebugLog("🔵 Staying in config mode until connection result arrives...")
                        printDebugLog("🔵 This may take 10-30 seconds...")
                    }
                        
                    override fun onConnectWifiSuccess() {
                        onConnectWifiSuccessCalled = true
                        printDebugLog("🟢 🟢 🟢 ✅ ✅ ✅ onConnectWifiSuccess() called! (timestamp: ${System.currentTimeMillis()})")
                        
                        if (!promiseHandled) {
                            promiseHandled = true
                            handler.removeCallbacks(timeoutRunnable)
                            
                            printDebugLog("🎉 🎉 🎉 Printer connected to WiFi successfully!")
                            printDebugLog("📊 Total time from setPrinterWifi call to success")
                            
                            // Exit config mode after successful connection
                            printDebugLog("🔵 Exiting WiFi config mode...")
                            try {
                                SunmiPrinterManager.getInstance().exitPrinterWifi(context, printer)
                                printDebugLog("🟢 Exited WiFi config mode successfully")
                            } catch (e: Exception) {
                                printDebugLog("🟡 Warning: Failed to exit WiFi config mode: ${e.message}")
                            }
                            
                            WiFiConfigStatusNotifier.onStatusUpdate("success")
                            promise.resolve(null)
                        } else {
                            printDebugLog("⚠️ WARNING: onConnectWifiSuccess called but promise already handled!")
                        }
                    }
                        
                    override fun onConnectWifiFailed() {
                        onConnectWifiFailedCalled = true
                        printDebugLog("🔴 🔴 🔴 ❌ onConnectWifiFailed() called! (timestamp: ${System.currentTimeMillis()})")
                        
                        if (!promiseHandled) {
                            promiseHandled = true
                            handler.removeCallbacks(timeoutRunnable)
                            
                            printDebugLog("🔴 Failed to connect to WiFi network")
                            printDebugLog("🔴 Possible reasons:")
                            printDebugLog("🔴   1. Wrong WiFi password")
                            printDebugLog("🔴   2. Network not available or out of range")
                            printDebugLog("🔴   3. Signal too weak")
                            printDebugLog("🔴   4. Router MAC filtering enabled")
                            printDebugLog("🔴   5. Network security incompatible with printer")
                            
                            // Exit config mode after failed connection
                            printDebugLog("🔵 Exiting WiFi config mode...")
                            try {
                                SunmiPrinterManager.getInstance().exitPrinterWifi(context, printer)
                                printDebugLog("🟢 Exited WiFi config mode successfully")
                            } catch (e: Exception) {
                                printDebugLog("🟡 Warning: Failed to exit WiFi config mode: ${e.message}")
                            }
                            
                            WiFiConfigStatusNotifier.onStatusUpdate("failed")
                            promise.reject("ERROR_WIFI_CONNECT_FAILED", "Failed to connect to WiFi. Check password and signal.", null)
                        } else {
                            printDebugLog("⚠️ WARNING: onConnectWifiFailed called but promise already handled!")
                        }
                    }
                    })
                    printDebugLog("🟢 setPrinterWifi() called successfully (timestamp: ${System.currentTimeMillis()})")
                    printDebugLog("🔵 Registered SetWifiCallback with 3 methods:")
                    printDebugLog("🔵   - onSetWifiSuccess")
                    printDebugLog("🔵   - onConnectWifiSuccess")
                    printDebugLog("🔵   - onConnectWifiFailed")
            } catch (e: Exception) {
                if (!promiseHandled) {
                    promiseHandled = true
                    handler.removeCallbacks(timeoutRunnable)
                    printDebugLog("🔴 Exception calling setPrinterWifi: ${e.message}")
                    e.printStackTrace()
                WiFiConfigStatusNotifier.onStatusUpdate("failed")
                    promise.reject("ERROR_SET_WIFI", "Failed to set WiFi: ${e.message}", e)
                }
            }
            
            printDebugLog("🔵 WiFi configuration request sent, waiting for callbacks...")
            printDebugLog("🔵 Expected: onSetWifiSuccess → onConnectWifiSuccess/Failed")
        } catch (e: Exception) {
            printDebugLog("🔴 ========== EXCEPTION IN configureWiFi ==========")
            printDebugLog("🔴 Error message: ${e.message}")
            printDebugLog("🔴 Exception type: ${e.javaClass.name}")
            printDebugLog("🔴 Stack trace:")
            e.printStackTrace()
            printDebugLog("🔴 ================================================")
            WiFiConfigStatusNotifier.onStatusUpdate("failed")
            promise.reject("ERROR_CONFIG_WIFI", e.message, e)
        }
    }

    fun quitWiFiConfig(context: Context, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            try {
                printDebugLog("🟢 Calling exitPrinterWifi...")
                
                SunmiPrinterManager.getInstance().exitPrinterWifi(context, printer)
                
                printDebugLog("🟢 Exited WiFi config mode successfully")
                promise.resolve(null)
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR quitting WiFi config: ${e.message}")
                promise.reject("ERROR_QUIT_WIFI_CONFIG", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun deleteWiFiSettings(context: Context, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            try {
                printDebugLog("🟢 Calling deletePrinterWifi...")
                
                SunmiPrinterManager.getInstance().deletePrinterWifi(context, printer)
                
                printDebugLog("🟢 Deleted WiFi settings successfully")
                promise.resolve(null)
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR deleting WiFi settings: ${e.message}")
                promise.reject("ERROR_DELETE_WIFI", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    companion object {
        @JvmStatic
        fun printDebugLog(message: String) {
            if (BuildConfig.DEBUG) {
                Log.d(SDK_TAG, message)
            }
        }
    }

    private fun haveBluetoothPermissions(context: Context): Boolean {
        val grantedPermissions: Boolean
        val fineLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothScanPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            val bluetoothConnectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)

            grantedPermissions = fineLocationPermission == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    bluetoothScanPermission == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    bluetoothConnectPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            grantedPermissions = fineLocationPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return grantedPermissions
    }

    private fun connectToPrinter(context: Context, force: Boolean, printerInterface: PrinterInterface, value: String, promise: Promise) {
        try {

            var currentPrinter: CloudPrinter?

            currentPrinter = devices.find { printer ->
                when (printerInterface.method) {
                    SearchMethod.BT -> printer.cloudPrinterInfo.mac == value
                    SearchMethod.USB -> printer.cloudPrinterInfo.name == value
                    else -> printer.cloudPrinterInfo.address == value
                }
            }

            if (currentPrinter == null && printerInterface.method == SearchMethod.LAN && force) {
                // Add the printer manually
                currentPrinter = SunmiPrinterManager.getInstance().createCloudPrinter(value, 9100)
                devices = devices + currentPrinter
            } else if (currentPrinter == null){
                // Printer not found
                promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_FOUND)
                return
            }

            this.cloudPrinter = currentPrinter
            printDebugLog("🟢 will connect to ${printerInterface.name} printer: $value")
            currentPrinter!!.connect(context, object : ConnectCallback {
                override fun onConnect() {
                    PrinterConnectionNotifier.onPrinterConnectionUpdate(true)
                }

                override fun onFailed(s: String) {
                    printDebugLog("🔴 did fail to connect: $s")
                    PrinterConnectionNotifier.onPrinterConnectionUpdate(false)
                }

                override fun onDisConnect() {
                    PrinterConnectionNotifier.onPrinterConnectionUpdate(false)
                }
            })
            promise.resolve()
        } catch (error: Exception) {
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_FOUND)
        }
    }
}

fun CloudPrinter.toDictionary(): Map<String, Any?> {
    val info = this.cloudPrinterInfo
    if (info.vid > 0 && info.pid > 0) {
        // USB printer
        return mapOf(
            "interface" to PrinterInterface.USB.name,
            "name" to info.name,
            "signalStrength" to null,
            "uuid" to null,
            "ip" to null,
            "serialNumber" to null,
            "mode" to null
        )
    } else if (info.mac != null) {
        // BLUETOOTH printer
        return mapOf(
            "interface" to PrinterInterface.BLUETOOTH.name,
            "name" to info.name,
            "signalStrength" to null,
            "uuid" to info.mac,
            "ip" to null,
            "serialNumber" to null,
            "mode" to null
        )
    }
    // LAN printer
    return mapOf(
        "interface" to PrinterInterface.LAN.name,
        "name" to info.name,
        "signalStrength" to null,
        "uuid" to null,
        "ip" to info.address,
        "serialNumber" to null,
        "mode" to null
    )
}

fun Promise.rejectWithSunmiError(error: SunmiPrinterError) {
    reject(error.error, error.reason, null)
}