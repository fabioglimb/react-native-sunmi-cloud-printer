package expo.modules.sunmicloudprinter

import com.sunmi.cloudprinter.bean.Router

object WiFiNetworkNotifier {
    private val observers = mutableListOf<(networks: List<Any>) -> Unit>()
    private val currentNetworks = mutableListOf<Router>()

    fun registerObserver(observer: (networks: List<Any>) -> Unit) {
        observers.add(observer)
    }

    fun deregisterObserver(observer: (networks: List<Any>) -> Unit) {
        observers.remove(observer)
    }

    fun onNetworkListReceived(networks: List<Any>) {
        observers.forEach { it(networks) }
    }
    
    // Method to handle individual Router objects from searchPrinterWifiList
    fun onNetworkFound(router: Router) {
        // Add to current list and notify observers
        currentNetworks.add(router)
        
        // Convert Router to a map for JS
        val networkMap = mapOf(
            "name" to router.name,
            "hasPwd" to router.isHasPwd, // Note: Router uses isHasPwd() method
            "rssi" to router.rssi,
            "essid" to router.essid
        )
        
        // Notify observers with the full list so far
        observers.forEach { it(listOf(networkMap)) }
    }
    
    fun clearNetworks() {
        currentNetworks.clear()
    }
}

