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

    public String BuildPath()
    {
        return Paths.get(Environment.getExternalStorageDirectory().getPath().toString(), "MelonLoader", this.packageName + ".apk").toString();
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
            String apkPath = BuildPath();

            if ((new File(apkPath)).isFile()) {
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
}
