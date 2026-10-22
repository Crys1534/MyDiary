package com.kiminonawa.mydiary.backup;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.backup.obj.BUContactsEntries;
import com.kiminonawa.mydiary.backup.obj.BUDiaryEntries;
import com.kiminonawa.mydiary.backup.obj.BUDiaryItem;
import com.kiminonawa.mydiary.backup.obj.BUMemoEntries;
import com.kiminonawa.mydiary.db.DBManager;
import com.kiminonawa.mydiary.main.topic.ITopic;
import com.kiminonawa.mydiary.shared.FileManager;
import com.kiminonawa.mydiary.shared.SPFManager;
import com.kiminonawa.mydiary.shared.ThemeManager;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by daxia on 2017/2/16.
 */

public class ImportAsyncTask extends AsyncTask<Void, Void, Boolean> {

    public interface ImportCallBack {
        void onImportCompiled(boolean importSuccessful);
    }

    private final static String TAG = "ImportAsyncTask";


    /*
     * Backup
     */
    private BackupManager backupManager;
    private FileManager backupFileManager, diartFileManager;
    private String backupJsonFilePath;
    private Uri backupZipSource;
    /*
     * DB
     */
    private DBManager dbManager;

    /*
     * UI
     */
    //From MyDiaryApplication Context
    private Context mContext;
    private ProgressDialog progressDialog;
    private ImportCallBack callBack;

