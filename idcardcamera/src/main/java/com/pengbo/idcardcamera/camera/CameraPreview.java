package com.pengbo.idcardcamera.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.pengbo.idcardcamera.utils.ScreenUtils;

import java.util.List;

/**
 * 相机预览
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static String TAG = CameraPreview.class.getName();

    private Camera camera;
    private Context mContext;
    private SurfaceHolder mSurfaceHolder;

    public CameraPreview(Context context) {
        super(context);
        init(context);
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }


    private void init(Context context) {
        mContext = context;
        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setKeepScreenOn(true);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    public void surfaceCreated(SurfaceHolder holder) {
        camera = CameraUtils.openCamera();
        if (camera != null) {
            try {
                camera.setPreviewDisplay(holder);

                //纠正预览方向
                camera.setDisplayOrientation(CameraUtils.calculateCameraPreviewOrientation((Activity) mContext));

                //设置最佳预览大小
                Camera.Parameters parameters = camera.getParameters();
                Point screenResolution = new Point(ScreenUtils.getScreenWidth(mContext), ScreenUtils.getScreenHeight(mContext));
                Point cameraResolution = getCameraResolution(parameters, screenResolution);
                Log.d(TAG, "surfaceCreated: " + cameraResolution.x + "," + cameraResolution.y);
                parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);

                camera.setParameters(parameters);
                camera.startPreview();
                focus();//首次对焦


//                Matrix matrix = calculateSurfaceHolderTransform(screenResolution, cameraResolution);
//                float[] values = new float[9];
//                matrix.getValues(values);
//                this.setTranslationX(values[Matrix.MTRANS_X]);
//                this.setTranslationY(values[Matrix.MTRANS_Y]);
//                this.setScaleX(values[Matrix.MSCALE_X]);
//                this.setScaleY(values[Matrix.MSCALE_Y]);
//                this.invalidate();


            } catch (Exception e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
                try {
                    //纠正预览方向
                    camera.setDisplayOrientation(CameraUtils.calculateCameraPreviewOrientation((Activity) mContext));

                    Camera.Parameters parameters = camera.getParameters();
                    camera.setParameters(parameters);
                    camera.startPreview();
                    //首次对焦
                    focus();
                } catch (Exception e1) {
                    e.printStackTrace();
                    camera = null;
                }
            }
        }
    }

    /**
     * 获取最佳预览大小，防止预览变形
     *
     * @param parameters
     * @param screenResolution
     * @return
     */
    private Point getCameraResolution(Camera.Parameters parameters, Point screenResolution) {
        float tmp = 0f;
        float mindiff = 100f;
        float x_d_y = (float) screenResolution.x / (float) screenResolution.y;
        if (x_d_y < 1) {
            //保证x是大的那个
            x_d_y = (float) screenResolution.y / (float) screenResolution.x;
        }
        Camera.Size best = null;
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        for (Camera.Size s : supportedPreviewSizes) {
            tmp = Math.abs(( (float) s.width/(float) s.height) - x_d_y);
            if (tmp < mindiff) {
                mindiff = tmp;
                best = s;
            }
        }
        return new Point(best.width, best.height);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        //因为设置了固定屏幕方向，所以在实际使用中不会触发这个方法
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        holder.removeCallback(this);
        //回收释放资源
        release();
    }

    /**
     * 释放资源
     */
    private void release() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    /**
     * 对焦，在CameraActivity中触摸对焦或者自动对焦
     */
    public void focus() {
        if (camera != null) {
            try {
                camera.autoFocus(null);
            } catch (Exception e) {
                Log.d(TAG, "takePhoto " + e);
            }
        }
    }

    /**
     * 开关闪光灯
     *
     * @return 闪光灯是否开启
     */
    public boolean switchFlashLight() {
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(parameters);
                return true;
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
                return false;
            }
        }
        return false;
    }

    /**
     * 拍摄照片
     *
     * @param pictureCallback 在pictureCallback处理拍照回调
     */
    public void takePhoto(Camera.PictureCallback pictureCallback) {
        if (camera != null) {
            try {
                camera.takePicture(null, null, pictureCallback);
            } catch (Exception e) {
                Log.d(TAG, "takePhoto " + e);
            }
        }
    }

    public void startPreview() {
        if (camera != null) {
            camera.startPreview();
        }
    }

    public void onStart() {
        addCallback();
    }

    public void onStop() {

    }

    public void addCallback() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.addCallback(this);
        }
    }
    //直接对surfaceView进行matrix会对裁剪产生影响，需要对裁剪尺寸进行matrix变化
    public Matrix calculateSurfaceHolderTransform(Point screenResolution,Point cameraResolution) {
        // 预览 View 的大小，比如 SurfaceView
        int viewHeight = Math.min(screenResolution.x, screenResolution.y);
        int viewWidth = Math.max(screenResolution.x, screenResolution.y);
        // 相机选择的预览尺寸
        int cameraHeight = Math.min(cameraResolution.x, cameraResolution.y);
        int cameraWidth = Math.max(cameraResolution.x, cameraResolution.y);
        // 计算出将相机的尺寸 => View 的尺寸需要的缩放倍数
        float ratioPreview = (float) cameraWidth / cameraHeight;
        float ratioView = (float) viewWidth / viewHeight;
        float scaleX, scaleY;
        if (ratioView < ratioPreview) {
            scaleX = ratioPreview / ratioView;
            scaleY = 1;
        } else {
            scaleX = 1;
            scaleY = ratioView / ratioPreview;
        }
        // 计算出 View 的偏移量
        float scaledWidth = viewWidth * scaleX;
        float scaledHeight = viewHeight * scaleY;
        float dx = (viewWidth - scaledWidth) / 2;
        float dy = (viewHeight - scaledHeight) / 2;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleX, scaleY);
        matrix.postTranslate(dx, dy);

        return matrix;
    }

}
