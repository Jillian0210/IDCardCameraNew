package com.pengbo.idcardcamera.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;



public class ImageUtils {

    /**
     * 保存图片
     *
     * @param src      源图片
     * @param filePath 要保存到的文件路径
     * @param format   格式
     * @return {@code true}: 成功<br>{@code false}: 失败
     */
    public static boolean save(Bitmap src, String filePath, CompressFormat format) {
        return save(src, FileUtils.getFileByPath(filePath), format, false);
    }



    /**
     * 保存图片
     *
     * @param src     源图片
     * @param file    要保存到的文件
     * @param format  格式
     */
    public static boolean save(Bitmap src, File file, CompressFormat format, boolean recycle) {
        if (isEmptyBitmap(src) || !FileUtils.createOrExistsFile(file)) {
            return false;
        }
        Log.d(TAG, "save: width "+src.getWidth() + ",height= " + src.getHeight());
        OutputStream os = null;
        boolean ret = false;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            ret = src.compress(format, 100, os);
            if (recycle && !src.isRecycled()) {
                src.recycle();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            FileUtils.closeIO(os);
        }
        return ret;
    }

    /**
     * 判断bitmap对象是否为空
     *
     * @param src 源图片
     * @return {@code true}: 是<br>{@code false}: 否
     */
    private static boolean isEmptyBitmap(Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }

    /**
     * 将byte[]转换成Bitmap
     *
     * @param bytes
     * @param width
     * @param height
     * @return
     */
    public static Bitmap getBitmapFromByte(byte[] bytes, int width, int height) {
        final YuvImage image = new YuvImage(bytes, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream(bytes.length);
        if (!image.compressToJpeg(new Rect(0, 0, width, height), 100, os)) {
            return null;
        }
        byte[] tmp = os.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(tmp, 0, tmp.length);
        return bmp;
    }

    private static final String TAG = "ImageUtils";



    /**
     *
     * <p>
     * <p>
     * orc识别上传图片要求如下：
     * 上传的身份证图片base64后大小不超过1M
     * 保持图片光照均匀，避免背光，顶光，测光等情况；图片保持正面，不要90度翻转；图片清晰无模糊
     * 另外，推荐 jpg 文件设置为：尺寸 1024×768 ，图像质量 75 以上 ，位深度 24 。如将输出的 jpg 文件大小控制在 100K 以内，在网络速度为50kb/s的情况下，整个识别速度会更快。
     *
     * @param image
     * @return
     */
    public static Bitmap compressImage(Bitmap image) {

       return compressByMatrix(image);
//        return compressOnQuality(image);
//        return image;

    }

    /**
     * 质量压缩方法
     * @param image
     * @return
     */
    public static Bitmap compressOnQuality(Bitmap image){
        int targetSize = 100;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(CompressFormat.JPEG, 100, baos);//质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        LogToFileUtils.write("压缩前图片大小为 " + baos.toByteArray().length / 2014 + "KB");

        Log.d(TAG, "compressImageByQuality:start " + baos.toByteArray().length / 1024);

        if (baos.toByteArray().length / 1024 > 500) {
            //大于500kb的压缩50%
            baos.reset();
            image.compress(CompressFormat.JPEG, 50, baos);
            Log.d(TAG, "compressImageByQuality: while() 1: " + baos.toByteArray().length / 1024);
        }

        int options = 100;
        while (baos.toByteArray().length / 1024 > targetSize && options > 75) {
            //循环判断如果压缩后图片是否大于100kb,大于1继续压缩
            baos.reset();//重置baos即清空baos
            //第一个参数 ：图片格式 ，第二个参数： 图片质量，100为最高，0为最差  ，第三个参数：保存压缩后的数据的流
            image.compress(CompressFormat.JPEG, options, baos);//这里压缩options%，把压缩后的数据存放到baos中
            options -= 5;//每次都减少
            Log.d(TAG, "compressImageByQuality: while() 2: " + baos.toByteArray().length / 1024);
        }
        LogToFileUtils.write("压缩后图片大小为 " + baos.toByteArray().length / 2014 + "KB");
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());//把压缩后的数据baos存放到ByteArrayInputStream中
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);//把ByteArrayInputStream数据生成图片
        return bitmap;
    }

    private static Bitmap compressByMatrix(Bitmap image){
        int size=100;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //先不压缩，先获取image数据流放在out里面，
        image.compress(Bitmap.CompressFormat.JPEG, 100, out);
        Log.d(TAG, "compressByMatrix:init 100  "+out.toByteArray().length/1024+"KB");

        //100kb/原始大小 ，再开平方，先把宽
        float zoom = (float)Math.sqrt(size * 1024 / (float)out.toByteArray().length);

        Matrix matrix = new Matrix();
        matrix.setScale(zoom, zoom);

        //缩放比例 zoom= 100*1024/origin 开平方
        Bitmap result = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);


        //压缩质量到75
        out.reset();
        result.compress(Bitmap.CompressFormat.JPEG, 75, out);
        Log.d(TAG, "compressByMatrix:compress 75  "+out.toByteArray().length/1024+"KB");

        //如果还是大于100KB那就进行 缩小宽高，循环压缩
        while(out.toByteArray().length > size * 1024){
            matrix.setScale(0.9f, 0.9f);
            result = Bitmap.createBitmap(result, 0, 0, result.getWidth(), result.getHeight(), matrix, true);
            out.reset();
            result.compress(Bitmap.CompressFormat.JPEG, 75, out);
            Log.d(TAG, "while end compressByMatrix: "+out.toByteArray().length/1024+"KB");
        }
        return result;
    }



    public static Bitmap decodeFile(String srcPath) {
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        //开始读入图片，此时把options.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(srcPath,newOpts);
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        //现在主流手机比较多是1280*720分辨率，所以高和宽我们设置为 1024×720
        float hh = 1280f;
        float ww = 720f;
        int be = 1;
        if (w > h && w > ww) {
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0)
            be = 1;
        newOpts.inSampleSize = be;
        newOpts.inJustDecodeBounds=false;
        Bitmap bitmap = BitmapFactory.decodeFile(srcPath, newOpts);
        return bitmap;
    }

    /**
     * Gets the corresponding path to a file from the given content:// URI
     *
     * @param selectedVideoUri The content:// URI to find the file path from
     * @param contentResolver  The content resolver to use to perform the query.
     * @return the file path as a string
     */
    public static String getFilePathFromContentUri(Uri selectedVideoUri,
                                                   ContentResolver contentResolver) {
        String filePath;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA};

        Cursor cursor = contentResolver.query(selectedVideoUri, filePathColumn, null, null, null);
