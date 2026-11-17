# Libraries ship their own consumer rules (Compose, coroutines, DataStore, libsu).
# These are only the classes started or looked up by name.

# Started by class name in a root/shell app_process, not through the manifest.
-keep class com.inputleaf.android.shizuku.StandaloneInjectorMain { public static void main(java.lang.String[]); }
-keep class com.inputleaf.android.shizuku.ClipboardHelperMain { public static void main(java.lang.String[]); }

# Shizuku creates the UserService and libsu the RootService by class name.
-keep class com.inputleaf.android.shizuku.InputInjectorService { <init>(...); }
-keep class com.inputleaf.android.privilege.RootInputInjectorService { <init>(); }

# StandaloneInjectorLauncher calls the private Shizuku.newProcess by reflection.
-keepclassmembers class rikka.shizuku.Shizuku {
    private static java.lang.Process newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

# libsu's root launcher (assets/main.jar) loads this class by its literal name.
-keep class com.topjohnwu.superuser.internal.RootServerMain { *; }
