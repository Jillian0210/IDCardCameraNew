package com.pengbo.idcardcamera.camera;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.Fragment;

import java.lang.ref.WeakReference;

/**
 * 给外面调用的入口类
 */
public class IDCardCamera {

    public final static int    TYPE_IDCARD_FRONT     = 1;//身份证正面
    public final static int    TYPE_IDCARD_BACK      = 2;//身份证反面
    //身份证件宽高比
    public final static float ID_CARD_RATIO_WIDTH = 86.0f;
    public final static float ID_CARD_RATIO_HEIGHT = 54.7f;
    public final static int    TYPE_BANK=3;//银行卡

    public final static int    RESULT_OK = 0X11;//成功
    public final static int    RESULT_CANCEL=0x13;//取消
    public final static int    PERMISSION_CODE_FIRST = 0x12;//拍照权限请求码
    public final static int    PERMISSION_CHOOSE_IMAGE=0X14;//选择图片权限请求码

    public final static String TAKE_TYPE             = "take_type";//拍摄类型标记
    public final static String FLAG_FLASH            = "flag_flash";//是否使用闪光灯
    public final static String FLAG_CAMERA            = "flag_camera";//是否使用相册选择图片
    public final static String IMAGE_PATH            = "image_path";//图片路径标记

    public final static String CROP_INPUT_URI="crop_input_uri";//裁剪输入文件
    public final static String CROP_OUTPUT_URI="crop_output_uri";//裁剪输出文件
    //类型和请求码
    public final static int TYPE_GALLERY = 100;//图集
    public final static int TYPE_SELECT_IMG_SYS_CROP = 103;//裁剪
    public final static int TYPE_SELECT_IMG_CUSTOM_CROP = 104;//裁剪选择的图片

    private final WeakReference<Activity> mActivity;
    private final WeakReference<Fragment> mFragment;

    public static IDCardCamera create(Activity activity) {
        return new IDCardCamera(activity);
    }

    private IDCardCamera(Activity activity) {
        this(activity, (Fragment) null);
    }


    private IDCardCamera(Activity activity, Fragment fragment) {
        this.mActivity = new WeakReference(activity);
        this.mFragment = new WeakReference(fragment);
    }

    /**
     * 打开相机
     *
     * @param cardType 身份证方向（TYPE_IDCARD_FRONT / TYPE_IDCARD_BACK）
     */
    public void openCamera(int cardType,boolean bCamera,int requestCode) {
        Activity activity = this.mActivity.get();
        Fragment fragment = this.mFragment.get();
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra(TAKE_TYPE, cardType);
        intent.putExtra(FLAG_CAMERA,bCamera);
        intent.putExtra(FLAG_FLASH,false);
        if (fragment != null) {
            fragment.startActivityForResult(intent, requestCode);
        } else {
            activity.startActivityForResult(intent, requestCode);
        }
    }

    /**
     * 获取图片路径
     *
     * @param data Intent
     * @return 图片路径
     */
    public static String getImagePath(Intent data) {
        if (data != null) {
            return data.getStringExtra(IMAGE_PATH);
        }
        return "";
    }
}

