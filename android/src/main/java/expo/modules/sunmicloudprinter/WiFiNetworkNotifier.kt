package expo.modules.sunmicloudprinter

object WiFiNetworkNotifier {
    private val observers = mutableListOf<(networks: List<Any>) -> Unit>()

    fun registerObserver(observer: (networks: List<Any>) -> Unit) {
        observers.add(observer)
    }

    fun deregisterObserver(observer: (networks: List<Any>) -> Unit) {
        observers.remove(observer)
    }

    fun onNetworkListReceived(networks: List<Any>) {
        observers.forEach { it(networks) }
    }
}

