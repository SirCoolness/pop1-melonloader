package com.melonloader.installer;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class GameDefinition {
    public String packageName;
    public String displayName;
    public String webSlug;

    public SupportedApplication installation = null;
    public InstallationStatus status = InstallationStatus.NOT_INSTALLED;

    private String baseDir;

    public GameDefinition(Context context)
    {
        baseDir = context.getExternalFilesDir(null).toString();
    }

    public String BuildPath()
    {
        return Paths.get(baseDir, "Builds", this.packageName + ".apk").toString();
    }

    public void Resolve(Context context)
    {
        List<SupportedApplication> supportedApplications = ApplicationFinder.GetSupportedApplications(context);

        for (SupportedApplication supportedApplication : supportedApplications) {
            if (supportedApplication.packageName.equals(this.packageName)) {
                this.status = InstallationStatus.UNMODIFIED;
                this.installation = supportedApplication;
                break;
            }
        }

        if (installation == null) {
            if (PatchedApkExists()) {
                this.status = InstallationStatus.INSTALL_READY;
            }
            return;
        }

        this.installation.CheckPatched();

        if (!this.installation.patched) {
            return;
        }

        this.status = InstallationStatus.PATCHED;
        // check if outdated
    }

    public boolean PatchedApkExists()
    {
        String apkPath = BuildPath();

        return (new File(apkPath)).isFile();
    }
}
