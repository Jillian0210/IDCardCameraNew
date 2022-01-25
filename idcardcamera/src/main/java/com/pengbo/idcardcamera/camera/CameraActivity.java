package com.pengbo.idcardcamera.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pengbo.idcardcamera.R;
import com.pengbo.idcardcamera.choose.ChooseImageManager;
import com.pengbo.idcardcamera.utils.FileUtils;
import com.pengbo.idcardcamera.utils.LogToFileUtils;
import com.pengbo.idcardcamera.utils.ScreenUtils;
import com.pengbo.idcardcamera.utils.CommonUtils;
import com.pengbo.idcardcamera.utils.ImageUtils;
import com.pengbo.idcardcamera.utils.PermissionUtils;

import java.io.File;

import static android.widget.RelativeLayout.ALIGN_PARENT_TOP;
import static com.pengbo.idcardcamera.camera.CameraUtils.calculateCameraPreviewOrientation;


/**
 * 拍照界面
 */
public class CameraActivity extends Activity implements View.OnClickListener, SensorEventListener {

    private CameraPreview mCameraPreview;
    private ImageView mIvCameraCrop;
    private ImageView mIvCameraFlash;
    private ViewGroup mFlCameraOption;
    private View mViewCameraTop;

    private int mCardType;//拍摄类型
    private boolean isToast = true;//是否弹吐司，为了保证for循环只弹一次

    //true: 手动裁剪 false：不显示自定裁剪框
    private boolean isCustomCrop = false;
    //使用闪光灯
    private boolean mShowFlashBtn = false;
    private TextView mCameraTips;
    private ChooseImageManager mChooseImageTask;
    private boolean mShowCameraBtn;
    private Handler mHandler;
    private View mIvChooseImage;
    private SensorManager mSensorManager;
    private Sensor mDefaultSensor;
    private int mSensorRotation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: 2022/1/13 测试状态下打开
//        LogToFileUtils.init(getApplicationContext());
        //动态请求需要的权限
        boolean checkPermissionFirst = PermissionUtils.checkPermissionFirst(this, IDCardCamera.PERMISSION_CODE_FIRST,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        if (checkPermissionFirst) {
            init();
        }
    }

