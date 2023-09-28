package com.zs.trainpaymentreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        stopRemindersFunction()
    }

    private fun stopRemindersFunction() {
        GlobalObjectList.delay = 30 * MINUTE
    }
}
