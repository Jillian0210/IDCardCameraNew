package com.pengbo.idcardcamera.test;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.pengbo.idcardcamera.camera.IDCardCamera;


public class MainActivity extends AppCompatActivity {
    private ImageView mIvFront;
    private ImageView mIvBack;
    private ImageView mIvBank;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIvFront = (ImageView) findViewById(R.id.iv_front);
        mIvBack = (ImageView) findViewById(R.id.iv_back);
        mIvBank = findViewById(R.id.iv_bank);
    }

    /**
     * 身份证正面
     */
    public void front(View view) {
        IDCardCamera.create(this).startLogWriteFile(true);
        IDCardCamera.create(this).openCamera(IDCardCamera.TYPE_IDCARD_FRONT,true,IDCardCamera.TYPE_IDCARD_FRONT);
    }

    /**
     * 身份证反面
     */
    public void back(View view) {
        IDCardCamera.create(this).startLogWriteFile(true);
        IDCardCamera.create(this).openCamera(IDCardCamera.TYPE_IDCARD_BACK,true,IDCardCamera.TYPE_IDCARD_BACK);
    }


    public void bank(View view){
        IDCardCamera.create(this).startLogWriteFile(true);
        IDCardCamera.create(this).openCamera(IDCardCamera.TYPE_BANK,true,IDCardCamera.TYPE_BANK);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == IDCardCamera.RESULT_OK) {
            //获取图片路径，显示图片
            final String path = IDCardCamera.getImagePath(data);
            if (!TextUtils.isEmpty(path)) {
                if (requestCode == IDCardCamera.TYPE_IDCARD_FRONT) { //身份证正面
                    mIvFront.setImageBitmap(BitmapFactory.decodeFile(path));
                } else if (requestCode == IDCardCamera.TYPE_IDCARD_BACK) {  //身份证反面
                    mIvBack.setImageBitmap(BitmapFactory.decodeFile(path));
                }else if (requestCode==IDCardCamera.TYPE_BANK){
                    //银行卡
                    mIvBank.setImageBitmap(BitmapFactory.decodeFile(path));
                }

                //实际开发中将图片上传到服务器成功后需要删除全部缓存图片
//                FileUtils.clearCache(this);
            }
        }
    }
}