    /**
     * @param backupZipSource the backup file chosen by the person (system file picker)
     */
    public ImportAsyncTask(Context context, ImportCallBack callBack, Uri backupZipSource) {
        this.mContext = context;
        this.dbManager = new DBManager(context);
        FileManager backFM = new FileManager(context, FileManager.BACKUP_DIR);
        this.backupJsonFilePath = backFM.getDirAbsolutePath() + "/"
                + BackupManager.BACKUP_JSON_FILE_NAME;
        this.backupZipSource = backupZipSource;

        this.backupFileManager = new FileManager(mContext, FileManager.BACKUP_DIR);
        this.diartFileManager = new FileManager(mContext, FileManager.DIARY_ROOT_DIR);

        this.callBack = callBack;
        this.progressDialog = new ProgressDialog(context);

        //Init progressDialog
        progressDialog.setMessage(context.getString(R.string.process_dialog_loading));
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.R.style.Widget_ProgressBar);
        progressDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        boolean importSuccessful = true;
        try {
            ZipManager zipManager = new ZipManager(mContext);

            FileManager zipBackupFM = new FileManager(mContext, FileManager.BACKUP_DIR);
            InputStream source = mContext.getContentResolver().openInputStream(backupZipSource);
            if (source == null) {
                throw new IOException("Can't read " + backupZipSource);
            }
            zipManager.unzip(source, zipBackupFM.getDirAbsolutePath() + "/");
            loadBackupJsonFileIntoManager();
            importSuccessful = importTopic();
            if (importSuccessful) {
                //The settings come after the notes (an old backup has none)
                restoreSettings();
            }
        } catch (Exception e) {
            Log.e(TAG, "import flow fail", e);
            importSuccessful = false;
        } finally {
            backupFileManager.clearDir();
        }
        return importSuccessful;
    }

    @Override
    protected void onPostExecute(Boolean importSuccessful) {
        super.onPostExecute(importSuccessful);
        progressDialog.dismiss();
        if (importSuccessful) {
            Toast.makeText(mContext, mContext.getString(R.string.toast_import_successful), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(mContext, mContext.getString(R.string.toast_import_fail), Toast.LENGTH_LONG).show();
        }
        callBack.onImportCompiled(importSuccessful);
    }

    private void loadBackupJsonFileIntoManager() throws Exception {
        FileInputStream fis = new FileInputStream(backupJsonFilePath);
        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader bufferedReader = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line);
        }
        String json = sb.toString();
        Gson gson = new Gson();
        backupManager = gson.fromJson(json, BackupManager.class);
        if (backupManager.getHeader() == null ||
                !backupManager.getHeader().equals(BackupManager.header)) {
            throw new Exception("This is not mydiary backup file");
        }
    }


    private boolean importTopic() {
        boolean importSuccessful = true;
        try {
            dbManager.opeDB();
            //Start a transaction
            dbManager.beginTransaction();
            for (int i = 0; i < backupManager.getBackup_topic_list().size(); i++) {
                saveTopicIntoDB(backupManager.getBackup_topic_list().get(i));
            }
            //Re-sort the topic

            //Check update success
            dbManager.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "importTopic fail", e);
            importSuccessful = false;
        } finally {
            dbManager.endTransaction();
            dbManager.closeDB();
        }
        return importSuccessful;
    }

    private void saveTopicIntoDB(BackupManager.BackupTopicListBean backupTopic) throws IOException {

        long newTopicId = dbManager.insertTopic(
                backupTopic.getTopic_title(),
                backupTopic.getTopic_type(),
                backupTopic.getTopic_color());
        //The background picture of the topic
        restoreTopicBackground(backupTopic.getTopic_type(), backupTopic.getTopic_id(), newTopicId);


        switch (backupTopic.getTopic_type()) {
            case ITopic.TYPE_MEMO:
                if (backupTopic.getMemo_topic_entries_list() != null) {
                    for (int x = 0; x < backupTopic.getMemo_topic_entries_list().size(); x++) {
                        BUMemoEntries memo = backupTopic.getMemo_topic_entries_list().get(x);
                        long newMemoId = dbManager.insertMemo(memo.getMemoEntriesContent(), memo.isChecked(), newTopicId);
                        dbManager.insertMemoOrder(newTopicId, newMemoId, memo.getMemoEntriesOrder());
                    }
                }
                break;
            case ITopic.TYPE_DIARY:
                if (backupTopic.getDiary_topic_entries_list() != null) {
                    for (int y = 0; y < backupTopic.getDiary_topic_entries_list().size(); y++) {
                        BUDiaryEntries diary = backupTopic.getDiary_topic_entries_list().get(y);
                        //Write the diary entries
                        long newDiaryId =
                                dbManager.insertDiaryInfo(diary.getDiaryEntriesTime(), diary.getDiaryEntriesTitle(),
                                        diary.getDiaryEntriesMood(), diary.getDiaryEntriesWeather(),
                                        diary.isDiaryEntriesAttachment(), newTopicId, diary.getDiaryEntriesLocation(),
                                        diary.getDiaryEntriesTags(), diary.getDiaryEntriesThumbnail());
                        //Write the diary item
                        for (int yi = 0; yi < diary.getDiaryItemList().size(); yi++) {
                            BUDiaryItem diaryItem = diary.getDiaryItemList().get(yi);
                            dbManager.insertDiaryContent(diaryItem.getDiaryItemType(),
                                    diaryItem.getDiaryItemPosition(),
                                    diaryItem.getDiaryItemContent(), newDiaryId);
                        }
                        //Copy the diary photo
                        copyDiaryPhoto(backupTopic.getTopic_id(), newTopicId,
                                diary.getDiaryEntriesId(), newDiaryId);
                    }
                }

                break;
            case ITopic.TYPE_CONTACTS:
                if (backupTopic.getContacts_topic_entries_list() != null) {
                    for (int z = 0; z < backupTopic.getContacts_topic_entries_list().size(); z++) {
                        BUContactsEntries contact = backupTopic.getContacts_topic_entries_list().get(z);
                        dbManager.insertContacts(contact.getContactsEntriesName(),
                                contact.getContactsEntriesPhonenumber(), "", newTopicId);
                    }
                }
                break;
        }
    }

    /**
     * The picture that the person chose as the background of a topic (it is in the backup folder of its kind:
     * diary / memo / contacts, in the folder of the old topic).
     */
    private void restoreTopicBackground(int topicType, long oldTopicId, long newTopicId) {
        String kindFolder;
        switch (topicType) {
            case ITopic.TYPE_MEMO:
                kindFolder = "memo";
                break;
            case ITopic.TYPE_CONTACTS:
                kindFolder = "contacts";
                break;
            default:
                kindFolder = "diary";
                break;
        }
        File source = new File(backupFileManager.getDirAbsolutePath() + "/" + kindFolder + "/"
                + oldTopicId + "/" + ThemeManager.CUSTOM_TOPIC_BG_FILENAME);
        if (!source.isFile()) {
            return;
        }
        try {
            File target = ThemeManager.getInstance().getTopicBgSavePathFile(mContext, newTopicId, topicType);
            FileUtils.copyFile(source, target);
        } catch (Exception e) {
            //The notes are more important than a background: go on
            Log.e(TAG, "can't restore the background of a topic", e);
        }
    }

    /**
     * The settings of the backup replace the ones of the app: language, theme and colors, name, profile pictures.
     * The passcode is restored only when the backup has one (a passcode is never taken away by an import).
     */
    private void restoreSettings() {
        BackupManager.BackupSettings settings = backupManager.getSettings();
        if (settings == null) {
            return;
        }
        try {
            SPFManager.setLocalLanguageCode(mContext, settings.language_code);
            SPFManager.setTheme(mContext, settings.theme);
            SPFManager.setMainColor(mContext, settings.theme_main_color);
            SPFManager.setSecondaryColor(mContext, settings.theme_secondary_color);
            SPFManager.setYourName(mContext, settings.your_name == null ? "" : settings.your_name);
            if (settings.release_note_close) {
                SPFManager.setReleaseNoteClose(mContext, true);
            }
            if (settings.encrypted_password != null && settings.encrypted_password.length() > 0) {
                SPFManager.setAndEncryptPassword(mContext, settings.encrypted_password);
            }
            //The theme that the screens use now
            ThemeManager.getInstance().setCurrentTheme(settings.theme);

            //The pictures of the profile
            File settingDir = new FileManager(mContext, FileManager.SETTING_DIR).getDir();
            File backupSettingDir = new File(backupFileManager.getDirAbsolutePath() + "/setting");
            restoreSettingPicture(backupSettingDir, settingDir, ThemeManager.CUSTOM_PROFILE_BANNER_BG_FILENAME,
                    settings.has_custom_profile_banner_bg);
            SPFManager.setCustomProfileBannerBg(mContext,
                    settings.has_custom_profile_banner_bg
                            && new File(settingDir, ThemeManager.CUSTOM_PROFILE_BANNER_BG_FILENAME).exists());
            restoreSettingPicture(backupSettingDir, settingDir, ThemeManager.CUSTOM_PROFILE_PICTURE_FILENAME,
                    settings.has_profile_picture);
        } catch (Exception e) {
            //The notes are already imported: a failed setting does not make the import fail
            Log.e(TAG, "can't restore the settings", e);
        }
    }

    private void restoreSettingPicture(File backupSettingDir, File settingDir, String fileName, boolean inBackup)
            throws IOException {
        File source = new File(backupSettingDir, fileName);
        File target = new File(settingDir, fileName);
        if (inBackup && source.isFile()) {
            FileUtils.copyFile(source, target);
        } else if (target.exists()) {
            //The backup was made without this picture
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }
    }

    private void copyDiaryPhoto(long oldTopicId, long newTopicId,
                                long oldDiaryId, long newDiaryId) throws IOException {
        File backupDiaryDir = new File(backupFileManager.getDirAbsolutePath() + "/diary/" +
                oldTopicId + "/" + oldDiaryId + "/");
        if (backupDiaryDir.exists() || backupDiaryDir.isDirectory()) {
            File newDiaryDir = new File(diartFileManager.getDirAbsolutePath() + "/" +
                    newTopicId + "/" + newDiaryId + "/");
            FileUtils.moveDirectory(backupDiaryDir, newDiaryDir);
        }
    }

}
