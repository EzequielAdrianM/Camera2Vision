package com.randolabs.ezequiel.camera2;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.randolabs.ezequiel.camera2.camera.Camera2Source;
import com.randolabs.ezequiel.camera2.camera.FaceGraphic;
import com.randolabs.ezequiel.camera2.databinding.ActivityMainBinding;
import com.randolabs.ezequiel.camera2.utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import com.randolabs.ezequiel.camera2.camera.CameraSource;
import com.randolabs.ezequiel.camera2.camera.CameraSourcePreview;
import com.randolabs.ezequiel.camera2.camera.GraphicOverlay;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Camera2 Vision";
    private Context context;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    // CAMERA VERSION ONE DECLARATIONS
    private CameraSource mCameraSource = null;

    // CAMERA VERSION TWO DECLARATIONS
    private Camera2Source mCamera2Source = null;

    // COMMON TO BOTH CAMERAS
    private FaceDetector previewFaceDetector = null;
    private FaceGraphic mFaceGraphic;
    private boolean wasActivityResumed = false;
    private boolean isRecordingVideo = false;
    private boolean flashEnabled = false;

    // DEFAULT CAMERA BEING OPENED
    private boolean usingFrontCamera = true;

    // MUST BE CAREFUL USING THIS VARIABLE.
    // ANY ATTEMPT TO START CAMERA2 ON API < 21 WILL CRASH.
    private boolean useCamera2 = false;

    private ActivityMainBinding binding;
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(binding.getRoot());
        context = getApplicationContext();

        if(checkGooglePlayAvailability()) {
            requestPermissionThenOpenCamera();

            binding.switchButton.setOnClickListener(v -> {
                if(usingFrontCamera) {
                    stopCameraSource();
                    createCameraSourceBack();
                    usingFrontCamera = false;
                } else {
                    stopCameraSource();
                    createCameraSourceFront();
                    usingFrontCamera = true;
                }
            });

            binding.flashButton.setOnClickListener(v -> {
                if(useCamera2) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if(flashEnabled) {
                            mCamera2Source.setFlashMode(Camera2Source.CAMERA_FLASH_OFF);
                            flashEnabled = false;
                            Toast.makeText(context, "FLASH OFF", Toast.LENGTH_SHORT).show();
                        } else {
                            mCamera2Source.setFlashMode(Camera2Source.CAMERA_FLASH_ON);
                            flashEnabled = true;
                            Toast.makeText(context, "FLASH ON", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    if(flashEnabled) {
                        mCameraSource.setFlashMode(CameraSource.CAMERA_FLASH_OFF);
                        flashEnabled = false;
                        Toast.makeText(context, "FLASH OFF", Toast.LENGTH_SHORT).show();
                    } else {
                        mCameraSource.setFlashMode(CameraSource.CAMERA_FLASH_ON);
                        flashEnabled = true;
                        Toast.makeText(context, "FLASH ON", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            binding.takePictureButton.setOnClickListener(v -> {
                binding.switchButton.setEnabled(false);
                binding.videoButton.setEnabled(false);
                binding.takePictureButton.setEnabled(false);
                if(useCamera2) {
                    if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mCamera2Source.takePicture(camera2SourceShutterCallback, camera2SourcePictureCallback);
                    }
                } else {
                    if(mCameraSource != null)mCameraSource.takePicture(cameraSourceShutterCallback, cameraSourcePictureCallback);
                }
            });

            binding.videoButton.setOnClickListener(v -> {
                binding.switchButton.setEnabled(false);
                binding.takePictureButton.setEnabled(false);
                binding.videoButton.setEnabled(false);
                if(isRecordingVideo) {
                    if(useCamera2) {
                        if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mCamera2Source.stopVideo();
                        }
                    } else {
                        if(mCameraSource != null)mCameraSource.stopVideo();
                    }
                } else {
                    if(useCamera2){
                        if(mCamera2Source != null) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mCamera2Source.recordVideo(camera2SourceVideoStartCallback, camera2SourceVideoStopCallback, camera2SourceVideoErrorCallback, formatter.format(new Date())+".mp4", true);
                        }
                    } else {
                        if(mCameraSource != null) {
                            if(mCameraSource.canRecordVideo(CamcorderProfile.QUALITY_720P)) {
                                mCameraSource.recordVideo(cameraSourceVideoStartCallback, cameraSourceVideoStopCallback, cameraSourceVideoErrorCallback, formatter.format(new Date())+".mp4", true);
                            }
                        }
                    }
                }
            });

            binding.mPreview.setOnTouchListener(CameraPreviewTouchListener);
        }
    }

    final CameraSource.ShutterCallback cameraSourceShutterCallback = () -> {
        //you can implement here your own shutter triggered animation
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.status.setText("shutter event triggered");
                Log.d(TAG, "Shutter Callback!");
            }
        });
    };
    final CameraSource.PictureCallback cameraSourcePictureCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(Bitmap picture) {
            Log.d(TAG, "Taken picture is ready!");
            runOnUiThread(() -> {
                binding.status.setText("picture taken");
                binding.switchButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera_picture.png"));
                picture.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    final CameraSource.VideoStartCallback cameraSourceVideoStartCallback = new CameraSource.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(() -> {
                binding.status.setText("video recording started");
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.stop_video));
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    final CameraSource.VideoStopCallback cameraSourceVideoStopCallback = new CameraSource.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording stopped");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    final CameraSource.VideoErrorCallback cameraSourceVideoErrorCallback = new CameraSource.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording error");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };
    final Camera2Source.VideoStartCallback camera2SourceVideoStartCallback = new Camera2Source.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(() -> {
                binding.status.setText("video recording started");
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.stop_video));
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    final Camera2Source.VideoStopCallback camera2SourceVideoStopCallback = new Camera2Source.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording stopped");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    final Camera2Source.VideoErrorCallback camera2SourceVideoErrorCallback = new Camera2Source.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(() -> {
                binding.status.setText("video recording error");
                binding.switchButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.videoButton.setText(getString(R.string.record_video));
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };
    final Camera2Source.ShutterCallback camera2SourceShutterCallback = () -> {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.status.setText("shutter event triggered");
                Log.d(TAG, "Shutter Callback for CAMERA2");
            }
        });
    };
    final Camera2Source.PictureCallback camera2SourcePictureCallback = new Camera2Source.PictureCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onPictureTaken(Bitmap image) {
            Log.d(TAG, "Taken picture is ready!");
            runOnUiThread(() -> {
                binding.status.setText("picture taken");
                binding.switchButton.setEnabled(true);
                binding.videoButton.setEnabled(true);
                binding.takePictureButton.setEnabled(true);
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera2_picture.png"));
                image.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    final Camera2Source.CameraError camera2SourceErrorCallback = new Camera2Source.CameraError() {
        @Override
        public void onCameraOpened() {
            runOnUiThread(() -> binding.status.setText("camera2 open success"));
        }
        @Override
        public void onCameraDisconnected() {}
        @Override
        public void onCameraError(int errorCode) {
            runOnUiThread(() -> {
                binding.status.setText(String.format(getString(R.string.errorCode)+" ", errorCode));
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setCancelable(false);
                builder.setTitle(getString(R.string.cameraError));
                builder.setMessage(String.format(getString(R.string.errorCode)+" ", errorCode));
                builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    binding.switchButton.setEnabled(false);
                    binding.takePictureButton.setEnabled(false);
                    binding.videoButton.setEnabled(false);
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            });
        }
    };

    private boolean checkGooglePlayAvailability() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        if(resultCode == ConnectionResult.SUCCESS) {
            binding.status.setText("google play is available");
            return true;
        } else {
            if(googleApiAvailability.isUserResolvableError(resultCode)) {
                Objects.requireNonNull(googleApiAvailability.getErrorDialog(MainActivity.this, resultCode, 2404)).show();
            }
        }
        return false;
    }

    private void requestPermissionThenOpenCamera() {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                useCamera2 = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
                createCameraSourceFront();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        } else {
            binding.status.setText("requesting camera permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void createCameraSourceFront() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        } else {
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
            binding.status.setText("face detector not available");
        }

        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                        .setFocusMode(Camera2Source.CAMERA_AF_CONTINUOUS_PICTURE)
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(Camera2Source.CAMERA_FACING_FRONT)
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if(mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
                }
            }
        } else {
            mCameraSource = new CameraSource.Builder(context, previewFaceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_FRONT)
                    .setFlashMode(CameraSource.CAMERA_FLASH_AUTO)
                    .setFocusMode(CameraSource.CAMERA_FOCUS_MODE_CONTINUOUS_PICTURE)
                    .setRequestedFps(30.0f)
                    .build();

            startCameraSource();
        }
    }

    private void createCameraSourceBack() {
        previewFaceDetector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setMode(FaceDetector.FAST_MODE)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(true)
                .build();

        if(previewFaceDetector.isOperational()) {
            previewFaceDetector.setProcessor(new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory()).build());
        } else {
            binding.status.setText("face detector not available");
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
        }

        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCamera2Source = new Camera2Source.Builder(context, previewFaceDetector)
                        .setFocusMode(Camera2Source.CAMERA_AF_AUTO)
                        .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                        .setFacing(Camera2Source.CAMERA_FACING_BACK)
                        .build();

                //IF CAMERA2 HARDWARE LEVEL IS LEGACY, CAMERA2 IS NOT NATIVE.
                //WE WILL USE CAMERA1.
                if(mCamera2Source.isCamera2Native()) {
                    startCameraSource();
                } else {
                    useCamera2 = false;
                    if(usingFrontCamera) createCameraSourceFront(); else createCameraSourceBack();
                }
            }
        } else {
            mCameraSource = new CameraSource.Builder(context, previewFaceDetector)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setFocusMode(CameraSource.CAMERA_FOCUS_MODE_CONTINUOUS_PICTURE)
                    .setFlashMode(CameraSource.CAMERA_FLASH_AUTO)
                    .setRequestedFps(30.0f)
                    .build();

            startCameraSource();
        }
    }

    private void startCameraSource() {
        if(useCamera2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mCamera2Source != null) {
                    binding.cameraVersion.setText(context.getString(R.string.cameraTwo));
                    binding.mPreview.start(mCamera2Source, binding.mGraphicOverlay, camera2SourceErrorCallback);
                }
            }
        } else {
            if (mCameraSource != null) {
                binding.cameraVersion.setText(context.getString(R.string.cameraOne));
                binding.mPreview.start(mCameraSource, binding.mGraphicOverlay);
            }
        }
    }

    private void stopCameraSource() {
        binding.mPreview.stop();
    }

    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(@NonNull Face face) {
            return new GraphicFaceTracker(binding.mGraphicOverlay);
        }
    }

    private class GraphicFaceTracker extends Tracker<Face> {
        private final GraphicOverlay mOverlay;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, context);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, @NonNull Face item) {
            mFaceGraphic.setId(faceId);
            Log.d(TAG, "NEW FACE ID: "+faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(@NonNull FaceDetector.Detections<Face> detectionResults, @NonNull Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            Log.d(TAG, "NEW KNOWN FACE UPDATE: "+face.getId());
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(@NonNull FaceDetector.Detections<Face> detectionResults) {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
            Log.d(TAG, "FACE MISSING");
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mFaceGraphic.goneFace();
            mOverlay.remove(mFaceGraphic);
            mOverlay.clear();
            Log.d(TAG, "FACE GONE");
        }
    }

    private final CameraSourcePreview.OnTouchListener CameraPreviewTouchListener = new CameraSourcePreview.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent pEvent) {
            v.onTouchEvent(pEvent);
            if (pEvent.getAction() == MotionEvent.ACTION_DOWN) {
                int autoFocusX = (int) (pEvent.getX() - Utils.dpToPx(60)/2);
                int autoFocusY = (int) (pEvent.getY() - Utils.dpToPx(60)/2);
                binding.ivAutoFocus.setTranslationX(autoFocusX);
                binding.ivAutoFocus.setTranslationY(autoFocusY);
                binding.ivAutoFocus.setVisibility(View.VISIBLE);
                binding.ivAutoFocus.bringToFront();
                binding.status.setText("focusing...");
                if(useCamera2) {
                    if(mCamera2Source != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            //needs to know in which zone of the screen is auto focus requested
                            // some Camera2 devices support multi-zone focusing.
                            mCamera2Source.autoFocus(success -> runOnUiThread(() -> {
                                binding.ivAutoFocus.setVisibility(View.GONE);
                                binding.status.setText("focus OK");
                            }), pEvent, v.getWidth(), v.getHeight());
                        }
                    } else {
                        binding.ivAutoFocus.setVisibility(View.GONE);
                    }
                } else {
                    if(mCameraSource != null) {
                        mCameraSource.autoFocus(success -> runOnUiThread(() -> {
                            binding.ivAutoFocus.setVisibility(View.GONE);
                            binding.status.setText("focus OK");
                        }));
                    } else {
                        binding.ivAutoFocus.setVisibility(View.GONE);
                    }
                }
            }
            if(pEvent.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return false;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "CAMERA PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        if(requestCode == REQUEST_STORAGE_PERMISSION) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "STORAGE PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(wasActivityResumed)
            //If the CAMERA2 is paused then resumed, it won't start again unless creating the whole camera again.
            if(useCamera2) {
                if(usingFrontCamera) {
                    createCameraSourceFront();
                } else {
                    createCameraSourceBack();
                }
            } else {
                startCameraSource();
            }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasActivityResumed = true;
        stopCameraSource();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraSource();
        if(previewFaceDetector != null) {
            previewFaceDetector.release();
        }
    }
}