import ExpoModulesCore
import SunmiPrinterSDK

protocol SunmiManagerDelegate: AnyObject {
  func didUpdateDevices(list: [SunmiDevice]) -> Void
  func didConnectPrinter() -> Void
  func didDisconnectPrinter() -> Void
  func didReceiveWiFiNetwork(network: [String: Any]) -> Void
  func didFinishReceivingWiFiList() -> Void
  func didEnterNetworkMode() -> Void
  func didStartConfigPrinter() -> Void
  func didConfigPrinterSuccess() -> Void
  func didConfigPrinterFail() -> Void
  func didReceivePrinterSN(serialNumber: String) -> Void
}

class SunmiManager: NSObject {
  
  weak var delegate: SunmiManagerDelegate?
  private var timeout: Float
  private var devices: [SunmiDevice] {
    didSet {
      // Send a notification with the new updates
      self.delegate?.didUpdateDevices(list: devices)
    }
  }
  private var ipManager: SunmiPrinterIPManager? {
    didSet {
      // Once we retain a reference to the IP Manager, we subscribe to its delegate and to disconnection events
      ipManager?.delegate = self
      ipManager?.deviceDisConnect({ [weak self] error in
        printDebugLog("LAN device did disconnect:\(String(describing: error))")
        self?.currentPrinter = nil
        self?.delegate?.didDisconnectPrinter()
      })
    }
  }
  private var bluetoothManager: SunmiPrinterManager? {
    didSet {
      // Once we retain a reference to the Bluetooth Manager, we subscribe to its delegate and to disconnection events
      bluetoothManager?.bluetoothDelegate = self
      bluetoothManager?.deviceDisConnect(block: { [weak self] periperhal,error  in
        printDebugLog("Bluetooth device (\(String(describing: periperhal)) did disconnect:\(String(describing: error))")
        self?.currentPrinter = nil
        self?.delegate?.didDisconnectPrinter()
      })
    }
  }
  private var currentPrinter: InternalSunmiPrinter?
  private var command: SunmiPrinterCommand?
  
  override init() {
    self.timeout = 8000
    self.devices = []
    super.init()
  }
  
  func setTimeout(_ timeout: Float) {
    printDebugLog("🟢 did set timeout: [timeout=\(timeout)]")
    self.timeout = timeout
  }
  
  func discoverPrinters(printerInterface: PrinterInterface, promise: Promise) {
    printDebugLog("🟢 did start to discover printers: [interface=\(printerInterface.rawValue)]")
    
    switch printerInterface {
    case .bluetooth:
      if bluetoothManager == nil {
        // Set the Bluetooth manager to, under the hood, setup the subscriptions to the events
        bluetoothManager = SunmiPrinterManager.shareInstance()
      }
      break
    case .lan:
      if ipManager == nil {
        // Set the IP manager to, under the hood, setup the subscriptions to the events
        ipManager = SunmiPrinterIPManager.shared()
      }
      break
    }
    
    
    // Every time we trigger discover, we clear the list of devices
    devices = []
    
    let deadline = dispatchTime(fromMilliseconds: Int(timeout))
    DispatchQueue.global(qos: .userInitiated).asyncAfter(deadline: deadline, execute: {
      switch printerInterface {
      case .bluetooth:
        SunmiPrinterManager.shareInstance().scanPeripheral()
        break
      case .lan:
        SunmiPrinterIPManager.shared().startSearchPrinter(withIp: nil)
        break
      }
      promise.resolve()
    })
  }
  
  func connectBluetoothPrinter(uuid: String, promise: Promise) {    
    let manager = SunmiPrinterManager.shareInstance()
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    let bluetoothPrinter = devices.first(where: { device in
      if case .bluetooth(let sunmiPrinter) = device {
        return sunmiPrinter.uuid == uuid
      } else {
        return false
      }
    })
    guard case .bluetooth(let sunmiPrinter) = bluetoothPrinter else {
      promise.rejectWithSunmiError(SunmiPrinterError.printerNotSetup)
      return
    }
    let peripheral = sunmiPrinter.bluetoothPeripheral!
    currentPrinter = .bluetooth(peripheral: peripheral)
    manager.connect(peripheral)
    promise.resolve()
  }
  
