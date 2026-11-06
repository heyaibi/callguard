package com.callguard.messaging

import android.app.role.RoleManager
import android.content.Context

/**
 * True when CallGuard holds the SMS role, so the telephony stack delivers texts to it and it must store them.
 * Read from RoleManager, because Telephony.Sms.getDefaultSmsPackage does not report the role on this platform.
 */
fun isDefaultSmsApp(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
