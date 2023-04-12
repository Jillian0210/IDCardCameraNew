package com.pengbo.idcardcamera.choose;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import com.pengbo.idcardcamera.camera.IDCardCamera;
import com.pengbo.idcardcamera.utils.FileUtils;
import com.pengbo.idcardcamera.utils.LogToFileUtils;
import com.pengbo.idcardcamera.utils.ScreenUtils;
import com.pengbo.idcardcamera.utils.ImageUtils;
import com.pengbo.idcardcamera.utils.PermissionUtils;
import com.pengbo.ucrop.UCrop;

/**
 * @author 从相册中选择照片
 */
public class ChooseImageManager {

    public Activity mContext;
    public OnSelectListener mOnSelectListener;
    //输出图片(裁剪后)的uri
    private Uri mCropUri;
    //身份证（正反面）、银行卡
    private int mCardType;

    //是否使用系统裁剪
    private boolean isSystemCrop=false;

    public ChooseImageManager(Activity context, int cardType, OnSelectListener onSelectListener) {
        mContext = context;
        mCardType = cardType;
        mOnSelectListener = onSelectListener;
    }


    /**
     * 从系统图库里面选择
     */
    public void takeImageFromGallery() {

        //这里可以加一步检测权限是否申请
        boolean checkPermissionFirst = PermissionUtils.checkPermissionFirst(mContext, IDCardCamera.PERMISSION_CHOOSE_IMAGE,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE});
        if (checkPermissionFirst) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            ComponentName componentName = intent.resolveActivity(mContext.getPackageManager());
            if (componentName != null) {
                LogToFileUtils.write("[takeImageFromGallery], start intent activity");
                mContext.startActivityForResult(intent, IDCardCamera.TYPE_GALLERY);
            } else {
                //部份手机找不到对应componentName，不做uri限制
                LogToFileUtils.write("[takeImageFromGallery], EXTERNAL_CONTENT_URI activity is illegal ");
                Intent intent2 = new Intent(Intent.ACTION_PICK);
                intent2.setType("image/*");
                mContext.startActivityForResult(intent2, IDCardCamera.TYPE_GALLERY);
            }
        } else {
            LogToFileUtils.write("[takeImageFromGallery], has no permission");
            mOnSelectListener.onError("checkPermission---->");
        }
    }


    private static final String TAG = "ChooseImageTask";

    /**
     * 代理Activity的返回值过程然后
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void handleResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == IDCardCamera.TYPE_GALLERY) {
                //图库选择图片
                if (data != null) {
                    LogToFileUtils.write("[handleResult],choose image from gallery success");
                    //获取图片的uri
                    Uri uri = data.getData();
                    //保存图片到mCropUri
                    mCropUri = FileUtils.getTempSchemeUri(mContext);

                    //跳转到裁剪页面
                    LogToFileUtils.write("[handleResult],origin uri=" + uri + ",after crop uri=" + mCropUri);
                    if (isSystemCrop){
                        //系统裁剪
                        LogToFileUtils.write("[handleResult],start system copper");
                        handleSysCropImage(mContext, uri, mCropUri);
                    }else {
                        //2021/11/11  自定义裁剪
                        UCrop.Options options = new UCrop.Options();
                        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
                        options.setCardType(mCardType);
                        UCrop.of(uri, mCropUri)
                                .withOptions(options)
                                .withAspectRatio(IDCardCamera.ID_CARD_RATIO_WIDTH, IDCardCamera.ID_CARD_RATIO_HEIGHT)
                                .start(mContext);
                        LogToFileUtils.write("[handleResult],start ucrop copper");
                    }

                } else {
                    LogToFileUtils.write("[handleResult],choose image from gallery fail");
                    if (mOnSelectListener != null) {
                        mOnSelectListener.onError("uri is null before crop !");
                    }
                }

            } else if (requestCode == IDCardCamera.TYPE_SELECT_IMG_SYS_CROP) {
                //系统裁剪之后发返回值

                Bitmap bitmapFormUri = ImageUtils.getBitmapFromUri(mContext, mCropUri);
                if (mOnSelectListener != null) {
                    if (null == bitmapFormUri) {
                        mOnSelectListener.onError("bitmap is null after crop handle !");
                        return;
                    }
                    mOnSelectListener.onSuccess(bitmapFormUri);
                    LogToFileUtils.write("[handleResult],use system cropper and crop success");
                }
            } else if (requestCode == UCrop.REQUEST_CROP) {
                //自定义裁剪
                final Uri resultUri = UCrop.getOutput(data);
                LogToFileUtils.write("[handleResult],use ucrop  cropper, crop result uri="+resultUri);
                Bitmap bitmapFormUri = ImageUtils.getBitmapFromUri(mContext, resultUri);
                if (mOnSelectListener != null) {
                    if (null == bitmapFormUri) {
                        mOnSelectListener.onError("bitmap is null after crop handle !");
                        return;
                    }
                    mOnSelectListener.onSuccess(bitmapFormUri);
                    LogToFileUtils.write("[handleResult],use ucrop cropper, crop success");
                }

            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            if (data!=null){
                final Throwable cropError = UCrop.getError(data);
                LogToFileUtils.write("[handleResult],use ucrop cropper, crop error="+(cropError!=null?cropError.toString():null));
            }

        }

    }



    /**
     * 图片类型的裁剪 系统裁剪
     *
     * @param activity
     * @param uri
     * @param outputUri
     */
    public void handleSysCropImage(Activity activity, Uri uri, Uri outputUri) {
        //打开系统自带的裁剪图片的intent
        Intent intent = new Intent("com.android.camera.action.CROP");
        //拍照需要添加，从相册选择不需要
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            //添加这一句表示对目标应用临时授权该Uri所代表的文件
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        }
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("scale", true);
        // 设置裁剪区域的宽高比例 注意这里的值最好设置为整型而不用float等其他类型。设置为整型比例，裁剪框才能固定比例
        intent.putExtra("aspectX", 86);
        intent.putExtra("aspectY", 54);
        // 设置裁剪区域的宽度和高度
        float screenMinSize = Math.min(ScreenUtils.getScreenWidth(activity), ScreenUtils.getScreenHeight(activity));
//        Log.d(TAG,"outputX="+screenMinSize+",outputY "+(screenMinSize * 54.0f / 86.0f));
        intent.putExtra("outputX", (int) screenMinSize);
        intent.putExtra("outputY", (int) (screenMinSize * 54.0f / 86.0f));

        // 人脸识别
        intent.putExtra("noFaceDetection", true);
        // 图片输出格式
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        // 若为false则表示不返回数据
        intent.putExtra("return-data", false);
        //拉伸,防止某些手机裁剪小图片导致黑边产生
        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", true);
        //输出图片到指定位置
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        activity.startActivityForResult(intent, IDCardCamera.TYPE_SELECT_IMG_SYS_CROP);
    }


    /**
     *
     * 监听选择图片
     */
    public interface OnSelectListener {

        void onSuccess(Bitmap bitmap);

        void onError(String message);//可以放一些异常和错误

    }
}