  func connectLanPrinter(ipAddress: String, force: Bool, promise: Promise) {
    let manager = SunmiPrinterIPManager.shared()!
    if ipManager == nil {
      ipManager = manager
    }
    printDebugLog("🟢 will connect to printer at \(ipAddress)")
    
    // Check if the IP address is in the list. If it's a new one, we must add a new fake device
    let exist = devices.contains(where: { device in
      if case .ip(let sunmiPrinter) = device {
        return sunmiPrinter.ip == ipAddress
      } else {
        return false
      }
    })
    
    if (!exist) {
      if (force) {
        let sunmiPrinter = SunmiPrinterDevice(interface: PrinterInterface.lan.rawValue, name: "Manual", ip: ipAddress)
        devices.append(.ip(sunmiPrinter))
      } else {
        promise.reject(SunmiPrinterError.printerNotFound)
        return
      }
    }
    
    currentPrinter = .ip(address: ipAddress)
    manager.connectSocket(withIP: ipAddress)
    promise.resolve()
  }

  func isBluetoothPrinterConnected(uuid: String, promise: Promise) {
    // We check if the currently connected printer is the one that it's connected to uuid
    guard let currentPrinter = currentPrinter,
          case let .bluetooth(periperhal) = currentPrinter,
            periperhal.identifier.uuidString == uuid else {
      promise.resolve(false)
      return
    }
    promise.resolve(SunmiPrinterManager.shareInstance().bluetoothIsConnection())
  }
  
  func isLanPrinterConnected(ipAddress: String, promise: Promise) {
    // We check if the currently connected printer is the one that it's connected to ipAddress
    guard let currentPrinter = currentPrinter,
          case let .ip(address: address) = currentPrinter,
            ipAddress == address else {
      promise.resolve(false)
      return
    }
    promise.resolve(SunmiPrinterIPManager.shared().isConnectedIPService())
  }
  
  func disconnectPrinter(promise: Promise) {
    printDebugLog("🟢 will disconnect printer")
    guard let currentPrinter = currentPrinter else {
      promise.reject(SunmiPrinterError.printerNotSetup)
      return
    }
    self.currentPrinter = nil
    switch currentPrinter {
    case .bluetooth:
      SunmiPrinterManager.shareInstance().disConnectPeripheral()
      promise.resolve()
      break
    case .ip:
      SunmiPrinterIPManager.shared().disConnectIPService()
      promise.resolve()
      break
    }
  }
  
  // -----------------------
  // Low Level API methods
  // -----------------------
    
  func lineFeed(lines: Int32, promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.lineFeed(lines)
    promise.resolve()
  }
  
  func setTextAlign(alignment: Int, promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.setAlignment(SMAlignStyle(rawValue: alignment))
    promise.resolve()
  }
  
  func setPrintModesBold(bold: Bool, doubleHeight: Bool, doubleWidth: Bool, promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.setPrintModesBold(bold, double_h: doubleHeight, double_w: doubleWidth)
    promise.resolve()
  }

  func restoreDefaultSettings(promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.restoreDefaultSettings()
    promise.resolve()
  }
  
  func restoreDefaultLineSpacing(promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.restoreDefaultLineSpacing()
    promise.resolve()
  }
  
  func addCut(fullCut: Bool, promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.cutPaper(fullCut)
    promise.resolve()
  }
  
  func addText(text: String, promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.appendText(text)
    promise.resolve()
  }
  
