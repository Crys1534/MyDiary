package com.kiminonawa.mydiary.backup;

import android.content.Context;
import android.util.Log;

import com.kiminonawa.mydiary.shared.FileManager;

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
    private FileManager diaryFileManager;
    private final int BUFFER_SIZE = 2048;


    public ZipManager(Context context) {
        //Copy data form diary
        diaryFileManager = new FileManager(context, FileManager.DIARY_ROOT_DIR);
    }

    /**
     * Writes the backup (all the diary files + the json file) into the given stream.
     * The stream is always closed.
     */
    public boolean zipFileAtPath(String backupJsonFilePath, OutputStream destination) {

        File sourceFile = diaryFileManager.getDir();
        try {
            BufferedInputStream origin = null;
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
                    destination));
            if (sourceFile.isDirectory()) {
                zipSubFolder(out, sourceFile, sourceFile.getParent().length());
            } else {
                byte data[] = new byte[BUFFER_SIZE];
                FileInputStream fi = new FileInputStream(sourceFile);
                origin = new BufferedInputStream(fi, BUFFER_SIZE);
                ZipEntry entry = new ZipEntry(sourceFile.getName());
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    out.write(data, 0, count);
                }
                origin.close();
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

    /**
     * Zips a subfolder
     */

    private void zipSubFolder(ZipOutputStream out, File folder,
                              int basePathLength) throws IOException {

        File[] fileList = folder.listFiles();
        BufferedInputStream origin = null;
        for (File file : fileList) {
            if (file.isDirectory()) {
                zipSubFolder(out, file, basePathLength);
            } else {
                byte data[] = new byte[BUFFER_SIZE];
                String unmodifiedFilePath = file.getPath();
                String relativePath = unmodifiedFilePath
                        .substring(basePathLength);
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
     * Unzips the backup read from the stream into the folder "location".
     * The stream is always closed.
     */
    public void unzip(InputStream backupZipStream, String location) throws IOException {
        int size;
        byte[] buffer = new byte[BUFFER_SIZE];

        if (!location.endsWith("/")) {
            location += "/";
        }
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
