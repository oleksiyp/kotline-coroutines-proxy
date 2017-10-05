import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aConnect
import kotlinx.coroutines.experimental.nio.aRead
import kotlinx.coroutines.experimental.nio.aWrite
import kotlinx.coroutines.experimental.runBlocking
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel


suspend fun pump(input: AsynchronousSocketChannel,
                 output: AsynchronousSocketChannel,
                 isActive: () -> Boolean) {
    val buffer = ByteBuffer.allocate(1000)
    try {
        while (isActive() && input.aRead(buffer) > 0) {
            println(buffer.position())
            buffer.flip()
            if (output.aWrite(buffer) < 0) {
                break
            }
            buffer.compact()
        }
    } catch (ex: IOException) {
        println(ex.message)
    }
}

fun main(args: Array<String>) {
    runBlocking(newSingleThreadContext("proxy")) {

        val connection = AsynchronousServerSocketChannel.open()
                .bind(InetSocketAddress(5557))

        while (isActive) {
            val inbound = connection.aAccept()

            launch(coroutineContext) {
                val outbound = AsynchronousSocketChannel.open()
                outbound.aConnect(InetSocketAddress("localhost", 22))

                val in2out = launch(coroutineContext) {
                    pump(inbound, outbound, this::isActive)
                }
                val out2in = launch(coroutineContext) {
                    pump(outbound, inbound, this::isActive)
                }

                out2in.join()
                in2out.cancel()

                in2out.join()
                out2in.cancel()

                inbound.close()
                outbound.close()
            }
        }
    }
}
