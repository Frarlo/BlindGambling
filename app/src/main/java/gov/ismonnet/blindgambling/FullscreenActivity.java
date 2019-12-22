package gov.ismonnet.blindgambling;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.ContentValues.TAG;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.RETR_TREE;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.approxPolyDP;
import static org.opencv.imgproc.Imgproc.arcLength;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.rectangle;
import static org.opencv.imgproc.Imgproc.threshold;

public class FullscreenActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private CameraBridgeViewBase openCvCamera;
    private BaseLoaderCallback openCvLoaderCallback;

    private Mat thresholdMat;

    private List<MatOfPoint> contours;
    private Mat hierarchy;

    private MatOfPoint2f contour2f;
    private MatOfPoint2f approx2f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        openCvCamera = (RotatingCameraView) findViewById(R.id.CameraView);
        openCvCamera.setVisibility(SurfaceView.VISIBLE);
        openCvCamera.setCvCameraViewListener(this);

        openCvLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                if (status == BaseLoaderCallback.SUCCESS) {
                    openCvCamera.enableView();
                    return;
                }
                super.onManagerConnected(status);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Hide navigation bar

        this.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        // Init OpenCV

        if(!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Couldn't find internal OpenCV library. Attempting to load it using OpenCV Engine service.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0,
                    this,
                    openCvLoaderCallback);
        } else {
            openCvLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(openCvCamera != null)
            openCvCamera.disableView();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(openCvCamera != null)
            openCvCamera.disableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(openCvCamera);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for(int i = 0; i < permissions.length; i++)
            if(permissions[i].equals(Manifest.permission.CAMERA) &&
                    grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Log.d(TAG, "The user denied access to the camera");
                // TODO: insult the user not giving camera permission
            }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        thresholdMat = new Mat();

        contours = new ArrayList<>();
        hierarchy = new Mat();

        contour2f = new MatOfPoint2f();
        approx2f = new MatOfPoint2f();
    }

    @Override
    public void onCameraViewStopped() {
        thresholdMat.release();

        contours.clear();
        hierarchy.release();

        contour2f.release();
        approx2f.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // Frame taken 30 times per second

        final Mat toDraw = inputFrame.rgba();
        final Mat grey = inputFrame.gray();
        
        threshold(grey, thresholdMat, 200, 255, THRESH_BINARY);

        for (Map.Entry<MatOfPoint, Boolean> entry : findCards().entrySet()) {

            final MatOfPoint contour = entry.getKey();
            final boolean isCard = entry.getValue();

            drawContours(toDraw,
                    Collections.singletonList(contour),
                    -1,
                    new Scalar(255D, 0D, 0D, 0D));

            if (!isCard)
                continue;

            contour.convertTo(contour2f, CvType.CV_32F);

            final RotatedRect rotatedRect = minAreaRect(contour2f);
            rectangle(toDraw, rotatedRect.boundingRect(), new Scalar(0D, 255D, 0D, 0D));

            final Point[] points = new Point[4];
            rotatedRect.points(points);

            for (int j = 0; j < 4; j++)
                line(toDraw,
                        points[j],
                        points[(j + 1) % 4],
                        new Scalar(0, 0, 255, 0));
        }

        return toDraw;
    }

    private Map<MatOfPoint, Boolean> findCards() {

        contours.clear();
        findContours(thresholdMat, contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);

        final Map<MatOfPoint, Boolean> ret = new HashMap<>();
        for (int i = 0; i < contours.size(); i++) {
            final MatOfPoint contour = contours.get(i);

            contour.convertTo(contour2f, CvType.CV_32F);
            approxPolyDP(contour2f,
                    approx2f,
                    0.02D * arcLength(contour2f, true),
                    true);
            final double vertexCount = approx2f.total();

            // [Next, Previous, First_Child, Parent]
            final double[] contourInfo = hierarchy.get(0, i);
            final double parentContour = contourInfo[3];

            ret.put(contour, vertexCount == 4 && parentContour == -1);
        }

        return ret;
    }
}
