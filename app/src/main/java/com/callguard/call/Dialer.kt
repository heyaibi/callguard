package com.callguard.call

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

/**
 * Placing calls and the default-dialer role. CallGuard holds ROLE_DIALER only when the user chooses it: then Telecom
 * routes calls and in-call UI to [CallGuardInCallService].
 */
object Dialer {
    /**
     * Places an outgoing call through Telecom and returns true when Telecom accepted the request. The call is shown on
     * [InCallActivity] once [CallGuardInCallService] adds it. Requires CALL_PHONE: the caller asks for it first, as
     * [DialActivity] does, and this returns false without it. Also false for a blank number or when Telecom refuses.
     */
    fun placeCall(
        context: Context,
        number: String,
    ): Boolean {
        val digits = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (digits.isEmpty()) return false
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return try {
            telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, digits, null), Bundle())
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** True while CallGuard holds the dialer role. */
    fun isDefaultDialer(context: Context): Boolean = context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    /**
     * The system prompt that asks the user to make CallGuard the default phone app, or null when RoleManager is missing.
     * Start it with an activity-result launcher; the user's answer arrives as the result code.
     */
    fun requestRoleIntent(context: Context): Intent? = context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
}
