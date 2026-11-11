package com.winlator.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.JsonReader;
import android.util.Log;

import com.winlator.PrefManager;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

public class InputControlsManager {
    private final Context context;
    private ArrayList<ControlsProfile> profiles;
    private int maxProfileId;
    private boolean profilesLoaded = false;

    public InputControlsManager(Context context) {
        this.context = context;
    }

    public static File getProfilesDir(Context context) {
        File profilesDir = new File(context.getFilesDir(), "profiles");
        if (!profilesDir.isDirectory()) profilesDir.mkdir();
        return profilesDir;
    }

    public ArrayList<ControlsProfile> getProfiles() {
        return getProfiles(false);
    }

    public ArrayList<ControlsProfile> getProfiles(boolean ignoreTemplates) {
        if (!profilesLoaded) loadProfiles(ignoreTemplates);
        return profiles;
    }

    private void copyAssetProfilesIfNeeded() {
        File profilesDir = InputControlsManager.getProfilesDir(context);
        if (FileUtils.isEmpty(profilesDir)) {
            FileUtils.copy(context, "inputcontrols/profiles", profilesDir);
            File[] copied = profilesDir.listFiles();
            if (copied != null) for (File f : copied) rememberProfileHash(context, f);
            return;
        }

        PrefManager.init(context);
        String newVersion = String.valueOf(AppUtils.getVersionCode(context));
        String oldVersion = PrefManager.getString("inputcontrols_app_version", "0");
        if (oldVersion.equals(newVersion)) return;
        PrefManager.putString("inputcontrols_app_version", newVersion);

        File[] files = profilesDir.listFiles();
        if (files == null) return;

        try {
            AssetManager assetManager = context.getAssets();
            String[] assetFiles = assetManager.list("inputcontrols/profiles");

            // Pre-compute max local ID for assigning new profile IDs
            int nextNewId = 0;
            for (File f : files) {
                ControlsProfile p = loadProfile(context, f);
                if (p != null) nextNewId = Math.max(nextNewId, p.id + 1);
            }

            for (String assetFile : assetFiles) {
                String assetPath = "inputcontrols/profiles/"+assetFile;
                ControlsProfile originProfile = loadProfile(context, assetManager.open(assetPath));

                File targetFile = null;
                for (File file : files) {
                    ControlsProfile targetProfile = loadProfile(context, file);
                    if (originProfile == null || targetProfile == null) continue;
                    if (ProfileUpdatePolicy.sameProfile(originProfile.id, originProfile.getName(), targetProfile.id, targetProfile.getName())) {
                        targetFile = file;
                        break;
                    }
                }

                if (targetFile != null) {
                    // Only overwrite a profile the player has not touched since the app wrote it (see ProfileUpdatePolicy).
                    byte[] assetBytes = FileUtils.read(context, assetPath);
                    ProfileUpdatePolicy.Action action = ProfileUpdatePolicy.decide(true,
                        ProfileUpdatePolicy.sha256Hex(FileUtils.read(targetFile)),
                        PrefManager.getString(ProfileUpdatePolicy.hashKey(targetFile.getName()), ""),
                        ProfileUpdatePolicy.sha256Hex(assetBytes));
                    if (action == ProfileUpdatePolicy.Action.OVERWRITE) {
                        FileUtils.copy(context, assetPath, targetFile);
                        rememberProfileHash(context, targetFile);
                    } else {
                        Log.i("InputControlsManager", "Keeping edited profile '" + originProfile.getName() + "' (" + targetFile.getName() + ")");
                    }
                } else if (originProfile != null) {
                    // New asset profile — add if not already present
                    File defaultFile = new File(profilesDir, assetFile);
                    boolean alreadyExists = false;
                    if (defaultFile.exists()) {
                        // Filename occupied by a different profile: does this asset profile (by name) already exist under any ID?
                        for (File file2 : files) {
                            ControlsProfile p = loadProfile(context, file2);
                            if (p != null && p.getName().equals(originProfile.getName())) {
                                alreadyExists = true;
                                break;
                            }
                        }
                    }
                    ProfileUpdatePolicy.NewAssetAction newAction = ProfileUpdatePolicy.decideNewAsset(defaultFile.exists(), alreadyExists);
                    if (newAction == ProfileUpdatePolicy.NewAssetAction.WRITE_SAME_FILE) {
                        FileUtils.copy(context, assetPath, defaultFile);
                        rememberProfileHash(context, defaultFile);
                    } else if (newAction == ProfileUpdatePolicy.NewAssetAction.WRITE_NEW_ID) {
                        // The player's own profile keeps the file; the bundled one gets a fresh id.
                        int newId = nextNewId++;
                        File freeFile = ControlsProfile.getProfileFile(context, newId);
                        try {
                            String json = FileUtils.readString(context, assetPath);
                            JSONObject data = new JSONObject(json);
                            data.put("id", newId);
                            FileUtils.writeString(freeFile, data.toString());
                            rememberProfileHash(context, freeFile);
                        } catch (Exception e) {
                            Log.w("InputControlsManager", "Failed to create profile '" + originProfile.getName() + "' (newId=" + newId + ", file=" + freeFile + ")", e);
                        }
                    }
                }
            }

            // Fix if controls-0.icp not exists
            File file = ControlsProfile.getProfileFile(context, 0);
            if (!file.isFile()) {
                FileUtils.copy(context, "inputcontrols/profiles/controls-0.icp", file);
            }
        }
        catch (IOException e) {}
    }

