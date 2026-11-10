package app.gamenative.ui.screen.wow

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** What the launcher does on its own at first start, so a new player doesn't have to find and press each button. */
internal enum class FirstRunStep { None, RequestStorage, RequestMicrophone, RequestNotifications, RequestBatteryExemption, AutoInstall, PromptLogin }

internal data class FirstRunInput(
    /** An install, update or launch is already running. */
    val busy: Boolean,
    val hasStorageAccess: Boolean,
    val micGranted: Boolean,
    /** Notifications are allowed, or the system needs no permission for them (before Android 13). */
    val notificationsOk: Boolean,
    /** The app is already exempt from battery optimization. */
    val batteryExempt: Boolean,
    val isWrongGame: Boolean,
    val isLegacyBeta: Boolean,
    val needsInstall: Boolean,
    val hasSavedLogin: Boolean,
    /** The player picked an install folder (or located existing files), so the default location is not wanted. */
    val locationChosen: Boolean,
    /** Wi-Fi or another unmetered, non-cellular connection. */
    val unmetered: Boolean,
    /** The game is about to start by itself; the microphone prompt must not interrupt that. */
    val autoLaunchPending: Boolean,
    val storageRequested: Boolean,
    val micRequested: Boolean,
    val notificationsRequested: Boolean,
    val batteryRequested: Boolean,
    val installAttempted: Boolean,
    val loginPrompted: Boolean,
)

/**
 * The next automatic step, or [FirstRunStep.None]. Every step has a "done once" flag in the input, so declining,
 * cancelling or failing a step ends the flow instead of repeating it; the matching button stays available.
 * Order: file access, microphone, notifications, battery exemption, install, login. Notifications and the battery
 * exemption keep the connection alive during long sessions; refusing either blocks nothing.
 */
internal fun nextFirstRunStep(i: FirstRunInput): FirstRunStep = when {
    i.busy -> FirstRunStep.None
    !i.hasStorageAccess -> if (i.storageRequested) FirstRunStep.None else FirstRunStep.RequestStorage
    !i.micGranted && !i.micRequested && !i.autoLaunchPending -> FirstRunStep.RequestMicrophone
    !i.notificationsOk && !i.notificationsRequested && !i.autoLaunchPending -> FirstRunStep.RequestNotifications
    !i.batteryExempt && !i.batteryRequested && !i.autoLaunchPending -> FirstRunStep.RequestBatteryExemption
    i.isWrongGame -> FirstRunStep.None
    i.needsInstall ->
        // A beta upgrade keeps its explanatory dialog; a chosen folder or mobile data keeps the manual button.
        if (!i.isLegacyBeta && !i.locationChosen && i.unmetered && !i.installAttempted) FirstRunStep.AutoInstall else FirstRunStep.None
    !i.hasSavedLogin && !i.loginPrompted -> FirstRunStep.PromptLogin
    else -> FirstRunStep.None
}

/** True while a permission prompt of the flow (microphone, notifications, battery) is still due before the game starts. */
internal fun permissionPromptPending(
    micGranted: Boolean, micRequested: Boolean,
    notificationsOk: Boolean, notificationsRequested: Boolean,
    batteryExempt: Boolean, batteryRequested: Boolean,
) = (!micGranted && !micRequested) || (!notificationsOk && !notificationsRequested) || (!batteryExempt && !batteryRequested)

internal fun firstRunPermissionsPending(context: Context) = permissionPromptPending(
    micGranted = MicPermission.granted(context), micRequested = MicPermission.wasAsked(context),
    notificationsOk = NotificationPermission.granted(context), notificationsRequested = NotificationPermission.wasAsked(context),
    batteryExempt = BatteryExemption.exempt(context), batteryRequested = BatteryExemption.wasAsked(context),
)

/** Saving the login the flow asked for starts the game, unless something still needs the player's attention. */
internal fun shouldLaunchAfterLogin(installed: Boolean, isWrongGame: Boolean, isOutdated: Boolean) =
    installed && !isWrongGame && !isOutdated

/** A multi-GB-capable download starts by itself only on an unmetered connection that isn't cellular. */
internal fun allowsAutoDownload(hasInternet: Boolean, notMetered: Boolean, isCellular: Boolean) =
    hasInternet && notMetered && !isCellular

internal fun isUnmeteredNetwork(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    allowsAutoDownload(
        hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    )
}.getOrDefault(false)
