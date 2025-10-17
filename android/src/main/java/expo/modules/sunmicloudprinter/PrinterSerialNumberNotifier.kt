package expo.modules.sunmicloudprinter

object PrinterSerialNumberNotifier {
    private val observers = mutableListOf<(serialNumber: String) -> Unit>()

    fun registerObserver(observer: (serialNumber: String) -> Unit) {
        observers.add(observer)
    }

    fun deregisterObserver(observer: (serialNumber: String) -> Unit) {
        observers.remove(observer)
    }

    fun onSerialNumberReceived(serialNumber: String) {
        observers.forEach { it(serialNumber) }
    }
}

