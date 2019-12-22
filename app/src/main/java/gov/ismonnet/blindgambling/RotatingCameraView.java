package gov.ismonnet.blindgambling;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.lang.reflect.Field;
import java.util.List;

import static android.content.ContentValues.TAG;

@SuppressWarnings("deprecation")
@SuppressLint("ObsoleteSdkInt")
public class RotatingCameraView extends JavaCameraView {

    protected final Activity mActivity;

    protected int mCameraId;
    protected int mLastDisplayRotation;
    protected int mActualCameraRotation;

    protected Bitmap mCacheBitmap;
    protected CvCameraViewListener2 mListener;

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
            Log.e(TAG, "Couldn't find the actual camera id");

        return true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);

        // Calculate the scale correctly

        final boolean rotated = mActualCameraRotation == 90 || mActualCameraRotation == 270;
        if (rotated && mScale != 0)
            mScale = Math.min((float) getWidth() / mFrameHeight, (float) getHeight() / mFrameWidth);
    }

    @Override
    @SuppressWarnings("SuspiciousNameCombination")
    protected Size calculateCameraFrameSize(List<?> supportedSizes,
                                            ListItemAccessor accessor,
                                            int surfaceWidth,
                                            int surfaceHeight) {

        mActualCameraRotation = getCameraDisplayOrientation();
        if(mActualCameraRotation == 90 || mActualCameraRotation == 270)
            return super.calculateCameraFrameSize(supportedSizes,
                    accessor,
                    surfaceHeight,
                    surfaceWidth);
        return super.calculateCameraFrameSize(supportedSizes,
                accessor,
                surfaceWidth,
                surfaceHeight);
    }

    @Override
    protected void AllocateCache() {
        super.AllocateCache();

        // Get access to the allocated cache

        try {
            final Class<CameraBridgeViewBase> clazz = CameraBridgeViewBase.class;
            final Field field = clazz.getDeclaredField("mCacheBitmap");
            field.setAccessible(true);
            mCacheBitmap = (Bitmap) field.get(this);
        } catch (Exception ex) {
            Log.e(TAG, "Couldn't get underlying bitmap instance using reflections", ex);
            mCacheBitmap = null;
        }

        // This is called just before starting the camera preview,
        // so I can use it to set parameters

        final Camera.Parameters params = mCamera.getParameters();
        changeParams(params);
        mCamera.setParameters(params);
    }

    protected int getCameraDisplayOrientation() {
        // Camera#setDisplayOrientation(int)

        Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

        final int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        mLastDisplayRotation = rotation;

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

    @Override
    protected void deliverAndDrawFrame(CvCameraViewFrame frame) {

        Canvas canvas = getHolder().lockCanvas();
        if (canvas == null)
            return;

        final Mat modified;
        if (mListener != null) {
            modified = mListener.onCameraFrame(frame);
        } else {
            modified = frame.rgba();
        }

        boolean bmpValid = true;
        if (modified != null) {
            try {
                Utils.matToBitmap(modified, mCacheBitmap);
            } catch(Exception e) {
                Log.e(TAG, "Mat type: " + modified);
                Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
                bmpValid = false;
            }
        }

        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        if (!bmpValid && mCacheBitmap == null)
            return;

        // When rotating the screen upside down, the surface is the same size
        // so surfaceChanged does not get invoked and the camera orientation is still the old one
        // This forcefully sets it
        final int display = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        if(mLastDisplayRotation != display)
            mActualCameraRotation = getCameraDisplayOrientation();

        final float scale = mScale == 0 ? 1 : mScale;

        final boolean rotate = mActualCameraRotation == 90 || mActualCameraRotation == 270;
        final float width = !rotate ? getWidth() : getHeight();
        final float height = !rotate ? getHeight() : getWidth();

        final int xToDraw = (int)((width - scale * mCacheBitmap.getWidth()) / 2);
        final int yToDraw = (int)((height - scale * mCacheBitmap.getHeight()) / 2);
        final int wToDraw = (int)((width - scale * mCacheBitmap.getWidth()) / 2 + scale * mCacheBitmap.getWidth());
        final int hToDraw = (int)((height - scale * mCacheBitmap.getHeight()) / 2 + scale * mCacheBitmap.getHeight());

        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);

        if(rotate) {
            canvas.translate(canvas.getWidth(), 0);
            canvas.rotate(mActualCameraRotation);
        } else {
            canvas.rotate(mActualCameraRotation,
                    getWidth() / 2f,
                    getHeight() / 2f);
        }

        canvas.drawBitmap(mCacheBitmap,
                new Rect(0, 0, mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
                new Rect(xToDraw, yToDraw, xToDraw + wToDraw, yToDraw + hToDraw),
                null);

        if (mFpsMeter != null) {
            mFpsMeter.measure();
            mFpsMeter.draw(canvas, 20, 30);
        }

        getHolder().unlockCanvasAndPost(canvas);
    }

    @Override
    public void setCvCameraViewListener(CvCameraViewListener2 listener) {
        super.setCvCameraViewListener(listener);
        mListener = getSuperListener();
    }

    @Override
    public void setCvCameraViewListener(CvCameraViewListener listener) {
        super.setCvCameraViewListener(listener);
        mListener = getSuperListener();
    }

    protected CvCameraViewListener2 getSuperListener() {
        try {
            final Class<CameraBridgeViewBase> clazz = CameraBridgeViewBase.class;
            final Field field = clazz.getDeclaredField("mListener");
            field.setAccessible(true);
            return (CvCameraViewListener2) field.get(this);
        } catch (Exception ex) {
            Log.e(TAG, "Couldn't get underlying CvCameraViewListener2 instance using reflections", ex);
            return null;
        }
    }

    protected void changeParams(Camera.Parameters params) {
        mCamera.setDisplayOrientation(mActualCameraRotation);
    }
}
