package com.vibiate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Loads launchable apps (labels + icons) off the main thread, classifies them into
 * [AppCategory] buckets, and caches the result. Covers every profile of this
 * user (e.g. a work profile) through [LauncherApps]. Refreshed via
 * [invalidate] when package or profile changes arrive.
 */
class AppRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadMutex = Mutex()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        // Package changes in other profiles never reach the package
        // broadcasts MainActivity listens to; LauncherApps reports them all.
        runCatching {
            launcherApps.registerCallback(
                object : LauncherApps.Callback() {
                    override fun onPackageRemoved(p: String, u: UserHandle) = invalidate()
                    override fun onPackageAdded(p: String, u: UserHandle) = invalidate()
                    override fun onPackageChanged(p: String, u: UserHandle) = invalidate()
                    override fun onPackagesAvailable(
                        p: Array<out String>, u: UserHandle, replacing: Boolean,
                    ) = invalidate()
                    override fun onPackagesUnavailable(
                        p: Array<out String>, u: UserHandle, replacing: Boolean,
                    ) = invalidate()
                    override fun onPackagesSuspended(p: Array<out String>, u: UserHandle) =
                        invalidate()
                    override fun onPackagesUnsuspended(p: Array<out String>, u: UserHandle) =
                        invalidate()
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /** Start loading if we haven't yet. Safe to call repeatedly. */
    fun ensureLoaded() {
        if (!_loaded.value) invalidate()
    }

    fun invalidate() {
        scope.launch { load() }
    }

    /**
     * Launch [entry]: own-profile apps start directly; work apps go through
     * LauncherApps; a paused work profile is asked to turn back on.
     */
    fun launch(entry: AppEntry) {
        launchComponent(context, entry.component, entry.user, entry.paused)
    }

    private suspend fun load() {
        loadMutex.withLock {
            val me = Process.myUserHandle()
            val entries = userManager.userProfiles.flatMap { user ->
                val own = user == me
                val serial = if (own) 0L else userManager.getSerialNumberForUser(user)
                val paused = !own && runCatching {
                    userManager.isQuietModeEnabled(user)
                }.getOrDefault(false)
                runCatching { launcherApps.getActivityList(null, user) }
                    .getOrDefault(emptyList())
                    .filter { !(own && it.componentName.packageName == context.packageName) }
                    .map { lai ->
                        AppEntry(
                            packageName = lai.componentName.packageName,
                            activityName = lai.componentName.className,
                            label = lai.label.toString(),
                            icon = lai.getIcon(0),
                            category = CategoryClassifier.classify(lai.applicationInfo),
                            user = if (own) null else user,
                            userSerial = serial,
                            paused = paused,
                        )
                    }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            _apps.value = entries
            _loaded.value = true
        }
    }
}

/** Start [component] in [user]'s profile (null = own user). */
fun launchComponent(
    context: Context,
    component: ComponentName,
    user: UserHandle?,
    paused: Boolean = false,
) {
    runCatching {
        if (user == null) {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else if (paused) {
            context.getSystemService(UserManager::class.java)
                .requestQuietModeEnabled(false, user)
        } else {
            context.getSystemService(LauncherApps::class.java)
                .startMainActivity(component, user, null, null)
        }
    }
}

/** System "App info" for [component] in [user]'s profile (null = own user). */
fun openAppInfo(context: Context, component: ComponentName, user: UserHandle?) {
    runCatching {
        if (user == null) {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${component.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(component, user, null, null)
        }
    }
}
