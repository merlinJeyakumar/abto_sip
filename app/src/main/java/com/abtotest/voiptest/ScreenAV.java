package com.abtotest.voiptest;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.abtollc.sdk.AbtoApplication;
import org.abtollc.sdk.AbtoCallEventsReceiver;
import org.abtollc.sdk.AbtoPhone;
import org.abtollc.sdk.OnCallConnectedListener;
import org.abtollc.sdk.OnCallDisconnectedListener;
import org.abtollc.sdk.OnCallErrorListener;
import org.abtollc.sdk.OnCallHeldListener;
import org.abtollc.sdk.OnInitializeListener;
import org.abtollc.sdk.OnRemoteAlertingListener;
import org.abtollc.sdk.OnToneReceivedListener;
import org.abtollc.sdk.OnVideoEventListener;

import java.io.File;


public class ScreenAV extends Activity implements OnCallConnectedListener,
        OnRemoteAlertingListener, OnCallDisconnectedListener,
        OnCallHeldListener, OnToneReceivedListener, OnCallErrorListener, OnInitializeListener {

    protected static final String THIS_FILE = "ScreenAV";

    public static final String POINT_TIME = "pointTime";
    public static final String TOTAL_TIME = "totalTime";

    private AbtoPhone phone;
    private int activeCallId = AbtoPhone.INVALID_CALL_ID;

    private TextView status;
    private Button pickUpVideo;
    private LinearLayout incomingCallLayout;
    private LinearLayout callInProgressLayout;
    SurfaceView localVideoSurface, remoteVideoSurface;


    private WakeLock mScreenWakeLock;
    private WakeLock mProximityWakeLock;

    private OrientationEventListener rotationListener;
    private Point videoViewSize;
    private boolean mInitialAutoSendVideoState;
    private boolean isRecording;

    /**
     * executes when activity have been created;
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onCreate(savedInstanceState);

        initWakeLocks();
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.screen_caller);

        phone = ((AbtoApplication) getApplication()).getAbtoPhone();
        mInitialAutoSendVideoState = phone.getConfig().isEnabledAutoSendRtpVideo();

        //Event handlers
        phone.setCallConnectedListener(this);
        phone.setCallDisconnectedListener(this);
        phone.setOnCallHeldListener(this);
        phone.setRemoteAlertingListener(this);
        phone.setToneReceivedListener(this);


        //Verify mode, in which was started this activity
        boolean bIsIncoming = getIntent().getBooleanExtra(AbtoPhone.IS_INCOMING, false);
        boolean startedFromService = getIntent().getBooleanExtra(AbtoPhone.ABTO_SERVICE_MARKER, false);
        if (startedFromService) {
            phone.initialize(true);
            phone.setInitializeListener(this);
        } else {
            answerCallByIntent();
        }

        // Cancel incoming call notification
        activeCallId = getIntent().getIntExtra(AbtoPhone.CALL_ID, AbtoPhone.INVALID_CALL_ID);
        //if(bIsIncoming) CallEventsReceiver.cancelIncCallNotification(this, activeCallId);//TODO
        Log.d(THIS_FILE, "callId - " + activeCallId);

        TextView name = (TextView) findViewById(R.id.caller_contact_name);
        name.setText(getIntent().getStringExtra(AbtoPhone.REMOTE_CONTACT));

        mTotalTime = getIntent().getLongExtra(TOTAL_TIME, 0);
        mPointTime = getIntent().getLongExtra(POINT_TIME, 0);
        if (mTotalTime != 0) {
            mHandler.removeCallbacks(mUpdateTimeTask);
            mHandler.postDelayed(mUpdateTimeTask, 100);
        }

        status = (TextView) findViewById(R.id.caller_call_status);
        status.setText(bIsIncoming ? "Incoming call" : "Dialing...");

        //Video controls
        localVideoSurface = findViewById(R.id.local_video);
        remoteVideoSurface = findViewById(R.id.remote_video);
        localVideoSurface.setVisibility(View.GONE);
        remoteVideoSurface.setVisibility(View.GONE);

        incomingCallLayout = (LinearLayout) findViewById(R.id.incoming_call_layout);
        incomingCallLayout.setVisibility(bIsIncoming ? View.VISIBLE : View.GONE);

        callInProgressLayout = (LinearLayout) findViewById(R.id.call_in_progress_layout);
        callInProgressLayout.setVisibility(View.GONE);

        pickUpVideo = (Button) findViewById(R.id.caller_pick_up_video_button);
        if (!startedFromService)
            pickUpVideo.setVisibility(phone.isVideoCall() ? View.VISIBLE : View.INVISIBLE);

        //Outgoing call mode
        startOutgoingCallByIntent();


        // Init render resolution changed listener
        phone.setVideoEventListener(new OnVideoEventListener() {
            @Override
            public void onRenderResolutionChanged(int callId, int width, int height) {
                videoViewSize = new Point(width, height);
                resizeRemoteVideoWindow(width, height, false);
            }
        });

        // Init screen orientation event listener
        rotationListener = new OrientationEventListener(this) {
            private AbtoPhone.Rotation angle = AbtoPhone.Rotation.ROTATION_0;

            @Override
            public void onOrientationChanged(int orientation) {
                try {
                    AbtoPhone.Rotation rotation = degreeToRotation((orientation + 45) / 90 * 90);
                    if (angle != rotation) {
                        // Rotate video view if orientation angle was changed more than 90 degrees
                        phone.rotateCapturer(rotation);
                        phone.rotateLocalVideo(rotation);
                        phone.rotateRemoteVideo(rotation);

                        angle = rotation;

                        // resize video view
                        boolean landscapeOrientation = angle == AbtoPhone.Rotation.ROTATION_90 || angle == AbtoPhone.Rotation.ROTATION_270;
                        if (videoViewSize != null) {
                            resizeRemoteVideoWindow(videoViewSize.x, videoViewSize.y, landscapeOrientation);
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            private AbtoPhone.Rotation degreeToRotation(int orientation) {
                if (orientation >= 60 && orientation <= 140) {
                    return AbtoPhone.Rotation.ROTATION_90;
                } else if (orientation >= 140 && orientation <= 220) {
                    return AbtoPhone.Rotation.ROTATION_180;
                } else if (orientation >= 220 && orientation <= 300) {
                    return AbtoPhone.Rotation.ROTATION_270;
                }

                return AbtoPhone.Rotation.ROTATION_0;
            }
        };

        // Enable listener
        rotationListener.enable();
    }

    public void resizeRemoteVideoWindow(int videoW, int videoH, boolean landscapeOrientation) {
        // Get screen size
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        int screenW = point.x;
        int screenH = point.y;

        if (landscapeOrientation) {
            // Switch remote video width with height for landscape orientation
            int temp = videoW;
            videoW = videoH;
            videoH = temp;
        }

        float videoSideKof = videoW / (float) videoH;
        float screenSideKof = screenW / (float) screenH;

        int viewW;
        int viewH;

        if (videoSideKof > screenSideKof) {
            viewW = screenW;
            viewH = (int) (viewW / (float) videoW * videoH);
        } else {
            viewH = screenH;
            viewW = (int) (viewH / (float) videoH * videoW);
        }

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) remoteVideoSurface.getLayoutParams();
        params.width = viewW;
        params.height = viewH;
        remoteVideoSurface.setLayoutParams(params);
    }

    private void answerCallByIntent() {
        if (getIntent().getBooleanExtra(AbtoCallEventsReceiver.KEY_PICK_UP_AUDIO, false)) {
            pickUp(null);
        }
        if (getIntent().getBooleanExtra(AbtoCallEventsReceiver.KEY_PICK_UP_VIDEO, false)) {
            pickUpVideo(null);
        }
    }

    private void startOutgoingCallByIntent() {
        //Skip if call is incoming
        if (getIntent().getBooleanExtra(AbtoPhone.IS_INCOMING, true)) return;

        //Get number and mode
        String sipNumber = getIntent().getStringExtra(AbtoPhone.REMOTE_CONTACT);
        boolean bVideo = getIntent().getBooleanExtra(MainActivity.START_VIDEO_CALL, false);

        // Start new call
        try {
            if (bVideo) phone.startVideoCall(sipNumber, phone.getCurrentAccountId());
            else phone.startCall(sipNumber, phone.getCurrentAccountId());


        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInitializeState(OnInitializeListener.InitializeState state, String message) {
        if (state == InitializeState.SUCCESS) {
            pickUpVideo.setVisibility(phone.isVideoCall() ? View.VISIBLE : View.INVISIBLE);
            phone.setInitializeListener(null);
            answerCallByIntent();
        }
    }

    @Override
    public void onCallConnected(String remoteContact) {
        incomingCallLayout.setVisibility(View.GONE);
        callInProgressLayout.setVisibility(View.VISIBLE);

        if (mTotalTime == 0L) {
            mPointTime = System.currentTimeMillis();
            mHandler.removeCallbacks(mUpdateTimeTask);
            mHandler.postDelayed(mUpdateTimeTask, 100);
        }

        //phone.setMicrophoneMute(false);
        //phone.setMicrophoneLevel((float)3.0);

        if (phone.isVideoCall()) {
            phone.setVideoWindows(localVideoSurface, remoteVideoSurface);
            localVideoSurface.setZOrderOnTop(true);
            localVideoSurface.setZOrderMediaOverlay(true);

            showVideoWindows(true);
            disableProximity();
        } else {
            showVideoWindows(false);
            enableProximity();
        }

        status.setText("CallConnected");
    }

    private File getRecordDir() {
        String filePath = getApplicationContext().getFilesDir() + File.separator + "Last_record_" + System.currentTimeMillis() + ".wav";
        File mFile = new File(filePath);
        mFile.getParentFile().mkdirs();
        return mFile;
    }

    @Override
    public void onCallDisconnected(String remoteContact, int callId, int statusCode) {
        if (callId == activeCallId) {
            finish();
            mTotalTime = 0;
        }
    }

    @Override
    public void onCallError(String remoteContact, int statusCode, String message) {
        Toast.makeText(ScreenAV.this, "onCallError: " + statusCode, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCallHeld(HoldState state) {
        if (state == HoldState.LOCAL_HOLD) status.setText("Local Hold");
        else if (state == HoldState.REMOTE_HOLD) status.setText("Remote Hold");
        else if (state == HoldState.ACTIVE) status.setText("Active");
    }

    @Override
    public void onRemoteAlerting(long accId, int statusCode) {
        String statusText = "";

        if (activeCallId == AbtoPhone.INVALID_CALL_ID) activeCallId = phone.getActiveCallId();

        switch (statusCode) {
            case TRYING:
                statusText = "Trying";
                break;
            case RINGING:
                statusText = "Ringing";
                break;
            case SESSION_PROGRESS:
                statusText = "Session in progress";
                break;
        }
        status.setText(statusText);
    }

    @Override
    public void onToneReceived(char tone) {
        Toast.makeText(ScreenAV.this, "DTMF received: " + tone, Toast.LENGTH_SHORT).show();
    }

    public void hangUP(View view) {
        try {
            //if(bIsIncoming) phone.rejectCall();else//TODO
            phone.hangUp();
        } catch (RemoteException e) {
            Log.e(THIS_FILE, e.getMessage());
        }
    }

    public void holdCall(View view) {
        try {
            phone.holdRetriveCall();
            //mInitialAutoSendVideoState = !mInitialAutoSendVideoState;
            //phone.muteLocalVideo(mInitialAutoSendVideoState);
            //phone.sendTone(1);
            phone.answerCall(200, false);

        } catch (RemoteException e) {
            Log.e(THIS_FILE, e.getMessage());
        }
    }

    public void pickUp(View view) {
        try {
            phone.answerCall(200, false);
        } catch (RemoteException e) {
            Log.e(THIS_FILE, e.getMessage());
        }
    }

    public void pickUpVideo(View view) {
        try {
            phone.answerCall(200, true);
        } catch (RemoteException e) {
            Log.e(THIS_FILE, e.getMessage());
        }
    }

    private void showVideoWindows(boolean show) {
        localVideoSurface.setVisibility(show ? View.VISIBLE : View.GONE);
        remoteVideoSurface.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ==========Timer==============
    private long mPointTime = 0;
    private long mTotalTime = 0;
    private Handler mHandler = new Handler();
    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            mTotalTime += System.currentTimeMillis() - mPointTime;
            mPointTime = System.currentTimeMillis();
            int seconds = (int) (mTotalTime / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            if (seconds < 10) {
                status.setText("" + minutes + ":0" + seconds);
            } else {
                status.setText("" + minutes + ":" + seconds);
            }

            mHandler.postDelayed(this, 1000);
        }
    };

    // =============================

    @Override
    protected void onPause() {

        if (mScreenWakeLock != null && mScreenWakeLock.isHeld()) {
            mScreenWakeLock.release();
        }

        mHandler.removeCallbacks(mUpdateTimeTask);
        disableProximity();

        super.onPause();
    }

    @Override
    protected void onResume() {

        if (mTotalTime != 0L) {
            mHandler.removeCallbacks(mUpdateTimeTask);
            mHandler.postDelayed(mUpdateTimeTask, 100);
        }

        if (mScreenWakeLock != null) {
            mScreenWakeLock.acquire();
        }
        super.onResume();

    }

    /**
     * executes when activity is destroyed;
     */
    public void onDestroy() {
        super.onDestroy();

        // Disable listener
        rotationListener.disable();

        mHandler.removeCallbacks(mUpdateTimeTask);

        phone.setCallConnectedListener(null);
        phone.setCallDisconnectedListener(null);
        phone.setOnCallHeldListener(null);
        phone.setRemoteAlertingListener(null);
        phone.setToneReceivedListener(null);
        phone.setVideoEventListener(null);
        disableProximity();
    }


    public void onStop() {
        super.onStop();
    }

    /**
     * overrides panel buttons keydown functionality;
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {

            try {
                phone.hangUp();
            } catch (RemoteException e) {
                Log.e(THIS_FILE, e.getMessage());
            }
        }
        return super.onKeyDown(keyCode, event);
    }


    private void initWakeLocks() {

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        int flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
        mScreenWakeLock = powerManager.newWakeLock(flags, "com.abtotest.voiptest:wakelogtag");
        mScreenWakeLock.setReferenceCounted(false);

        int field = 0x00000020;
        try {
            field = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
        } catch (Throwable t) {
        }
        mProximityWakeLock = powerManager.newWakeLock(field, getLocalClassName());
    }


    private void enableProximity() {
        if (!mProximityWakeLock.isHeld()) {
            mProximityWakeLock.acquire();
        }
    }

    private void disableProximity() {
        if (mProximityWakeLock.isHeld()) {
            mProximityWakeLock.release();
        }
    }


    public void onRecord(View view) {
        if (isRecording) {
            try {
                isRecording = false;
                phone.stopRecording();
                Toast.makeText(this, "Record Finished " + getRecordDir().getAbsolutePath(), Toast.LENGTH_SHORT).show();
                ((Button) findViewById(R.id.recordButton)).setText("Record");
            } catch (RemoteException e) {
                ((Button) findViewById(R.id.recordButton)).setText("Recording");
                Toast.makeText(this, "Unable to stop recording", Toast.LENGTH_SHORT).show();
                isRecording = true;
                e.printStackTrace();
            }
            return;
        }
        try {
            isRecording = true;
            phone.startRecording(getRecordDir().getAbsolutePath());
            Toast.makeText(this, "Record Started", Toast.LENGTH_SHORT).show();
            ((Button) findViewById(R.id.recordButton)).setText("Recording");
        } catch (RemoteException e) {
            isRecording = false;
            ((Button) findViewById(R.id.recordButton)).setText("Record");
            Toast.makeText(this, "Unable to record", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}

