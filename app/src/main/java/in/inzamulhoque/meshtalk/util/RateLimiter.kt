package `in`.inzamulhoque.meshtalk.util

import java.util.concurrent.ConcurrentHashMap

class RateLimiter(private val maxMessages: Int, private val windowMs: Long) {
    private val clientHistory = ConcurrentHashMap<String, MutableList<Long>>()

    fun isAllowed(clientId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = clientHistory.getOrPut(clientId) { mutableListOf() }
        
        synchronized(timestamps) {
            // Remove old timestamps
            timestamps.removeAll { it < now - windowMs }
            
            if (timestamps.size >= maxMessages) {
                return false
            }
            
            timestamps.add(now)
            return true
        }
    }

    fun clear() {
        clientHistory.clear()
    }
}
