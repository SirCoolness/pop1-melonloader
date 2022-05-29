package com.melonloader.installer.activites;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.melonloader.installer.ApplicationFinder;
import com.melonloader.installer.GameDefinition;
import com.melonloader.installer.GenericDialogFrament;
import com.melonloader.installer.InstallationStatus;
import com.melonloader.installer.ServerSideSettings;
import com.melonloader.installer.SplitApkInstaller;
import com.sircoolness.poponeinstaller.R;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SpecializedGame extends AppCompatActivity {
    final static int INSTALL_STEP = 1269;

    ActivityResultLauncher<Intent> patchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    Reload();

                    if (result.getResultCode() != Activity.RESULT_OK) {
                        return;
                    }

                    game.status = InstallationStatus.INSTALL_READY;
                    ReloadButtons();
                    CompleteInstallation();
                }
            }
    );

    ActivityResultLauncher<Intent> installLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    OnInstallDone(result);
                }
            }
    );

    ActivityResultLauncher<String> requestPermissionsAndMod = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    InstallMods();
                } else {
                    toast("Missing required permissions.");
                }
            }
    );

    GameDefinition game;
    ServerSideSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.game = new GameDefinition (this) {{
            packageName = "com.BigBoxVR.PopulationONE";
//            packageName = "com.noodlecake.altosadventure";
            displayName = "Population: ONE";
            webSlug = "population_one";
        }};

        setContentView(R.layout.activity_specialized_game);

        AsyncTask.execute(() -> {
            settings = ServerSideSettings.Reload();
            if (settings == null) {
                return;
            }

            runOnUiThread(() -> HandleSettingsReady(0));
        });

