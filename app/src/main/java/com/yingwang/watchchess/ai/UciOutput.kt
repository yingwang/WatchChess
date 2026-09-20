package com.yingwang.watchchess.ai

import java.io.BufferedReader
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** readLine 可能永远阻塞，所以由独立线程读，协议线程只在有期限的队列上等。 */
internal class UciOutput(reader: BufferedReader) : AutoCloseable {
    private data class Line(val text: String?)
    private val lines = LinkedBlockingQueue<Line>(1024)
    private val pump = thread(isDaemon = true, name = "fairy-uci-output") {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                lines.put(Line(line))
            }
        } catch (_: Exception) {
            // 进程退出、流关闭和中断都作为 EOF 交给协议调用方。
        } finally {
            lines.offer(Line(null))
        }
    }

    fun readUntil(deadlineNanos: Long): String? {
        val remaining = deadlineNanos - System.nanoTime()
        if (remaining <= 0) return null
        return lines.poll(remaining, TimeUnit.NANOSECONDS)?.text
    }

    override fun close() { pump.interrupt() }
}
