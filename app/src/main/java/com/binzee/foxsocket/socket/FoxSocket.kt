package com.binzee.foxsocket.socket

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.Exception
import java.net.Socket
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashSet

/**
 * tcp socket客户端
 *
 * @author tong.xw
 * 2021/06/17 09:19
 */
class FoxSocket constructor(val socket: Socket, vararg callbacks: Callback?) {

    companion object {
        private const val TAG = "FoxSocket"

        interface Callback {
            fun onOpen(client: FoxSocket)
            fun onMessage(client: FoxSocket, message: String)
            fun onClose(client: FoxSocket)
            fun onError(client: FoxSocket, e: Exception?)
        }
    }

    @Volatile
    var isRunning = true
        private set(value) {
            field = value
            if (!value) {
                if (!socket.isClosed) socket.close()
                executor.shutdownNow()
                invokeCallback {
                    it.onClose(this)
                }
            }
        }

    // 读取线程和存活确认线程
    private val executor = Executors.newFixedThreadPool(2)
    private val callbackSet = Collections.synchronizedSet(HashSet<Callback>())

    init {
        for (callback in callbacks) {
            if (callback == null) continue
            addCallback(callback)
        }
        invokeCallback {
            it.onOpen(this)
        }
        executor.execute {
            try {
                var run = true
                runInLoop({ run }) {
                    if (!socket.isConnected) run = false
                    Thread.sleep(2000)
                }
            } catch (e: Exception) {
                invokeCallback {
                    it.onError(this, e)
                }
            } finally {
                isRunning = false
            }
        }
        executor.execute {
            try {
                runInLoop({ isRunning }) {
                    val reader =
                        BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    val message = reader.readLine()
                    if (message == null) {
                        isRunning = false
                    }
                    invokeCallback {
                        it.onMessage(this, message)
                    }
                }
            } catch (e: Exception) {
                invokeCallback {
                    it.onError(this, e)
                }
            } finally {
                if (isRunning) isRunning = false
            }
        }
    }

    /**
     * 发送消息
     */
    fun send(message: String) {
        try {
            val writer =
                PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
            writer.println(message)
        } catch (e: Exception) {
            invokeCallback {
                it.onError(this, e)
            }
        }
    }

    fun addCallback(callback: Callback) = callbackSet.add(callback)

    fun removeCallback(callback: Callback) = callbackSet.remove(callback)

    ///////////////////////////////////////////////////////////////////////////
    // 内部方法
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 调用所有的回调
     */
    private fun invokeCallback(block: (callback: Callback) -> Unit) {
        for (c in callbackSet)
            block.invoke(c)
    }

    /**
     * 自动补货中断异常的循环
     *
     * @param condition 循环条件
     * @param block 循环体
     */
    private fun runInLoop(condition: () -> Boolean = { true }, block: () -> Unit) {
        try {
            while (condition.invoke()) {
                block.invoke()
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "runInLoop: ", e)
        }
    }
}