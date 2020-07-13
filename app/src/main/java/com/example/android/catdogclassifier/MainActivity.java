package com.example.android.catdogclassifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {

    Interpreter interpreter;
    TextView classified;
    ImageView imageView;
    Button capture,loadImage;
    final int IMAGE_CAPTURE_REQUEST=1;
    final int IMAGE_SHAPE=224;
    final int IMAGE_LOAD_REQUEST=2;
    final int READ_STORAGE_PERMISSION=3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            interpreter = new Interpreter(loadModel());
        } catch (IOException e) {
            e.printStackTrace();
        }

        classified=(TextView)findViewById(R.id.classify);
        imageView=(ImageView)findViewById(R.id.image);
        capture=(Button)findViewById(R.id.capture);
        loadImage=(Button)findViewById(R.id.load_image);

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if(intent.resolveActivity(getPackageManager())!=null)
                    startActivityForResult(intent,IMAGE_CAPTURE_REQUEST);
            }
        });

        loadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                    Intent intent=new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent,IMAGE_LOAD_REQUEST);
                }else {
                    ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},READ_STORAGE_PERMISSION);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==READ_STORAGE_PERMISSION){
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                Intent intent=new Intent(Intent.ACTION_VIEW,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent,IMAGE_LOAD_REQUEST);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==IMAGE_CAPTURE_REQUEST && resultCode==RESULT_OK){
            Bitmap bitmap=(Bitmap)data.getExtras().get("data");
            new AsyncClassify().execute(bitmap);
            bitmap=Bitmap.createScaledBitmap(bitmap,550,700,true);
            imageView.setImageBitmap(bitmap);
        }
        if(requestCode==IMAGE_LOAD_REQUEST && resultCode==RESULT_OK && data!=null){
            Uri selectedImage=data.getData();
            String[] filePathColumn={MediaStore.Images.Media.DATA};
            Cursor cursor=getContentResolver().query(selectedImage,filePathColumn,null,null,null);
            cursor.moveToFirst();
            String picturePath=cursor.getString(cursor.getColumnIndex(filePathColumn[0]));
            cursor.close();
            Bitmap bitmap=BitmapFactory.decodeFile(picturePath);
            new AsyncClassify().execute(bitmap);
            bitmap=Bitmap.createScaledBitmap(bitmap,550,700,true);
            imageView.setImageBitmap(bitmap);
        }
    }

    MappedByteBuffer loadModel() throws IOException {
        AssetFileDescriptor fileDescriptor=getAssets().openFd("model.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();

        long startOFFSet=fileDescriptor.getStartOffset(),declaredLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOFFSet,declaredLength);
    }

    String classifyPicture(Bitmap bitmap){
        bitmap=Bitmap.createScaledBitmap(bitmap,IMAGE_SHAPE,IMAGE_SHAPE,true);
        ByteBuffer input=ByteBuffer.allocateDirect(IMAGE_SHAPE*IMAGE_SHAPE*3*4).order(ByteOrder.nativeOrder());
        for(int i=0;i<IMAGE_SHAPE;i++){
            for(int j=0;j<IMAGE_SHAPE;j++){
                int px=bitmap.getPixel(j,i);

                int r= Color.red(px);
                int g=Color.green(px);
                int b=Color.blue(px);

                float rf=r/255.0f;
                float rg=g/255.0f;
                float rb=b/255.0f;

                input.putFloat(rf);
                input.putFloat(rg);
                input.putFloat(rb);
            }
        }
        int bufferSize = 1000 * java.lang.Float.SIZE / java.lang.Byte.SIZE;
        ByteBuffer modelOutput = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
        interpreter.run(input, modelOutput);

        modelOutput.rewind();
        FloatBuffer probabilities = modelOutput.asFloatBuffer();
        String finalLabel="";
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("labels.txt")));
            for (int i = 0; i < probabilities.capacity(); i++) {
                String label = reader.readLine();
                float probability = probabilities.get(i);
                if(probability>=0.5)
                    finalLabel=label;
            }
        } catch (IOException e) {
            // File not found?
        }
        return finalLabel;
    }

    private class AsyncClassify extends AsyncTask<Bitmap,Void,String>{
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            classified.setText(s);
        }

        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            return classifyPicture(bitmaps[0]);
        }
    }
}
