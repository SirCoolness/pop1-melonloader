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

    GameDefinition game = new GameDefinition () {{
        packageName = "com.BigBoxVR.PopulationONE";
        displayName = "Population: ONE";
        webSlug = "population_one";
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
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
            try {
                InstallMods();
                Toast.makeText(this, "Mods Installed.", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();

                Toast.makeText(this, "Failed to install mods.", Toast.LENGTH_SHORT).show();
            }
        });

        installButton.setVisibility(View.GONE);
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
                patchButton.setEnabled(false);
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
                installButton.setVisibility(View.VISIBLE);
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

    public void InstallMods() throws IOException {
        String modsFolder = "/storage/emulated/0/Android/data/" + this.game.packageName + "/files/Mods";

        requestWritePermission();
        Files.createDirectories(Paths.get(modsFolder));

        requestWritePermission();
        copyAssets("mods/AuthenticationHelper.dll", modsFolder + "/AuthenticationHelper.dll");

        requestWritePermission();
        copyAssets("mods/BhapticsPopOne.dll", modsFolder + "/BhapticsPopOne.dll");
    }

    public void OnInstallDone(ActivityResult result)
    {
        Toast.makeText(this, "Install Status " + result.getResultCode(), Toast.LENGTH_SHORT).show();
        Reload();

        try {
            InstallMods();
            Toast.makeText(this, "Mods Installed.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();

            Toast.makeText(this, "Failed to install mods.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestWritePermission()
    {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 100);
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
}