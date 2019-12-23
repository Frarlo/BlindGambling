package gov.ismonnet.blindgambling;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;
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
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.content.ContentValues.TAG;
import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_MEAN_C;
import static org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.RETR_TREE;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.adaptiveThreshold;
import static org.opencv.imgproc.Imgproc.approxPolyDP;
import static org.opencv.imgproc.Imgproc.arcLength;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.findContours;

public class FullscreenActivity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final int CARD_MAX_AREA = 120000;
    private static final int CARD_MIN_AREA = 25000;

    private CameraBridgeViewBase openCvCamera;
    private BaseLoaderCallback openCvLoaderCallback;

    private Mat blurMat;
    private Mat thresholdMat;
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
        blurMat = new Mat();
        thresholdMat = new Mat();
        hierarchy = new Mat();

        contour2f = new MatOfPoint2f();
        approx2f = new MatOfPoint2f();
    }

    @Override
    public void onCameraViewStopped() {
        blurMat.release();
        thresholdMat.release();
        hierarchy.release();

        contour2f.release();
        approx2f.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // Frame taken 30 times per second

        final Mat toDraw = inputFrame.rgba();
        final Mat grey = inputFrame.gray();

        GaussianBlur(grey, blurMat, new Size(5,5),0);
        adaptiveThreshold(blurMat, thresholdMat,
                255,
                ADAPTIVE_THRESH_MEAN_C,
                THRESH_BINARY,
                75, 10);

        final List<MatOfPoint> contours = new ArrayList<>();
        findContours(thresholdMat, contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);

        drawContours(toDraw,
                contours,
                -1,
                new Scalar(255, 0, 0, 0), 5);

        for (MatOfPoint card : findCards(contours, hierarchy))
            drawContours(toDraw,
                    Collections.singletonList(card),
                    -1,
                    new Scalar(0, 0, 255, 0), 5);

        return toDraw;
    }

    private Collection<MatOfPoint> findCards(List<MatOfPoint> contours, Mat hierarchy) {

        // Sort the contours by size, so that when computing whether
        // a contour is a card, it can be easily checked if its parent contour,
        // if it exists, is a card itself

        final List<Integer> sortedContoursIdx = new ArrayList<>();

        for(int i = 0; i < contours.size(); i++)
            sortedContoursIdx.add(i);
        Collections.sort(sortedContoursIdx, (i0, i1) -> Double.compare(
                contourArea(contours.get(i0)),
                contourArea(contours.get(i1))));

        final List<MatOfPoint> sortedContours = new ArrayList<>();
        final List<double[]> sortedHierarchy = new ArrayList<>();

        for(int i : sortedContoursIdx) {
            sortedContours.add(contours.get(i));
            sortedHierarchy.add(hierarchy.get(0, i));
        }

        // Search possible cards in the contours

        final Map<Integer, MatOfPoint> cards = new ArrayMap<>();
        for (int i = 0; i < sortedContours.size(); i++) {
            final MatOfPoint contour = sortedContours.get(i);

            // [Next, Previous, First_Child, Parent]
            final double[] contourInfo = sortedHierarchy.get(i);
            final int parentContour = (int) contourInfo[3];

            if(parentContour != -1 && cards.containsKey(parentContour)) // Not in another card
                continue;

            final double area = contourArea(contour);
            if(area < CARD_MIN_AREA || area > CARD_MAX_AREA) // Size is decent
                continue;

            contour.convertTo(contour2f, CvType.CV_32F);
            approxPolyDP(contour2f,
                    approx2f,
                    0.02D * arcLength(contour2f, true),
                    true);
            final double vertexCount = approx2f.total();

//            if(vertexCount != 4) // Is a quad
//                continue;

            cards.put(i, contour);
        }

        return cards.values();
    }
}