  func addImage(base64: String, imageWidth: Int, imageHeight: Int, promise: Promise) {
    guard let image = imageFromBase64(base64) else {
      promise.rejectWithSunmiError(SunmiPrinterError.notValidImage)
      return
    }
      
    let imgHeight = image.size.height
    let imgWidth = image.size.width
      
    let size = CGSize(width: CGFloat(imageWidth), height: imgHeight*CGFloat(imageWidth)/imgWidth)
    guard let scaledImage = scaleImage(image, size: size) else {
      promise.rejectWithSunmiError(SunmiPrinterError.notValidImageSize)
      return
    }
    
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.append(scaledImage, mode: SMImageAlgorithm_DITHERING)
    promise.resolve()
  }
  
  func clearBuffer(promise: Promise) {
    self.command = nil
    let command = makeSunmiCommand()
    command.clearBuffer()
    self.command = command
    promise.resolve()
  }
  
  func sendData(promise: Promise) {
    guard let printer = currentPrinter else {
      promise.rejectWithSunmiError(SunmiPrinterError.printerNotSetup)
      return
    }
    
    switch printer {
    case .bluetooth:
      let bluetoothManager = SunmiPrinterManager.shareInstance()
      guard bluetoothManager.bluetoothIsConnection() else {
        promise.rejectWithSunmiError(SunmiPrinterError.printerNotConnected)
        return
      }
      bluetoothManager.sendSuccess({
        promise.resolve()
      })
      bluetoothManager.sendFail({ error in
        promise.reject(error ?? SunmiPrinterError.printerNotSetup)
      })
      bluetoothManager.sendPrint(command?.getData())
    case .ip:
      let ipManager = SunmiPrinterIPManager.shared()
      guard let ipManager = ipManager, ipManager.isConnectedIPService() else {
        promise.rejectWithSunmiError(SunmiPrinterError.printerNotConnected)
        return
      }
      guard let commandData = command?.getData() else {
        promise.rejectWithSunmiError(SunmiPrinterError.emptyBuffer)
        return
      }
      ipManager.controlDevicePrinting(commandData, success: {
        promise.resolve()
      }, fail: { error in
        promise.reject(error!)
      })
    }
  }
  
  func sendAndReceivePrinterState(promise: Promise) {
    guard let printer = currentPrinter else {
      promise.rejectWithSunmiError(SunmiPrinterError.printerNotSetup)
      return
    }
    
    switch printer {
    case .bluetooth:
      let bluetoothManager = SunmiPrinterManager.shareInstance()
      guard bluetoothManager.bluetoothIsConnection() else {
        promise.rejectWithSunmiError(SunmiPrinterError.printerNotConnected)
        return
      }
      bluetoothManager.sendPrint(command?.getData())
      bluetoothManager.receivedDeviceData({
        deviceSn, printerStatus, taskNumber in
        promise.resolve(printerStatus.sdkStatus)
      })
    case .ip:
      let ipManager = SunmiPrinterIPManager.shared()
      guard let ipManager = ipManager, ipManager.isConnectedIPService() else {
        promise.rejectWithSunmiError(SunmiPrinterError.printerNotConnected)
        return
      }
      guard let commandData = command?.getData() else {
        promise.rejectWithSunmiError(SunmiPrinterError.emptyBuffer)
        return
      }
      ipManager.controlDevicePrinting(commandData, success: nil, fail: nil, response: {
        deviceSn, printerStatus, taskNumber in
        promise.resolve(printerStatus.sdkStatus)
      })
    }
  }
  
  func openCashDrawer(promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.openCashBox()
    promise.resolve()
  }
  
  func getDeviceState(promise: Promise) {
    guard let command = command else {
      promise.reject(SunmiPrinterError.emptyBuffer)
      return
    }
    command.getDeviceState()
    sendAndReceivePrinterState(promise: promise)
  }
  
  // -----------------------
  // WiFi Configuration APIs
  // -----------------------
  
