package com.zenithblue.fluxlinux.core.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * A simple single-request HTTP server to serve the installation script to Termux.
 */
class LocalInstallServer {

    private var serverSocket: ServerSocket? = null
    private var scriptContent: String = ""
    private var isRunning = false
    
    // Callback to notify when download happens (optional)
    var onDownload: (() -> Unit)? = null

    suspend fun start(script: String): Int {
        this.scriptContent = script
        return withContext(Dispatchers.IO) {
            // Bind to an ephemeral port (0)
            serverSocket = ServerSocket(0)
            isRunning = true
            
            // Start listener thread
            val port = serverSocket?.localPort ?: throw Exception("Failed to bind port")
            
            Thread {
                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val client = serverSocket!!.accept()
                        handleClient(client)
                    } catch (e: Exception) {
                        // Socket closed or error
                    }
                }
            }.start()
            
            return@withContext port
        }
    }

    private fun handleClient(client: Socket) {
        try {
            // Read request (we ignore headers, just serve the script for any request)
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine() // e.g., "GET /install HTTP/1.1"
            
            // Simple response
            val writer = PrintWriter(client.getOutputStream(), true)
            
            // Headers
            writer.println("HTTP/1.1 200 OK")
            writer.println("Content-Type: text/x-shellscript")
            writer.println("Content-Length: ${scriptContent.toByteArray().size}")
            writer.println("Connection: close")
            writer.println("")
            
            // Body
            writer.println(scriptContent)
            writer.flush()
            
            client.close()
            
            // Notify download occurred
            onDownload?.invoke()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        serverSocket = null
    }
}
