package com.binzee.foxsocket.example

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.binzee.foxsocket.R
import com.binzee.foxsocket.SERVER_IP
import com.binzee.foxsocket.SERVER_PORT
import com.binzee.foxsocket.socket.FoxSocket
import java.lang.Exception
import java.net.Socket
import java.util.concurrent.Executors

/**
 * 客户端Activity
 *
 * @author tong.xw
 * 2021/06/17 12:26
 */
class ClientActivity : AppCompatActivity(), FoxSocket.Companion.Callback {
    private lateinit var client: FoxSocket  // 客户端
    private val monitor: TextView get() = findViewById(R.id.tv_monitor) // 显示区
    private val executor = Executors.newFixedThreadPool(2)  // 客户端线程和发送信息线程

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        executor.execute {
            client = FoxSocket(Socket(
                SERVER_IP,
                SERVER_PORT
            ), this)
        }
        findViewById<View>(R.id.btn_send).setOnClickListener {
            val inputField = findViewById<TextView>(R.id.et_input)
            val message = inputField.text.toString()
            inputField.text = ""
            executor.execute {
                client.send(message)
                appendMonitor("发送消息：$message")
            }
        }
    }

    override fun onOpen(client: FoxSocket) {
        appendMonitor("服务已连接")
    }

    override fun onMessage(client: FoxSocket, message: String) {
        appendMonitor("服务器：$message")
    }

    override fun onClose(client: FoxSocket) {
        appendMonitor("服务已断开")
    }

    override fun onError(client: FoxSocket, e: Exception?) {
        Log.e("BINZEE_FOX", "onError: ", e)
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