# IDCardCamera
[![](https://jitpack.io/v/Jillian0210/IDCardCameraNew.svg)](https://jitpack.io/#Jillian0210/IDCardCameraNew)

Android 自定义身份证相机

## 效果图
拍照
![拍照](
https://github.com/Jillian0210/IDCardCameraNew/raw/master/screenshot/Screenshot_camera.jpg
)
选择图片裁剪
![选择图片](
https://github.com/Jillian0210/IDCardCameraNew/raw/master/screenshot/Screenshot_choose_img.jpg
)

## APK
[点击下载APK](https://github.com/Jillian0210/IDCardCameraNew/raw/master/apk/app-release.apk)

## 功能特点
- 自定义相机界面,自动裁剪
- 支持相册选择图片,使用ucrop裁剪


## 使用
### Step 1. 添加 JitPack 仓库
在项目的 build.gradle 添加 JitPack 仓库
```
	allprojects {
		repositories {
			//...
			maven { url 'https://jitpack.io' }
		}
	}
```

### Step 2. 添加依赖
在需要使用的 module 中添加依赖
```
	dependencies {
	        implementation'com.github.Jillian0210:IDCardCameraNew:v2.0.0'
	}
```

### Step 3. 打开拍照界面
- 身份证正面
```
IDCardCamera.create(this).openCamera(IDCardCamera.TYPE_IDCARD_FRONT);
```
- 身份证反面
```
IDCardCamera.create(this).openCamera(IDCardCamera.TYPE_IDCARD_BACK);
```
**注意：** create() 方法的参数传的是上下文，在 Activity 中传 activity.this，在 Fragment 中传 fragment.this

### Step 4. 在 onActivityResult 方法中获取裁剪后的图片
```
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	if (resultCode == IDCardCamera.RESULT_CODE) {
		//获取图片路径，显示图片
		final String path = IDCardCamera.getImagePath(data);
		if (!TextUtils.isEmpty(path)) {
			if (requestCode == IDCardCamera.TYPE_IDCARD_FRONT) { //身份证正面
				mIvFront.setImageBitmap(BitmapFactory.decodeFile(path));
			} else if (requestCode == IDCardCamera.TYPE_IDCARD_BACK) {  //身份证反面
				mIvBack.setImageBitmap(BitmapFactory.decodeFile(path));
			}
		}
	}
}
```

### 清理缓存
实际开发中将图片上传到服务器成功后需要删除全部缓存图片，调用如下方法即可：
```java
FileUtils.clearCache(this);
```
## 感谢
- [IDCardCamera](https://github.com/wildma/IDCardCamera) 
- [ucrop](https://github.com/Yalantis/uCrop)



