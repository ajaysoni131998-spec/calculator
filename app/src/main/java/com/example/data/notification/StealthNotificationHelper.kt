package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.FirestoreCallSignal
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * Stealth Notification Manager
 * 
 * 1. Generates innocent-looking "Math Problem / Calculator Quiz" notifications
 *    whenever a private message is received on this device.
 * 2. Generates realistic incoming phone call notifications disguised with
 *    a REALISTIC RANDOM MOBILE NUMBER (e.g. +91 98412 80924) when voice/video calls arrive.
 *    Nobody glancing at the phone screen can know who is calling or that a secret messenger is active!
 */
object StealthNotificationHelper {

    const val MATH_CHANNEL_ID = "stealth_math_quiz_channel"
    private const val MATH_CHANNEL_NAME = "Math Quiz & Equations"
    private const val MATH_CHANNEL_DESC = "Daily math practice equations and calculator alerts"

    const val CALL_CHANNEL_ID = "stealth_incoming_calls_channel"
    private const val CALL_CHANNEL_NAME = "Phone Calls"
    private const val CALL_CHANNEL_DESC = "Incoming phone call alerts and telecommunications"

    const val ACTION_ANSWER_CALL = "com.example.ACTION_ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.example.ACTION_DECLINE_CALL"

    private const val MATH_NOTIFICATION_ID = 1319
    private const val CALL_NOTIFICATION_ID = 1320

    private val mathProblems = listOf(
        Pair("Math Practice: 38 + 64 = ?", "Tap to calculate the answer & maintain your daily streak."),
        Pair("Algebra Quiz: Solve 4x - 12 = 28", "Open calculator to verify the value of x."),
        Pair("Trigonometry Alert: sin²(θ) + cos²(θ) = ?", "Quick math practice question ready for calculation."),
        Pair("Calculus Challenge: d/dx [3x² + 5x] = ?", "Daily math streak active. Calculate your answer now."),
        Pair("Math Equation: √144 + 5² = ?", "Solve problem to keep your calculator practice alive."),
        Pair("Percentage Puzzle: What is 15% of 240?", "Tap to solve using scientific calculator functions."),
        Pair("Math Daily: 7 × 8 + 19 = ?", "Solve to keep your mental math streak uninterrupted.")
    )

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Math Notification Channel
            val mathChannel = NotificationChannel(
                MATH_CHANNEL_ID,
                MATH_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = MATH_CHANNEL_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(mathChannel)

            // 2. High-Priority Disguised Call Notification Channel with Ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CALL_CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setSound(ringtoneUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    /**
     * Backward-compatible alias
     */
    fun createNotificationChannel(context: Context) {
        createNotificationChannels(context)
    }

    /**
     * Generates a realistic random Indian mobile number from the callId.
     * Deterministic per callId so it stays consistent during the ring duration.
     * e.g. "+91 98451 28491"
     */
    fun getDisguisedCallerNumber(callId: String): String {
        val hash = callId.hashCode().absoluteValue
        val prefixes = listOf("98", "97", "99", "88", "91", "96", "89", "70")
        val prefix = prefixes[hash % prefixes.size]
        val part1 = (hash / 10 % 900 + 100).toString()
        val part2 = (hash % 90000 + 10000).toString()
        return "+91 $prefix$part1 $part2"
    }

    /**
     * Displays a high-priority disguised phone call alert.
     * Uses a random phone number title so anyone snooping sees a regular phone call.
     */
    fun showStealthCallNotification(context: Context, signal: FirestoreCallSignal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannels(context)

        val isVideo = signal.callType.equals("VIDEO", ignoreCase = true)
        val disguisedNumber = getDisguisedCallerNumber(signal.callId)
        val callTypeLabel = if (isVideo) "Incoming Video Call..." else "Incoming Voice Call..."

        // Fullscreen / Content Intent to open MainActivity directly
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ACTION_INCOMING_CALL", true)
            putExtra("CALL_ID", signal.callId)
            putExtra("IS_VIDEO", isVideo)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            201,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer Action
        val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_ANSWER_CALL
            putExtra("CALL_ID", signal.callId)
            putExtra("IS_VIDEO", isVideo)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra("CALL_ID", signal.callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            203,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(disguisedNumber)
            .setContentText(callTypeLabel)
            .setSubText("Phone")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun cancelCallNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }

    fun showStealthMathNotification(context: Context, conversationId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannels(context)

        // Select a random realistic math problem
        val randomProblem = mathProblems[Random.nextInt(mathProblems.size)]

        // Intent to launch MainActivity (which opens Calculator by default)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_FROM_STEALTH_NOTIFICATION", true)
            putExtra("PEER_CONVERSATION_ID", conversationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(1000, 9999),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MATH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentTitle(randomProblem.first)
            .setContentText(randomProblem.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomProblem.second))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(MATH_NOTIFICATION_ID, notification)
    }
}
