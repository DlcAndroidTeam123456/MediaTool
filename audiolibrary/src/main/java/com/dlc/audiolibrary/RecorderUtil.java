package com.dlc.audiolibrary;

import android.app.Activity;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Administrator on 2018\5\31 0031.
 */

public class RecorderUtil {
    private Camera myCamera;
    private SurfaceHolder holder;
    private MediaRecorder mRecorder;
    private boolean isRecording;
    private boolean isPause;
    private String path;
    private ArrayList<String> paths = new ArrayList<>();
    private SurfaceView mSurfaceview;
    private Activity activity;
    private InitCallback initCallback;

    public RecorderUtil(final Activity activity, SurfaceView mSurfaceview, final InitCallback initCallback) {
        this.mSurfaceview = mSurfaceview;
        this.activity = activity;
        this.initCallback = initCallback;
//        mSurfaceview.setZOrderOnTop(true);
        holder = mSurfaceview.getHolder();// 取得holder
        if (holder.isCreating()) {
            holder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    spm("surfaceCreated");
                    initCamera(initCallback);
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    spm("surfaceChanged");
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    spm("surfaceDestroyed");
                }
            }); // holder加入回调接口
        } else {
            initCamera(initCallback);
        }
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    /**
     * 获取系统时间，保存文件以系统时间戳命名
     */
    private String getDate() {
        Calendar ca = Calendar.getInstance();
        int year = ca.get(Calendar.YEAR);
        int month = ca.get(Calendar.MONTH);
        int day = ca.get(Calendar.DATE);
        int minute = ca.get(Calendar.MINUTE);
        int hour = ca.get(Calendar.HOUR);
        int second = ca.get(Calendar.SECOND);

        String date = "" + year + (month + 1) + day + hour + minute + second;

        return date;
    }

    /**
     * 获取SD path
     */
    private String getSDDirPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
            return sdDir.toString() + "/dlcRecordVideo";
        }
        return null;
    }

    private boolean isIniting;
    //初始化Camera设置
    private void initCamera() {
        if (isIniting){
            return;
        }
        isIniting = true;
        spm("initCamera");
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    myCamera = Camera.open();
                    setCameraDisplayOrientation(activity, getDefaultCameraId());
                    Camera.Parameters myParameters = myCamera.getParameters();
                    Camera.Size preSize = getCloselyPreSize(true, mSurfaceview.getWidth(), mSurfaceview.getHeight(), myParameters.getSupportedPreviewSizes());
                    myParameters.setPreviewSize(preSize.width, preSize.height);
                    myParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    myCamera.setParameters(myParameters);
                    myCamera.setPreviewDisplay(holder);
                    handler.sendEmptyMessage(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (myCamera != null) {
                        myCamera.release();
                    }
                    Message message = new Message();
                    message.obj = e;
                    message.what = 1;
                    handler.sendMessage(message);

                }
                super.run();
            }
        };
        thread.start();
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    myCamera.startPreview();
                    isIniting = false;
                    break;
                case 1:
                    Exception e = (Exception) msg.obj;
                    initCallback.initFail(e);
                    isIniting = false;
