package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.SmartSmsApp
import com.example.util.SmsNotificationHelper

class MainActivity : ComponentActivity() {

    private var composeRecipientState by mutableStateOf<String?>(null)
    private var composeBodyState by mutableStateOf<String?>(null)
    private var openThreadKeyState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            SmartSmsApp(
                initialComposeRecipient = composeRecipientState,
                initialComposeBody = composeBodyState,
                initialThreadKey = openThreadKeyState,
                onConsumeComposeIntent = {
                    composeRecipientState = null
                    composeBodyState = null
                },
                onConsumeOpenThreadIntent = {
                    openThreadKeyState = null
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val notifThreadKey = intent.getStringExtra(SmsNotificationHelper.EXTRA_THREAD_KEY)
        if (!notifThreadKey.isNullOrBlank() &&
            (intent.action == SmsNotificationHelper.ACTION_OPEN_THREAD || intent.hasExtra(SmsNotificationHelper.EXTRA_THREAD_KEY))
        ) {
            openThreadKeyState = notifThreadKey
            return
        }

        val action = intent.action
        val data = intent.data
        val scheme = data?.scheme?.lowercase()

        if (scheme in setOf("sms", "smsto", "mms", "mmsto")) {
            val rawTarget = data?.schemeSpecificPart?.substringBefore('?')?.let { Uri.decode(it) }.orEmpty()
            val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            if (rawTarget.isNotBlank() || !body.isNullOrBlank()) {
                composeRecipientState = rawTarget.takeIf { it.isNotBlank() }
                composeBodyState = body?.takeIf { it.isNotBlank() }
            }
        } else if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                composeBodyState = shared
            }
        }
    }
}