//        this.Reload();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Reload();
    }

    protected void Reload()
    {
        game.Resolve(this);

        ReloadButtons();
    }

    public void ReloadButtons()
    {
        Log.i("GameStatus", game.status.toString());
        TextView textView = (TextView) findViewById(R.id.applicationName);
        textView.setText(this.game.displayName);

        ImageView iconView = (ImageView) findViewById(R.id.applicationIcon);
        if (this.game.installation == null) {
            iconView.setVisibility(View.GONE);
        } else {
            iconView.setImageDrawable(this.game.installation.icon);
        }

        Button patchButton = findViewById(R.id.patchButton);
        Button modsButton = findViewById(R.id.modsButton);
        Button installButton = findViewById(R.id.installButton);

        modsButton.setText("Install Mods");
//        modsButton.setText("Mods");
        modsButton.setEnabled(this.game.status == InstallationStatus.PATCHED);
        modsButton.setOnClickListener((View var1) -> {
            InstallMods();
        });

        installButton.setText("Finish Installation");
        installButton.setOnClickListener((View var1) -> {
            this.CompleteInstallation();
        });

        patchButton.setOnClickListener((View var1) -> {
            // Then you start a new Activity via Intent
            Intent intent = new Intent();
            intent.setClass(this, ViewApplication.class);
            intent.putExtra("target.packageName", this.game.packageName);
            intent.putExtra("target.auto", true);
            intent.putExtra("target.output_file", this.game.BuildPath());
            patchLauncher.launch(intent);
        });

        switch (this.game.status) {
            case NOT_INSTALLED:
            case OUTDATED:
            case PATCHED:
                installButton.setVisibility(View.GONE);
                break;
            default:
                if (game.PatchedApkExists()) {
                    installButton.setVisibility(View.VISIBLE);
                } else {
                    installButton.setVisibility(View.GONE);
                }
        }

        switch (this.game.status) {
            case NOT_INSTALLED:
            case OUTDATED:
                patchButton.setEnabled(false);
                installButton.setVisibility(View.GONE);
                break;
            default:
                patchButton.setEnabled(this.game.installation != null);
        }

        switch (this.game.status) {
            case PATCHED:
                patchButton.setText("Patched");
                break;
            case INSTALL_READY:
                patchButton.setText("Patch");
                break;
            case NOT_INSTALLED:
                patchButton.setText("Not Installed");
                break;
            case OUTDATED:
                patchButton.setText("OUTDATED");
                break;
            case UNMODIFIED:
                patchButton.setText("Patch");
        }
    }

    public void CompleteInstallation()
    {
        // Then you start a new Activity via Intent
        Intent intent = new Intent();
        intent.setClass(this, InstallGameActivity.class);
        intent.putExtra("target.packageName", this.game.packageName);
        intent.putExtra("target.auto", true);
        intent.putExtra("target.output_file", this.game.BuildPath());

        installLauncher.launch(intent);
    }

    public void InstallMods() {
        String modsFolder = "/storage/emulated/0/Android/data/" + this.game.packageName + "/files/Mods";

        try {
            requestWritePermission();
            Files.createDirectories(Paths.get(modsFolder));

            requestWritePermission();
            copyAssets("mods/AuthenticationHelper.dll", modsFolder + "/AuthenticationHelper.dll");

            requestWritePermission();
            copyAssets("mods/BhapticsPopOne.dll", modsFolder + "/BhapticsPopOne.dll");

            toast("Mods Installed.");
        } catch (IOException e) {
            e.printStackTrace();

            toast("Failed to install mods.");
        } catch (Exception e) {
            toast("Requesting Permission.");
        }
    }

    public void OnInstallDone(ActivityResult result)
    {
        toast("Install Status " + result.getResultCode());
        Reload();

        InstallMods();
    }

    private void requestWritePermission() throws Exception {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsAndMod.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            throw new Exception("Requesting Permissions");
        }
    }

    private void copyAssets(String assetName, String dest) throws IOException {
        AssetManager assetManager = getAssets();
        String[] files = assetManager.list("");

        InputStream in = null;
        OutputStream out = null;

        in = assetManager.open(assetName);
        File outFile = new File(dest);
        out = new FileOutputStream(outFile);
        copyFile(in, out);

        in.close();
        in = null;

        out.flush();
        out.close();
        out = null;
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void HandleSettingsReady(int step) {
        URL apkUrl = null;
        try {
            apkUrl = new URL(settings.ApkUrl);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        switch (step) {
            case 0:
                if (settings.ShowCustomPrompt && settings.CustomPromptMessage.length() > 0) {
                    AlertMessage(settings.CustomPromptMessage, () -> {
                        HandleSettingsReady(1);
                    });
                    return;
                }
                break;
        }

        if (settings.LatestVersion.equals(getResources().getString(R.string.app_version_simple)) || apkUrl == null) {
            return;
        }

        GenericDialogFrament newFragment = new GenericDialogFrament(getResources().getString(R.string.app_name) + " needs to be updated.");
        if (!settings.ForceUpdate) {
            newFragment.negativeAnswer = "cancel";
            newFragment.afterPositive = () -> UpdateApp();
        } else {
            newFragment.after = () -> UpdateApp();
        }

        newFragment.show(getSupportFragmentManager(), "Install Prompt");
    }

    private void AlertMessage(String message, Runnable after) {
        GenericDialogFrament newFragment = new GenericDialogFrament(message);
        newFragment.after = after;
        newFragment.show(getSupportFragmentManager(), "Server Message");
    }

    private void UpdateApp() {
        String downloadLocation = Paths.get(getExternalFilesDir(null).toString(), "update_app.apk").toString();

        AsyncTask.execute(() -> {
            if (settings.ApkUrl == null)
                return;

            Log.i(getLocalClassName(), "Downloading [" + settings.ApkUrl.toString() + "]");

            URL url = null;
            URLConnection connection = null;
            try {
                url = new URL(settings.ApkUrl);

                connection = url.openConnection();
                connection.connect();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            int lenghtOfFile = connection.getContentLength();

            Log.i(getLocalClassName(), "File Size " + lenghtOfFile);

            // download the file
            InputStream input = null;
            try {
                input = new BufferedInputStream(url.openStream(),
                        8192);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // Output stream
            OutputStream output = null;
            try {
                output = new FileOutputStream(downloadLocation);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }

            byte data[] = new byte[8192];

            try {
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }

                output.flush();

                // closing streams
                output.close();
                input.close();
            } catch (IOException e)
            {
                e.printStackTrace();
                return;
            }

            runOnUiThread(() -> {
                Intent intent = new Intent();
                intent.setClass(this, InstallGameActivity.class);
                intent.putExtra("target.packageName", this.game.packageName);
                intent.putExtra("target.auto", true);
                intent.putExtra("target.install_only", true);
                intent.putExtra("target.output_file", downloadLocation);

                startActivity(intent);
            });
        });
    }
}