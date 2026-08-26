package com.inputleaf.android.shizuku

import android.util.Log
import android.view.InputEvent
import java.lang.reflect.Method

/**
 * Resolves a privileged [InputManager.injectInputEvent][android.hardware.input.InputManager]
 * handle across Android versions.
 *
 * Android 14 removed the hidden `InputManager.getInstance()` singleton and moved injection
 * onto `InputManagerGlobal`. Calling the old method in a Shizuku UserService constructor
 * crashes the process and looks like a bind timeout.
 */
internal object HiddenInputManager {
    private const val TAG = "HiddenInputManager"
    private const val INVALID_UID = -1

    internal data class Target(
        val instance: Any,
        val injectMethod: Method
    )

    fun resolve(): Target? {
        return try {
            resolveOrNull()
        } catch (e: Throwable) {
            logError("No InputManager injectInputEvent available", e)
            null
        }
    }

    private fun resolveOrNull(): Target? {
        val attempts = listOf(
            { fromNamedSingleton("android.hardware.input.InputManagerGlobal") },
            { fromNamedSingleton("android.hardware.input.InputManager") },
            { fromApplicationInputService() },
            { fromIInputManagerBinder() }
        )
        for (attempt in attempts) {
            try {
                val target = attempt()
                if (target != null) {
                    logInfo(
                        "Using ${target.instance.javaClass.name}#${target.injectMethod.name}" +
                            "(${target.injectMethod.parameterCount} args)"
                    )
                    return target
                }
            } catch (e: Throwable) {
                logWarn("Input manager resolve attempt failed", e)
            }
        }
        logError("No InputManager injectInputEvent available")
        return null
    }

    fun inject(target: Target?, event: InputEvent, mode: Int): Boolean {
        if (target == null) return false
        return invokeInject(target, event, mode) as? Boolean ?: false
    }

    internal fun findInjectMethod(clazz: Class<*>): Method? {
        val candidates = collectMethods(clazz).filter { it.isInjectCandidate() }
        return candidates.firstOrNull { it.name == "injectInputEvent" && it.parameterCount == 2 }
            ?: candidates.firstOrNull { it.name == "injectInputEvent" && it.parameterCount == 3 }
            ?: candidates.firstOrNull { it.name == "injectInputEventToTarget" }
            ?: candidates.firstOrNull()
    }

    internal fun invokeInject(target: Target, event: Any, mode: Int): Any? {
        val params = target.injectMethod.parameterTypes
        val args = Array(params.size) { index ->
            when (index) {
                0 -> event
                1 -> mode
                else -> if (params[index] == Int::class.javaPrimitiveType) INVALID_UID else null
            }
        }
        return target.injectMethod.invoke(target.instance, *args)
    }

    private fun fromNamedSingleton(className: String): Target? {
        val clazz = Class.forName(className)
        val instance = invokeNoArg(clazz, "getInstance") ?: return null
        return bind(instance, clazz)
    }

    private fun fromApplicationInputService(): Target? {
        val application = invokeNoArg(
            Class.forName("android.app.ActivityThread"),
            "currentApplication"
        ) ?: return null
        val instance = application.javaClass.methods
            .firstOrNull { method ->
                method.name == "getSystemService" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == String::class.java
            }
            ?.invoke(application, "input")
            ?: return null
        return bind(instance, instance.javaClass)
    }

    private fun fromIInputManagerBinder(): Target? {
        val binder = invoke(
            Class.forName("android.os.ServiceManager"),
            "getService",
            arrayOf(String::class.java),
            arrayOf("input")
        ) ?: return null
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        val instance = invoke(
            stub,
            "asInterface",
            arrayOf(Class.forName("android.os.IBinder")),
            arrayOf(binder)
        ) ?: return null
        return bind(instance, instance.javaClass)
    }

    private fun bind(instance: Any, declaredClass: Class<*>): Target? {
        val method = findInjectMethod(instance.javaClass) ?: findInjectMethod(declaredClass)
        method?.isAccessible = true
        return method?.let { Target(instance, it) }
    }

    private fun invokeNoArg(clazz: Class<*>, name: String): Any? {
        return invoke(clazz, name, emptyArray(), emptyArray())
    }

    private fun invoke(
        clazz: Class<*>,
        name: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any?>
    ): Any? {
        val method = try {
            clazz.getMethod(name, *parameterTypes)
        } catch (_: NoSuchMethodException) {
            clazz.getDeclaredMethod(name, *parameterTypes)
        }
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    private fun collectMethods(clazz: Class<*>): List<Method> {
        val seen = LinkedHashSet<Method>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            seen += current.methods
            seen += current.declaredMethods
            current = current.superclass
        }
        return seen.toList()
    }

    private fun Method.isInjectCandidate(): Boolean {
        if (name != "injectInputEvent" && name != "injectInputEventToTarget") return false
        if (parameterCount < 2) return false
        if (parameterTypes[0].isPrimitive) return false
        return parameterTypes[1] == Int::class.javaPrimitiveType
    }

    private fun logInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
        }
    }

    private fun logWarn(message: String, error: Throwable) {
        try {
            Log.w(TAG, message, error)
        } catch (_: Throwable) {
        }
    }

    private fun logError(message: String, error: Throwable? = null) {
        try {
            if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
        } catch (_: Throwable) {
        }
    }
}
