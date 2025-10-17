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
        if (printer != null) {
            // WiFi configuration only works with Bluetooth-connected printers
            val printerInfo = printer.cloudPrinterInfo
            val isBluetoothPrinter = printerInfo?.mac != null && printerInfo.mac.isNotEmpty()
            
            printDebugLog("🔵 enterNetworkMode called")
            printDebugLog("🔵 Printer info: mac=${printerInfo?.mac}, ip=${printerInfo?.address}, name=${printerInfo?.name}")
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
            
            // Use provided serial number, or cached one, or empty string
            val snToUse = when {
                serialNumber.isNotEmpty() -> {
                    printDebugLog("🔵 Using provided serial number: $serialNumber")
                    serialNumber
                }
                cachedSerialNumber != null && cachedSerialNumber!!.isNotEmpty() -> {
                    printDebugLog("🔵 Using cached serial number: $cachedSerialNumber")
                    cachedSerialNumber!!
                }
                else -> {
                    printDebugLog("🟡 WARNING: No serial number available, using empty string")
                    ""
                }
            }
            
            try {
                printDebugLog("🟢 Calling startPrinterWifi with SN: ${if (snToUse.isEmpty()) "<empty>" else snToUse}")
                
                SunmiPrinterManager.getInstance().startPrinterWifi(context, printer, snToUse)
                
                printDebugLog("🟢 Entered network mode successfully")
                WiFiConfigStatusNotifier.onStatusUpdate("entered_network_mode")
                promise.resolve(null)
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR entering network mode: ${e.message}")
                printDebugLog("🔴 Exception type: ${e.javaClass.name}")
                printDebugLog("🔴 Stack trace: ${e.stackTraceToString()}")
                WiFiConfigStatusNotifier.onStatusUpdate("failed")
                promise.reject("ERROR_ENTER_NETWORK_MODE", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun getWiFiList(context: Context, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            try {
                // WiFi configuration only works with Bluetooth-connected printers
                val printerInfo = printer.cloudPrinterInfo
                val isBluetoothPrinter = printerInfo?.mac != null && printerInfo.mac.isNotEmpty()
                
                if (!isBluetoothPrinter) {
                    printDebugLog("🔴 ERROR: WiFi list search requires Bluetooth connection")
                    promise.reject(
                        "ERROR_BLUETOOTH_REQUIRED",
                        "WiFi configuration only works with printers connected via Bluetooth.",
                        null
                    )
                    return
                }
                
                printDebugLog("🟢 Calling searchPrinterWifiList...")
                
                SunmiPrinterManager.getInstance().searchPrinterWifiList(context, printer, object : WifiResult {
                    override fun onRouterFound(router: Router) {
                        printDebugLog("🟢 WiFi found: ${router.name}, signal: ${router.rssi}")
                        // Notify through event emitter
                        WiFiNetworkNotifier.onNetworkFound(router)
                    }
                    
                    override fun onFinish() {
                        printDebugLog("🟢 WiFi search completed")
                        promise.resolve(null)
                    }
                    
                    override fun onFailed() {
                        printDebugLog("🔴 WiFi search failed")
                        promise.reject("ERROR_WIFI_SEARCH_FAILED", "Failed to search WiFi networks", null)
                    }
                })
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR getting WiFi list: ${e.message}")
                promise.reject("ERROR_GET_WIFI_LIST", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
        }
    }

    fun configureWiFi(context: Context, ssid: String, password: String, promise: Promise) {
        val printer = cloudPrinter
        if (printer != null) {
            try {
                printDebugLog("🟢 Calling setPrinterWifi: SSID=$ssid")
                
                val essid = ssid.toByteArray(Charsets.UTF_8)
                WiFiConfigStatusNotifier.onStatusUpdate("will_start_config")
                
                SunmiPrinterManager.getInstance().setPrinterWifi(context, printer, essid, password, object : SetWifiCallback {
                    override fun onSetWifiSuccess() {
                        printDebugLog("🟢 WiFi configuration saved to printer")
                        WiFiConfigStatusNotifier.onStatusUpdate("saved")
                    }
                    
                    override fun onConnectWifiSuccess() {
                        printDebugLog("🟢 🟢 🟢 WiFi connected successfully")
                        WiFiConfigStatusNotifier.onStatusUpdate("success")
                        promise.resolve(null)
                    }
                    
                    override fun onConnectWifiFailed() {
                        printDebugLog("🔴 Failed to connect to WiFi")
                        WiFiConfigStatusNotifier.onStatusUpdate("failed")
                        promise.reject("ERROR_WIFI_CONNECT_FAILED", "Failed to connect to WiFi network", null)
                    }
                })
            } catch (e: Exception) {
                printDebugLog("🔴 ERROR configuring WiFi: ${e.message}")
                WiFiConfigStatusNotifier.onStatusUpdate("failed")
                promise.reject("ERROR_CONFIG_WIFI", e.message, e)
            }
        } else {
            printDebugLog("🔴 ERROR: Printer not connected")
            promise.rejectWithSunmiError(SunmiPrinterError.PRINTER_NOT_CONNECTED)
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