  func getPrinterSerialNumber(promise: Promise) {
    let manager = SunmiPrinterManager.shareInstance()
    
    // CRITICAL: Ensure delegate is set BEFORE calling any methods
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    // Verify delegate is properly set
    manager.bluetoothDelegate = self
    
    // Check if bluetooth is connected
    guard manager.bluetoothIsConnection() else {
      printDebugLog("🔴 ERROR: Bluetooth not connected. Cannot get serial number.")
      promise.reject(SunmiPrinterError.printerNotConnected)
      return
    }
    
    printDebugLog("🟢 Requesting printer serial number...")
    manager.getPrinterSN()
    promise.resolve()
  }
  
  func enterNetworkMode(serialNumber: String, promise: Promise) {
    let manager = SunmiPrinterManager.shareInstance()
    
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    // Ensure delegate is set
    manager.bluetoothDelegate = self
    
    guard manager.bluetoothIsConnection() else {
      printDebugLog("🔴 ERROR: Bluetooth not connected. Cannot enter network mode.")
      promise.reject(SunmiPrinterError.printerNotConnected)
      return
    }
    
    printDebugLog("🟢 Entering network mode with SN: \(serialNumber)")
    manager.enterNetworkMode(serialNumber)
    promise.resolve()
  }
  
  func getWiFiList(promise: Promise) {
    let manager = SunmiPrinterManager.shareInstance()
    
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    // Ensure delegate is set
    manager.bluetoothDelegate = self
    
    guard manager.bluetoothIsConnection() else {
      printDebugLog("🔴 ERROR: Bluetooth not connected. Cannot get WiFi list.")
      promise.reject(SunmiPrinterError.printerNotConnected)
      return
    }
    
    printDebugLog("🟢 Requesting WiFi list...")
    manager.getWifiList()
    promise.resolve()
  }
  
  func configureWiFi(ssid: String, password: String, promise: Promise) {
    let manager = SunmiPrinterManager.shareInstance()
    
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    // Ensure delegate is set
    manager.bluetoothDelegate = self
    
    guard manager.bluetoothIsConnection() else {
      printDebugLog("🔴 ERROR: Bluetooth not connected. Cannot configure WiFi.")
      promise.reject(SunmiPrinterError.printerNotConnected)
      return
    }
    
    printDebugLog("🟢 Configuring WiFi: SSID=\(ssid)")
    manager.connectAP(ssid, password: password)
    promise.resolve()
  }
  
  func quitWiFiConfig(promise: Promise) {
    let manager = SunmiPrinterManager.shareInstance()
    
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    // Ensure delegate is set
    manager.bluetoothDelegate = self
    
    guard manager.bluetoothIsConnection() else {
      printDebugLog("🔴 ERROR: Bluetooth not connected. Cannot quit WiFi config.")
      promise.reject(SunmiPrinterError.printerNotConnected)
      return
    }
    
    printDebugLog("🟢 Quitting WiFi configuration mode...")
    manager.quitConnectAP()
    promise.resolve()
  }
  
  func deleteWiFiSettings(promise: Promise) {
    let manager = SunmiPrinterManager.shareInstance()
    
    if bluetoothManager == nil {
      bluetoothManager = manager
    }
    
    // Ensure delegate is set
    manager.bluetoothDelegate = self
    
    guard manager.bluetoothIsConnection() else {
      printDebugLog("🔴 ERROR: Bluetooth not connected. Cannot delete WiFi settings.")
      promise.reject(SunmiPrinterError.printerNotConnected)
      return
    }
    
    printDebugLog("🟢 Deleting WiFi settings...")
    manager.deleteWifiSetting()
    promise.resolve()
  }
}

extension SunmiManager: PrinterManagerDelegate {
  func discoveredDevice(_ bluetoothDevice: SunmiBlePrinterModel) {
    printDebugLog("🟢 did discover a bluetooth device: [\(bluetoothDevice.deviceName), \(bluetoothDevice.uuidString)]")
    
    let hasDevice = devices.contains(where: { device in
      if case .bluetooth(let sunmiBluetoothDevice) = device {
        return sunmiBluetoothDevice.uuid == bluetoothDevice.uuidString
      } else {
        return false
      }
    })
    
    // We only include the device if we're sure the device is not already in the list
    if !hasDevice {
      devices.append(.bluetooth(bluetoothDevice))
    }
  }
  
