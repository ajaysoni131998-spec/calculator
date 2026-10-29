package com.example
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.StealthPreferences
import com.example.data.repository.ChatRepository
import com.example.ui.auth.GmailAuthScreen
import com.example.ui.calculator.CalculatorScreen
import com.example.ui.calculator.CalculatorViewModel
import com.example.ui.messenger.MessengerMainScreen
import com.example.ui.messenger.MessengerViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

import com.example.ui.decoy.DecoyPhotoVaultScreen

enum class AppScreen {
    CALCULATOR,
    AUTH,
    MESSENGER,
    DECOY_PHOTO_VAULT
}

class MainActivity : ComponentActivity() {

    private lateinit var preferences: StealthPreferences
    private var isVaultUnlocked = mutableStateOf(false)
    private var isDecoyMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val firebaseAppCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            )
        } catch (e: Exception) {
            // Non-blocking
        }

        val database = AppDatabase.getInstance(this)
        preferences = StealthPreferences(this)
        val authManager = AuthManager(this)
        authManager.attemptAutoSignIn(lifecycleScope)

        // Initialize stealth disguised math notification channel
        com.example.data.notification.StealthNotificationHelper.createNotificationChannel(this)

        // Ensure both devices have Camera & Microphone access for calling
        val permissionsNeeded = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.RECORD_AUDIO)
        }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.CAMERA)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsNeeded.isNotEmpty()) {
            requestPermissions(permissionsNeeded.toTypedArray(), 101)
        }

        val repository = ChatRepository(
            context = this,
            contactDao = database.contactDao(),
            messageDao = database.messageDao(),
            callLogDao = database.callLogDao(),
            statusStoryDao = database.statusStoryDao()
        )

        // Check if opened from stealth incoming call notification
        val isCallAlert = intent?.getBooleanExtra("ACTION_INCOMING_CALL", false) == true ||
                intent?.getBooleanExtra("ACTION_ANSWER_CALL", false) == true
        if (isCallAlert) {
            isVaultUnlocked.value = true
        }

        setContent {
            MyApplicationTheme {
                val calcViewModel = remember {
                    CalculatorViewModel(preferences)
                }
                val messengerViewModel = remember {
                    MessengerViewModel(repository, preferences)
                }
                SideEffect {
                    activeMessengerViewModel = messengerViewModel
                }

                // If launched with Answer action, auto accept incoming call
                LaunchedEffect(intent) {
                    if (intent?.getBooleanExtra("ACTION_ANSWER_CALL", false) == true) {
                        isVaultUnlocked.value = true
                        com.example.data.notification.StealthNotificationHelper.cancelCallNotification(this@MainActivity)
                        messengerViewModel.acceptIncomingCall()
                    }
                }

                val currentUser by authManager.currentUser.collectAsState()
                var hasSkippedAuth by remember { mutableStateOf(false) }

                LaunchedEffect(currentUser) {
                    val user = currentUser
                    if (user != null) {
                        messengerViewModel.onUserSignedIn(user.email, user.displayName)
                        try {
                            val firestore = FirebaseFirestore.getInstance(
                                getString(R.string.firestore_database_id)
                            )
                            val profile = hashMapOf<String, Any>(
                                "userId" to user.uid,
                                "stealthUserId" to (user.email ?: preferences.myUserId),
                                "displayName" to (user.displayName ?: "Ghost User"),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            if (!user.email.isNullOrBlank()) {
                                profile["email"] = user.email!!
                            }
                            firestore.collection("users").document(user.uid).set(profile, SetOptions.merge())
                        } catch (e: Exception) {
                            // Non-blocking sync error
                        }
                    }
                }

                val currentScreen = when {
                    !isVaultUnlocked.value -> AppScreen.CALCULATOR
                    isDecoyMode.value -> AppScreen.DECOY_PHOTO_VAULT
                    currentUser == null && !hasSkippedAuth -> AppScreen.AUTH
                    else -> AppScreen.MESSENGER
                }

                // Hardware Back button handling
                BackHandler(enabled = isVaultUnlocked.value) {
                    if (currentScreen == AppScreen.MESSENGER && messengerViewModel.uiState.value.activeContact != null) {
                        messengerViewModel.closeConversation()
                    } else {
                        isVaultUnlocked.value = false
                        isDecoyMode.value = false
                    }
                }

                Crossfade(
                    targetState = currentScreen,
                    label = "screen_transition",
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                    when (screen) {
                        AppScreen.CALCULATOR -> {
                            CalculatorScreen(
                                viewModel = calcViewModel,
                                onUnlock = { isDecoy ->
                                    isDecoyMode.value = isDecoy
                                    messengerViewModel.setDecoyMode(isDecoy)
                                    isVaultUnlocked.value = true
                                }
                            )
                        }
                        AppScreen.DECOY_PHOTO_VAULT -> {
                            DecoyPhotoVaultScreen(
                                onLockToCalculator = {
                                    isVaultUnlocked.value = false
                                    isDecoyMode.value = false
                                }
                            )
                        }
                        AppScreen.AUTH -> {
                            GmailAuthScreen(
                                onGoogleSignInClicked = {
                                    authManager.signInWithGoogle(
                                        activity = this@MainActivity,
                                        scope = lifecycleScope,
                                        onSuccess = {
                                            val user = authManager.currentUser.value
                                            messengerViewModel.onUserSignedIn(user?.email, user?.displayName)
                                        },
                                        onError = {}
                                    )
                                },
                                onSkipOffline = {
                                    hasSkippedAuth = true
                                },
                                onLockToCalculator = {
                                    isVaultUnlocked.value = false
                                    isDecoyMode.value = false
                                }
                            )
                        }
                        AppScreen.MESSENGER -> {
                            MessengerMainScreen(
                                viewModel = messengerViewModel,
                                currentUser = currentUser,
                                onGoogleSignIn = {
                                    authManager.signInWithGoogle(
                                        activity = this@MainActivity,
                                        scope = lifecycleScope,
                                        onSuccess = {
                                            val user = authManager.currentUser.value
                                            messengerViewModel.onUserSignedIn(user?.email, user?.displayName)
                                        },
                                        onError = {}
                                    )
                                },
                                onSignOut = {
                                    authManager.signOut(lifecycleScope)
                                    hasSkippedAuth = false
                                },
                                onLockToCalculator = {
                                    messengerViewModel.closeConversation()
                                    isVaultUnlocked.value = false
                                    isDecoyMode.value = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private var activeMessengerViewModel: MessengerViewModel? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("ACTION_INCOMING_CALL", false) ||
            intent.getBooleanExtra("ACTION_ANSWER_CALL", false)
        ) {
            isVaultUnlocked.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        activeMessengerViewModel?.closeConversation()
        // Stealth auto-lock: if user minimizes the app or screen turns off, re-lock to calculator
        if (preferences.lockOnMinimize) {
            isVaultUnlocked.value = false
        }
    }
}
