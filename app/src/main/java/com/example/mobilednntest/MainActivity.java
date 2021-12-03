package com.example.mobilednntest;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity{
    private static int RESULT_LOAD_IMAGE = 1;
    CameraSurfaceView cameraSurfaceView;
    private static final int SINGLE_PERMISSION = 1004; //권한 변수
    Module module_detection = null; // detection 모델
    Module module_depth = null; // depth 모델
    private Bitmap raw_bitmap;
    public boolean depth_flag = true;

    //
    public void depthShow(Bitmap rawbitmap){
        if (module_depth != null) {
            depth_flag = false;
            ((TextView) findViewById(R.id.depth_text)).setText("");
            raw_bitmap = rawbitmap;
            Bitmap bitmap = null;
            int width = rawbitmap.getWidth();
            int height = rawbitmap.getHeight();
//            System.out.printf("%d %d\n", width, height);
            bitmap = Bitmap.createBitmap(rawbitmap, 0, 84, 360, 192);
            bitmap = Bitmap.createScaledBitmap(bitmap, 360, 192, true);
            //Input Tensor
            final Tensor input = TensorImageUtils.bitmapToFloat32Tensor(
                    bitmap,
                    TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                    TensorImageUtils.TORCHVISION_NORM_STD_RGB
            );

            //Calling the forward of the model to run our input
            System.out.printf("1\n");
            final Tensor output = module_depth.forward(IValue.from(input)).toTensor();
            System.out.printf("2\n");
            final float[] deptharray = output.getDataAsFloatArray();
            Bitmap depthbitmap = arrayFlotToBitmap(deptharray, 360, 192);

            Bitmap finaldepthbitmap = Bitmap.createScaledBitmap(depthbitmap, width, height,true);

            ((ImageView) findViewById(R.id.result_image)).setImageBitmap(finaldepthbitmap);
            depth_flag = true;
        }
        else{
            ((TextView) findViewById(R.id.depth_text)).setText("Please upload model");
        }
    }
    private Bitmap arrayFlotToBitmap(float[] floatArray,int width,int height){

        byte alpha = (byte) 255 ;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4) ;

        int i = 0 ;
        while (i<floatArray.length){
            byte temValue = (byte) Math.floor(floatArray[i]*255);
            byteBuffer.put(4*i, temValue) ;
            byteBuffer.put(4*i+1, temValue) ;
            byteBuffer.put(4*i+2, temValue) ;
            byteBuffer.put(4*i+3, alpha) ;
            i++ ;
        }
        bmp.copyPixelsFromBuffer(byteBuffer) ;
        return bmp ;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) { // on수Create 가 가장 먼저 실행되는 함
        super.onCreate(savedInstanceState);
        //권한이 있는지 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {//권한없음
            //권한 요청 코드
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, SINGLE_PERMISSION);
        } else {//권한있음

            /*..권한이 있는경우 실행할 코드....*/
            setContentView(R.layout.activity_main);

            Button loadModelButton = (Button) findViewById(R.id.loadModel_btn);
            Button detectButton = (Button) findViewById(R.id.detect_btn);
            Button autofocusButton = (Button) findViewById(R.id.autofocus_btn);

            SurfaceView mCameraView = (SurfaceView) findViewById(R.id.cameraView);
            cameraSurfaceView = new CameraSurfaceView(this);
            cameraSurfaceView.init(mCameraView);

            loadModelButton.setOnClickListener(new View.OnClickListener() {


                @Override
                public void onClick(View arg0) {
                    try {
                        System.out.println("detection model load");
                        module_detection = Module.load(fetchModelFile(MainActivity.this, "resnet18_traced.pt"));
                        System.out.println("depth model load");
                        module_depth = Module.load(fetchModelFile(MainActivity.this, "project_monodepth2_trace.pt"));
                        System.out.println("model load complete");
                    } catch (IOException e) {
                        finish();
                    }
                }
            });

            detectButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {

                    if (module_detection != null) {
                        Bitmap bitmap = null;

                        //Read the image as Bitmap
                        bitmap = raw_bitmap;
                        //Here we reshape the image into 400*400
                        bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

                        //Input Tensor
                        final Tensor input = TensorImageUtils.bitmapToFloat32Tensor(
                                bitmap,
                                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                                TensorImageUtils.TORCHVISION_NORM_STD_RGB
                        );
                        //Calling the forward of the model to run our input
                        final Tensor output = module_detection.forward(IValue.from(input)).toTensor();

                        final float[] score_arr = output.getDataAsFloatArray();

                        // Fetch the index of the value with maximum score
                        float max_score = -Float.MAX_VALUE;
                        int ms_ix = -1;
                        for (int i = 0; i < score_arr.length; i++) {
                            if (score_arr[i] > max_score) {
                                max_score = score_arr[i];
                                ms_ix = i;
                            }
                        }
                        //Fetching the name from the list based on the index
                        String detected_class = ModelClasses.MODEL_CLASSES[ms_ix];

                        //Writing the detected class in to the text view of the layout
                        TextView textView = findViewById(R.id.result_text);
                        textView.setText(detected_class);
                    }
                    else{
                        TextView textView = findViewById(R.id.result_text);
                        textView.setText("Please upload model");
                    }
                }
            });

            autofocusButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    cameraSurfaceView.autofocus();
                }
            });
        }


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //This functions return the selected image from gallery
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

//            // image 위치에 뿌려주는 것
//            ImageView imageView = (ImageView) findViewById(R.id.image);
//            imageView.setImageBitmap(BitmapFactory.decodeFile(picturePath));
//
//            //Setting the URI so we can read the Bitmap from the image
//            imageView.setImageURI(null);
//            imageView.setImageURI(selectedImage);

            // 일단 같은 이미지 뿌려줌
            ImageView resultimageView = (ImageView) findViewById(R.id.result_image);
            Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
            resultimageView.setImageBitmap(bitmap);
            System.out.println(bitmap.getHeight());
            System.out.println(bitmap.getWidth());
            System.out.println(bitmap.getConfig());

            //Setting the URI so we can read the Bitmap from the image
            resultimageView.setImageURI(null);
            resultimageView.setImageURI(selectedImage);

        }


    }

    // Pytorch model fetch
    public static String fetchModelFile(Context context, String modelName) throws IOException {
        File file = new File(context.getFilesDir(), modelName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(modelName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    //권한 요청에 대한 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case SINGLE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    /*권한이 있는경우 실행할 코드....*/
                } else {
                    // 하나라도 거부한다면.
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
                    alertDialog.setTitle("앱 권한");
                    alertDialog.setMessage("해당 앱의 원할한 기능을 이용하시려면 애플리케이션 정보>권한> 에서 모든 권한을 허용해 주십시오");
                    // 권한설정 클릭시 이벤트 발생
                    alertDialog.setPositiveButton("권한설정",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                                    startActivity(intent);
                                    dialog.cancel();
                                }
                            });
                    //취소
                    alertDialog.setNegativeButton("취소",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                    alertDialog.show();
                }
                return;
        }

    }
}