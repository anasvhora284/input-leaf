package com.inputleaf.android.shizuku

import android.content.AttributionSource
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import androidx.annotation.RequiresApi

/**
 * Privileged side of the standalone injector handoff: reaches the app's
 * [InjectorHandoffProvider] the way `content call` does and passes it the injector binder.
 *
 * Runs only inside the shell/root `app_process`, where hidden APIs are not restricted,
 * so the provider plumbing goes through reflection rather than compile-time stubs.
 */
internal object InjectorHandoff {
    const val METHOD_ATTACH = "attachInjector"
    const val KEY_INJECTOR = "injector"
    const val KEY_CLIENT = "client"

    private const val TAG = "StandaloneInjector"

    /** @return the app's client token, which dies with the app process. */
    fun deliver(authority: String, userId: Int, injector: IBinder): IBinder {
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        val token = Binder()
        val holder = getContentProviderExternal(am, authority, userId, token)
            ?: error("Provider $authority not found for user $userId")
        try {
            val provider = holder.javaClass.getField("provider").get(holder)
                ?: error("Provider $authority has no binder")
            val extras = Bundle().apply { putBinder(KEY_INJECTOR, injector) }
            val reply = callProvider(provider, authority, extras)
            return reply?.getBinder(KEY_CLIENT) ?: error("Provider $authority returned no client token")
        } finally {
            runCatching { removeContentProviderExternal(am, authority, token, userId) }
        }
    }

    private fun getContentProviderExternal(am: Any, authority: String, userId: Int, token: IBinder): Any? {
        val method = am.javaClass.methods.first { it.name == "getContentProviderExternal" }
        return when (method.parameterCount) {
            // Q+: (name, userId, token, tag)
            4 -> method.invoke(am, authority, userId, token, TAG)
            else -> method.invoke(am, authority, userId, token)
        }
    }

    private fun removeContentProviderExternal(am: Any, authority: String, token: IBinder, userId: Int) {
        val asUser = am.javaClass.methods.firstOrNull { it.name == "removeContentProviderExternalAsUser" }
        if (asUser != null) {
            asUser.invoke(am, authority, token, userId)
        } else {
            am.javaClass.getMethod("removeContentProviderExternal", String::class.java, IBinder::class.java)
                .invoke(am, authority, token)
        }
    }

    private fun callProvider(provider: Any, authority: String, extras: Bundle): Bundle? {
        val iface = Class.forName("android.content.IContentProvider")
        val callingPackage = if (Process.myUid() == 0) "root" else "com.android.shell"
        val string = String::class.java
        val bundle = Bundle::class.java
        // IContentProvider carries several call() overloads (S adds AttributionSource and
        // keeps a legacy one), so pick by exact signature rather than by name.
        val result = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                iface.getMethod("call", AttributionSource::class.java, string, string, string, bundle)
                    .invoke(provider, attributionSource(callingPackage), authority, METHOD_ATTACH, null, extras)
            // R: (callingPkg, attributionTag, authority, method, arg, extras)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                iface.getMethod("call", string, string, string, string, string, bundle)
                    .invoke(provider, callingPackage, null, authority, METHOD_ATTACH, null, extras)
            // Q: (callingPkg, authority, method, arg, extras)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                iface.getMethod("call", string, string, string, string, bundle)
                    .invoke(provider, callingPackage, authority, METHOD_ATTACH, null, extras)
            else ->
                iface.getMethod("call", string, string, string, bundle)
                    .invoke(provider, callingPackage, METHOD_ATTACH, null, extras)
        }
        return result as Bundle?
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun attributionSource(callingPackage: String): AttributionSource =
        AttributionSource.Builder(Process.myUid()).setPackageName(callingPackage).build()
}
