package com.binzee.foxsocket.socket

import android.util.Log
import java.lang.Exception
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.HashSet

/**
 * tcp socket服务器
 *
 * @author tong.xw
 * 2021/06/17 09:19
 */
class FoxSocketServer(private val port: Int) {
    companion object {
        private const val TAG = "FoxSocketServer"

        interface Callback {
            fun onServerStart()
            fun onOpen(client: FoxSocket)
            fun onMessage(client: FoxSocket, message: String)
            fun onClose(client: FoxSocket)
            fun onError(client: FoxSocket?, ex: Exception?)
            fun onServerError(ex: Exception?)
            fun onServerStop()
        }
    }

    // CPU 核心数
    private val cpuCoreCount: Int get() = Runtime.getRuntime().availableProcessors()

    @Volatile
    var isRunning = false
        private set(value) {
            if (!value) {
                if (!serverSocket.isClosed) serverSocket.close()
                invokeCallback {
                    it.onServerStop()
                }
            }
            field = value
        }

    // 实际使用的server
    private lateinit var serverSocket: ServerSocket

    // 回调合集
    private val callbackSet = Collections.synchronizedSet(HashSet<Callback>())

    // 核心线程为 server线程、accept线程、心跳线程
    private val workThreadPool =
        ThreadPoolExecutor(3, cpuCoreCount, 3, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())

    // 已经获取的socket合集
    private val socketSet = Collections.synchronizedSet(HashSet<FoxSocket>())

    /**
     * 开启服务
     */
    fun start() {
        isRunning = true
        workThreadPool.execute {
            serverSocket = ServerSocket(port)

            // 心跳
            workThreadPool.execute {
                try {
                    runInLoop {
                        broadcast("heartbeat: ${System.currentTimeMillis()}")
                        Thread.sleep(30000)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "heartbeat error: ", e)
                    isRunning = false
                }
            }

            // accept
            workThreadPool.execute {
                invokeCallback {
                    it.onServerStart()
                }

                // 循环accept
                try {
                    runInLoop({ isRunning }) {
                        // 循环获取
                        val socket = serverSocket.accept()
                        val foxSocket = FoxSocket(socket, object : FoxSocket.Companion.Callback {
                            override fun onOpen(client: FoxSocket) {
                                invokeCallback {
                                    it.onOpen(client)
                                }
                            }

                            override fun onMessage(client: FoxSocket, message: String) {
                                invokeCallback {
                                    it.onMessage(client, message)
                                }
                            }

                            override fun onClose(client: FoxSocket) {
                                invokeCallback {
                                    it.onClose(client)
                                }
                            }

                            override fun onError(client: FoxSocket, e: Exception?) {
                                invokeCallback {
                                    it.onError(client, e)
                                }
                                client.removeCallback(this)
                            }

                        })
                        socketSet.add(foxSocket)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "accept loop error: ", e)
                    invokeCallback {
                        it.onServerError(e)
                    }
                }
            }
        }
    }

    fun close() {
        isRunning = false
    }

    fun addCallback(callback: Callback) = callbackSet.add(callback)

    fun removeCallback(callback: Callback) = callbackSet.remove(callback)

    /**
     * 广播信息
     */
    fun broadcast(message: String) {
        for (client in socketSet)
            client.send(message)
    }

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