package com.melonloader.installer.activites;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.melonloader.installer.ApkInstallerHelper;
import com.melonloader.installer.ApplicationFinder;
import com.melonloader.installer.InstallationStatus;
import com.melonloader.installer.SplitApkInstaller;
import com.melonloader.installer.SupportedApplication;
import com.melonloader.installer.core.Main;
import com.sircoolness.poponeinstaller.R;

public class InstallGameActivity extends AppCompatActivity {
    private ApkInstallerHelper installerHelper = null;

    private String targetPackageName;
    private boolean automated = false;
    private String outputFile;

    private boolean pending = false;
    private boolean failed = false;
    private boolean firstTime = true;

    private SplitApkInstaller installer;

    ActivityResultLauncher<Intent> uninstallActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    LogStatus(result);
                    if (CheckIfInstalled()) {
                        OnFail();
                        return;
                    }

                    AsyncTask.execute(() -> {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        runOnUiThread(() -> StartInstall());
                    });
                }
            }
    );

    ActivityResultLauncher<Intent> installActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    LogStatus(result);

                    Log.i(getLocalClassName(), "onActivityResult: " + result.getResultCode());

                    if (!CheckIfInstalled()) {
                        OnFail();
                        return;
                    }

                    OnComplete();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        installer = new SplitApkInstaller(this, installActivityLauncher);

        setContentView(R.layout.activity_installer);

        setResult(Activity.RESULT_CANCELED);

        targetPackageName = getIntent().getStringExtra("target.packageName");
        if (targetPackageName == null) {
            finish();
            return;
        }

        this.automated = getIntent().getBooleanExtra("target.auto", false);

        if (this.automated) {
            this.outputFile = getIntent().getStringExtra("target.output_file");
        }

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        Log.e("melonloader", "on create");
        this.pending = false;
        this.failed = true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        Reload();

        if (automated && this.firstTime) {
            this.firstTime = false;
            StartInstall();
        }
    }

    protected void Reload() {
        ConstraintLayout retryView = findViewById(R.id.retryView);
        ConstraintLayout installprogressindicator = findViewById(R.id.installprogressindicator);

        Button retryButton = findViewById(R.id.retryButton);
        TextView retryMessage = findViewById(R.id.retryMessage);

        TextView loadingStatus = findViewById(R.id.loadingStatus);

        retryButton.setEnabled(false);
        retryButton.setText("retry");
        retryMessage.setText("Failed to install application.");

        loadingStatus.setText("Installing patched apk.");

        if (pending) {
            retryView.setVisibility(View.GONE);
            installprogressindicator.setVisibility(View.VISIBLE);

            retryButton.setEnabled(false);
            return;
        }

        if (failed) {
            installprogressindicator.setVisibility(View.GONE);
            retryView.setVisibility(View.VISIBLE);

            retryButton.setEnabled(true);
            retryButton.setOnClickListener((View arg) -> {
                StartInstall();
            });
            return;
        }

        loadingStatus.setText("complete");
    }

    protected void UninstallPackage(String packageName)
    {
//        pending = Intent.ACTION_DELETE;

        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + packageName));

        uninstallActivityLauncher.launch(intent);
    }

    protected void InstallApk(String path)
    {
        this.installer.InstallPackages(path);
    }

    protected void OnFail()
    {
        pending = false;
        failed = true;
        Reload();
    }

    protected void StartInstall()
    {
        failed = false;
        pending = true;

        Reload();

        if (CheckIfInstalled()) {
            UninstallPackage(targetPackageName);
            return;
        }

        InstallApk(this.outputFile);
    }

    protected boolean CheckIfInstalled()
    {
        try {
            getPackageManager().getApplicationInfo(targetPackageName, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return true;
    }

    protected void OnComplete()
    {
        setResult(RESULT_OK);

        Intent resultIntent = new Intent();
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    protected void LogStatus(ActivityResult result)
    {
        Log.d(getLocalClassName(), "Install Status " + result.getResultCode());
    }
}
