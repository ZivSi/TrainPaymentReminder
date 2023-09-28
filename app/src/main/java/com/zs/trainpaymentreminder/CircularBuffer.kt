package com.zs.trainpaymentreminder

class CircularBuffer<T>(private val bufferCapacity: Int) {
    private val buffer = ArrayDeque<T>(bufferCapacity)

    fun getSize(): Int {
        return buffer.size
    }

    fun getCapacity(): Int {
        return bufferCapacity
    }

    fun push(value: T) {
        if (buffer.size == bufferCapacity) {
            buffer.removeLast()
        }
        buffer.addFirst(value)
    }

    fun getData(): List<T> {
        return buffer.toList()
    }

    override fun toString(): String {
        return buffer.toString()
    }
}
