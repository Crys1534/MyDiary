package com.kiminonawa.mydiary.backup;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.main.MainActivity;
import com.kiminonawa.mydiary.shared.gui.MyDiaryButton;

import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * Created by daxia on 2017/2/14.
 * <p>
 * The backup file is chosen with the file picker of Android itself (it works with the phone
 * storage, Google Drive, etc. and does not need any storage permission).
 */

public class BackupActivity extends AppCompatActivity implements View.OnClickListener,
        ExportAsyncTask.ExportCallBack, ImportAsyncTask.ImportCallBack {


    private final int EXPORT_SRC_PICKER_CODE = 0;
    private final int IMPORT_SRC_PICKER_CODE = 1;
    /*
     * UI
     */
    private TextView TV_backup_export_src, TV_backup_import_src;
    private MyDiaryButton But_backup_export, But_backup_import;

    /*
     * The files the person chose
     */
    private Uri exportUri, importUri;
    private String exportFileName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        //UI
        TV_backup_export_src = (TextView) findViewById(R.id.TV_backup_export_src);
        TV_backup_export_src.setOnClickListener(this);

        TV_backup_import_src = (TextView) findViewById(R.id.TV_backup_import_src);
        TV_backup_import_src.setOnClickListener(this);

        But_backup_export = (MyDiaryButton) findViewById(R.id.But_backup_export);
        But_backup_export.setOnClickListener(this);
        But_backup_export.setEnabled(false);

        But_backup_import = (MyDiaryButton) findViewById(R.id.But_backup_import);
        But_backup_import.setOnClickListener(this);
        But_backup_import.setEnabled(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) {
            return;
        }
        Uri uri = intent.getData();
        if (requestCode == EXPORT_SRC_PICKER_CODE) {
            exportUri = uri;
            exportFileName = getDisplayName(uri);
            TV_backup_export_src.setText(exportFileName);
            But_backup_export.setEnabled(true);
        } else if (requestCode == IMPORT_SRC_PICKER_CODE) {
            importUri = uri;
            TV_backup_import_src.setText(getDisplayName(uri));
            But_backup_import.setEnabled(true);
        }
    }

    /**
     * The name of the file the person chose.
     */
    private String getDisplayName(Uri uri) {
        String name = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (name == null || name.length() == 0) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.TV_backup_export_src:
                //"Save as": the person chooses the place and the name of the new file
                Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                exportIntent.addCategory(Intent.CATEGORY_OPENABLE);
                exportIntent.setType("application/zip");
                exportIntent.putExtra(Intent.EXTRA_TITLE, BackupManager.BACKUP_ZIP_FILE_HEADER
                        + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
                        + BackupManager.BACKUP_ZIP_FILE_SUB_FILE_NAME);
                try {
                    startActivityForResult(exportIntent, EXPORT_SRC_PICKER_CODE);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, getString(R.string.backup_export_can_not_write), Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.TV_backup_import_src:
                Intent importIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                importIntent.addCategory(Intent.CATEGORY_OPENABLE);
                importIntent.setType("*/*");
                try {
                    startActivityForResult(importIntent, IMPORT_SRC_PICKER_CODE);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this, getString(R.string.backup_import_can_not_read), Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.But_backup_export:
                if (exportUri != null) {
                    new ExportAsyncTask(this, this, exportUri, exportFileName).execute();
                }
                break;
            case R.id.But_backup_import:
                if (importUri != null) {
                    new ImportAsyncTask(this, this, importUri).execute();
                }
                break;
        }
    }


    @Override
    public void onImportCompiled(boolean importSuccessful) {
        Intent backMainActivityIntent = new Intent(this, MainActivity.class);
        backMainActivityIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(backMainActivityIntent);
    }


    @Override
    public void onExportCompiled(boolean exportSuccessful) {
        //The file is already where the person chose to save it. Nothing else to do.
        if (exportSuccessful) {
            But_backup_export.setEnabled(false);
            TV_backup_export_src.setText(R.string.backup_export_path_hint);
            exportUri = null;
        }
    }
}
