package com.example.gieterp

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

object VolleyProvider {
    @Volatile
    private var requestQueue: RequestQueue? = null

    fun getRequestQueue(context: Context): RequestQueue {
        return requestQueue ?: synchronized(this) {
            requestQueue ?: Volley.newRequestQueue(context.applicationContext).also {
                requestQueue = it
            }
        }
    }
}