    /** Remembers the SHA-256 of a profile file the app has just written, so a later update can tell whether the player changed it. */
    public static void rememberProfileHash(Context context, File file) {
        String hash = ProfileUpdatePolicy.sha256Hex(FileUtils.read(file));
        if (hash == null) return;
        try {
            PrefManager.init(context);
            PrefManager.putString(ProfileUpdatePolicy.hashKey(file.getName()), hash).join();
        }
        catch (RuntimeException e) {
            Log.w("InputControlsManager", "Could not remember hash of " + file.getName(), e);
        }
    }

    public void loadProfiles(boolean ignoreTemplates) {
        File profilesDir = InputControlsManager.getProfilesDir(context);
        copyAssetProfilesIfNeeded();

        ArrayList<ControlsProfile> profiles = new ArrayList<>();
        File[] files = profilesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                ControlsProfile profile = loadProfile(context, file);
                if (profile == null) continue;
                if (!(ignoreTemplates && profile.isTemplate())) profiles.add(profile);
                maxProfileId = Math.max(maxProfileId, profile.id);
            }
        }

        Collections.sort(profiles);
        this.profiles = profiles;
        profilesLoaded = true;
    }

    public ControlsProfile createProfile(String name) {
        ControlsProfile profile = new ControlsProfile(context, ++maxProfileId);
        profile.setName(name);
        profile.save();
        profiles.add(profile);
        return profile;
    }

    public ControlsProfile duplicateProfile(ControlsProfile source) {
        String newName;
        for (int i = 1;;i++) {
            newName = source.getName() + " ("+i+")";
            boolean found = false;
            for (ControlsProfile profile : profiles) {
                if (profile.getName().equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        int newId = ++maxProfileId;
        File newFile = ControlsProfile.getProfileFile(context, newId);

        try {
            JSONObject data = new JSONObject(FileUtils.readString(ControlsProfile.getProfileFile(context, source.id)));
            data.put("id", newId);
            data.put("name", newName);
            if (data.has("template")) data.remove("template");
            FileUtils.writeString(newFile, data.toString());
        }
        catch (JSONException e) {}

        ControlsProfile profile = loadProfile(context, newFile);
        profiles.add(profile);
        return profile;
    }

    /**
     * Copy of {@code source} under a fresh id and the given name. The copy is not part of the assets, so app updates
     * never touch it. Returns null if it could not be written.
     */
    public ControlsProfile copyProfileAs(ControlsProfile source, String newName) {
        getProfiles(false);
        int newId = ++maxProfileId;
        File newFile = ControlsProfile.getProfileFile(context, newId);
        try {
            String json = ProfileUpdatePolicy.copyJson(FileUtils.readString(ControlsProfile.getProfileFile(context, source.id)), newId, newName);
            if (!FileUtils.writeString(newFile, json)) return null;
        }
        catch (JSONException e) {
            return null;
        }
        ControlsProfile profile = loadProfile(context, newFile);
        if (profile != null) profiles.add(profile);
        return profile;
    }

    /**
     * Writes the bundled asset profile over {@code target} (id and name of the target stay). If the target is the asset
     * profile itself the file is byte-identical to the asset and its hash is remembered again, so updates treat it as unchanged.
     */
    public boolean resetProfileToAsset(ControlsProfile target, String assetPath) {
        getProfiles(false);
        File file = ControlsProfile.getProfileFile(context, target.id);
        try {
            String assetJson = FileUtils.readString(context, assetPath);
            JSONObject asset = new JSONObject(assetJson);
            boolean sameProfile = asset.optInt("id", -1) == target.id && target.getName().equals(asset.optString("name"));
            if (sameProfile) {
                FileUtils.copy(context, assetPath, file);
                rememberProfileHash(context, file);
            }
            else if (!FileUtils.writeString(file, ProfileUpdatePolicy.copyJson(assetJson, target.id, target.getName()))) return false;
        }
        catch (Exception e) {
            Log.w("InputControlsManager", "Failed to reset profile '" + target.getName() + "' from " + assetPath, e);
            return false;
        }
        // The in-memory entry may hold the old elements: swap in a freshly read one.
        ControlsProfile fresh = loadProfile(context, file);
        if (fresh != null) {
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).id == target.id) {
                    profiles.set(i, fresh);
                    break;
                }
            }
        }
        return true;
    }

    public void removeProfile(ControlsProfile profile) {
        File file = ControlsProfile.getProfileFile(context, profile.id);
        if (file.isFile() && file.delete()) profiles.remove(profile);
    }

    public ControlsProfile importProfile(JSONObject data) {
        try {
            if (!data.has("id") || !data.has("name")) return null;
            int newId = ++maxProfileId;
            File newFile = ControlsProfile.getProfileFile(context, newId);
            data.put("id", newId);
            FileUtils.writeString(newFile, data.toString());
            ControlsProfile newProfile = loadProfile(context, newFile);

            int foundIndex = -1;
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (profile.getName().equals(newProfile.getName())) {
                    foundIndex = i;
                    break;
                }
            }

            if (foundIndex != -1) {
                profiles.set(foundIndex, newProfile);
            }
            else profiles.add(newProfile);
            return newProfile;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public File exportProfile(ControlsProfile profile) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File destination = new File(downloadsDir, "Winlator/profiles/"+profile.getName()+".icp");
        FileUtils.copy(ControlsProfile.getProfileFile(context, profile.id), destination);
        MediaScannerConnection.scanFile(context, new String[]{destination.getAbsolutePath()}, null, null);
        return destination.isFile() ? destination : null;
    }

    public static ControlsProfile loadProfile(Context context, File file) {
        try {
            return loadProfile(context, new FileInputStream(file));
        }
        catch (FileNotFoundException e) {
            return null;
        }
    }

    public static ControlsProfile loadProfile(Context context, InputStream inStream) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(inStream, StandardCharsets.UTF_8))) {
            int profileId = 0;
            String profileName = null;
            float cursorSpeed = Float.NaN;
            int fieldsRead = 0;

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                if (name.equals("id")) {
                    profileId = reader.nextInt();
                    fieldsRead++;
                }
                else if (name.equals("name")) {
                    profileName = reader.nextString();
                    fieldsRead++;
                }
                else if (name.equals("cursorSpeed")) {
                    cursorSpeed = (float) reader.nextDouble();
                    fieldsRead++;
                }
                else {
                    if (fieldsRead == 3) break;
                    reader.skipValue();
                }
            }

            ControlsProfile profile = new ControlsProfile(context, profileId);
            profile.setName(profileName);
            profile.setCursorSpeed(cursorSpeed);
            return profile;
        }
        catch (IOException e) {
            return null;
        }
    }

    public ControlsProfile getProfile(int id) {
        for (ControlsProfile profile : getProfiles()) if (profile.id == id) return profile;
        return null;
    }
}