  func didConectPrinter() {
    printDebugLog("🟢 did connect to Bluetooth printer")
    delegate?.didConnectPrinter()
  }
  
  func willDisconnectPrinter() {
    printDebugLog("🔴 did disconnect from Bluetooth printer")
    currentPrinter = nil
    delegate?.didDisconnectPrinter()
  }
  
  func receiveDeviceSn(_ sn: String?) {
    printDebugLog("🟢 🟢 🟢 DELEGATE CALLBACK: received printer serial number: \(sn ?? "unknown")")
    if let sn = sn {
      printDebugLog("🟢 Notifying delegate with SN: \(sn)")
      delegate?.didReceivePrinterSN(serialNumber: sn)
    } else {
      printDebugLog("🔴 Serial number is nil!")
    }
  }
  
  func willStartReceiveDeviceSn() {
    printDebugLog("🟢 DELEGATE CALLBACK: willStartReceiveDeviceSn - printer is processing request")
  }
  
  func didEnterNetworkMode() {
    printDebugLog("🟢 🟢 🟢 DELEGATE CALLBACK: entered network mode")
    delegate?.didEnterNetworkMode()
  }
  
  func receiveAPInfo(_ apInfo: [AnyHashable : Any]?) {
    printDebugLog("🟢 🟢 🟢 DELEGATE CALLBACK: received WiFi network info: \(String(describing: apInfo))")
    if let apInfo = apInfo as? [String: Any] {
      printDebugLog("🟢 Notifying delegate with network: \(apInfo)")
      delegate?.didReceiveWiFiNetwork(network: apInfo)
    } else {
      printDebugLog("🔴 Could not cast apInfo to [String: Any]")
    }
  }
  
  func didReceiveAllApInfo() {
    printDebugLog("🟢 🟢 🟢 DELEGATE CALLBACK: finished receiving all WiFi networks")
    delegate?.didFinishReceivingWiFiList()
  }
  
  func didFailReceiveApInfo() {
    printDebugLog("🔴 🔴 🔴 DELEGATE CALLBACK: failed to receive WiFi info")
  }
  
  func willStartConfigPrinter() {
    printDebugLog("🟢 🟢 🟢 DELEGATE CALLBACK: will start configuring WiFi")
    delegate?.didStartConfigPrinter()
  }
  
  func configPrinterSuccess() {
    printDebugLog("🟢 🟢 🟢 DELEGATE CALLBACK: WiFi configuration success")
    delegate?.didConfigPrinterSuccess()
  }
  
  func configPrinterFail() {
    printDebugLog("🔴 🔴 🔴 DELEGATE CALLBACK: WiFi configuration failed")
    delegate?.didConfigPrinterFail()
  }
}

extension SunmiManager: IPPrinterManagerDelegate {
  func discoverIPPrinter(_ printerModel: SunmiIpPrinterModel!) {
    printDebugLog("🟢 did discover an ip device: [\(printerModel.deviceName), \(printerModel.deviceIP), \(printerModel.deviceSN)]")
    
    let hasDevice = devices.contains(where: { device in
      if case .ip(let sunmiIpDevice) = device {
        return sunmiIpDevice.name == printerModel.name
      } else {
        return false
      }
    })
    
    // We only include the device if we're sure the device is not already in the list
    if !hasDevice {
      devices.append(.ip(printerModel))
    }
  }
  
  func didConnectedIPPrinter() {
    printDebugLog("🟢 did connect to IP printer: \(String(describing: delegate))")
    delegate?.didConnectPrinter()
  }
  
  func didConnectedIPPrinterWithError(_ error: (any Error)!) {
    printDebugLog("🔴 did fail to connect to printer")
    printDebugLog(error.debugDescription)
    currentPrinter = nil
    delegate?.didDisconnectPrinter()
  }
  
