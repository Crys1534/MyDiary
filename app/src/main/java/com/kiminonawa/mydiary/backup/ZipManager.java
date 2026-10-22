package com.kiminonawa.mydiary.backup;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.kiminonawa.mydiary.shared.FileManager;

import dalvik.system.ZipPathValidator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


/**
 * Created by daxia on 2017/2/16.
 */

public class ZipManager {

    private static final String TAG = "ZipManager";
    //The folders (inside the files of the app) that go in the backup:
    //diary = photos of the entries + the background of every diary, memo / contacts = the background of every topic,
    //setting = the pictures of the profile
    private static final String[] BACKUP_FOLDERS = {"diary", "memo", "contacts", "setting"};
    //Work folders that are not worth saving
    private static final String[] SKIP_FOLDERS = {"editCache", "temp"};
    private File rootDir;
    private final int BUFFER_SIZE = 2048;


    public ZipManager(Context context) {
        rootDir = new FileManager(context, FileManager.ROOT_DIR).getDir();
    }

    /**
     * Writes the backup (all the files of the folders above + the json file) into the given stream.
     * The stream is always closed.
     */
    public boolean zipFileAtPath(String backupJsonFilePath, OutputStream destination) {
        try {
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    destination));
            int basePathLength = rootDir.getPath().length();
            for (String name : BACKUP_FOLDERS) {
                File folder = new File(rootDir, name);
                if (folder.isDirectory()) {
                    zipSubFolder(out, folder, basePathLength);
                }
            }
            //Zip the json file
            zipBackupJsonFile(backupJsonFilePath, out);

            out.close();
        } catch (Exception e) {
            Log.e(TAG, "zip fail", e);
            try {
                destination.close();
            } catch (IOException ignored) {
            }
            return false;
        }
        return true;
    }

    private void zipBackupJsonFile(String backupJsonFilePath, ZipOutputStream out) throws IOException {
        byte data[] = new byte[BUFFER_SIZE];
        FileInputStream fi = new FileInputStream(backupJsonFilePath);
        BufferedInputStream jsonFileOrigin = new BufferedInputStream(fi, BUFFER_SIZE);
        ZipEntry entry = new ZipEntry(BackupManager.BACKUP_JSON_FILE_NAME);
        out.putNextEntry(entry);
        int count;
        while ((count = jsonFileOrigin.read(data, 0, BUFFER_SIZE)) != -1) {
            out.write(data, 0, count);
        }
    }

    private boolean isSkippedFolder(File folder) {
        for (String skip : SKIP_FOLDERS) {
            if (skip.equals(folder.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zips a subfolder
     */

    private void zipSubFolder(ZipOutputStream out, File folder,
                              int basePathLength) throws IOException {

        File[] fileList = folder.listFiles();
        if (fileList == null) {
            return;
        }
        BufferedInputStream origin = null;
        for (File file : fileList) {
            if (file.isDirectory()) {
                if (isSkippedFolder(file)) {
                    continue;
                }
                zipSubFolder(out, file, basePathLength);
            } else {
                byte data[] = new byte[BUFFER_SIZE];
                String unmodifiedFilePath = file.getPath();
                String relativePath = unmodifiedFilePath
                        .substring(basePathLength);
                //Android 14 refuses zip names that start with "/": the names of a backup are always relative
                while (relativePath.startsWith("/")) {
                    relativePath = relativePath.substring(1);
                }
                FileInputStream fi = new FileInputStream(unmodifiedFilePath);
                origin = new BufferedInputStream(fi, BUFFER_SIZE);
                ZipEntry entry = new ZipEntry(relativePath);
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
            }
        }
    }

    /**
     * The backups made by the older versions have names like "/diary/3/photo" (with a "/" at the start).
     * Android 14 refuses to read such names, so its check is turned off here. It is safe because unzip() checks
     * by itself that every file stays inside the backup folder.
     */
    private static void allowOldBackupNames() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ZipPathValidator.clearCallback();
            } catch (Throwable e) {
                Log.e(TAG, "can't turn off the zip name check", e);
            }
        }
    }

    /**
     * Unzips the backup read from the stream into the folder "location".
     * The stream is always closed.
     */
    public void unzip(InputStream backupZipStream, String location) throws IOException {
        int size;
        byte[] buffer = new byte[BUFFER_SIZE];

        if (!location.endsWith("/")) {
            location += "/";
        }
        allowOldBackupNames();
        File locationDir = new File(location);
        if (!locationDir.isDirectory()) {
            locationDir.mkdirs();
        }
        String locationCanonicalPath = locationDir.getCanonicalPath() + File.separator;
        ZipInputStream zin = new ZipInputStream(
                new BufferedInputStream(backupZipStream, BUFFER_SIZE));
        try {
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                File unzipFile = new File(location + ze.getName());
                //A file that wants to leave the backup folder (for example "../x") is not safe
                if (!unzipFile.getCanonicalPath().startsWith(locationCanonicalPath)) {
                    throw new IOException("Unsafe file name in the backup: " + ze.getName());
                }

                if (ze.isDirectory()) {
                    if (!unzipFile.isDirectory()) {
                        unzipFile.mkdirs();
                    }
                } else {
                    // check for and create parent directories if they don't exist
                    File parentDir = unzipFile.getParentFile();
                    if (null != parentDir) {
                        if (!parentDir.isDirectory()) {
                            parentDir.mkdirs();
                        }
                    }

                    // unzip the file
                    FileOutputStream out = new FileOutputStream(unzipFile, false);
                    BufferedOutputStream fout = new BufferedOutputStream(out, BUFFER_SIZE);
                    try {
                        while ((size = zin.read(buffer, 0, BUFFER_SIZE)) != -1) {
                            fout.write(buffer, 0, size);
                        }

                        zin.closeEntry();
                    } finally {
                        fout.flush();
                        fout.close();
                    }
                }
            }
        } finally {
            zin.close();
        }
    }
}
