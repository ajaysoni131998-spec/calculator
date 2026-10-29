package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class PrivacyAnalysis(
    val riskLevel: String, // "SAFE", "MEDIUM", "HIGH"
    val warning: String,
    val stealthSuggestion: String
)

object GhostAiManager {

    private const val TAG = "GhostAiManager"

    // Lazily evaluate Gemini 3.8 Flash model
    private val model by lazy {
        try {
            Firebase.ai.generativeModel("gemini-3.8-flash")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Firebase AI model: ${e.message}")
            null
        }
    }

    /**
     * AI Feature 1: Stealth Message Camouflage
     * Rewrites sensitive confidential messages into innocent disguises
     * (Math homework, grocery shopping, work slides, or secret code).
     */
    suspend fun camouflageMessage(
        originalText: String,
        style: String = "HOMEWORK"
    ): String = withContext(Dispatchers.IO) {
        val trimmed = originalText.trim()
        if (trimmed.isBlank()) return@withContext ""

        val stylePrompt = when (style.uppercase()) {
            "HOMEWORK" -> "an innocent math or physics homework problem/question for school or university"
            "GROCERY" -> "a casual grocery shopping list or kitchen ingredient reminder"
            "WORK" -> "a boring corporate project update about slides, review sync, or spreadsheets"
            "CIPHER" -> "a short military-style cryptic status code like 'OMEGA-21: LOG VERIFIED'"
            else -> "an innocent everyday casual message that completely hides secret intent"
        }

        try {
            val prompt = """
                You are a privacy camouflage assistant. 
                Convert the following confidential message into $stylePrompt so that anyone looking over someone's shoulder will not suspect anything.
                Original confidential message: "$trimmed"
                Output ONLY the disguised message text directly. Do not use quotes, explanation, or conversational text.
            """.trimIndent()

            val aiModel = model
            if (aiModel != null) {
                val response = withTimeout(15_000L) {
                    aiModel.generateContent(prompt)
                }
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    return@withContext text
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini camouflage failed: ${e.message}", e)
        }

        // Smart Built-in Fallback Disguises
        return@withContext when (style.uppercase()) {
            "HOMEWORK" -> "Please verify equation f(x) = 2x^2 + 5x - 3 for Chapter 4 problem 9 before tomorrow."
            "GROCERY" -> "Don't forget: 2L whole milk, brown bread, fresh eggs, and butter from the supermarket."
            "WORK" -> "Uploaded updated Q3 presentation slides for review. Please check section 4."
            "CIPHER" -> "ALPHA-NODE-09 :: TIMESTAMP OK :: VERIFIED"
            else -> "Sounds good! I'll check my notes and get back to you shortly."
        }
    }

    /**
     * AI Feature 2: Privacy Scanner
     * Checks if a message draft contains phone numbers, passwords, coordinates, or dangerous plaintext.
     */
    suspend fun scanMessagePrivacy(text: String): PrivacyAnalysis = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext PrivacyAnalysis("SAFE", "No message content", "")
        }

        // Fast client heuristic check
        val containsDigits = trimmed.count { it.isDigit() } >= 6
        val containsSensitiveWords = listOf("password", "otp", "pin", "card", "meet at", "location", "secret", "call me").any {
            trimmed.contains(it, ignoreCase = true)
        }

        try {
            val aiModel = model
            if (aiModel != null) {
                val prompt = """
                    Analyze this message for privacy risks (passwords, phone numbers, location leaks, sensitive secrets):
                    "$trimmed"
                    Provide a 1-sentence assessment and 1 suggested stealth alternative.
                """.trimIndent()

                val response = withTimeout(12_000L) {
                    aiModel.generateContent(prompt)
                }
                val resultText = response.text?.trim()
                if (!resultText.isNullOrBlank()) {
                    val risk = if (containsDigits || containsSensitiveWords) "MEDIUM" else "SAFE"
                    return@withContext PrivacyAnalysis(
                        riskLevel = risk,
                        warning = if (risk != "SAFE") "Notice: Contains potentially identifying digits or keywords" else "Privacy Check Passed",
                        stealthSuggestion = resultText
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Privacy scanner error: ${e.message}")
        }

        val risk = if (containsDigits || containsSensitiveWords) "MEDIUM" else "SAFE"
        return@withContext PrivacyAnalysis(
            riskLevel = risk,
            warning = if (risk != "SAFE") "Notice: Contains numbers or sensitive keywords. 1-minute auto-destruct will protect it." else "Privacy Check Passed: Safe to transmit",
            stealthSuggestion = if (risk != "SAFE") "Tip: Use AI Camouflage to disguise numbers as homework problem indices." else "Message is ready for encrypted transmission."
        )
    }

    /**
     * AI Feature 3: Smart Privacy Auto-Replies
     * Generates 3 contextual, privacy-preserving quick replies.
     */
    suspend fun generateSmartReplies(lastMessageText: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = lastMessageText.trim()
        if (trimmed.isBlank()) {
            return@withContext listOf("Understood 👍", "Will update soon", "All clear 🔒")
        }

        try {
            val aiModel = model
            if (aiModel != null) {
                val prompt = """
                    Generate 3 brief, polite, privacy-safe reply options (max 4-5 words each) to this secret chat message:
                    "$trimmed"
                    Separate each reply with a newline. No numbers or bullet points.
                """.trimIndent()

                val response = withTimeout(10_000L) {
                    aiModel.generateContent(prompt)
                }
                val lines = response.text?.lines()
                    ?.map { it.trim().removePrefix("-").removePrefix("•").trim() }
                    ?.filter { it.isNotBlank() }
                    ?.take(3)

                if (!lines.isNullOrEmpty()) {
                    return@withContext lines
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Smart replies generation error: ${e.message}")
        }

        return@withContext listOf(
            "Understood, on it 👍",
            "Confirmed. Deleting now 🤫",
            "Call me via encrypted line 📞"
        )
    }
}
