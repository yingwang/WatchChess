package com.yingwang.watchchess.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.PipedReader
import java.io.PipedWriter
import java.io.StringReader
import java.util.concurrent.TimeUnit

class FairyProtocolTest {
    @Test fun `select cyclone coordinates before uci handshake`() {
        assertEquals(listOf("ucicyclone", "uci"), FairyProtocol.handshake)
    }

    @Test fun `use classical xiangqi evaluation with bounded memory`() {
        val commands = FairyProtocol.configure(emptySet())
        assertTrue(commands.contains("setoption name Use NNUE value false"))
        assertTrue(commands.contains("setoption name UCI_Variant value xiangqi"))
        assertTrue(commands.contains("setoption name Threads value 1"))
        assertTrue(commands.contains("setoption name Hash value 4"))
        assertEquals("isready", commands.last())
        assertFalse(commands.any { "EvalFile" in it || "NumaPolicy" in it })
    }

    @Test fun `disable numa when the engine advertises the option`() {
        assertTrue(FairyProtocol.configure(setOf("NumaPolicy"))
            .contains("setoption name NumaPolicy value none"))
    }

    @Test fun `a silent engine cannot bypass the read deadline`() {
        val pipe = PipedReader()
        val writer = PipedWriter(pipe)
        val output = UciOutput(BufferedReader(pipe))
        try {
            writer.write("partial line without newline")
            writer.flush()
            val start = System.nanoTime()
            assertNull(output.readUntil(start + TimeUnit.MILLISECONDS.toNanos(100)))
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
        } finally {
            output.close()
            writer.close()
            pipe.close()
        }
    }

    @Test fun `read complete lines and eof in order`() {
        UciOutput(BufferedReader(StringReader("info depth 6\nbestmove b0c2\n"))).use { output ->
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            assertEquals("info depth 6", output.readUntil(deadline))
            assertEquals("bestmove b0c2", output.readUntil(deadline))
            assertNull(output.readUntil(deadline))
        }
    }
}
