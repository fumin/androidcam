package com.topunion.camera

import java.util.concurrent.ArrayBlockingQueue

class Deque<E>(private val n: Int) {
    private val deque = ArrayBlockingQueue<E>(n)

    fun append(e: E) {
        if (this.deque.size >= n) {
            this.deque.poll()
        }
        this.deque.offer(e)
    }

    @Suppress("UNCHECKED_CAST")
    fun all(esIn: Array<E>): Array<E> {
        var es = esIn
        for (e in this.deque.toArray()) {
            es += e as E
        }
        return es
    }
}