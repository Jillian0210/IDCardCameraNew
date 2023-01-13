package com.pengbo.ucrop.task;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.pengbo.ucrop.util.BitmapLoadUtils;
import com.pengbo.ucrop.callback.BitmapCropCallback;
import com.pengbo.ucrop.model.CropParameters;
import com.pengbo.ucrop.model.ExifInfo;
import com.pengbo.ucrop.model.ImageState;
import com.pengbo.ucrop.util.FileUtils;
import com.pengbo.ucrop.util.ImageHeaderParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;


/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
public class BitmapCropTask extends AsyncTask<Void, Void, Throwable> {

    private static final String TAG = "BitmapCropTask";


    private Bitmap mViewBitmap;

    private final RectF mCropRect;
    private final RectF mCurrentImageRect;

    private float mCurrentScale, mCurrentAngle;
    private final int mMaxResultImageSizeX, mMaxResultImageSizeY;

    private final Bitmap.CompressFormat mCompressFormat;
    private final int mCompressQuality;
    private final String mImageInputPath, mImageOutputPath;
    private final ExifInfo mExifInfo;
    private final BitmapCropCallback mCropCallback;

    private int mCroppedImageWidth, mCroppedImageHeight;
    private int cropOffsetX, cropOffsetY;

    private ProgressDialog mProgressDialog;
    private final WeakReference<Context> mContext;
    private final Uri mImageInputUri, mImageOutputUri;