//                    new Handler().postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            initCamera();
//                        }
//                    },1000);
                    break;
            }

            super.handleMessage(msg);
        }
    };

    /**
     * 获取摄像头ID
     *
     * @return
     */
    private int getDefaultCameraId() {
        int defaultId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                defaultId = i;
            }
        }
        if (defaultId == -1) {
            if (numberOfCameras > 0) {
                //没有后置摄像头
                defaultId = 0;
            } else {
                Log.i("tag", "没有摄像头");
            }
        }
        return defaultId;
    }

    /**
     * 适配相机旋转
     *
     * @param activity
     * @param cameraId
     */
    private void setCameraDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        //前置
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        }
        //后置
        else {
            result = (info.orientation - degrees + 360) % 360;
        }
        myCamera.setDisplayOrientation(result);
    }

    /**
     * 通过对比得到与宽高比最接近的预览尺寸（如果有相同尺寸，优先选择）
     *
     * @param isPortrait    是否竖屏
     * @param surfaceWidth  需要被进行对比的原宽
     * @param surfaceHeight 需要被进行对比的原高
     * @param preSizeList   需要对比的预览尺寸列表
     * @return 得到与原宽高比例最接近的尺寸
     */
    private Camera.Size getCloselyPreSize(boolean isPortrait, int surfaceWidth, int surfaceHeight, List<Camera.Size> preSizeList) {
        int reqTmpWidth;
        int reqTmpHeight;
        // 当屏幕为垂直的时候需要把宽高值进行调换，保证宽大于高
        if (isPortrait) {
            reqTmpWidth = surfaceHeight;
            reqTmpHeight = surfaceWidth;
        } else {
            reqTmpWidth = surfaceWidth;
            reqTmpHeight = surfaceHeight;
        }
        //先查找preview中是否存在与surfaceview相同宽高的尺寸
        for (Camera.Size size : preSizeList) {
            if ((size.width == reqTmpWidth) && (size.height == reqTmpHeight)) {
                return size;
            }
        }

        // 得到与传入的宽高比最接近的size
        float reqRatio = ((float) reqTmpWidth) / reqTmpHeight;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Camera.Size retSize = null;
        for (Camera.Size size : preSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    public void initCamera(InitCallback initCallback) {
        try {
            initCamera();
            initCallback.initSuccess();
        } catch (Exception e) {
            initCallback.initFail(e);
        }
    }

    public void startRecord() throws IOException {
        if (isRecording) {
            return;
        }
        record();
    }

    private void record() throws IOException {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder(); // Create MediaRecorder
        }
        myCamera.unlock();
        mRecorder.setCamera(myCamera);
        // Set audio and video source and encoder
        // 这两项需要放在setOutputFormat之前
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
        mRecorder.setPreviewDisplay(holder.getSurface());

        // Set output file path
        String dirPath = getSDDirPath();
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        path = dir + "/" + getDate() + ".mp4";
        paths.add(path);
        mRecorder.setOutputFile(path);
        mRecorder.prepare();
        mRecorder.start();   // Recording is now started
        isRecording = true;
        isPause = false;
    }

    public void stopRecord() throws Exception {
        if (!isRecording) {
            return;
        }
        if (!isPause) {
            mRecorder.stop();
            mRecorder.reset();
        }
        isPause = false;
        isRecording = false;
        if (paths.size() <= 1) {
            paths.clear();
        } else {
            mergeVideo();
            for (String path2 : paths) {
                File file = new File(path2);
                if (file.exists()) {
                    file.delete();
                }
            }
            paths.clear();
        }
    }

    public void pause() {
        if (!isRecording || isPause) {
            return;
        }
        isPause = true;
        mRecorder.stop();
        mRecorder.reset();
    }

    public void restart() throws IOException {
        if (!isPause) {
            return;
        }
        record();
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isPause() {
        return isPause;
    }

    public String getPath() {
        return path;
    }


    public void onResume() {
        if (myCamera == null) {
            return;
        }
        holder = mSurfaceview.getHolder();// 取得holder
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCamera();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        }); // holder加入回调接口
    }

    public void onPause() {
        if (myCamera == null) {
            return;
        }
        try {
            stopRecord();
        } catch (Exception e) {
            e.printStackTrace();
            isRecording = false;
            isPause = false;
            paths.clear();
        }
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        if (myCamera != null) {
            myCamera.release();
        }
    }

    /**
     *  * 合成视频
     *  * paths 所有视频路径
     *  
     */
    private void mergeVideo() throws IOException {
        long begin = System.currentTimeMillis();

        List<Movie> movies = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Movie movie = MovieCreator.build(paths.get(i));
            movies.add(movie);
        }
        List<Track> videoTracks = new ArrayList<>();
        List<Track> audioTracks = new ArrayList<>();
        for (Movie movie : movies) {
            for (Track track : movie.getTracks()) {
                if ("vide".equals(track.getHandler())) {
                    videoTracks.add(track);
                }
                if ("soun".equals(track.getHandler())) {
                    audioTracks.add(track);
                }
            }
        }
        Movie result = new Movie();
        if (videoTracks.size() > 0) {
            result.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
        }
        if (audioTracks.size() > 0) {
            result.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
        }

        Container container = new DefaultMp4Builder().build(result);
        path = getSDDirPath() + "/" + getDate() + ".mp4";
        FileChannel fc = new FileOutputStream(new File(path)).getChannel();
        container.writeContainer(fc);
        fc.close();

        long end = System.currentTimeMillis();
        spm("merge use time:" + (end - begin));
    }

    public interface InitCallback {
        void initSuccess();

        void initFail(Exception e);
    }

    private void spm(String spm) {
        Log.d("spm", spm);
    }
}
