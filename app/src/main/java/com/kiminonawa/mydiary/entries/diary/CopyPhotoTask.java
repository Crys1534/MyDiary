package com.kiminonawa.mydiary.entries.diary;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.kiminonawa.mydiary.R;
import com.kiminonawa.mydiary.shared.photo.BitmapHelper;
import com.kiminonawa.mydiary.shared.photo.ExifUtil;
import com.kiminonawa.mydiary.shared.FileManager;

import java.io.FileOutputStream;
import java.io.IOException;


/**
 * Created by daxia on 2016/11/21.
 */

public class CopyPhotoTask extends AsyncTask<Void, Void, String> {

    public interface CopyPhotoCallBack {
        void onCopyCompiled(String fileName);
    }

    private Uri uri;
    private String srcFileName;
    private ProgressDialog progressDialog;
    private CopyPhotoTask.CopyPhotoCallBack callBack;
    private Context mContext;
    private int reqWidth, reqHeight;
    private FileManager fileManager;
    private boolean isAddPicture = false;


    /**
     * From select image
     */
    public CopyPhotoTask(Context context, Uri uri,
                         int reqWidth, int reqHeight,
                         FileManager fileManager, CopyPhotoCallBack callBack) {
        this.uri = uri;
        isAddPicture = false;
        initTask(context, reqWidth, reqHeight, fileManager, callBack);

    }


    /**
     * From take a picture
     */
    public CopyPhotoTask(Context context, String srcFileName,
                         int reqWidth, int reqHeight,
                         FileManager fileManager, CopyPhotoCallBack callBack) {
        this.srcFileName = fileManager.getDirAbsolutePath() + "/" + srcFileName;
        isAddPicture = true;
        initTask(context, reqWidth, reqHeight, fileManager, callBack);
    }

    public void initTask(Context context,
                         int reqWidth, int reqHeight,
                         FileManager fileManager, CopyPhotoCallBack callBack) {
        this.mContext = context;
        this.reqWidth = reqWidth;
        this.reqHeight = reqHeight;
        this.fileManager = fileManager;
        this.callBack = callBack;
        this.progressDialog = new ProgressDialog(context);

        progressDialog.setMessage(context.getString(R.string.process_dialog_loading));
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.R.style.Widget_ProgressBar);
        progressDialog.show();
    }

    @Override
    protected String doInBackground(Void... params) {
        String returnFileName = null;
        try {
            //1.Create bitmap
            //2.Get uri exif
            Bitmap bitmap;
            if (isAddPicture) {
                bitmap = BitmapHelper.getBitmapFromTempFileSrc(srcFileName, reqWidth, reqHeight);
                if (bitmap != null) {
                    returnFileName = savePhotoToTemp(ExifUtil.rotateBitmap(srcFileName, bitmap));
                }
            } else {
                //rotateBitmap && resize
                bitmap = BitmapHelper.getBitmapFromReturnedImage(mContext, uri, reqWidth, reqHeight);
                if (bitmap != null) {
                    returnFileName = savePhotoToTemp(ExifUtil.rotateBitmap(mContext, uri, bitmap));
                }
            }
            //bitmap == null means that Android could not read the file as a picture
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("CopyPhotoTask", e.toString());
            returnFileName = null;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            Log.e("CopyPhotoTask", e.toString());
            returnFileName = null;
        }
        return returnFileName;
    }

    @Override
    protected void onPostExecute(String fileName) {
        super.onPostExecute(fileName);
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            //The screen was closed or rotated while the picture was loading
            e.printStackTrace();
        }
        //fileName is null when the picture could not be loaded
        callBack.onCopyCompiled(fileName);
    }


    private String savePhotoToTemp(Bitmap bitmap) throws Exception {
        if (bitmap == null) {
            throw new IOException("No bitmap to save");
        }
        FileOutputStream out = null;
        String fileName = FileManager.createRandomFileName();
        try {
            out = new FileOutputStream(fileManager.getDirAbsolutePath() + "/" + fileName);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // bmp is your Bitmap instance
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return fileName;
    }

}
