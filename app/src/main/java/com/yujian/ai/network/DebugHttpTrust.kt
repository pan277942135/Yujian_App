package com.yujian.ai.network

import com.yujian.ai.BuildConfig
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Debug 构建下放宽 HTTPS 证书校验，用于连接使用自签名/IP 证书的测试服务器（如 https://81.71.1.11）。
 * 仅在 DEBUG 构建生效，release 不受影响。
 */
object DebugHttpTrust {
    fun installIfDebug() {
        if (!BuildConfig.DEBUG) return
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), null)
        }
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }
}
