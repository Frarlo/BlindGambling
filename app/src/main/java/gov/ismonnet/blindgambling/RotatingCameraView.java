package gov.ismonnet.blindgambling;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.opencv.android.JavaCameraView;
import org.opencv.core.Size;

import java.util.List;

import static android.content.ContentValues.TAG;

@SuppressWarnings("deprecation")
@SuppressLint("ObsoleteSdkInt")
public class RotatingCameraView extends JavaCameraView {

    private static final int MAGIC_TEXTURE_ID = 11;

    private final Activity mActivity;

    private int mCameraId;
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
    protected boolean initializeCamera(int width, int height) {
        final boolean ret = super.initializeCamera(width, height);
        if(!ret)
            return false;

        // Find the actual camera ID

        mCameraId = CAMERA_ID_ANY;

        final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
            Camera.getCameraInfo(camIdx, cameraInfo);

            if (mCameraIndex == CAMERA_ID_ANY && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = camIdx;
                break;
            }
            if (mCameraIndex == CAMERA_ID_BACK && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = camIdx;
                break;
            }
            if (mCameraIndex == CAMERA_ID_FRONT && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCameraId = camIdx;
                break;
            }
        }

        if(mCameraId == CAMERA_ID_ANY)
            Log.e(TAG, "Coudln't find the actual camera id");

        return true;
    }

    @Override
    @SuppressWarnings("SuspiciousNameCombination")
    protected Size calculateCameraFrameSize(List<?> supportedSizes, ListItemAccessor accessor, int surfaceWidth, int surfaceHeight) {

        final int actualCameraRotation = getCameraDisplayOrientation();
        if(actualCameraRotation == 90 || actualCameraRotation == 270) {
            final Size size = super.calculateCameraFrameSize(supportedSizes,
                    accessor,
                    surfaceHeight,
                    surfaceWidth);
            return new Size(size.height, size.width);
        }

        return super.calculateCameraFrameSize(supportedSizes,
                accessor,
                surfaceWidth,
                surfaceHeight);
    }

    @Override
    protected void AllocateCache() {
        super.AllocateCache();

        // This is called just before starting the camera preview,
        // so I can use it to set parameters

        final Camera.Parameters params = mCamera.getParameters();
        changeParams(params);
        mCamera.setParameters(params);
    }



    protected void changeParams(Camera.Parameters params) {
        final int actualCameraRotation = getCameraDisplayOrientation();
        mCamera.setDisplayOrientation(actualCameraRotation);
    }

    protected int getCameraDisplayOrientation() {
        // Camera#setDisplayOrientation(int)

        Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

        final int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();

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

        return result;
    }


}