//      也可用下面的方法拿到cursor

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        filePath = cursor.getString(columnIndex);
        cursor.close();
        return filePath;
    }

    /**
     * 通过uri获取图片并进行压缩
     *
     * @param uri
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static Bitmap getBitmapFromUri(Context mContext, Uri uri) {
        try {
            // 读取uri所在的图片
            InputStream input = mContext.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            LogToFileUtils.write("通过uri获取图片成功 uri=" + uri);
            return bitmap;
        } catch (Exception e) {
            LogToFileUtils.write("通过uri获取图片失败 uri=" + uri);
            Toast.makeText(mContext, "通过uri获取图片失败", Toast.LENGTH_SHORT).show();
            Log.d(TAG, e.getMessage());
            return null;
        }

    }
    public static Bitmap getBitmapFromUriAndSampleCompress(Context mContext, Uri uri) {
        try {
            // 读取uri所在的图片

            BitmapFactory.Options newOpts = new BitmapFactory.Options();
            //开始读入图片，此时把options.inJustDecodeBounds 设回true了
            newOpts.inJustDecodeBounds = true;
//            BitmapFactory.decodeFile(srcPath,newOpts);

            InputStream input = mContext.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(input,null,newOpts);

            int w = newOpts.outWidth;
            int h = newOpts.outHeight;
            //现在主流手机比较多是1280*720分辨率，所以高和宽我们设置为 1024×720
            float hh = 1280f;
            float ww = 720f;
            int be = 1;
            if (w > h && w > ww) {
                be = (int) (newOpts.outWidth / ww);
            } else if (w < h && h > hh) {
                be = (int) (newOpts.outHeight / hh);
            }
            if (be <= 0)
                be = 1;
            newOpts.inSampleSize = be;
            newOpts.inJustDecodeBounds=false;
//            Bitmap bitmap = BitmapFactory.decodeFile(srcPath, newOpts);

            InputStream input2 = mContext.getContentResolver().openInputStream(uri);
            Bitmap bitmap2 = BitmapFactory.decodeStream(input2,null,newOpts);

            input.close();
            LogToFileUtils.write("通过uri获取图片成功 uri=" + uri);

            return bitmap2;
        } catch (Exception e) {
            LogToFileUtils.write("通过uri获取图片失败 uri=" + uri);
            Toast.makeText(mContext, "通过uri获取图片失败", Toast.LENGTH_SHORT).show();
            Log.d(TAG, e.getMessage());
            return null;
        }

    }

}
