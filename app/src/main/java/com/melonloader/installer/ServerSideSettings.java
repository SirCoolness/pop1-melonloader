package com.melonloader.installer;

import com.sircoolness.poponeinstaller.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerSideSettings {
    public String LatestVersion;
    public boolean ForceUpdate;
    public String ApkUrl;
    public boolean ShowCustomPrompt;
    public String CustomPromptMessage;

    private static String settingsSource = "https://md.sircoolness.dev/api/bhaptics-app/v1";
    private static String[] authorizedDomains = new String[] {"sircoolness.dev", "sidequestvr.com"};
    private static ServerSideSettings result = null;

    public static ServerSideSettings Get() throws IOException, JSONException {
        if (result != null)
            return result;

        HttpURLConnection urlConnection = null;
        URL url = new URL(settingsSource);
        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setReadTimeout(10000 /* milliseconds */ );
        urlConnection.setConnectTimeout(15000 /* milliseconds */ );
        urlConnection.setDoOutput(true);
        urlConnection.connect();

        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder sb = new StringBuilder();

        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
        }
        br.close();

        String jsonString = sb.toString();
        System.out.println("JSON: " + jsonString);

        JSONObject settings = new JSONObject(jsonString);

        result = new ServerSideSettings() {{
           LatestVersion = (String) settings.get("latest_version");
           ForceUpdate = (boolean) settings.get("force_update");
           ApkUrl = (String) settings.get("apk_url");
           ShowCustomPrompt = (boolean) settings.get("show_custom_prompt");
           CustomPromptMessage = (String) settings.get("custom_prompt_message");
        }};

        try {
            URL apkUrl = new URL(result.ApkUrl);
            String host = apkUrl.getHost();

            boolean matched = false;

            for (String authorizedDomain : authorizedDomains) {
                if (host.equals(authorizedDomain) || host.endsWith("." + authorizedDomain)) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                throw new Exception("Invalid domain");
            }
        } catch (Exception e) {
            e.printStackTrace();

            result.ApkUrl = "";
            result.ForceUpdate = false;
        }

        return result;
    }
}