    /**
     * 处理请求权限的响应
     *
     * @param requestCode  请求码
     * @param permissions  权限数组
     * @param grantResults 请求权限结果数组
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isPermissions = true;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                isPermissions = false;
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) { //用户选择了"不再询问"
                    if (isToast) {
                        Toast.makeText(this, "请手动打开该应用需要的权限", Toast.LENGTH_SHORT).show();
                        isToast = false;
                    }
                }
            }
        }
        isToast = true;

        if (requestCode == IDCardCamera.PERMISSION_CODE_FIRST) {
            if (isPermissions) {

                Log.d("onRequestPermission", "onRequestPermissionsResult: " + "允许所有权限");
                init();
            } else {
                Log.d("onRequestPermission", "onRequestPermissionsResult: " + "有权限不允许");
                finish();
            }
        } else if (requestCode == IDCardCamera.PERMISSION_CHOOSE_IMAGE) {
            if (isPermissions) {
                //选择照片

            }
        }
    }

    private void init() {
        setContentView(R.layout.activity_camera);
        mCardType = getIntent().getIntExtra(IDCardCamera.TAKE_TYPE, 0);
        mShowFlashBtn = getIntent().getBooleanExtra(IDCardCamera.FLAG_FLASH, false);
        mShowCameraBtn = getIntent().getBooleanExtra(IDCardCamera.FLAG_CAMERA, false);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mDefaultSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        initView();
        initListener();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorManager!=null&&mDefaultSensor!=null){
            mSensorManager.registerListener(this, mDefaultSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mSensorManager!=null){
            mSensorManager.unregisterListener(this);
        }
    }


    private void initView() {
        mCameraPreview = findViewById(R.id.camera_preview);
        mIvCameraCrop = findViewById(R.id.iv_camera_crop);
        mIvCameraFlash = findViewById(R.id.iv_camera_flash);
        mFlCameraOption = findViewById(R.id.fl_camera_option);
        mViewCameraTop = findViewById(R.id.view_camera_crop_top);
        mCameraTips = findViewById(R.id.view_camera_crop_hint);
        mIvChooseImage = findViewById(R.id.iv_choose_image);

        mIvCameraFlash.setVisibility(mShowFlashBtn ? View.VISIBLE : View.GONE);
        mIvChooseImage.setVisibility(mShowCameraBtn ? View.VISIBLE : View.GONE);

        switch (mCardType) {
            case IDCardCamera.TYPE_IDCARD_FRONT:
                mIvCameraCrop.setImageResource(R.mipmap.camera_idcard_front);
                mCameraTips.setText(R.string.idcard_front_tips);
                break;
            case IDCardCamera.TYPE_IDCARD_BACK:
                mIvCameraCrop.setImageResource(R.mipmap.camera_idcard_back);
                mCameraTips.setText(R.string.idcard_back_tips);
                break;
            case IDCardCamera.TYPE_BANK:
                mIvCameraCrop.setImageResource(R.mipmap.camera_bank);
                mCameraTips.setText(R.string.bank_tips);
                break;
        }


        //确定裁剪框的宽高 和位置
        int marginLeft = ScreenUtils.dp2px(this, 15);
        float idCardWidth = (float) (ScreenUtils.getScreenWidth(this))-marginLeft*2;
        float idCardHeight = idCardWidth * IDCardCamera.ID_CARD_RATIO_HEIGHT / IDCardCamera.ID_CARD_RATIO_WIDTH;
        RelativeLayout.LayoutParams cropLayoutParams = (RelativeLayout.LayoutParams) mIvCameraCrop.getLayoutParams();
        cropLayoutParams.width = (int) idCardWidth;
        cropLayoutParams.height = (int) idCardHeight;
        int marginTop= (int) ((ScreenUtils.getScreenHeight(this)-idCardHeight)/3);
        cropLayoutParams.setMargins(marginLeft, marginTop, marginLeft, 0);
        cropLayoutParams.addRule(ALIGN_PARENT_TOP);

        mIvCameraCrop.setLayoutParams(cropLayoutParams);
        mIvCameraCrop.invalidate();



        /*增加0.5秒过渡界面，解决个别手机首次申请权限导致预览界面启动慢的问题*/
        if (mHandler == null) {
            mHandler = new Handler();
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCameraPreview.setVisibility(View.VISIBLE);
                    }
                });
            }
        }, 500);
    }

    private void initListener() {
        mCameraPreview.setOnClickListener(this);
        mIvCameraFlash.setOnClickListener(this);
        mIvChooseImage.setOnClickListener(this);
        findViewById(R.id.iv_camera_close).setOnClickListener(this);
        findViewById(R.id.iv_camera_take).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.camera_preview) {
            mCameraPreview.focus();
        } else if (id == R.id.iv_camera_close) {
            //取消
            Intent intent = new Intent();
            setResult(IDCardCamera.RESULT_CANCEL, intent);
            finish();
        } else if (id == R.id.iv_camera_take) {
            if (!CommonUtils.isFastClick()) {
                takePhoto();
            }
        } else if (id == R.id.iv_camera_flash) {
            if (CameraUtils.hasFlash(this)) {
                boolean isFlashOn = mCameraPreview.switchFlashLight();
                mIvCameraFlash.setImageResource(isFlashOn ? R.mipmap.camera_flash_on : R.mipmap.camera_flash_off);
            } else {
                Toast.makeText(this, R.string.no_flash, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.iv_choose_image) {
            //选择图片
            chooseImage();
        }
    }

    private void chooseImage() {
        mChooseImageTask = new ChooseImageManager(this, mCardType, new ChooseImageManager.OnSelectListener() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                //选择图片裁剪返回
                saveToLocalAndGoBack(bitmap);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CameraActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });

        mChooseImageTask.takeImageFromGallery();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mChooseImageTask!=null){
            mChooseImageTask.handleResult(requestCode, resultCode, data);
        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        mCameraPreview.setEnabled(false);
        CameraUtils.getCamera().setOneShotPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(final byte[] bytes, Camera camera) {
                final Camera.Size size = camera.getParameters().getPreviewSize(); //获取预览大小
                camera.stopPreview();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //可以从width 和height 看出图片的方向需要纠正
                        final int w = size.width;
                        final int h = size.height;
                        Bitmap rightRotationBitmap = setRightRotationBitmap(bytes, w, h);
                        final Bitmap cropBitmap = cropImageForVertical(rightRotationBitmap);
                        /*保存自动裁剪的图片后，直接返回*/
                        saveToLocalAndGoBack(cropBitmap);

                    }
                }).start();
            }
        });
    }


    public int calculateSensorRotation(float x, float y) {
        //x是values[0]的值，X轴方向加速度，从左侧向右侧移动，values[0]为负值；从右向左移动，values[0]为正值
        //y是values[1]的值，Y轴方向加速度，从上到下移动，values[1]为负值；从下往上移动，values[1]为正值
        //不考虑Z轴上的数据，
        if (Math.abs(x) > 6 && Math.abs(y) < 4) {
            if (x > 6) {
                return 270;
            } else {
                return 90;
            }
        } else if (Math.abs(y) > 6 && Math.abs(x) < 4) {
            if (y > 6) {
                return 0;
            } else {
                return 180;
            }
        }

        return -1;
    }

    /*
     ************************************************SensorEventListener**************************************
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        //手机移动一段时间后静止，然后静止一段时间后进行对焦
        // 读取加速度传感器数值，values数组0,1,2分别对应x,y,z轴的加速度
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            int x = (int) event.values[0];
            int y = (int) event.values[1];
            int z = (int) event.values[2];

        }
        mSensorRotation = calculateSensorRotation(event.values[0], event.values[1]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * 解决拍出的图片的方向问题
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    private Bitmap setRightRotationBitmap(byte[] data, int width, int height) {
        final Bitmap result;
        if (data != null && data.length > 0) {
            //直接用这个方法获取的bitmap是null的
//            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            //使用下面方式获取bitmap
            Bitmap bitmap = ImageUtils.getBitmapFromByte(data, width, height);
            Matrix matrix = new Matrix();
            //利用传感器获取当前屏幕方向对应角度 加上 开始预览是角度
            int rotation = (calculateCameraPreviewOrientation(this) + mSensorRotation) % 360;
            if (CameraUtils.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
                //如果是后置摄像头因为没有镜面效果直接旋转特定角度
                matrix.setRotate(rotation);
            } else {
                //如果是前置摄像头需要做镜面操作，然后对图片做镜面postScale(-1, 1)
                //因为镜面效果需要360-rotation，才是前置摄像头真正的旋转角度
                rotation = (360 - rotation) % 360;
                matrix.setRotate(rotation);
                matrix.postScale(-1, 1);
            }
            result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } else {
            result = null;
        }
        return result;
    }

    private static final String TAG = "CameraActivity";

    /**
     * 裁剪图片 针对竖屏
     *
     * @param bitmap 纠正后的图片
     */
    private Bitmap cropImageForVertical(Bitmap bitmap) {
        /*计算裁剪区域的的坐标点 相对屏幕*/
        //暂时考虑padding和margin
        float left = mIvCameraCrop.getLeft();
        float top=mIvCameraCrop.getTop();
        float right = mIvCameraCrop.getRight();
        //底部坐标=top+自身高度
        float bottom = mIvCameraCrop.getHeight() + top;

        /*计算裁剪框坐标点占原图坐标点的比例*/
        int previewWidth = mCameraPreview.getWidth();
        int previewHeight = mCameraPreview.getHeight();
        float leftProportion = left / previewWidth;
        float topProportion = top / previewHeight;
        float rightProportion = right / previewWidth;
        float bottomProportion = bottom / previewHeight;

//        Log.d(TAG, String.format("cropImageForVertical: mIvCameraCrop left=%s,top=%s,right=%s,bottom=%s",left,top,right,bottom));
//        Log.d(TAG, String.format("cropImageForVertical: mCameraPreview left=%s,top=%s,right=%s,bottom=%s",
//                mCameraPreview.getLeft(), mCameraPreview.getTop(), mCameraPreview.getRight(), mCameraPreview.getBottom()));
//        Log.d(TAG, String.format("cropImageForVertical: leftProportion=%s,topProportion=%s,rightProportion=%s,bottomProportion=%s",
//                leftProportion,topProportion,rightProportion,bottomProportion));

        /*自动裁剪*/
        Bitmap mCropBitmap = Bitmap.createBitmap(bitmap,
                (int) (leftProportion * (float) bitmap.getWidth()),
                (int) (topProportion * (float) bitmap.getHeight()),
                (int) ((rightProportion - leftProportion) * (float) bitmap.getWidth()),
                (int) ((bottomProportion - topProportion) * (float) bitmap.getHeight()));
        return mCropBitmap;

    }

    public static final String jpgSuffix =".jpg";
    /**
     * 保存图片到本地并且返回图片地址
     *
     * @param bitmap
     */
    private void saveToLocalAndGoBack(Bitmap bitmap) {
        //压缩处理
        Bitmap compressBitmap = ImageUtils.compressImage(bitmap);
        //保存处理
        String imagePath = new StringBuffer().append(FileUtils.getImageCacheDir(CameraActivity.this)).append(File.separator)
                .append(System.currentTimeMillis()).append(jpgSuffix).toString();
        if (ImageUtils.save(compressBitmap, imagePath, Bitmap.CompressFormat.JPEG)) {
            Intent intent = new Intent();
            intent.putExtra(IDCardCamera.IMAGE_PATH, imagePath);
            intent.putExtra(IDCardCamera.TAKE_TYPE, mCardType);
            setResult(IDCardCamera.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mCameraPreview != null) {
            mCameraPreview.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCameraPreview != null) {
            mCameraPreview.onStop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

}