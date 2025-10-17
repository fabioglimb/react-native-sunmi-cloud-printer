package expo.modules.sunmicloudprinter

object WiFiConfigStatusNotifier {
    private val observers = mutableListOf<(status: String) -> Unit>()

    fun registerObserver(observer: (status: String) -> Unit) {
        observers.add(observer)
    }

    fun deregisterObserver(observer: (status: String) -> Unit) {
        observers.remove(observer)
    }

    fun onStatusUpdate(status: String) {
        observers.forEach { it(status) }
    }
}

