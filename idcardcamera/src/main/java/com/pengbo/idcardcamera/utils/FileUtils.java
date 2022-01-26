package com.pengbo.idcardcamera.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public  class FileUtils {

    /**
     * 判断目录是否存在，不存在则判断是否创建成功
     *
     * @param file 文件
     * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
     */
    public static boolean createOrExistsDir(File file) {
        // 如果存在，是目录则返回true，是文件则返回false，不存在则返回是否创建成功
        return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
    }


    /**
     * 判断文件是否存在，不存在则判断是否创建成功
     *
     * @param file 文件
     * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
     */
    public static boolean createOrExistsFile(File file) {
        if (file == null)
            return false;
        // 如果存在，是文件则返回true，是目录则返回false
        if (file.exists())
            return file.isFile();
        if (!createOrExistsDir(file.getParentFile()))
            return false;
        try {
            return file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据文件路径获取文件
     *
     * @param filePath 文件路径
     * @return 文件
     */
    public static File getFileByPath(String filePath) {
        return isSpace(filePath) ? null : new File(filePath);
    }

    /**
     * 判断字符串是否为 null 或全为空白字符
     *
     * @param s
     * @return
     */
    private static boolean isSpace(final String s) {
        if (s == null)
            return true;
        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 关闭IO
     *
     * @param closeables closeable
     */
    public static void closeIO(Closeable... closeables) {
        if (closeables == null)
            return;
        try {
            for (Closeable closeable : closeables) {
                if (closeable != null) {
                    closeable.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取缓存图片的目录
     *
     * @param context Context
     * @return 缓存图片的目录
     */
    public static String getImageCacheDir(Context context) {
        File file;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            file = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        } else {
            file = context.getCacheDir();
        }
        String path = file.getPath() + "/cache";
        LogToFileUtils.write("[getImageCacheDir] , get image cache path="+path);
        File cachePath = new File(path);
        if (!cachePath.exists())
            cachePath.mkdir();
        return path;
    }

    /**
     * 删除缓存图片目录中的全部图片
     *
     * @param context
     */
    public static void clearCache(Context context) {
        String cacheImagePath = getImageCacheDir(context);
        File cacheImageDir = new File(cacheImagePath);
        File[] files = cacheImageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }


    /**
     * 创建Scheme为file临时的uri
     *
     * @param context
     * @return
     */
    public static Uri getTempSchemeFileUri(@NonNull Context context) {
        return getTempSchemeFileUri(context, null);
    }

    /**
     * 创建Scheme为file临时的uri
     *
     * @param context
     * @return
     */
    public static Uri getTempSchemeFileUri(@NonNull Context context, String suffix) {
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "/" + timeStamp + (TextUtils.isEmpty(suffix) ? ".jpg" : suffix));
        if (!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            String fileProvider = context.getPackageName();
            if (!TextUtils.isEmpty(fileProvider)) {
                fileProvider = fileProvider + ".fileprovider";
            }
            try {
                uri = FileProvider.getUriForFile(context, fileProvider, file);// android 10 fixed
            } catch (Exception e) {
                uri = Uri.fromFile(file);
            }
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }
    public static Uri getTempSchemeUri(@NonNull Context context) {
//        if ( Build.VERSION.SDK_INT >= 30){
//            return getTempSchemeUriRForSysCrop(context);
//        }else {
            return getTempSchemeFileUri(context);
//        }

    }

    /**
     * 使用系统裁剪时调用
     * @param context
     * @return
     */
    private static Uri getTempSchemeUriRForSysCrop(@NonNull Context context){
        String fileName = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date())+"_CROP.jpg";

        File imgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + fileName);
        // 通过 MediaStore API 插入file 为了拿到系统裁剪要保存到的uri（因为App没有权限不能访问公共存储空间，需要通过 MediaStore API来操作）
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DATA, imgFile.getAbsolutePath());
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,  values);

    }

}
