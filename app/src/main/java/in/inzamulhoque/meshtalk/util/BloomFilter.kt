package `in`.inzamulhoque.meshtalk.util

import java.util.BitSet

class BloomFilter(private val bitSize: Int, private val hashCount: Int) {
    private val bitSet = BitSet(bitSize)

    fun add(data: String) {
        for (i in 0 until hashCount) {
            val hash = hash(data, i)
            bitSet.set(Math.abs(hash % bitSize))
        }
    }

    fun contains(data: String): Boolean {
        for (i in 0 until hashCount) {
            val hash = hash(data, i)
            if (!bitSet.get(Math.abs(hash % bitSize))) return false
        }
        return true
    }

    private fun hash(data: String, seed: Int): Int {
        var h = data.hashCode() xor (seed * 0x517cc1b7).toInt()
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h *= 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)
        return h
    }
    
    fun toByteArray(): ByteArray {
        val bytes = bitSet.toByteArray()
        // Ensure the byte array is at least the expected size or consistent
        val expectedSize = (bitSize + 7) / 8
        if (bytes.size == expectedSize) return bytes
        val result = ByteArray(expectedSize)
        bytes.copyInto(result, 0, 0, Math.min(bytes.size, expectedSize))
        return result
    }
    
    companion object {
        fun fromByteArray(bytes: ByteArray, bitSize: Int, hashCount: Int): BloomFilter {
            val filter = BloomFilter(bitSize, hashCount)
            val incoming = BitSet.valueOf(bytes)
            for (i in 0 until bitSize) {
                if (incoming.get(i)) filter.bitSet.set(i)
            }
            return filter
        }
    }
}
