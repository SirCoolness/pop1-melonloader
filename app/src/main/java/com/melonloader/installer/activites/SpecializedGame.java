package com.melonloader.installer.activites;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
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

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.melonloader.installer.ApplicationFinder;
import com.melonloader.installer.GameDefinition;
import com.melonloader.installer.InstallationStatus;
import com.melonloader.installer.SplitApkInstaller;
import com.sircoolness.poponeinstaller.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
}