  func finshedSearchPrinter() {
    printDebugLog("🟢 did finish search printer")
  }
  
  func didCancelSearching() {
    printDebugLog("🟢 did cancel search printer")
  }
}

private extension SunmiManager {
  func dispatchTime(fromMilliseconds milliseconds: Int) -> DispatchTime {
      let seconds = milliseconds / 1000
      let nanoSeconds = (milliseconds % 1000) * 1_000_000
      let uptimeNanoseconds = DispatchTime.now().uptimeNanoseconds + UInt64(seconds) * 1_000_000_000 + UInt64(nanoSeconds)
      return DispatchTime(uptimeNanoseconds: uptimeNanoseconds)
  }
  
  func imageFromBase64(_ base64: String) -> UIImage? {
    if let data = Data(base64Encoded: base64) {
      return UIImage(data: data)
    }
    return nil
  }
  
  func scaleImage(_ image: UIImage, size: CGSize) -> UIImage? {
    let scale: CGFloat = max(size.width/image.size.width, size.height/image.size.height);
    let width: CGFloat = image.size.width * scale;
    let height: CGFloat = image.size.height * scale;
    let imageRect: CGRect = CGRectMake((size.width - width)/2.0,
                                       (size.height - height)/2.0,
                                       width,
                                       height);

    UIGraphicsBeginImageContextWithOptions(size, false, 0);
    image.draw(in: imageRect)
    let newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
  }
  
  func makeSunmiCommand() -> SunmiPrinterCommand {
    let command = SunmiPrinterCommand()
    command.setUtf8Mode(1)
    command.setPrintWidth(576)
    return command
  }
}

extension SunmiBlePrinterModel: SunmiPrinter {
  
  var interface: String {
    return PrinterInterface.bluetooth.rawValue
  }
  
  var name: String {
    return self.deviceName
  }
  
  var signalStrength: Float? {
    return Float(truncating: self.rssi)
  }
  
  var uuid: String? {
    return self.uuidString
  }
  
  var ip: String? {
    return nil
  }
  
  var serialNumber: String? {
    return nil
  }
  
  var mode: String? {
    return nil
  }
  
  var bluetoothPeripheral: CBPeripheral? {
    return self.peripheral
  }
}

extension SunmiIpPrinterModel: SunmiPrinter {
  var interface: String {
    return PrinterInterface.lan.rawValue
  }
  
  var name: String {
    return self.deviceName
  }
  
  var signalStrength: Float? {
    return nil
  }
  
  var uuid: String? {
    return nil
  }
  
  var ip: String? {
    return self.deviceIP
  }
  
  var serialNumber: String? {
    return self.deviceSN
  }
  
  var mode: String? {
    return self.deviceMode
  }
  
  var bluetoothPeripheral: CBPeripheral? {
    return nil
  }
}

extension Promise {
  func rejectWithSunmiError(_ error: SunmiPrinterError) {
    reject(error.code, error.localizedDescription)
  }
}

private extension SMPrinterStatus {
  var sdkStatus: String {
    switch (self) {
    case SMPrinterStatus_Printing:
      return "RUNNING"
    case SMPrinterStatus_NoPaper:
      return "OUT_PAPER"
    case SMPrinterStatus_PaperJam:
      return "JAM_PAPER"
    case SMPrinterStatus_NoPaperPickup, SMPrinterStatus(17):
      return "PICK_PAPER"
    case SMPrinterStatus_CoverOpened, SMPrinterStatus(33):
      return "COVER"
    case SMPrinterStatus_HeadOverheating:
      return "OVER_HOT"
    case SMPrinterStatus_MotorOverheating:
      return "MOTOR_HOT"
    case SMPrinterStatus_RollIsExhausted:
      return "NEAR_OUT_PAPER"
    default:
      return "UNKNOWN"
    }
  }
}
