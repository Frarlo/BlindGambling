package gov.ismonnet.blindgambling;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.opencv.android.JavaCameraView;

import static android.content.ContentValues.TAG;

public class RotatingCameraView extends JavaCameraView {

    private static final int MAGIC_TEXTURE_ID = 11;

    private final Activity mActivity;
    private SurfaceTexture mSurfaceTexture;

    public RotatingCameraView(Context context, int cameraId) {
        super(context, cameraId);
        this.mActivity = (Activity) context;
    }

    public RotatingCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mActivity = (Activity) context;
    }

    @Override
    @SuppressLint("ObsoleteSdkInt")
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        super.surfaceChanged(holder, format, width, height);

        // https://developer.android.com/guide/topics/media/camera#camera-preview
        // stop preview before making changes
        try {
            final boolean stopped = Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
            if (stopped)
                mCamera.stopPreview();

            // set preview size and make any resize, rotate or
            // reformatting changes here
            setCameraDisplayOrientation();

            // start preview with new settings
            if (stopped) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    if(mSurfaceTexture == null)
                        mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                    mCamera.setPreviewTexture(mSurfaceTexture);
                } else {
                    mCamera.setPreviewDisplay(null);
                }
                mCamera.startPreview();
            }
        } catch (Exception e){
            Log.d(TAG, "Error changing camera orientation");
            e.printStackTrace();
        }
    }

    protected void setCameraDisplayOrientation() {
        // Camera#setDisplayOrientation(int)

//        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
//        android.hardware.Camera.getCameraInfo(mCameraIndex, info);
//        final int cameraRotation = info.orientation;
        final int cameraRotation = mCamera.getParameters().getInt("rotation");
        final int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (mCameraIndex == CAMERA_ID_FRONT) {
            result = (cameraRotation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraRotation - degrees + 360) % 360;
        }

        mCamera.setDisplayOrientation(result);
    }
}
