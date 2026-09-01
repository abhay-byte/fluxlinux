package com.termux.x11;

import static android.system.Os.getuid;
import static android.system.Os.getenv;

import android.annotation.SuppressLint;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Keep;

import java.io.OutputStream;
import java.io.PrintStream;

@Keep @SuppressLint({"StaticFieldLeak", "UnsafeDynamicallyLoadedCode"})
public class CmdEntryPoint extends ICmdEntryInterface.Stub {
    public static final String ACTION_START = "com.termux.x11.CmdEntryPoint.ACTION_START";
    static final Handler handler;
    public static Context ctx;
    private final Intent intent = createIntent();

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static int main(String[] args) {
        return main(args, null);
    }

    /** Run the embedded server and return its native Xorg status to the owner thread. */
    public static int main(String[] args, Runnable onStarted) {
        android.util.Log.i("CmdEntryPoint", "commit " + BuildConfig.COMMIT);
        CmdEntryPoint endpoint = new CmdEntryPoint(args, onStarted);
        if (!endpoint.started)
            return 1;
        try {
            return waitForServer();
        } finally {
            endpoint.close();
        }
    }

    private final boolean started;
    private final Runnable delayedBroadcast = new Runnable() {
        @Override
        public void run() {
            sendBroadcastDelayed();
        }
    };

    CmdEntryPoint(String[] args, Runnable onStarted) {
        started = start(args);
        if (!started) {
            Log.e("CmdEntryPoint", "Embedded X11 native start failed");
            return;
        }

        if (onStarted != null)
            onStarted.run();
        // The embedded owner does not need the standalone TCP discovery
        // listener. Send the same-package ACTION_START directly, then retry
        // briefly while the in-app display Activity finishes registering.
        sendBroadcast(intent);
        handler.postDelayed(delayedBroadcast, 100);
    }

    private void close() {
        handler.removeCallbacks(delayedBroadcast);
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private Intent createIntent() {
        String targetPackage = getenv("TERMUX_X11_OVERRIDE_PACKAGE");
        if (targetPackage == null)
            targetPackage = ctx != null ? ctx.getPackageName() : "com.termux.x11";
        // We should not care about multiple instances, it should be called only by `Termux:X11` app
        // which is single instance...
        Bundle bundle = new Bundle();
        bundle.putBinder(null, this);

        Intent intent = new Intent(ACTION_START);
        intent.putExtra(null, bundle);
        intent.setPackage(targetPackage);

        if (getuid() == 0 || getuid() == 2000)
            intent.setFlags(0x00400000 /* FLAG_RECEIVER_FROM_SHELL */);

        return intent;
    }

    private void sendBroadcast() {
        sendBroadcast(intent);
    }

    static void sendBroadcast(Intent intent) {
        try {
            ctx.sendBroadcast(intent);
        } catch (Exception e) {
            if (e instanceof NullPointerException && ctx == null)
                Log.i("Broadcast", "Context is null, falling back to manual broadcasting");
            else
                Log.e("Broadcast", "Falling back to manual broadcasting, failed to broadcast intent through Context:", e);

            String packageName;
            try {
                packageName = android.app.ActivityThread.getPackageManager().getPackagesForUid(getuid())[0];
            } catch (RemoteException ex) {
                throw new RuntimeException(ex);
            }
            IActivityManager am;
            try {
                //noinspection JavaReflectionMemberAccess
                am = (IActivityManager) android.app.ActivityManager.class
                        .getMethod("getService")
                        .invoke(null);
            } catch (Exception e2) {
                try {
                    am = (IActivityManager) Class.forName("android.app.ActivityManagerNative")
                            .getMethod("getDefault")
                            .invoke(null);
                } catch (Exception e3) {
                    throw new RuntimeException(e3);
                }
            }

            assert am != null;
            IIntentSender sender = am.getIntentSender(1, packageName, null, null, 0, new Intent[] { intent },
                    null, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT, null, 0);
            try {
                //noinspection JavaReflectionMemberAccess
                IIntentSender.class
                        .getMethod("send", int.class, Intent.class, String.class, IBinder.class, IIntentReceiver.class, String.class, Bundle.class)
                        .invoke(sender, 0, intent, null, null, new IIntentReceiver.Stub() {
                            @Override public void performReceive(Intent i, int r, String d, Bundle e, boolean o, boolean s, int a) {}
                        }, null, null);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // In some cases Android Activity part can not connect opened port.
    // In this case opened port works like a lock file.
    private void sendBroadcastDelayed() {
        if (!connected())
            sendBroadcast(intent);

        handler.postDelayed(delayedBroadcast, 1000);
    }

    /** @noinspection DataFlowIssue*/
    @SuppressLint("DiscouragedPrivateApi")
    public static Context createContext() {
        Context context;
        PrintStream err = System.err;
        try {
            // Normal library integration: use the current app context so
            // ACTION_START and native-library lookup stay same-package.
            Object application = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            if (application instanceof Context)
                return (Context) application;
        } catch (Exception ignored) {
            // app_process compatibility fallback below is retained for tests
            // and downstream consumers, but FluxLinux no longer invokes it.
        }
        try {
            java.lang.reflect.Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            // Hiding harmless framework errors, like this:
            // java.io.FileNotFoundException: /data/system/theme_config/theme_compatibility.xml: open failed: ENOENT (No such file or directory)
            System.setErr(new PrintStream(new OutputStream() { public void write(int arg0) {} }));
            if (System.getenv("OLD_CONTEXT") != null) {
                context = android.app.ActivityThread.systemMain().getSystemContext();
            } else {
                context = ((android.app.ActivityThread) Class.
                        forName("sun.misc.Unsafe").
                        getMethod("allocateInstance", Class.class).
                        invoke(unsafe, android.app.ActivityThread.class))
                        .getSystemContext();
            }
        } catch (Exception e) {
            Log.e("Context", "Failed to instantiate context:", e);
            context = null;
        } finally {
            System.setErr(err);
        }
        return context;
    }

    public static native boolean start(String[] args);
    /** Supply the app-private shared runtime directory to the embedded native server. */
    public static native void setTmpDir(String path);
    /** Supply the app-private XKB tree to the embedded native server. */
    public static native void setXkbConfigRoot(String path);
    /** Start frame callbacks from a Looper that is actively being pumped. */
    public static native void startFrameCallbacks();
    private static native int waitForServer();
    public static native void stop();
    public native ParcelFileDescriptor getXConnection();
    public native ParcelFileDescriptor getLogcatOutput();
    private static native boolean connected();
    private native void listenForConnections();

    static {
        try {
            if (Looper.getMainLooper() == null)
                Looper.prepareMainLooper();
        } catch (Exception e) {
            Log.e("CmdEntryPoint", "Something went wrong when preparing MainLooper", e);
        }
        // EmbeddedX11 initializes this class from its server thread. The
        // server thread blocks in waitForServer() and never enters a Looper,
        // so delayed ACTION_START broadcasts must run on Android's main
        // Looper where MainActivity can receive them.
        handler = new Handler(Looper.getMainLooper());
        ctx = createContext();

        try {
            // :termux-x11 is an Android library dependency. System.loadLibrary
            // resolves its compiled libXlorie.so from applicationInfo.nativeLibraryDir;
            // no APK path, extracted app-data copy, or app_process is involved.
            System.loadLibrary("Xlorie");
        } catch (UnsatisfiedLinkError | Exception e) {
            Log.e("CmdEntryPoint", "Failed to load embedded libXlorie.so", e);
            System.err.println("Failed to load embedded X11 native library.");
        }
    }
}