    public BitmapCropTask(Context context, @Nullable Bitmap viewBitmap, @NonNull ImageState imageState, @NonNull CropParameters cropParameters,
                          @Nullable BitmapCropCallback cropCallback) {

        mContext = new WeakReference<>(context);
        mViewBitmap = viewBitmap;
        mCropRect = imageState.getCropRect();
        mCurrentImageRect = imageState.getCurrentImageRect();

        mCurrentScale = imageState.getCurrentScale();
        mCurrentAngle = imageState.getCurrentAngle();
        mMaxResultImageSizeX = cropParameters.getMaxResultImageSizeX();
        mMaxResultImageSizeY = cropParameters.getMaxResultImageSizeY();

        mCompressFormat = cropParameters.getCompressFormat();
        mCompressQuality = cropParameters.getCompressQuality();

        mImageInputPath = cropParameters.getImageInputPath();
        mImageOutputPath = cropParameters.getImageOutputPath();
        mImageInputUri = cropParameters.getContentImageInputUri();
        mImageOutputUri = cropParameters.getContentImageOutputUri();
        mExifInfo = cropParameters.getExifInfo();

        mCropCallback = cropCallback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Log.d(TAG, "onPreExecute: ");
        if (mContext !=null){
            Context context = mContext.get();
            if (context instanceof Activity){
                mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setMessage("正在裁剪中,请稍等...");
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialog.show();
            }
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    @Nullable
    protected Throwable doInBackground(Void... params) {
        if (mViewBitmap == null) {
            return new NullPointerException("ViewBitmap is null");
        } else if (mViewBitmap.isRecycled()) {
            return new NullPointerException("ViewBitmap is recycled");
        } else if (mCurrentImageRect.isEmpty()) {
            return new NullPointerException("CurrentImageRect is empty");
        }

        if (mImageOutputUri == null) {
            return new NullPointerException("ImageOutputUri is null");
        }

        try {
            crop();
            mViewBitmap = null;
        } catch (Throwable throwable) {
            return throwable;
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean crop() throws IOException {
        Context context = mContext.get();
        if (context == null) {
            return false;
        }

        // Downsize if needed
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            float cropWidth = mCropRect.width() / mCurrentScale;
            float cropHeight = mCropRect.height() / mCurrentScale;

            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {

                float scaleX = mMaxResultImageSizeX / cropWidth;
                float scaleY = mMaxResultImageSizeY / cropHeight;
                float resizeScale = Math.min(scaleX, scaleY);

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(mViewBitmap,
                        Math.round(mViewBitmap.getWidth() * resizeScale),
                        Math.round(mViewBitmap.getHeight() * resizeScale), false);
                if (mViewBitmap != resizedBitmap) {
                    mViewBitmap.recycle();
                }
                mViewBitmap = resizedBitmap;

                mCurrentScale /= resizeScale;
            }
        }

        // Rotate if needed
        if (mCurrentAngle != 0) {
            Matrix tempMatrix = new Matrix();
            tempMatrix.setRotate(mCurrentAngle, mViewBitmap.getWidth() / 2, mViewBitmap.getHeight() / 2);

            Bitmap rotatedBitmap = Bitmap.createBitmap(mViewBitmap, 0, 0, mViewBitmap.getWidth(), mViewBitmap.getHeight(),
                    tempMatrix, true);
            if (mViewBitmap != rotatedBitmap) {
                mViewBitmap.recycle();
            }
            mViewBitmap = rotatedBitmap;
        }

        cropOffsetX = Math.round((mCropRect.left - mCurrentImageRect.left) / mCurrentScale);
        cropOffsetY = Math.round((mCropRect.top - mCurrentImageRect.top) / mCurrentScale);
        mCroppedImageWidth = Math.round(mCropRect.width() / mCurrentScale);
        mCroppedImageHeight = Math.round(mCropRect.height() / mCurrentScale);

        boolean shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight);
        Log.i(TAG, "Should crop: " + shouldCrop);

        if (shouldCrop) {
            saveImage(Bitmap.createBitmap(mViewBitmap, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight));
            if (mCompressFormat.equals(Bitmap.CompressFormat.JPEG)) {
                copyExifForOutputFile(context);
            }
            return true;
        } else {
            FileUtils.copyFile(context ,mImageInputUri, mImageOutputUri);
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void copyExifForOutputFile(Context context) throws IOException {
        boolean hasImageInputUriContentSchema = BitmapLoadUtils.hasContentScheme(mImageInputUri);
        boolean hasImageOutputUriContentSchema = BitmapLoadUtils.hasContentScheme(mImageOutputUri);
        /*
         * ImageHeaderParser.copyExif with output uri as a parameter
         * uses ExifInterface constructor with FileDescriptor param for overriding output file exif info,
         * which doesn't support ExitInterface.saveAttributes call for SDK lower than 21.
         *
         * See documentation for ImageHeaderParser.copyExif and ExifInterface.saveAttributes implementation.
         */
        if (hasImageInputUriContentSchema && hasImageOutputUriContentSchema) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ImageHeaderParser.copyExif(context, mCroppedImageWidth, mCroppedImageHeight, mImageInputUri, mImageOutputUri);
            } else {
                Log.e(TAG, "It is not possible to write exif info into file represented by \"content\" Uri if Android < LOLLIPOP");
            }
        } else if (hasImageInputUriContentSchema) {
            ImageHeaderParser.copyExif(context, mCroppedImageWidth, mCroppedImageHeight, mImageInputUri, mImageOutputPath);
        } else if (hasImageOutputUriContentSchema) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ExifInterface originalExif = new ExifInterface(mImageInputPath);
                ImageHeaderParser.copyExif(context, originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputUri);
            } else {
                Log.e(TAG, "It is not possible to write exif info into file represented by \"content\" Uri if Android < LOLLIPOP");
            }
        } else {
            ExifInterface originalExif = new ExifInterface(mImageInputPath);
            ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath);
        }
    }

    private void saveImage(@NonNull Bitmap croppedBitmap) {
        Context context = mContext.get();
        if (context == null) {
            return;
        }

        OutputStream outputStream = null;
        ByteArrayOutputStream outStream = null;
        try {
            outputStream = context.getContentResolver().openOutputStream(mImageOutputUri);
            outStream = new ByteArrayOutputStream();
            croppedBitmap.compress(mCompressFormat, mCompressQuality, outStream);
            outputStream.write(outStream.toByteArray());
            croppedBitmap.recycle();
        } catch (IOException exc) {
            Log.e(TAG, exc.getLocalizedMessage());
        } finally {
            BitmapLoadUtils.close(outputStream);
            BitmapLoadUtils.close(outStream);
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private boolean shouldCrop(int width, int height) {
        int pixelError = 1;
        pixelError += Math.round(Math.max(width, height) / 1000f);
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0)
                || Math.abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || Math.abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || Math.abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || Math.abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0;
    }

    @Override
    protected void onPostExecute(@Nullable Throwable t) {
        if (mProgressDialog!=null&&mProgressDialog.isShowing()){
            mProgressDialog.dismiss();
            mProgressDialog=null;
        }
        if (mCropCallback != null) {
            if (t == null) {
                Uri uri;

                if (BitmapLoadUtils.hasContentScheme(mImageOutputUri)) {
                    uri = mImageOutputUri;
                } else {
                    uri = Uri.fromFile(new File(mImageOutputPath));
                }
                mCropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight);
            } else {
                mCropCallback.onCropFailure(t);
            }
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        if (mProgressDialog!=null&&mProgressDialog.isShowing()){
            mProgressDialog.dismiss();
            mProgressDialog=null;
        }
    }
}
