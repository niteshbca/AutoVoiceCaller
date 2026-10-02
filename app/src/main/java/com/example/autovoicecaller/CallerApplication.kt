package com.example.autovoicecaller

import android.app.Application

class CallerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.initialize(this)
    }
}
