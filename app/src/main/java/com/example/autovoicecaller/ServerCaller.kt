package com.example.autovoicecaller

import android.content.Context
import com.example.autovoicecaller.BuildConfig
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import java.io.IOException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ServerCaller(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    private val baseUrl = context.getString(R.string.server_url)
    
    enum class Provider(val displayName: String, val baseUrl: String) {
        CUSTOM("Custom Server", ""),
        EXOTEL("Exotel", "https://api.exotel.com/v1/Accounts"),
        KNOWLARITY("Knowlarity", "https://api.knowlarity.com/v1/Accounts"),
        PLIVO("Plivo", "https://api.plivo.com/v1/Account"),
        TWILIO("Twilio", "https://api.twilio.com/2010-04-01/Accounts")
    }
    
    var currentProvider: Provider = Provider.CUSTOM
        private set
    
    data class CallRequest(
        val phone: String,
        val message: String? = null,
        val audioUrl: String? = null
    )
    
    data class CallResponse(
        val success: Boolean,
        val callId: String? = null,
        val channelId: String? = null,
        val status: String? = null,
        val error: String? = null
    )
    
    interface CallCallback {
        fun onSuccess(callId: String, channelId: String)
        fun onError(error: String)
    }
    
    fun setProvider(provider: Provider, accountSid: String? = null, authToken: String? = null) {
        currentProvider = provider
        if (provider != Provider.CUSTOM && accountSid != null && authToken != null) {
            // Store credentials for provider-specific calls
        }
    }
    
    fun makeCall(phone: String, message: String, callback: CallCallback) {
        when (currentProvider) {
            Provider.EXOTEL -> makeExotelCall(phone, message, callback)
            Provider.KNOWLARITY -> makeKnowlarityCall(phone, message, callback)
            Provider.PLIVO -> makePlivoCall(phone, message, callback)
            Provider.TWILIO -> makeTwilioCall(phone, message, callback)
            else -> makeCustomServerCall(phone, message, callback)
        }
    }
    
    private fun makeCustomServerCall(phone: String, message: String, callback: CallCallback) {
        val requestBody = CallRequest(phone = phone, message = message)
        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl/call")
            .post(body)
            .build()
        
        enqueue(request, callback)
    }
    
    private fun makeExotelCall(phone: String, message: String, callback: CallCallback) {
        // Exotel API: POST /Accounts/{sid}/Calls/connect.json
        // Requires: from (your Exophone), to (customer), caller_id, url (for audio)
        callback.onError("Exotel: Configure your Exophone and flow URL in code")
    }
    
    private fun makeKnowlarityCall(phone: String, message: String, callback: CallCallback) {
        // Knowlarity API similar to Exotel
        callback.onError("Knowlarity: Configure your SRN and template in code")
    }
    
    private fun makePlivoCall(phone: String, message: String, callback: CallCallback) {
        // Plivo API
        callback.onError("Plivo: Configure your Plivo number and answer_url in code")
    }
    
    private fun makeTwilioCall(phone: String, message: String, callback: CallCallback) {
        val sid = BuildConfig.TWILIO_SID
        val token = BuildConfig.TWILIO_TOKEN
        val fromNumber = BuildConfig.TWILIO_FROM_NUMBER
        
        if (sid.startsWith("YOUR_") || token.startsWith("YOUR_")) {
            callback.onError("Twilio credentials not configured. Set TWILIO_SID and TWILIO_TOKEN as GitHub secrets or environment variables.")
            return
        }
        
        // Twilio requires a TwiML URL that returns audio instructions
        // For now, we'll use a simple TwiML that says the message
        val twimlUrl = "https://twimlets.com/message?Message%5B0%5D=${Uri.encode(message)}"
        
        val formBody = okhttp3.FormBody.Builder()
            .add("To", phone)
            .add("From", fromNumber)
            .add("Url", twimlUrl)
            .build()
        
        val credentials = Credentials.basic(sid, token)
        val request = Request.Builder()
            .url("${Provider.TWILIO.baseUrl}/$sid/Calls.json")
            .header("Authorization", credentials)
            .post(formBody)
            .build()
        
        enqueue(request, callback)
    }
    
    private fun enqueue(request: Request, callback: CallCallback) {
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback.onError("Network error: ${e.message}")
            }
            
            override fun onResponse(call: okhttp3.Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    callback.onError("Server error: ${response.code} - $responseBody")
                    return
                }
                
                try {
                    val result = gson.fromJson(responseBody, CallResponse::class.java)
                    if (result.success) {
                        callback.onSuccess(result.callId!!, result.channelId!!)
                    } else {
                        callback.onError(result.error ?: "Unknown server error")
                    }
                } catch (e: Exception) {
                    callback.onError("Parse error: ${e.message}")
                }
            }
        })
    }
    
    fun checkHealth(callback: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/health")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(false)
            }
            
            override fun onResponse(call: okhttp3.Call, response: Response) {
                callback(response.isSuccessful)
            }
        })
    }
    
    companion object {
        private const val TAG = "ServerCaller"
    }
}