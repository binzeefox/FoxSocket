package com.binzee.foxsocket.example

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.binzee.foxsocket.R
import com.binzee.foxsocket.SERVER_PORT
import com.binzee.foxsocket.socket.FoxSocket
import com.binzee.foxsocket.socket.FoxSocketServer
import java.lang.Exception
import java.util.concurrent.Executors

/**
 * 服务端Activity
 *
 * @author tong.xw
 * 2021/06/17 12:26
 */
class ServerActivity : AppCompatActivity(), FoxSocketServer.Companion.Callback {
    private lateinit var server: FoxSocketServer
    private val monitor: TextView get() = findViewById(R.id.tv_monitor)
    private val sendExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化服务器
        server = FoxSocketServer(SERVER_PORT).apply {
            addCallback(this@ServerActivity)
            start()
        }

        // 发送键按钮
        findViewById<View>(R.id.btn_send).setOnClickListener {
            val inputField = findViewById<TextView>(R.id.et_input)
            val message = inputField.text.toString()
            inputField.text = ""
            sendExecutor.execute {
                server.broadcast(message)
                appendMonitor("服务器广播：$message")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server.removeCallback(this)
    }

    override fun onServerStart() {
        appendMonitor("服务器已开启")
    }

    override fun onOpen(client: FoxSocket) {
        appendMonitor("客户端已连接：${client.socket.remoteSocketAddress}")
    }

    override fun onMessage(client: FoxSocket, message: String) {
        appendMonitor("@${client.socket.remoteSocketAddress}: $message")
    }

    override fun onClose(client: FoxSocket) {
        appendMonitor("客户端已断开：${client.socket.remoteSocketAddress}")
    }

    override fun onError(client: FoxSocket?, ex: Exception?) {
        Log.e("BINZEE_FOX", "onError: ${client?.socket?.remoteSocketAddress}", ex)
    }

    override fun onServerStop() {
        appendMonitor("服务器已关闭")
    }

    override fun onServerError(ex: Exception?) {
        Log.e("BINZEE_FOX", "onServerError: ", ex)
    }

    ///////////////////////////////////////////////////////////////////////////
    // 内部方法
    ///////////////////////////////////////////////////////////////////////////

    private fun appendMonitor(str: String) {
        runOnUiThread {
            monitor.text = "${monitor.text}\n$str"
        }
    }

    private fun clearMonitor() {
        runOnUiThread {
            monitor.text = ""
        }
    }
}