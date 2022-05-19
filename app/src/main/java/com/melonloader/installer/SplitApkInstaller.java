package com.melonloader.installer;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.melonloader.installer.activites.SpecializedGame;
import com.sircoolness.poponeinstaller.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SplitApkInstaller {
    public static final String TAG = "SplitApkInstaller";
    PackageInstaller packageInstaller;
    Activity appContext;

    ActivityResultLauncher<Intent> installActivityLauncher;

    public SplitApkInstaller(ComponentActivity context, ActivityResultLauncher<Intent> installActivityLauncher)
    {
        this.installActivityLauncher = installActivityLauncher;
        this.appContext = context;
        this.packageInstaller = context.getPackageManager().getPackageInstaller();
    }

    public int InstallPackages(String baseApk)
    {
//        Log.e(TAG, baseApk);
//
//        requestInstallPermission();
//
//        return InstallPackages(baseApk, new ArrayList<String>());
        InternalInstall(baseApk);
        return PackageInstaller.STATUS_PENDING_USER_ACTION;
    }

    public int InstallPackages(String baseApk, List<String> splitApks)
    {
        List<String> files = new ArrayList<String>(splitApks);
        files.add(0, baseApk);

        long accSize = 0;

        for (String filePath : files) {
            final File file = new File(filePath);
            if (!file.isFile()) {
                Log.e(TAG, "file " + filePath + " does not exist or is directory.");
                return PackageInstaller.STATUS_FAILURE_INVALID;
            }

            accSize += file.length();
        }

        final InstallParams installParams = makeInstallParams(accSize);

        try {
            for (PackageInstaller.SessionInfo allSession : packageInstaller.getAllSessions()) {
                packageInstaller.abandonSession(allSession.getSessionId());
            }

            int sessionId = runInstallCreate(installParams);

            for (String filePath : files) {
                final File file = new File(filePath);

                runInstallWrite(file.length(), sessionId, file.getName(), filePath);
            }

            return doCommitSession(sessionId, false );
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return PackageInstaller.STATUS_FAILURE;
    }


    private int runInstallCreate(InstallParams installParams) throws RemoteException {
        final int sessionId = doCreateSession(installParams.sessionParams);
        Log.i(TAG, "Success: created install session [" + sessionId + "]");
        return sessionId;
    }

    private int doCreateSession(PackageInstaller.SessionParams params)
            throws RemoteException {

        int sessionId = 0 ;
        try {
            sessionId = packageInstaller.createSession(params);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sessionId;
    }

    private int runInstallWrite(long size, int sessionId , String splitName ,String path ) throws RemoteException {
        long sizeBytes = -1;

        String opt;
        sizeBytes = size;
        return doWriteSession(sessionId, path, sizeBytes, splitName, true /*logSuccess*/);
    }


    private int doWriteSession(int sessionId, String inPath, long sizeBytes, String splitName,
                               boolean logSuccess) throws RemoteException {
        if ("-".equals(inPath)) {
            inPath = null;
        } else if (inPath != null) {
            final File file = new File(inPath);
            if (file.isFile()) {
                sizeBytes = file.length();
            }
        }

        final PackageInstaller.SessionInfo info = packageInstaller.getSessionInfo(sessionId);

        PackageInstaller.Session session = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            session = packageInstaller.openSession(sessionId);

            if (inPath != null) {
                requestWritePermission();
                in = new FileInputStream(inPath);
            }

            out = session.openWrite(splitName, 0, sizeBytes);

            int total = 0;
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                total += c;
                out.write(buffer, 0, c);
            }
            session.fsync(out);

            if (logSuccess) {
                Log.i(TAG, "Success: streamed " + total + " bytes");
            }
            return PackageInstaller.STATUS_SUCCESS;
        } catch (IOException e) {
            Log.e(TAG, "Error: failed to write; " + e.getMessage());
            return PackageInstaller.STATUS_FAILURE;
        } finally {
            try {
                out.close();
                in.close();
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private int doCommitSession(int sessionId, boolean logSuccess) throws RemoteException {
        PackageInstaller.Session session = null;
        try {
            try {
                session = packageInstaller.openSession(sessionId);
            } catch (IOException e) {
                e.printStackTrace();
            }

            IntentSender installIntent = PendingIntent.getActivity(appContext, sessionId,
                    new Intent(appContext, SpecializedGame.class), 0).getIntentSender();

            this.packageInstaller.registerSessionCallback(new PackageInstaller.SessionCallback() {
                @Override
                public void onCreated(int i) {
                    if (i != sessionId) {
                        return;
                    }

                    Log.d(TAG, "doCommitSession:  [" + i + "] onCreated");
                }

                @Override
                public void onBadgingChanged(int i) {
                    if (i != sessionId) {
                        return;
                    }

                    Log.d(TAG, "doCommitSession:  [" + i + "] onBadgingChanged");
                }

                @Override
                public void onActiveChanged(int i, boolean b) {
                    if (i != sessionId) {
                        return;
                    }

                    Log.d(TAG, "doCommitSession:  [" + i + "] onActiveChanged " + b);
                }

                @Override
                public void onProgressChanged(int i, float v) {
                    if (i != sessionId) {
                        return;
                    }

                    Log.d(TAG, "doCommitSession:  [" + i + "] onProgressChanged " + v);
                }

                @Override
                public void onFinished(int i, boolean b) {
                    if (i != sessionId) {
                        return;
                    }
                    packageInstaller.unregisterSessionCallback(this);

                    Log.d(TAG, "doCommitSession:  [" + i + "] onFinished " + b);
                }
            });

            session.commit(installIntent);
            Log.i(TAG, "install request sent");

            Log.d(TAG, "doCommitSession: " + packageInstaller.getMySessions());

            Log.d(TAG, "doCommitSession: after session commit ");
            return 1;
        } finally {
            session.close();
        }
    }

    private static class InstallParams {
        PackageInstaller.SessionParams sessionParams;
    }

    private InstallParams makeInstallParams(long totalSize ) {
        final PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        final InstallParams params = new InstallParams();
        params.sessionParams = sessionParams;
        String opt;
        sessionParams.setSize(totalSize);
        sessionParams.setAppPackageName("Test Game");
        return params;
    }

    private void requestWritePermission()
    {
        if (appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ((Activity)appContext).requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 100);
        }
    }

    private void requestInstallPermission()
    {
        if (appContext.checkSelfPermission(Manifest.permission.INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            ((Activity)appContext).requestPermissions(new String[] { Manifest.permission.INSTALL_PACKAGES }, 101);
        }
    }

    protected void InternalInstall(String path)
    {
        Uri filePath = uriFromFile(appContext, new File(path));

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(filePath, "application/vnd.android.package-archive");

        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
//            appContext.startActivity(install);
            installActivityLauncher.launch(install);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Log.e("TAG", "Error in opening the file!");
        }
    }

    private static Uri uriFromFile(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
        } else {
            return Uri.fromFile(file);
        }
    }
}
