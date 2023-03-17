package com.yersuaksu.twt;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigestSpi;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;

import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

public class MainActivity extends ActionBarActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new MainFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout:
                MSTwitter.clearCredentials(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        MainFragment mainFragment = (MainFragment)getSupportFragmentManager().findFragmentById(R.id.container);
        mainFragment.onFragmentResult(requestCode, resultCode, data);
    }


    public static class MainFragment extends Fragment implements Camera.PictureCallback {

        static String TWITTER_CONSUMER_KEY = "0lcHEGQeC3gDy3ckUDHbqfahv";
        static String TWITTER_CONSUMER_SECRET = "biHEmIohIvB7Mke6rP29eP8N6ZYmKWLnpfAOtabvmABhiwsZNG";

        private ProgressDialog mDialog;
        private Camera mCamera;
        private ImageView mPhotoDisplay;
        private Button mTakePicture;
        private Button mRetakeButton;
        private LinearLayout mWrapper;
        private Button mUploadButton;
        private Bitmap mCurrentPhoto;
        private MSTwitter mMSTwitter;
        private SurfaceView mCameraSurface;
        private String mTweetMessage;
        private boolean mCurrentlyTweeting;

        public MainFragment() {
        }

        @Override
        public void onPause(){
            super.onPause();
            try{
                mCamera.release();
                mWrapper.removeView(mCameraSurface);
                mCameraSurface = null;
            }catch(Exception e){
                e.printStackTrace();
            }
        }


        protected void onFragmentResult(int requestCode, int resultCode, Intent data){
            mMSTwitter.onCallingActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void onResume(){
            super.onResume();
            initCamera();
        }

        private void openCamera(){
            try{
                mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        private void tweet() {
            String tweetImagePath = MSTwitter.putBitmapInDiskCache(this.getActivity(), mCurrentPhoto);

            // start the tweet
            mMSTwitter.startTweet(mTweetMessage, tweetImagePath);

        }

        private void handleFinish(String message){
            mDialog.dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(message).setPositiveButton("ok",new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
            setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_FRONT, mCamera);
            mCurrentlyTweeting = false;
            startPreview();
        }

        private void handleTweetMessage(int event, String message) {

            String note = "";
            switch (event) {
                case MSTwitter.MSTWEET_STATUS_AUTHORIZING:
                    note = "Authorizing app with twitter.com";
                    break;
                case MSTwitter.MSTWEET_STATUS_STARTING:
                    note = "Tweet data send started";
                    break;
                case MSTwitter.MSTWEET_STATUS_FINSIHED_SUCCCESS:
                    note = "Tweet sent successfully";
                    handleFinish(note);
                    break;
                case MSTwitter.MSTWEET_STATUS_FINSIHED_FAILED:
                    note = "Tweet failed:" + message;
                    handleFinish(note);
                    break;
            }

            // add note to results TextView
            SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm:ss.S");
            String timeS = timeFmt.format(new Date());

            Log.d("Photo Upload","\n[Message received at " + timeS +"]\n" + note);

        }


        private void initCamera(){
            openCamera();
            setCameraDisplayOrientation(getActivity(), Camera.CameraInfo.CAMERA_FACING_FRONT, mCamera);
            mCameraSurface = new SurfaceView(getActivity());
            mCameraSurface.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mWrapper.addView(mCameraSurface);
            SurfaceHolder previewHolder = mCameraSurface.getHolder();
            previewHolder.addCallback(surfaceCallback);

            try {
                mCamera.setPreviewDisplay(previewHolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            mCurrentlyTweeting = false;
            mTweetMessage = "";
            mPhotoDisplay = (ImageView) rootView.findViewById(R.id.photo);
            mUploadButton = (Button) rootView.findViewById(R.id.upload_picture);
            mRetakeButton = (Button) rootView.findViewById(R.id.take_new_picture);
            mWrapper = (LinearLayout) rootView.findViewById(R.id.wrapper);

            // make a MSTwitter event handler to receive tweet send events
            MSTwitter.MSTwitterResultReceiver myMSTReceiver = new MSTwitter.MSTwitterResultReceiver() {
                @Override
                public void onRecieve(int tweetLifeCycleEvent, String tweetMessage) {
                    handleTweetMessage(tweetLifeCycleEvent, tweetMessage);
                }
            };

            mMSTwitter = new MSTwitter(getActivity(), TWITTER_CONSUMER_KEY, TWITTER_CONSUMER_SECRET, myMSTReceiver);


            mUploadButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    if (!mCurrentlyTweeting){
                        mCurrentlyTweeting = true;
                        // show  loading dialog
                        mDialog = ProgressDialog.show(getActivity(), "",
                                "Uploading Photo. Please wait...", true);
                        mDialog.show();
                        tweet();
                    }
                }
            });

            mRetakeButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    startPreview();
                    mRetakeButton.setVisibility(View.GONE);
                    mTakePicture.setText(getResources().getString(R.string.take_photo));
                }
            });


            mTakePicture = (Button) rootView.findViewById(R.id.take_picture);
            mTakePicture.setOnClickListener(new View.OnClickListener(){
                public void onClick(View view){
                    mTakePicture.setText("3");

                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mTakePicture.setText(getResources().getString(R.string.countdown_2));
                        }
                    }, 1000);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mTakePicture.setText(getResources().getString(R.string.countdown_1));
                        }
                    }, 2000);
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mCamera.takePicture(null, null, null, MainFragment.this);
                        }
                    }, 3000);
                }
            });
            return rootView;
        }

        private void initPreview(int width, int height) {
            if (mCamera!=null && mCameraSurface.getHolder().getSurface()!=null) {
                try {
                    mCamera.setPreviewDisplay(mCameraSurface.getHolder());
                }
                catch (Throwable t) {
                    Log.e("PreviewDemo-surfaceCallback",
                            "Exception in setPreviewDisplay()", t);
                }

            }
        }


        private void setCameraDisplayOrientation(Activity activity,
                                                 int cameraId, android.hardware.Camera camera) {
            android.hardware.Camera.CameraInfo info =
                    new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation = activity.getWindowManager().getDefaultDisplay()
                    .getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            Camera.Parameters params = camera.getParameters();
            params.setRotation(result);
            camera.setDisplayOrientation(result);
        }

        private void startPreview() {
            mCameraSurface.setVisibility(View.VISIBLE);
            mPhotoDisplay.setVisibility(View.GONE);
            mUploadButton.setVisibility(View.GONE);
            mRetakeButton.setVisibility(View.GONE);
            mTakePicture.setVisibility(View.VISIBLE);
            mTakePicture.setText(getResources().getString(R.string.take_photo));
            if (mCamera!=null) {
                mCamera.startPreview();
            }
        }

        @Override
        public void onPictureTaken(byte[] bytes, Camera camera) {

            BitmapFactory.Options options = new BitmapFactory.Options();

            mCurrentPhoto = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length,options);

            mCameraSurface.setVisibility(View.GONE);
            mTakePicture.setVisibility(View.GONE);
            mUploadButton.setVisibility(View.VISIBLE);
            mPhotoDisplay.setVisibility(View.VISIBLE);
            mRetakeButton.setVisibility(View.VISIBLE);
            mPhotoDisplay.setImageBitmap(mCurrentPhoto);
        }

        SurfaceHolder.Callback surfaceCallback=new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                // no-op -- wait until surfaceChanged()
            }

            public void surfaceChanged(SurfaceHolder holder,
                                       int format, int width,
                                       int height) {
                initPreview(width, height);
                startPreview();
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
                // no-op
            }
        };

        public void setTweetMessage(String message){
            mTweetMessage = message;
        }

    }
}
