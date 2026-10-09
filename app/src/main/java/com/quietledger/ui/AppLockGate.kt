package com.quietledger.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * A screen-level privacy lock. It delegates authentication entirely to Android;
 * Quiet Ledger never asks for, receives, or saves a PIN or biometric template.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var locked by remember(enabled) { mutableStateOf(enabled) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(enabled, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_STOP) locked = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!enabled || !locked) {
        content()
    } else {
        LockScreen(
            errorMessage = errorMessage,
            onUnlock = {
                if (activity == null) {
                    errorMessage = "This device cannot show the Android security prompt."
                } else {
                    errorMessage = null
                    authenticateWithDevice(
                        activity = activity,
                        onSuccess = { locked = false },
                        onError = { errorMessage = it }
                    )
                }
            }
        )
    }
}

@Composable
private fun LockScreen(errorMessage: String?, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text("Quiet Ledger is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use your fingerprint or phone screen lock to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
            Text("Unlock")
        }
    }
}

private fun authenticateWithDevice(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_CANCELED
            ) {
                onError(errString.toString())
            }
        }
    }

    val authenticatorFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Quiet Ledger")
        .setSubtitle("Your fingerprint or phone screen lock stays on this device")
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setAllowedAuthenticators(authenticatorFlags)
            } else {
                setNegativeButtonText("Cancel")
            }
        }
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}
