package com.example.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Handles incoming disguised call actions (Answer / Decline) from the notification.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val callId = intent.getStringExtra("CALL_ID") ?: ""

        Log.d("CallActionReceiver", "Received call action: $action, callId: $callId")

        when (action) {
            StealthNotificationHelper.ACTION_DECLINE_CALL -> {
                StealthNotificationHelper.cancelCallNotification(context)
                if (callId.isNotBlank()) {
                    try {
                        val dbId = context.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(dbId)
                        firestore.collection("calls").document(callId).update("status", "REJECTED")
                    } catch (e: Exception) {
                        Log.e("CallActionReceiver", "Failed to reject call in Firestore", e)
                    }
                }
            }

            StealthNotificationHelper.ACTION_ANSWER_CALL -> {
                StealthNotificationHelper.cancelCallNotification(context)
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("ACTION_ANSWER_CALL", true)
                    putExtra("CALL_ID", callId)
                    putExtra("IS_VIDEO", intent.getBooleanExtra("IS_VIDEO", false))
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
