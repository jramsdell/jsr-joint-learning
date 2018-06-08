package io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jsoniter.JsonIterator
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class ConcurrentConnectionManager() {
    val manager = PoolingHttpClientConnectionManager()
        .apply { maxTotal = 3000; defaultMaxPerRoute = 3000 }


    val client = HttpClients.custom()
        .setConnectionManager(manager)
        .build()

    val monitor = IdleConnectionMonitorThread(manager)
        .apply { isDaemon = true; start() }

    private val gson = Gson()

    fun doPostOrGetRequest(url: String, data: Map<String, String>? = null, header: Pair<String, String>? = null): JSONObject {
        val request: HttpRequestBase
        if (data != null) {

            val params = ArrayList<NameValuePair>()
            data.entries.forEach { (k,v) -> params.add(BasicNameValuePair(k, v)) }

            request = HttpPost(url)
//            input.setContentType("application/json")
//            println(gson.toJson(data))

            request.entity = UrlEncodedFormEntity(params, "UTF-8")
            if (header != null) request.addHeader(header.first, header.second)
        } else {
            request = HttpGet(url)
        }
//        return JsonIterator.parse( client.execute(request).entity.content.bufferedReader().readText()) as JsonObject
        return JSONObject( client.execute(request).entity.content.bufferedReader().readText())
    }
}

class IdleConnectionMonitorThread(val cm: PoolingHttpClientConnectionManager) : Thread() {
//    val cm = ConcurrentConnectionManager.manager
    val lock = ReentrantLock()
    var shutdown = AtomicBoolean(false)


    override fun run() {
        try {
            while (!shutdown.get()) {
                lock.withLock {
                    Thread.sleep(1000)
                    cm.closeExpiredConnections()
                    cm.closeIdleConnections(30, TimeUnit.SECONDS)
                }
            }
        } catch (e: InterruptedException) { shutdown.set(true) }
    }
}


