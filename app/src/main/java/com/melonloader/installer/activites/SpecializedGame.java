package com.melonloader.installer.activites;

import android.app.Activity;
import android.content.Intent;
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
        packageName = "com.noodlecake.altosadventure";
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

        modsButton.setText("Mods");
        modsButton.setEnabled(this.game.status == InstallationStatus.PATCHED);

        switch (this.game.status) {
            case PATCHED:
            case OUTDATED:
            case NOT_INSTALLED:
                patchButton.setEnabled(false);
                break;
            default:
                patchButton.setEnabled(true);
        }

        switch (this.game.status) {
            case PATCHED:
                patchButton.setText("Patched");
                break;
            case INSTALL_READY:
                patchButton.setText("Finish Installation");
                patchButton.setOnClickListener((View var1) -> {
                    this.CompleteInstallation();
                });
                break;
            case NOT_INSTALLED:
                patchButton.setText("Not Installed");
                break;
            case OUTDATED:
                patchButton.setText("OUTDATED");
                break;
            case UNMODIFIED:
                patchButton.setText("Patch");
                patchButton.setOnClickListener((View var1) -> {
                    // Then you start a new Activity via Intent
                    Intent intent = new Intent();
                    intent.setClass(this, ViewApplication.class);
                    intent.putExtra("target.packageName", this.game.packageName);
                    intent.putExtra("target.auto", true);
                    intent.putExtra("target.output_file", this.game.BuildPath());
                    patchLauncher.launch(intent);
                });
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

    public void OnInstallDone(ActivityResult result)
    {
        Toast.makeText(this, "Install Status " + result.getResultCode(), Toast.LENGTH_SHORT).show();
        Reload();
    }
}