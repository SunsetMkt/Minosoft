/*
 * Minosoft
 * Copyright (C) 2020 Moritz Zwerger
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.If not, see <https://www.gnu.org/licenses/>.
 *
 * This software is not affiliated with Mojang AB, the original developer of Minecraft.
 */

package de.bixilon.minosoft.data.assets;

import com.google.errorprone.annotations.DoNotCall;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bixilon.minosoft.Minosoft;
import de.bixilon.minosoft.config.ConfigurationPaths;
import de.bixilon.minosoft.config.StaticConfiguration;
import de.bixilon.minosoft.logging.Log;
import de.bixilon.minosoft.logging.LogLevels;
import de.bixilon.minosoft.protocol.protocol.ProtocolDefinition;
import de.bixilon.minosoft.util.CountUpAndDownLatch;
import de.bixilon.minosoft.util.HTTP;
import de.bixilon.minosoft.util.Util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AssetsManager {
    public static final String ASSETS_INDEX_VERSION = "1.17"; // version.json -> assetIndex -> id
    public static final String ASSETS_INDEX_HASH = "951e1a1272565745cc8de8132705934a42f604b1"; // version.json -> assetIndex -> sha1
    public static final String ASSETS_CLIENT_JAR_VERSION = "20w46a"; // version.json -> id
    public static final String ASSETS_CLIENT_JAR_HASH = "ab636db71346ff05c9f4a146e10bb2fbb2990014"; // sha1 hash of file generated by minosoft (client jar file mappings: name -> hash)
    public static final String[] RELEVANT_ASSETS = {"minecraft/lang/", "minecraft/sounds/", "minecraft/textures/", "minecraft/font/"};

    private static final HashMap<String, String> assets = new HashMap<>();

    public static void downloadAssetsIndex() throws IOException {
        Util.downloadFileAsGz(String.format("https://launchermeta.mojang.com/v1/packages/%s/%s.json", ASSETS_INDEX_HASH, ASSETS_INDEX_VERSION), getAssetDiskPath(ASSETS_INDEX_HASH));
    }

    private static HashMap<String, String> parseAssetsIndex(String hash) throws IOException {
        return parseAssetsIndex(readJsonAssetByHash(hash).getAsJsonObject());
    }

    private static HashMap<String, String> parseAssetsIndex(JsonObject json) {
        if (json.has("objects")) {
            json = json.getAsJsonObject("objects");
        }
        HashMap<String, String> ret = new HashMap<>();
        for (String key : json.keySet()) {
            JsonElement value = json.get(key);
            if (value.isJsonPrimitive()) {
                ret.put(key, value.getAsString());
                continue;
            }
            ret.put(key, value.getAsJsonObject().get("hash").getAsString());
        }
        return ret;
    }

    public static void downloadAllAssets(CountUpAndDownLatch latch) throws IOException {
        if (assets.size() > 0) {
            return;
        }
        try {
            downloadAssetsIndex();
        } catch (Exception e) {
            Log.printException(e, LogLevels.DEBUG);
            Log.warn("Could not download assets index. Please check your internet connection");
        }
        assets.putAll(verifyAssets(AssetsSource.MOJANG, latch, parseAssetsIndex(ASSETS_INDEX_HASH)));
        assets.putAll(verifyAssets(AssetsSource.MINOSOFT_GIT, latch, parseAssetsIndex(Util.readJsonAsset("mapping/resources.json"))));
        latch.addCount(1); // client jar
        // download assets
        generateJarAssets();
        assets.putAll(parseAssetsIndex(ASSETS_CLIENT_JAR_HASH));
        latch.countDown();
    }

    private static HashMap<String, String> verifyAssets(AssetsSource source, CountUpAndDownLatch latch, HashMap<String, String> assets) {
        latch.addCount(assets.size());
        assets.keySet().parallelStream().forEach((filename) -> {
            try {
                String hash = assets.get(filename);
                boolean compressed = (source == AssetsSource.MOJANG);
                if (!verifyAssetHash(hash, compressed)) {
                    AssetsManager.downloadAsset(source, hash);
                }
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
        return assets;
    }

    public static boolean doesAssetExist(String name) {
        return assets.containsKey(name);
    }

    public static HashMap<String, String> getAssets() {
        return assets;
    }

    public static InputStreamReader readAsset(String name) throws IOException {
        return readAssetByHash(assets.get(name));
    }

    public static InputStream readAssetAsStream(String name) throws IOException {
        return readAssetAsStreamByHash(assets.get(name));
    }

    public static JsonElement readJsonAsset(String name) throws IOException {
        return readJsonAssetByHash(assets.get(name));
    }

    private static void downloadAsset(AssetsSource source, String hash) throws Exception {
        switch (source) {
            case MOJANG -> downloadAsset(String.format("https://resources.download.minecraft.net/%s/%s", hash.substring(0, 2), hash), hash);
            case MINOSOFT_GIT -> downloadAsset(String.format(Minosoft.getConfig().getString(ConfigurationPaths.StringPaths.RESOURCES_URL), hash.substring(0, 2), hash), hash, false);
        }
    }

    private static InputStreamReader readAssetByHash(String hash) throws IOException {
        return new InputStreamReader(readAssetAsStreamByHash(hash));
    }

    private static InputStream readAssetAsStreamByHash(String hash) throws IOException {
        return new GZIPInputStream(new FileInputStream(getAssetDiskPath(hash)));
    }

    private static JsonElement readJsonAssetByHash(String hash) throws IOException {
        return JsonParser.parseReader(readAssetByHash(hash));
    }

    private static long getAssetSize(String hash) throws FileNotFoundException {
        File file = new File(getAssetDiskPath(hash));
        if (!file.exists()) {
            return -1;
        }
        return file.length();
    }

    private static boolean verifyAssetHash(String hash, boolean compressed) throws FileNotFoundException {
        // file does not exist
        if (getAssetSize(hash) == -1) {
            return false;
        }
        if (!Minosoft.config.getBoolean(ConfigurationPaths.BooleanPaths.DEBUG_VERIFY_ASSETS)) {
            return true;
        }
        try {
            if (compressed) {
                return hash.equals(Util.sha1Gzip(new File(getAssetDiskPath(hash))));
            }
            return hash.equals(Util.sha1(new File(getAssetDiskPath(hash))));
        } catch (IOException ignored) {
        }
        return false;
    }

    private static boolean verifyAssetHash(String hash) {
        try {
            return verifyAssetHash(hash, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void generateJarAssets() throws IOException {
        long startTime = System.currentTimeMillis();
        Log.verbose("Generating client.jar assets...");
        if (verifyAssetHash(ASSETS_CLIENT_JAR_HASH)) {
            // ToDo: Verify all jar assets
            readAssetAsStreamByHash(ASSETS_CLIENT_JAR_HASH);
            Log.verbose("client.jar assets probably already generated, skipping");
            return;
        }
        JsonObject manifest = HTTP.getJson("https://launchermeta.mojang.com/mc/game/version_manifest.json").getAsJsonObject();
        String assetsVersionJsonUrl = null;
        for (JsonElement versionElement : manifest.getAsJsonArray("versions")) {
            JsonObject version = versionElement.getAsJsonObject();
            if (version.get("id").getAsString().equals(ASSETS_CLIENT_JAR_VERSION)) {
                assetsVersionJsonUrl = version.get("url").getAsString();
                break;
            }
        }
        if (assetsVersionJsonUrl == null) {
            throw new RuntimeException(String.format("Invalid version manifest or invalid ASSETS_CLIENT_JAR_VERSION (%s)", ASSETS_CLIENT_JAR_VERSION));
        }
        String versionJsonHash = assetsVersionJsonUrl.replace("https://launchermeta.mojang.com/v1/packages/", "").replace(String.format("/%s.json", ASSETS_CLIENT_JAR_VERSION), "");
        downloadAsset(assetsVersionJsonUrl, versionJsonHash);
        // download jar
        JsonObject clientJarJson = readJsonAssetByHash(versionJsonHash).getAsJsonObject().getAsJsonObject("downloads").getAsJsonObject("client");
        downloadAsset(clientJarJson.get("url").getAsString(), clientJarJson.get("sha1").getAsString());

        HashMap<String, String> clientJarAssetsHashMap = new HashMap<>();
        ZipInputStream versionJar = new ZipInputStream(readAssetAsStreamByHash(clientJarJson.get("sha1").getAsString()));
        ZipEntry currentFile;
        while ((currentFile = versionJar.getNextEntry()) != null) {
            if (!currentFile.getName().startsWith("assets") || currentFile.isDirectory()) {
                continue;
            }
            boolean relevant = false;
            for (String prefix : RELEVANT_ASSETS) {
                if (currentFile.getName().startsWith("assets/" + prefix)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant) {
                continue;
            }
            String hash = saveAsset(versionJar);

            clientJarAssetsHashMap.put(currentFile.getName().substring("assets/".length()), hash);
        }
        JsonObject clientJarAssetsMapping = new JsonObject();
        clientJarAssetsHashMap.forEach(clientJarAssetsMapping::addProperty);
        String json = new GsonBuilder().create().toJson(clientJarAssetsMapping);
        String assetHash = saveAsset(json.getBytes());
        Log.verbose(String.format("Generated jar assets in %dms (elements=%d, hash=%s)", (System.currentTimeMillis() - startTime), clientJarAssetsHashMap.size(), assetHash));
    }

    @DoNotCall
    private static String saveAsset(byte[] data) throws IOException {
        String hash = Util.sha1(data);
        String destination = getAssetDiskPath(hash);
        File outFile = new File(destination);
        if (outFile.exists() && outFile.length() > 0) {
            return hash;
        }
        Util.createParentFolderIfNotExist(destination);
        OutputStream out = new GZIPOutputStream(new FileOutputStream(destination));
        out.write(data);
        out.close();
        return hash;
    }

    private static String saveAsset(InputStream data) throws IOException {
        File tempDestinationFile = null;
        while (tempDestinationFile == null || tempDestinationFile.exists()) { // file exist? lol
            tempDestinationFile = new File(System.getProperty("java.io.tmpdir") + "/minosoft/" + Util.generateRandomString(32));
        }
        Util.createParentFolderIfNotExist(tempDestinationFile);

        OutputStream out = new GZIPOutputStream(new FileOutputStream(tempDestinationFile));
        MessageDigest crypt;
        try {
            crypt = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        byte[] buffer = new byte[ProtocolDefinition.DEFAULT_BUFFER_SIZE];
        int length;
        while ((length = data.read(buffer, 0, buffer.length)) != -1) {
            crypt.update(buffer, 0, length);
            out.write(buffer, 0, length);
        }
        out.close();
        String hash = Util.byteArrayToHexString(crypt.digest());

        // move file to desired destination
        File outputFile = new File(getAssetDiskPath(hash));
        Util.createParentFolderIfNotExist(outputFile);
        tempDestinationFile.renameTo(outputFile);
        return hash;
    }

    private static void downloadAsset(String url, String hash) throws IOException {
        downloadAsset(url, hash, true);
    }

    private static void downloadAsset(String url, String hash, boolean compressed) throws IOException {
        if (verifyAssetHash(hash)) {
            return;
        }
        Log.verbose(String.format("Downloading %s -> %s", url, hash));
        if (compressed) {
            Util.downloadFileAsGz(url, getAssetDiskPath(hash));
            return;
        }
        Util.downloadFile(url, getAssetDiskPath(hash));
    }

    private static String getAssetDiskPath(String hash) throws FileNotFoundException {
        if (hash == null) {
            throw new FileNotFoundException("Could not find asset with hash: null");
        }
        return StaticConfiguration.HOME_DIRECTORY + String.format("assets/objects/%s/%s.gz", hash.substring(0, 2), hash);
    }
}
