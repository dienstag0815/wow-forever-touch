package com.winlator.inputcontrols;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Pure decisions for the copy of the bundled input profiles (no Android classes, unit tested).
 *
 * A profile that comes from the assets is overwritten by an app update only while the local file is still
 * byte-identical to what the app wrote last time (remembered SHA-256). Anything else was edited by the player
 * and is kept.
 */
public final class ProfileUpdatePolicy {
    public enum Action { OVERWRITE, KEEP, CREATE }

    /** Preference key prefix for the remembered hash; the file name is appended. */
    public static final String HASH_KEY_PREFIX = "inputcontrols_hash_";

    private ProfileUpdatePolicy() {}

    public static String hashKey(String fileName) {
        return HASH_KEY_PREFIX + fileName;
    }

    /**
     * @param localExists  a local profile with the same id and name exists
     * @param localHash    SHA-256 of that local file (null if unreadable)
     * @param storedHash   hash the app remembered when it last wrote the file (null/empty = unknown, e.g. installed before this protection)
     * @param assetHash    SHA-256 of the bundled asset
     */
    public static Action decide(boolean localExists, String localHash, String storedHash, String assetHash) {
        if (!localExists) return Action.CREATE;
        if (localHash == null || localHash.isEmpty()) return Action.KEEP;
        // Identical to the new asset: overwriting changes nothing and records the hash.
        if (localHash.equals(assetHash)) return Action.OVERWRITE;
        if (storedHash == null || storedHash.isEmpty()) return Action.KEEP;
        return localHash.equals(storedHash) ? Action.OVERWRITE : Action.KEEP;
    }

    public static String sha256Hex(byte[] data) {
        if (data == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xf, 16));
                sb.append(Character.forDigit(b & 0xf, 16));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The profile JSON with another id and name; the template flag is dropped so the copy shows up as a normal profile. */
    public static String copyJson(String json, int newId, String newName) throws JSONException {
        JSONObject data = new JSONObject(json);
        data.put("id", newId);
        data.put("name", newName);
        if (data.has("template")) data.remove("template");
        return data.toString();
    }
}
