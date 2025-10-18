package expo.modules.sunmicloudprinter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.RemoteException
import android.util.Base64
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import expo.modules.kotlin.Promise

/**
 * Manager for Sunmi Inner Printer (embedded printer in Sunmi devices)
 * This uses the Sunmi Inner Printer SDK for devices with built-in thermal printers.
 * 
 * Note: This only works on Android Sunmi devices with embedded printers.
 * iOS will return errors for all methods.
 */
class SunmiInnerPrinterManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SunmiInnerPrinter"
        
        @JvmStatic
        fun printDebugLog(message: String) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, message)
            }
        }
    }
    
    private var printerService: SunmiPrinterService? = null
    private var isServiceBound = false
    
    // Callback for printer service connection
    private val innerPrinterCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService?) {
            printDebugLog("🟢 Inner printer service connected")
            printerService = service
            isServiceBound = true
        }
        
        override fun onDisconnected() {
            printDebugLog("🔴 Inner printer service disconnected")
            printerService = null
            isServiceBound = false
        }
    }
    
    // Generic result callback
    private fun createResultCallback(promise: Promise) = object : InnerResultCallback() {
        override fun onRunResult(isSuccess: Boolean) {
            if (isSuccess) {
                printDebugLog("🟢 Operation successful")
                promise.resolve(true)
            } else {
                printDebugLog("🔴 Operation failed")
                promise.reject("ERROR_OPERATION_FAILED", "Printer operation failed", null)
            }
        }
        
        override fun onReturnString(result: String?) {
            printDebugLog("🟢 Received string result: $result")
            promise.resolve(result)
        }
        
        override fun onRaiseException(code: Int, msg: String?) {
            printDebugLog("🔴 Exception raised: code=$code, msg=$msg")
            promise.reject("ERROR_PRINTER_EXCEPTION", msg ?: "Printer exception occurred (code: $code)", null)
        }
        
        override fun onPrintResult(code: Int, msg: String?) {
            printDebugLog("Print result: code=$code, msg=$msg")
            if (code == 0) {
                promise.resolve(true)
            } else {
                promise.reject("ERROR_PRINT_FAILED", msg ?: "Print failed (code: $code)", null)
            }
        }
    }
    
    /**
     * Initialize and bind to the printer service
     */
    fun bindService(): Boolean {
        return try {
            printDebugLog("🔵 Binding to inner printer service...")
            InnerPrinterManager.getInstance().bindService(context, innerPrinterCallback)
            true
        } catch (e: RemoteException) {
            printDebugLog("🔴 Failed to bind service: ${e.message}")
            false
        }
    }
    
    /**
     * Unbind from the printer service
     */
    fun unbindService() {
        try {
            printDebugLog("🔵 Unbinding from inner printer service...")
            InnerPrinterManager.getInstance().unBindService(context, innerPrinterCallback)
            printerService = null
            isServiceBound = false
        } catch (e: Exception) {
            printDebugLog("🔴 Failed to unbind service: ${e.message}")
        }
    }
    
    /**
     * Check if printer service is available
     */
    fun hasPrinterService(): Boolean {
        return printerService != null
    }
    
    // ===============================================
    // INITIALIZATION & STATUS
    // ===============================================
    
    /**
     * Initialize printer - resets printer logic but doesn't clear buffer
     */
    fun printerInit(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Initializing printer...")
            service.printerInit(createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_INIT_FAILED", e.message, e)
        }
    }
    
    /**
     * Get printer status
     * Returns status code:
     * 0: Normal
     * 1: Preparing
     * 2: Abnormal communication
     * 3: Out of paper
     * 4: Overheated
     * 8: No printer connected
     * 9: Firmware upgrade
     * 505: Printer not detected
     */
    fun getPrinterStatus(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            val status = service.updatePrinterState()
            printDebugLog("🔵 Printer status: $status")
            promise.resolve(status)
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_GET_STATUS", e.message, e)
        }
    }
    
    /**
     * Get printer serial number
     */
    fun getPrinterSerialNo(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            val serialNo = service.printerSerialNo
            printDebugLog("🔵 Printer serial: $serialNo")
            promise.resolve(serialNo)
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_GET_SERIAL", e.message, e)
        }
    }
    
    /**
     * Get printer firmware version
     */
    fun getPrinterVersion(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            val version = service.printerVersion
            printDebugLog("🔵 Printer version: $version")
            promise.resolve(version)
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_GET_VERSION", e.message, e)
        }
    }
    
    /**
     * Get printer model
     */
    fun getPrinterModel(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            val model = service.printerModal
            printDebugLog("🔵 Printer model: $model")
            promise.resolve(model)
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_GET_MODEL", e.message, e)
        }
    }
    
    /**
     * Get printer paper width (58mm or 80mm)
     */
    fun getPrinterPaper(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            val paperType = service.printerPaper
            val paper = if (paperType == 1) "58mm" else "80mm"
            printDebugLog("🔵 Printer paper: $paper")
            promise.resolve(paper)
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_GET_PAPER", e.message, e)
        }
    }
    
    // ===============================================
    // PRINTING - TEXT
    // ===============================================
    
    /**
     * Print text
     */
    fun printText(text: String, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Printing text: ${text.take(50)}...")
            service.printText(text, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_PRINT_TEXT", e.message, e)
        }
    }
    
    /**
     * Print text with custom font
     */
    fun printTextWithFont(text: String, typeface: String, fontSize: Float, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Printing text with font: $typeface, size: $fontSize")
            service.printTextWithFont(text, typeface, fontSize, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_PRINT_TEXT", e.message, e)
        }
    }
    
    /**
     * Set text alignment
     * 0 = LEFT, 1 = CENTER, 2 = RIGHT
     */
    fun setAlignment(alignment: Int, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Setting alignment: $alignment")
            service.setAlignment(alignment, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_SET_ALIGNMENT", e.message, e)
        }
    }
    
    /**
     * Set font size
     */
    fun setFontSize(fontSize: Float, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Setting font size: $fontSize")
            service.setFontSize(fontSize, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_SET_FONT_SIZE", e.message, e)
        }
    }
    
    /**
     * Line wrap - print N lines
     */
    fun lineWrap(lines: Int, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Line wrap: $lines lines")
            service.lineWrap(lines, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_LINE_WRAP", e.message, e)
        }
    }
    
    // ===============================================
    // PRINTING - IMAGES & BARCODES
    // ===============================================
    
    /**
     * Print bitmap from base64 string
     */
    fun printBitmap(base64Data: String, width: Int, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Printing bitmap, width: $width")
            
            // Remove data URL prefix if present
            val pureBase64 = if (base64Data.contains(",")) {
                base64Data.substring(base64Data.indexOf(",") + 1)
            } else {
                base64Data
            }
            
            val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            
            if (decodedBitmap == null) {
                promise.reject("ERROR_INVALID_IMAGE", "Failed to decode image", null)
                return
            }
            
            // Scale bitmap if needed
            val scaledBitmap = if (width > 0 && width != decodedBitmap.width) {
                val aspectRatio = decodedBitmap.height.toFloat() / decodedBitmap.width.toFloat()
                val scaledHeight = (width * aspectRatio).toInt()
                Bitmap.createScaledBitmap(decodedBitmap, width, scaledHeight, false)
            } else {
                decodedBitmap
            }
            
            service.printBitmap(scaledBitmap, createResultCallback(promise))
        } catch (e: Exception) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_PRINT_BITMAP", e.message, e)
        }
    }
    
    /**
     * Print 1D barcode
     * @param data Barcode data
     * @param symbology Barcode type (8 = CODE128, 0 = UPC-A, etc)
     * @param height Height in pixels
     * @param width Width (2-6)
     * @param textPosition Text position (0 = no text, 1 = above, 2 = below, 3 = both)
     */
    fun printBarCode(data: String, symbology: Int, height: Int, width: Int, textPosition: Int, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Printing barcode: $data")
            service.printBarCode(data, symbology, height, width, textPosition, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_PRINT_BARCODE", e.message, e)
        }
    }
    
    /**
     * Print QR code
     * @param data QR code data
     * @param moduleSize Module size (4-16, default 8)
     * @param errorLevel Error correction level (0-3, default 1)
     */
    fun printQRCode(data: String, moduleSize: Int, errorLevel: Int, promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Printing QR code, size: $moduleSize, error level: $errorLevel")
            service.printQRCode(data, moduleSize, errorLevel, createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_PRINT_QRCODE", e.message, e)
        }
    }
    
    // ===============================================
    // PAPER CONTROL
    // ===============================================
    
    /**
     * Cut paper
     */
    fun cutPaper(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Cutting paper...")
            service.cutPaper(createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_CUT_PAPER", e.message, e)
        }
    }
    
    /**
     * Open cash drawer
     */
    fun openCashDrawer(promise: Promise) {
        val service = printerService
        if (service == null) {
            promise.reject("ERROR_NO_SERVICE", "Printer service not available", null)
            return
        }
        
        try {
            printDebugLog("🔵 Opening cash drawer...")
            service.openDrawer(createResultCallback(promise))
        } catch (e: RemoteException) {
            printDebugLog("🔴 ERROR: ${e.message}")
            promise.reject("ERROR_OPEN_DRAWER", e.message, e)
        }
    }
}

