package com.example.mobilednntest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private CameraDevice camera;
    private SurfaceView mCameraView;
    private SurfaceHolder mCameraHolder;
    private Camera mCamera;
    private boolean recording = false;
    private Bitmap bitmap;
    private ImageView resultimageView;
    private MainActivity activity;

    public CameraSurfaceView(Context context) {
        super(context);
        activity = (MainActivity) context;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera){
        try {
            Camera.Parameters parameters = camera.getParameters();
            int imageFormat = parameters.getPreviewFormat();
            Bitmap bitmap = null;

            if (imageFormat == ImageFormat.NV21) {
                int w = parameters.getPreviewSize().width;
                int h = parameters.getPreviewSize().height;

                YuvImage yuvImage = new YuvImage(data, imageFormat, w, h, null);
                Rect rect = new Rect(0, 0, w, h);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(rect, 50, outputStream);
                bitmap = BitmapFactory.decodeByteArray(outputStream.toByteArray(), 0, outputStream.size());
            }
            else if (imageFormat == ImageFormat.JPEG || imageFormat == ImageFormat.RGB_565) {
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            }

            if(bitmap != null) {
                Bitmap finalBitmap = bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight());
                activity.runOnUiThread(new Runnable(){
                    @Override
                    public void run(){
                        if (activity.depth_flag) {
                            activity.depthShow(finalBitmap);
                        }
//                        ((ImageView) findViewById(R.id.result_image)).setImageBitmap(finalBitmap);
                    }
                });
            }
        } catch(Exception e){

        }
    }

    // Camera 기능
    public void init(SurfaceView surfaceView){
        System.out.println("init");
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        parameters.setPreviewSize(640, 360);
//        parameters.setPreviewFpsRange(10000,10000);
        mCamera.setParameters(parameters);
        parameters.setPreviewFpsRange(10, 10);
        //mCamera.setDisplayOrientation(90);

        // surfaceview setting
        mCameraView = surfaceView;
        mCameraHolder = mCameraView.getHolder();
        mCameraHolder.addCallback(this);
        mCameraHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        resultimageView = (ImageView) findViewById(R.id.result_image);
    }

    public void autofocus(){
        System.out.println("autofocus");
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {

            }
        });
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            System.out.println("surfaceCreated");
            if (mCamera == null) {
                mCamera.reconnect();
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            }
        } catch (IOException e) {
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        // View 가 존재하지 않을 때
        System.out.println("surfaceChanged");
        if (mCameraHolder.getSurface() == null) {
            return;
        }

        // 작업을 위해 잠시 멈춘다
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // 에러가 나더라도 무시한다.
        }



        // View 를 재생성한다.
        try {
            // 카메라 설정을 다시 한다.
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
//        parameters.setPreviewFpsRange(10000,10000);
            mCamera.setParameters(parameters);
            mCamera.setPreviewDisplay(mCameraHolder);
            mCamera.startPreview();
            mCamera.setPreviewCallback(this);
        } catch (Exception e) {
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            System.out.println("release");
        }
    }
}