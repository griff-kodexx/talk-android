/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.bluelinelabs.logansquare.LoganSquare;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.ParticipantDisplayItem;
import com.nextcloud.talk.adapters.ParticipantsAdapter;
import com.nextcloud.talk.api.ApiService;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.RetrofitHelper;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.CallActivityBinding;
import com.nextcloud.talk.events.ConfigurationChangeEvent;
import com.nextcloud.talk.events.MediaStreamEvent;
import com.nextcloud.talk.events.NetworkEvent;
import com.nextcloud.talk.events.PeerConnectionEvent;
import com.nextcloud.talk.events.RaiseHandEvent;
import com.nextcloud.talk.events.SessionDescriptionSendEvent;
import com.nextcloud.talk.events.WebSocketCommunicationEvent;
import com.nextcloud.talk.models.ExternalSignalingServer;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.conversations.RoomsOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.polls.PollsResult;
import com.nextcloud.talk.models.json.signaling.DataChannelMessage;
import com.nextcloud.talk.models.json.signaling.DataChannelMessageNick;
import com.nextcloud.talk.models.json.signaling.NCIceCandidate;
import com.nextcloud.talk.models.json.signaling.NCMessagePayload;
import com.nextcloud.talk.models.json.signaling.NCMessageWrapper;
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage;
import com.nextcloud.talk.models.json.signaling.Signaling;
import com.nextcloud.talk.models.json.signaling.SignalingOverall;
import com.nextcloud.talk.models.json.signaling.settings.IceServer;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.models.kikaoutitilies.KikaoUtilitiesConstants;
import com.nextcloud.talk.models.kikaoutitilies.RequestToActionGenericResult;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.CountDownTimer;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.animations.PulseAnimation;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.power.PowerManagerUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.nextcloud.talk.webrtc.MagicAudioManager;
import com.nextcloud.talk.webrtc.MagicPeerConnectionWrapper;
import com.nextcloud.talk.webrtc.MagicWebRTCUtils;
import com.nextcloud.talk.webrtc.MagicWebSocketInstance;
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper;
import com.wooplr.spotlight.SpotlightView;

import org.apache.commons.lang3.StringEscapeUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;
import org.parceler.Parcel;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import autodagger.AutoInjector;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.zhanghai.android.effortlesspermissions.AfterPermissionDenied;
import me.zhanghai.android.effortlesspermissions.EffortlessPermissions;
import me.zhanghai.android.effortlesspermissions.OpenAppDetailsDialogFragment;
import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import pub.devrel.easypermissions.AfterPermissionGranted;
import retrofit2.Response;

@AutoInjector(NextcloudTalkApplication.class)
public class CallActivity extends CallBaseActivity {

    @Inject
    NcApi ncApi;
    @Inject
    EventBus eventBus;
    @Inject
    UserUtils userUtils;
    @Inject
    AppPreferences appPreferences;
    @Inject
    Cache cache;

    //kikao stuff
    @Inject
    RetrofitHelper retrofitHelper;

    ApiService apiService;

    private Integer allocatedSpeakTime = 0;

    private Long talkingSince = 0L;

    private Boolean speakTimerStarted = false;

    private Boolean speakerhasUnmuted = false;

    private Boolean speakerIsApproved = false;

    private CountDownTimer countDownTimer;

    private boolean handRaised = false;

    Timer timer;

    private Conversation.ConversationType conversationType;

    private boolean audioOn = false;

    private CompositeDisposable mCompositeDisposable = new CompositeDisposable();

    public static final String TAG = "CallActivity";

    private static CallActivity mInstanceActivity;
    public static CallActivity getmInstanceActivity() {
        return mInstanceActivity;
    }

    private static final String[] PERMISSIONS_CALL = {
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
    };

    private static final String[] PERMISSIONS_CAMERA = {
        Manifest.permission.CAMERA
    };

    private static final String[] PERMISSIONS_MICROPHONE = {
        Manifest.permission.RECORD_AUDIO
    };

    private static final String MICROPHONE_PIP_INTENT_NAME = "microphone_pip_intent";
    private static final String MICROPHONE_PIP_INTENT_EXTRA_ACTION = "microphone_pip_action";
    private static final int MICROPHONE_PIP_REQUEST_MUTE = 1;
    private static final int MICROPHONE_PIP_REQUEST_UNMUTE = 2;

    private BroadcastReceiver mReceiver;

    private PeerConnectionFactory peerConnectionFactory;
    private MediaConstraints audioConstraints;
    private MediaConstraints videoConstraints;
    private MediaConstraints sdpConstraints;
    private MediaConstraints sdpConstraintsForMCU;
    private MagicAudioManager audioManager;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private EglBase rootEglBase;
    private Disposable signalingDisposable;
    private List<PeerConnection.IceServer> iceServers;
    private CameraEnumerator cameraEnumerator;
    private String roomToken;
    private UserEntity conversationUser;
    private String conversationName;
    private String callSession;
    private MediaStream localMediaStream;
    private String credentials;
    private List<MagicPeerConnectionWrapper> magicPeerConnectionWrapperList = new ArrayList<>();
    private Map<String, Participant> participantMap = new HashMap<>();

    private boolean videoOn = false;
    private boolean microphoneOn = false;

    private boolean isVoiceOnlyCall;
    private boolean isIncomingCallFromNotification;
    private Handler callControlHandler = new Handler();
    private Handler callInfosHandler = new Handler();
    private Handler cameraSwitchHandler = new Handler();

    // push to talk
    private boolean isPTTActive = false;
    private PulseAnimation pulseAnimation;

    private String baseUrl;
    private String roomId;

    private SpotlightView spotlightView;

    private ExternalSignalingServer externalSignalingServer;
    private MagicWebSocketInstance webSocketClient;
    private WebSocketConnectionHelper webSocketConnectionHelper;
    private boolean hasMCU;
    private boolean hasExternalSignalingServer;
    private String conversationPassword;

    private PowerManagerUtils powerManagerUtils;

    private Handler handler;

    private CallStatus currentCallStatus;

    private MediaPlayer mediaPlayer;

    private Map<String, ParticipantDisplayItem> participantDisplayItems;
    private ParticipantsAdapter participantsAdapter;

    private CallActivityBinding binding;

    @Parcel
    public enum CallStatus {
        CONNECTING, CALLING_TIMEOUT, JOINED, IN_CONVERSATION, RECONNECTING, OFFLINE, LEAVING, PUBLISHER_FAILED
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        mInstanceActivity = this;

        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        binding = CallActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = retrofitHelper.getApiService();

        hideNavigationIfNoPipAvailable();

        Bundle extras = getIntent().getExtras();
        roomId = extras.getString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), "");
        roomToken = extras.getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), "");
        conversationUser = extras.getParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY());
        conversationPassword = extras.getString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), "");
        conversationName = extras.getString(BundleKeys.INSTANCE.getKEY_CONVERSATION_NAME(), "");
        isVoiceOnlyCall = extras.getBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), false);
        conversationType = (Conversation.ConversationType) extras.get(BundleKeys.INSTANCE.getKEY_CONVERSATION_TYPE());


        if (extras.containsKey(BundleKeys.INSTANCE.getKEY_FROM_NOTIFICATION_START_CALL())) {
            isIncomingCallFromNotification = extras.getBoolean(BundleKeys.INSTANCE.getKEY_FROM_NOTIFICATION_START_CALL());
        }

        credentials = ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken());

        baseUrl = extras.getString(BundleKeys.INSTANCE.getKEY_MODIFIED_BASE_URL(), "");
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = conversationUser.getBaseUrl();
        }

        powerManagerUtils = new PowerManagerUtils();

        if (extras.getString("state", "").equalsIgnoreCase("resume")) {
            setCallState(CallStatus.IN_CONVERSATION);
        } else {
            setCallState(CallStatus.CONNECTING);
        }

        initClickListeners();
        binding.microphoneButton.setOnTouchListener(new MicrophoneButtonTouchListener());

        pulseAnimation = PulseAnimation.create().with(binding.microphoneButton)
            .setDuration(310)
            .setRepeatCount(PulseAnimation.INFINITE)
            .setRepeatMode(PulseAnimation.REVERSE);

        basicInitialization();
        participantDisplayItems = new HashMap<>();
        initViews();
        initKikaoControls();
        if (!isConnectionEstablished()) {
            initiateCall();
        }
        updateSelfVideoViewPosition();

        binding.requestOtpButton.setOnClickListener(l-> {
            //request OTP
            requestOtpNetworkCall();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            cache.evictAll();
        } catch (IOException e) {
            Log.e(TAG, "Failed to evict cache");
        }
    }

    private void initClickListeners() {
        binding.pictureInPictureButton.setOnClickListener(l -> enterPipMode());

        binding.speakerButton.setOnClickListener(l -> {
            if (audioManager != null) {
                audioManager.toggleUseSpeakerphone();
                if (audioManager.isSpeakerphoneAutoOn()) {
                    binding.speakerButton.getHierarchy().setPlaceholderImage(R.drawable.ic_volume_up_white_24dp);
                } else {
                    binding.speakerButton.getHierarchy().setPlaceholderImage(R.drawable.ic_volume_mute_white_24dp);
                }
            }
        });

        binding.microphoneButton.setOnClickListener(l -> onMicrophoneClick());
        binding.microphoneButton.setOnLongClickListener(l -> {
            if (!microphoneOn) {
                callControlHandler.removeCallbacksAndMessages(null);
                callInfosHandler.removeCallbacksAndMessages(null);
                cameraSwitchHandler.removeCallbacksAndMessages(null);
                isPTTActive = true;
                binding.callControls.setVisibility(View.VISIBLE);
                if (!isVoiceOnlyCall) {
                    binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                }
            }
            onMicrophoneClick();
            return true;
        });

        binding.cameraButton.setOnClickListener(l -> onCameraClick());

        binding.hangupButton.setOnClickListener(l -> {
            setCallState(CallStatus.LEAVING);
            hangup(true);
        });

        binding.switchSelfVideoButton.setOnClickListener(l -> switchCamera());

        binding.gridview.setOnItemClickListener((parent, view, position, id) -> animateCallControls(true, 0));

        binding.callStates.callStateRelativeLayout.setOnClickListener(l -> {
            if (currentCallStatus.equals(CallStatus.CALLING_TIMEOUT)) {
                setCallState(CallStatus.RECONNECTING);
                hangupNetworkCalls(false);
            }
        });
    }

    private void createCameraEnumerator() {
        boolean camera2EnumeratorIsSupported = false;
        try {
            camera2EnumeratorIsSupported = Camera2Enumerator.isSupported(this);
        } catch (final Throwable throwable) {
            Log.w(TAG, "Camera2Enumator threw an error");
        }

        if (camera2EnumeratorIsSupported) {
            cameraEnumerator = new Camera2Enumerator(this);
        } else {
            cameraEnumerator = new Camera1Enumerator(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration());
        }
    }

    private void basicInitialization() {
        rootEglBase = EglBase.create();
        createCameraEnumerator();

        //Create a new PeerConnectionFactory instance.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
            rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory();

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        localMediaStream = peerConnectionFactory.createLocalMediaStream("NCMS");

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = MagicAudioManager.create(getApplicationContext(), !isVoiceOnlyCall);
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...");
        audioManager.start(this::onAudioManagerDevicesChanged);

        iceServers = new ArrayList<>();

        //create sdpConstraints
        sdpConstraints = new MediaConstraints();
        sdpConstraintsForMCU = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        String offerToReceiveVideoString = "true";

        if (isVoiceOnlyCall) {
            offerToReceiveVideoString = "false";
        }

        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", offerToReceiveVideoString));

        sdpConstraintsForMCU.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        sdpConstraintsForMCU.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        sdpConstraintsForMCU.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraintsForMCU.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("internalSctpDataChannels", "true"));
        sdpConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        if (!isVoiceOnlyCall) {
            cameraInitialization();
        }

        microphoneInitialization();
    }

    private void handleFromNotification() {
        int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.getRooms(credentials, ApiUtils.getUrlForRooms(apiVersion, baseUrl))
            .retry(3)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<RoomsOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull RoomsOverall roomsOverall) {
                    for (Conversation conversation : roomsOverall.getOcs().getData()) {
                        if (roomId.equals(conversation.getRoomId())) {
                            roomToken = conversation.getToken();
                            conversationType = conversation.getType();
                            break;
                        }
                    }

                    checkPermissions();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        Log.d(TAG, "initViews");
        binding.callInfosLinearLayout.setVisibility(View.VISIBLE);
        binding.selfVideoViewWrapper.setVisibility(View.VISIBLE);

        if (!isPipModePossible()) {
            binding.pictureInPictureButton.setVisibility(View.GONE);
        }

        if (isVoiceOnlyCall) {
            binding.speakerButton.setVisibility(View.VISIBLE);
            binding.switchSelfVideoButton.setVisibility(View.GONE);
            binding.cameraButton.setVisibility(View.GONE);
            binding.selfVideoRenderer.setVisibility(View.GONE);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                                 ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.BELOW, R.id.callInfosLinearLayout);
            int callControlsHeight = Math.round(getApplicationContext().getResources().getDimension(R.dimen.call_controls_height));
            params.setMargins(0, 0, 0, callControlsHeight);
            binding.gridview.setLayoutParams(params);
        } else {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                                 ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 0);
            binding.gridview.setLayoutParams(params);

            binding.speakerButton.setVisibility(View.GONE);
            if (cameraEnumerator.getDeviceNames().length < 2) {
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
            initSelfVideoView();
        }

        binding.gridview.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent me) {
                int action = me.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    animateCallControls(true, 0);
                }
                return false;
            }
        });

        binding.conversationRelativeLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent me) {
                int action = me.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    animateCallControls(true, 0);
                }
                return false;
            }
        });

        animateCallControls(true, 0);

        initGridAdapter();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initSelfVideoView() {
        try {
            binding.selfVideoRenderer.init(rootEglBase.getEglBaseContext(), null);
        } catch (IllegalStateException e) {
            Log.d(TAG, "selfVideoRenderer already initialized", e);
        }

        binding.selfVideoRenderer.setZOrderMediaOverlay(true);
        // disabled because it causes some devices to crash
        binding.selfVideoRenderer.setEnableHardwareScaler(false);
        binding.selfVideoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        binding.selfVideoRenderer.setOnTouchListener(new SelfVideoTouchListener());
    }

    private void initGridAdapter() {
        Log.d(TAG, "initGridAdapter");
        int columns;
        int participantsInGrid = participantDisplayItems.size();
        if (getResources() != null && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (participantsInGrid > 2) {
                columns = 2;
            } else {
                columns = 1;
            }
        } else {
            if (participantsInGrid > 2) {
                columns = 3;
            } else if (participantsInGrid > 1) {
                columns = 2;
            } else {
                columns = 1;
            }
        }

        binding.gridview.setNumColumns(columns);

        binding.conversationRelativeLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                binding.conversationRelativeLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int height = binding.conversationRelativeLayout.getMeasuredHeight();
                binding.gridview.setMinimumHeight(height);
            }
        });

        binding.callInfosLinearLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                binding.callInfosLinearLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        participantsAdapter = new ParticipantsAdapter(
            this,
            participantDisplayItems,
            binding.conversationRelativeLayout,
            binding.callInfosLinearLayout,
            columns,
            isVoiceOnlyCall);
        binding.gridview.setAdapter(participantsAdapter);

        if (isInPipMode) {
            updateUiForPipMode();
        }
    }


    private void checkPermissions() {
        if (isVoiceOnlyCall) {
            onMicrophoneClick();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CALL, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CALL, new int[]{1, 1});
            }
        }

    }

    private boolean isConnectionEstablished() {
        return (currentCallStatus.equals(CallStatus.JOINED) || currentCallStatus.equals(CallStatus.IN_CONVERSATION));
    }

    @AfterPermissionGranted(100)
    private void onPermissionsGranted() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CALL)) {
            if (!videoOn && !isVoiceOnlyCall) {
                onCameraClick();
            }

            if (!microphoneOn) {
                onMicrophoneClick();
            }

            if (!isVoiceOnlyCall) {
                if (cameraEnumerator.getDeviceNames().length == 0) {
                    binding.cameraButton.setVisibility(View.GONE);
                }

                if (cameraEnumerator.getDeviceNames().length > 1) {
                    binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                }
            }

            if (!isConnectionEstablished()) {
                fetchSignalingSettings();
            }
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_CALL)) {
            checkIfSomeAreApproved();
        }

    }

    private void checkIfSomeAreApproved() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator.getDeviceNames().length == 0) {
                binding.cameraButton.setVisibility(View.GONE);
            }

            if (cameraEnumerator.getDeviceNames().length > 1) {
                binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
            }

            if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
                if (!videoOn) {
                    onCameraClick();
                }
            } else {
                binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
                binding.cameraButton.setAlpha(0.7f);
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
        }

        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE)) {
            if (!microphoneOn) {
                onMicrophoneClick();
            }
        } else {
            binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
        }

        if (!isConnectionEstablished()) {
            fetchSignalingSettings();
        }
    }

    @AfterPermissionDenied(100)
    private void onPermissionsDenied() {
        if (!isVoiceOnlyCall) {
            if (cameraEnumerator.getDeviceNames().length == 0) {
                binding.cameraButton.setVisibility(View.GONE);
            } else if (cameraEnumerator.getDeviceNames().length == 1) {
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
        }

        if ((EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) ||
            EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE))) {
            checkIfSomeAreApproved();
        } else if (!isConnectionEstablished()) {
            fetchSignalingSettings();
        }
    }

    private void onAudioManagerDevicesChanged(
        final MagicAudioManager.AudioDevice device, final Set<MagicAudioManager.AudioDevice> availableDevices) {
        Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "selected: " + device);

        final boolean shouldDisableProximityLock = (device.equals(MagicAudioManager.AudioDevice.WIRED_HEADSET)
            || device.equals(MagicAudioManager.AudioDevice.SPEAKER_PHONE)
            || device.equals(MagicAudioManager.AudioDevice.BLUETOOTH));

        if (shouldDisableProximityLock) {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITHOUT_PROXIMITY_SENSOR_LOCK);
        } else {
            powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);
        }
    }


    private void cameraInitialization() {
        videoCapturer = createCameraCapturer(cameraEnumerator);

        //Create a VideoSource instance
        if (videoCapturer != null) {
            SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(false);
            videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("NCv0", videoSource);
        localMediaStream.addTrack(localVideoTrack);
        localVideoTrack.setEnabled(false);
        localVideoTrack.addSink(binding.selfVideoRenderer);
    }

    private void microphoneInitialization() {
        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("NCa0", audioSource);
        localAudioTrack.setEnabled(false);
        localMediaStream.addTrack(localAudioTrack);
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    binding.selfVideoRenderer.setMirror(true);
                    return videoCapturer;
                }
            }
        }


        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    binding.selfVideoRenderer.setMirror(false);
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    public void onMicrophoneClick() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_MICROPHONE)) {

            if (!appPreferences.getPushToTalkIntroShown()) {
                spotlightView = new SpotlightView.Builder(this)
                    .introAnimationDuration(300)
                    .enableRevealAnimation(true)
                    .performClick(false)
                    .fadeinTextDuration(400)
                    .headingTvColor(getResources().getColor(R.color.colorPrimary))
                    .headingTvSize(20)
                    .headingTvText(getResources().getString(R.string.nc_push_to_talk))
                    .subHeadingTvColor(getResources().getColor(R.color.bg_default))
                    .subHeadingTvSize(16)
                    .subHeadingTvText(getResources().getString(R.string.nc_push_to_talk_desc))
                    .maskColor(Color.parseColor("#dc000000"))
                    .target(binding.microphoneButton)
                    .lineAnimDuration(400)
                    .lineAndArcColor(getResources().getColor(R.color.colorPrimary))
                    .enableDismissAfterShown(true)
                    .dismissOnBackPress(true)
                    .usageId("pushToTalk")
                    .show();

                appPreferences.setPushToTalkIntroShown(true);
            }

            if (!isPTTActive) {
                microphoneOn = !microphoneOn;

                if (microphoneOn) {
                    binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_white_24px);
                    updatePictureInPictureActions(R.drawable.ic_mic_white_24px,
                                                  getResources().getString(R.string.nc_pip_microphone_mute),
                                                  MICROPHONE_PIP_REQUEST_MUTE);
                } else {
                    binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
                    updatePictureInPictureActions(R.drawable.ic_mic_off_white_24px,
                                                  getResources().getString(R.string.nc_pip_microphone_unmute),
                                                  MICROPHONE_PIP_REQUEST_UNMUTE);
                }

                toggleMedia(microphoneOn, false);
            } else {
                binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_white_24px);
                pulseAnimation.start();
                toggleMedia(true, false);
            }

            if (isVoiceOnlyCall && !isConnectionEstablished()) {
                fetchSignalingSettings();
            }

        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_MICROPHONE)) {
            // Microphone permission is permanently denied so we cannot request it normally.

            OpenAppDetailsDialogFragment.show(
                R.string.nc_microphone_permission_permanently_denied,
                R.string.nc_permissions_settings, (AppCompatActivity) this);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_MICROPHONE, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_MICROPHONE, new int[]{1});
            }
        }

        Log.d(TAG, "We are here....");
        if (allocatedSpeakTime >0 && (binding.requestToSpeakButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.action_cancel)) || binding.requestToInterveneButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.action_cancel)))){
            speakerhasUnmuted = true;
            Log.d(TAG, "We are not here...."+speakTimerStarted);
            if (!speakTimerStarted){

                //start timer if plenary
                speakTimerStarted = true;
                if (conversationType.equals(Conversation.ConversationType.ROOM_PLENARY_CALL)) {
                    startTimer();
                }
                //make api request that speaker has started speaking


                JSONObject json = new JSONObject();
                try {
                    json.put("token", roomToken);
                    json.put("userId", conversationUser.getUserId());
                    json.put("activityType", 0);
                    json.put("approved", true);
                    json.put("started", true);
                    json.put("paused", false);
                    json.put("canceled", false);
                    json.put("talkingSince", System.currentTimeMillis());
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                requestToSpeakStartLoading();


                Log.d(TAG,"Calling apiservice");


                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                int activeSession = -99;

                if (binding.requestToSpeakButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.action_cancel))){
                    activeSession = sharedPref.getInt(KikaoUtilitiesConstants.ACTIVE_SESSION_REQUEST_TO_SPEAK_ID, 0);

                }else if (binding.requestToInterveneButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.action_cancel))){
                    activeSession = sharedPref.getInt(KikaoUtilitiesConstants.ACTIVE_SESSION_REQUEST_TO_INTERVENE_ID,
                                                      0);
                }

                apiService.userUnMuted(credentials, activeSession , roomToken, RequestBody.create(MediaType.parse("application/json"),
                                                                                                  json.toString()))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<RequestToActionGenericResult>() {
                        @Override
                        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                            // unused atm
                        }

                        @Override
                        public void onNext(@io.reactivex.annotations.NonNull RequestToActionGenericResult requestToActionGenericResult) {

                        }

                        @Override
                        public void onError(@io.reactivex.annotations.NonNull Throwable e) {

                        }

                        @Override
                        public void onComplete() {
                            // unused atm
                        }
                    });

            }

        }

    }

    public void onCameraClick() {
        if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA)) {
            videoOn = !videoOn;

            if (videoOn) {
                binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_white_24px);
                if (cameraEnumerator.getDeviceNames().length > 1) {
                    binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                }
            } else {
                binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }

            toggleMedia(videoOn, true);
        } else if (EffortlessPermissions.somePermissionPermanentlyDenied(this, PERMISSIONS_CAMERA)) {
            // Camera permission is permanently denied so we cannot request it normally.
            OpenAppDetailsDialogFragment.show(
                R.string.nc_camera_permission_permanently_denied,
                R.string.nc_permissions_settings, (AppCompatActivity) this);
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS_CAMERA, 100);
            } else {
                onRequestPermissionsResult(100, PERMISSIONS_CAMERA, new int[]{1});
            }
        }

    }

    public void switchCamera() {
        CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
        if (cameraVideoCapturer != null) {
            cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean currentCameraIsFront) {
                    binding.selfVideoRenderer.setMirror(currentCameraIsFront);
                }

                @Override
                public void onCameraSwitchError(String s) {

                }
            });
        }
    }

    private void toggleMedia(boolean enable, boolean video) {
        String message;
        if (video) {
            message = "videoOff";
            if (enable) {
                binding.cameraButton.setAlpha(1.0f);
                message = "videoOn";
                startVideoCapture();
            } else {
                binding.cameraButton.setAlpha(0.7f);
                if (videoCapturer != null) {
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Failed to stop capturing video while sensor is near the ear");
                    }
                }
            }

            if (localMediaStream != null && localMediaStream.videoTracks.size() > 0) {
                localMediaStream.videoTracks.get(0).setEnabled(enable);
            }
            if (enable) {
                binding.selfVideoRenderer.setVisibility(View.VISIBLE);
            } else {
                binding.selfVideoRenderer.setVisibility(View.INVISIBLE);
            }
        } else {
            message = "audioOff";
            if (enable) {
                message = "audioOn";
                binding.microphoneButton.setAlpha(1.0f);
            } else {
                binding.microphoneButton.setAlpha(0.7f);
            }

            if (localMediaStream != null && localMediaStream.audioTracks.size() > 0) {
                localMediaStream.audioTracks.get(0).setEnabled(enable);
            }
        }

        if (isConnectionEstablished() && magicPeerConnectionWrapperList != null) {
            if (!hasMCU) {
                for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
                    magicPeerConnectionWrapper.sendChannelData(new DataChannelMessage(message));
                }
            } else {
                for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
                    if (magicPeerConnectionWrapper.getSessionId().equals(webSocketClient.getSessionId())) {
                        magicPeerConnectionWrapper.sendChannelData(new DataChannelMessage(message));
                        break;
                    }
                }
            }
        }
    }


    private void animateCallControls(boolean show, long startDelay) {
        if (isVoiceOnlyCall) {
            if (spotlightView != null && spotlightView.getVisibility() != View.GONE) {
                spotlightView.setVisibility(View.GONE);
            }
        } else if (!isPTTActive) {
            float alpha;
            long duration;

            if (show) {
                callControlHandler.removeCallbacksAndMessages(null);
                callInfosHandler.removeCallbacksAndMessages(null);
                cameraSwitchHandler.removeCallbacksAndMessages(null);
                alpha = 1.0f;
                duration = 1000;
                if (binding.callControls.getVisibility() != View.VISIBLE) {
                    binding.callControls.setAlpha(0.0f);
                    binding.callControls.setVisibility(View.VISIBLE);

                    binding.callInfosLinearLayout.setAlpha(0.0f);
                    binding.callInfosLinearLayout.setVisibility(View.VISIBLE);

                    binding.switchSelfVideoButton.setAlpha(0.0f);
                    if (videoOn) {
                        binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
                    }
                } else {
                    callControlHandler.postDelayed(() -> animateCallControls(false, 0), 5000);
                    return;
                }
            } else {
                alpha = 1.0f; //hack to make always visible
                duration = 1000;
            }

            binding.callControls.setEnabled(false);
            binding.callControls.animate()
                .translationY(0)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (!show) {
                            binding.callControls.setVisibility(View.VISIBLE); //hack to make always visible
                            if (spotlightView != null && spotlightView.getVisibility() != View.GONE) {
                                spotlightView.setVisibility(View.GONE);
                            }
                        } else {
                            callControlHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isPTTActive) {
                                        animateCallControls(false, 0);
                                    }
                                }
                            }, 7500);
                        }

                        binding.callControls.setEnabled(true);
                    }
                });

            binding.callInfosLinearLayout.setEnabled(false);
            binding.callInfosLinearLayout.animate()
                .translationY(0)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (!show) {
                            binding.callInfosLinearLayout.setVisibility(View.GONE);
                        } else {
                            callInfosHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isPTTActive) {
                                        animateCallControls(false, 0);
                                    }
                                }
                            }, 7500);
                        }

                        binding.callInfosLinearLayout.setEnabled(true);
                    }
                });

            binding.switchSelfVideoButton.setEnabled(false);
            binding.switchSelfVideoButton.animate()
                .translationY(0)
                .alpha(alpha)
                .setDuration(duration)
                .setStartDelay(startDelay)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (!show) {
                            binding.switchSelfVideoButton.setVisibility(View.GONE);
                        }

                        binding.switchSelfVideoButton.setEnabled(true);
                    }
                });

        }
    }

    @Override
    public void onDestroy() {
        if (!currentCallStatus.equals(CallStatus.LEAVING)) {
            setCallState(CallStatus.LEAVING);
            hangup(true);
        }
        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.IDLE);
        super.onDestroy();
        mInstanceActivity = null;
        mCompositeDisposable.dispose();
    }

    private void fetchSignalingSettings() {
        Log.d(TAG, "fetchSignalingSettings");
        int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, new int[]{ApiUtils.APIv3, 2, 1});

        ncApi.getSignalingSettings(credentials, ApiUtils.getUrlForSignalingSettings(apiVersion, baseUrl))
            .subscribeOn(Schedulers.io())
            .retry(3)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<SignalingSettingsOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull SignalingSettingsOverall signalingSettingsOverall) {
                    if (signalingSettingsOverall.getOcs() != null && signalingSettingsOverall.getOcs().getSettings() != null) {
                        externalSignalingServer = new ExternalSignalingServer();

                        if (!TextUtils.isEmpty(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer()) &&
                            !TextUtils.isEmpty(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket())) {
                            externalSignalingServer = new ExternalSignalingServer();
                            externalSignalingServer.setExternalSignalingServer(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingServer());
                            externalSignalingServer.setExternalSignalingTicket(signalingSettingsOverall.getOcs().getSettings().getExternalSignalingTicket());
                            hasExternalSignalingServer = true;
                        } else {
                            hasExternalSignalingServer = false;
                        }
                        Log.d(TAG, "   hasExternalSignalingServer: " + hasExternalSignalingServer);

                        if (!conversationUser.getUserId().equals("?")) {
                            try {
                                userUtils.createOrUpdateUser(null, null, null, null, null, null, null,
                                                             conversationUser.getId(), null, null, LoganSquare.serialize(externalSignalingServer))
                                    .subscribeOn(Schedulers.io())
                                    .subscribe();
                            } catch (IOException exception) {
                                Log.e(TAG, "Failed to serialize external signaling server", exception);
                            }
                        } else {
                            try {
                                conversationUser.setExternalSignalingServer(LoganSquare.serialize(externalSignalingServer));
                            } catch (IOException exception) {
                                Log.e(TAG, "Failed to serialize external signaling server", exception);
                            }
                        }

                        if (signalingSettingsOverall.getOcs().getSettings().getStunServers() != null) {
                            List<IceServer> stunServers =
                                signalingSettingsOverall.getOcs().getSettings().getStunServers();
                            if (apiVersion == ApiUtils.APIv3) {
                                for (IceServer stunServer : stunServers) {
                                    if (stunServer.getUrls() != null) {
                                        for (String url : stunServer.getUrls()) {
                                            Log.d(TAG, "   STUN server url: " + url);
                                            iceServers.add(new PeerConnection.IceServer(url));
                                        }
                                    }
                                }
                            } else {
                                if (signalingSettingsOverall.getOcs().getSettings().getStunServers() != null) {
                                    for (IceServer stunServer : stunServers) {
                                        Log.d(TAG, "   STUN server url: " + stunServer.getUrl());
                                        iceServers.add(new PeerConnection.IceServer(stunServer.getUrl()));
                                    }
                                }
                            }
                        }

                        if (signalingSettingsOverall.getOcs().getSettings().getTurnServers() != null) {
                            List<IceServer> turnServers =
                                signalingSettingsOverall.getOcs().getSettings().getTurnServers();
                            for (IceServer turnServer : turnServers) {
                                if (turnServer.getUrls() != null) {
                                    for (String url : turnServer.getUrls()) {
                                        Log.d(TAG, "   TURN server url: " + url);
                                        iceServers.add(new PeerConnection.IceServer(
                                            url, turnServer.getUsername(), turnServer.getCredential()
                                        ));
                                    }
                                }
                            }
                        }
                    }

                    checkCapabilities();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    Log.e(TAG, e.getMessage(), e);
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void checkCapabilities() {
        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
            .retry(3)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<CapabilitiesOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull CapabilitiesOverall capabilitiesOverall) {
                    // FIXME check for compatible Call API version
                    if (hasExternalSignalingServer) {
                        setupAndInitiateWebSocketsConnection();
                    } else {
                        joinRoomAndCall();
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void joinRoomAndCall() {
        callSession = ApplicationWideCurrentRoomHolder.getInstance().getSession();

        int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        Log.d(TAG, "joinRoomAndCall");
        Log.d(TAG, "   baseUrl= " + baseUrl);
        Log.d(TAG, "   roomToken= " + roomToken);
        Log.d(TAG, "   callSession= " + callSession);

        String url = ApiUtils.getUrlForParticipantsActive(apiVersion, baseUrl, roomToken);
        Log.d(TAG, "   url= " + url);

        if (TextUtils.isEmpty(callSession)) {
            ncApi.joinRoom(credentials, url, conversationPassword)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
                        callSession = roomOverall.getOcs().getData().getSessionId();
                        Log.d(TAG, " new callSession by joinRoom= " + callSession);

                        ApplicationWideCurrentRoomHolder.getInstance().setSession(callSession);
                        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomId(roomId);
                        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomToken(roomToken);
                        ApplicationWideCurrentRoomHolder.getInstance().setUserInRoom(conversationUser);
                        callOrJoinRoomViaWebSocket();
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, "joinRoom onError", e);
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "joinRoom onComplete");
                    }
                });
        } else {
            // we are in a room and start a call -> same session needs to be used
            callOrJoinRoomViaWebSocket();
        }
    }

    private void callOrJoinRoomViaWebSocket() {
        if (hasExternalSignalingServer) {
            webSocketClient.joinRoomWithRoomTokenAndSession(roomToken, callSession);
        } else {
            performCall();
        }
    }

    private void performCall() {
        Integer inCallFlag;
        if (isVoiceOnlyCall) {
            inCallFlag = (int) Participant.ParticipantFlags.IN_CALL_WITH_AUDIO.getValue();
        } else {
            inCallFlag = (int) Participant.ParticipantFlags.IN_CALL_WITH_AUDIO_AND_VIDEO.getValue();
        }

        int apiVersion = ApiUtils.getCallApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.joinCall(credentials, ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken), inCallFlag)
            .subscribeOn(Schedulers.io())
            .retry(3)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<GenericOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                    if (!currentCallStatus.equals(CallStatus.LEAVING)) {
                        setCallState(CallStatus.JOINED);

                        ApplicationWideCurrentRoomHolder.getInstance().setInCall(true);
                        ApplicationWideCurrentRoomHolder.getInstance().setDialing(false);

                        if (!TextUtils.isEmpty(roomToken)) {
                            NotificationUtils.INSTANCE.cancelExistingNotificationsForRoom(getApplicationContext(),
                                                                                          conversationUser,
                                                                                          roomToken);
                        }

                        if (!hasExternalSignalingServer) {
                            int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser,
                                                                             new int[]{ApiUtils.APIv3, 2, 1});

                            ncApi.pullSignalingMessages(credentials,
                                                        ApiUtils.getUrlForSignaling(apiVersion,
                                                                                    baseUrl,
                                                                                    roomToken))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .repeatWhen(observable -> observable)
                                .takeWhile(observable -> isConnectionEstablished())
                                .retry(3, observable -> isConnectionEstablished())
                                .subscribe(new Observer<SignalingOverall>() {
                                    @Override
                                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                                        signalingDisposable = d;
                                    }

                                    @Override
                                    public void onNext(
                                        @io.reactivex.annotations.NonNull
                                            SignalingOverall signalingOverall) {
                                        receivedSignalingMessages(signalingOverall.getOcs().getSignalings());
                                    }

                                    @Override
                                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                        dispose(signalingDisposable);
                                    }

                                    @Override
                                    public void onComplete() {
                                        dispose(signalingDisposable);
                                    }
                                });
                        }
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void setupAndInitiateWebSocketsConnection() {
        if (webSocketConnectionHelper == null) {
            webSocketConnectionHelper = new WebSocketConnectionHelper();
        }

        if (webSocketClient == null) {
            webSocketClient = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                externalSignalingServer.getExternalSignalingServer(),
                conversationUser, externalSignalingServer.getExternalSignalingTicket(),
                TextUtils.isEmpty(credentials));
        } else {
            if (webSocketClient.isConnected() && currentCallStatus.equals(CallStatus.PUBLISHER_FAILED)) {
                webSocketClient.restartWebSocket();
            }
        }

        joinRoomAndCall();
    }

    private void initiateCall() {
        if (!TextUtils.isEmpty(roomToken)) {
            checkPermissions();
        } else {
            handleFromNotification();
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(WebSocketCommunicationEvent webSocketCommunicationEvent) {
        switch (webSocketCommunicationEvent.getType()) {
            case "hello":
                if (!webSocketCommunicationEvent.getHashMap().containsKey("oldResumeId")) {
                    if (currentCallStatus.equals(CallStatus.RECONNECTING)) {
                        hangup(false);
                    } else {
                        initiateCall();
                    }
                }
                break;
            case "roomJoined":
                startSendingNick();

                if (webSocketCommunicationEvent.getHashMap().get("roomToken").equals(roomToken)) {
                    performCall();
                }
                break;
            case "participantsUpdate":
                if (webSocketCommunicationEvent.getHashMap().get("roomToken").equals(roomToken)) {
                    processUsersInRoom(
                        (List<HashMap<String, Object>>) webSocketClient
                            .getJobWithId(
                                Integer.valueOf(webSocketCommunicationEvent.getHashMap().get("jobId"))));
                }
                break;
            case "signalingMessage":
                processMessage((NCSignalingMessage) webSocketClient.getJobWithId(
                    Integer.valueOf(webSocketCommunicationEvent.getHashMap().get("jobId"))));
                break;
            case "peerReadyForRequestingOffer":
                webSocketClient.requestOfferForSessionIdWithType(
                    webSocketCommunicationEvent.getHashMap().get("sessionId"), "video");
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(RaiseHandEvent raiseHandEvent) throws IOException {
        Log.d(TAG,"raiseHand message event initiated");

        NCMessageWrapper ncMessageWrapper = new NCMessageWrapper();
        ncMessageWrapper.setEv("message");
        ncMessageWrapper.setSessionId(callSession);
        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(raiseHandEvent.getPeerId());
        ncSignalingMessage.setRoomType(raiseHandEvent.getVideoStreamType());
        ncSignalingMessage.setType(raiseHandEvent.getType());
        NCMessagePayload ncMessagePayload = new NCMessagePayload();
        ncMessagePayload.setType(raiseHandEvent.getType());
        ncMessagePayload.setState(raiseHandEvent.getRaiseHand());
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();
        ncMessagePayload.setTimestamp(ts);

        // Set all we need
        ncSignalingMessage.setPayload(ncMessagePayload);
        ncMessageWrapper.setSignalingMessage(ncSignalingMessage);


        if (!hasExternalSignalingServer) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{")
                .append("\"fn\":\"")
                .append(StringEscapeUtils.escapeJson(LoganSquare.serialize(ncMessageWrapper.getSignalingMessage()))).append("\"")
                .append(",")
                .append("\"sessionId\":")
                .append("\"").append(StringEscapeUtils.escapeJson(callSession)).append("\"")
                .append(",")
                .append("\"ev\":\"message\"")
                .append("}");

            List<String> strings = new ArrayList<>();
            String stringToSend = stringBuilder.toString();
            strings.add(stringToSend);

            int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, new int[] {ApiUtils.APIv3, 2, 1});

            ncApi.sendSignalingMessages(credentials, ApiUtils.getUrlForSignaling(apiVersion, baseUrl, roomToken),
                                        strings.toString())
                .retry(3)
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<SignalingOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull SignalingOverall signalingOverall) {
                        runOnUiThread(()->{
                            //todo do this to after confirmation of the request from the server. Should show the
                            // loading icon or error otherwise
                            switch (raiseHandEvent.getType()){
                                case "raiseHand":
                                    //do nothing since it's a simple raise handle request
                                    break;
                            }

                        });
                        receivedSignalingMessages(signalingOverall.getOcs().getSignalings());
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, "", e);
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });
        } else {
            webSocketClient.sendCallMessage(ncMessageWrapper);
        }

    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {
            if (signalingDisposable != null && !signalingDisposable.isDisposed()) {
                signalingDisposable.dispose();
                signalingDisposable = null;
            }
        }
    }

    private void receivedSignalingMessages(@Nullable List<Signaling> signalingList) {
        if (signalingList != null) {
            for (Signaling signaling : signalingList) {
                try {
                    receivedSignalingMessage(signaling);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to process received signaling message", e);
                }
            }
        }
    }

    private void receivedSignalingMessage(Signaling signaling) throws IOException {
        String messageType = signaling.getType();

        if (!isConnectionEstablished() && !currentCallStatus.equals(CallStatus.CONNECTING)) {
            return;
        }

        if ("usersInRoom".equals(messageType)) {
            processUsersInRoom((List<HashMap<String, Object>>) signaling.getMessageWrapper());
        } else if ("message".equals(messageType)) {
            NCSignalingMessage ncSignalingMessage = LoganSquare.parse(signaling.getMessageWrapper().toString(),
                                                                      NCSignalingMessage.class);
            processMessage(ncSignalingMessage);
        } else {
            Log.e(TAG, "unexpected message type when receiving signaling message");
        }
    }

    private void processMessage(NCSignalingMessage ncSignalingMessage) {
        if (ncSignalingMessage.getRoomType().equals("video") || ncSignalingMessage.getRoomType().equals("screen")) {
            MagicPeerConnectionWrapper magicPeerConnectionWrapper =
                getPeerConnectionWrapperForSessionIdAndType(ncSignalingMessage.getFrom(),
                                                            ncSignalingMessage.getRoomType(), false);

            String type = null;
            if (ncSignalingMessage.getPayload() != null && ncSignalingMessage.getPayload().getType() != null) {
                type = ncSignalingMessage.getPayload().getType();
            } else if (ncSignalingMessage.getType() != null) {
                type = ncSignalingMessage.getType();
            }

            if (type != null) {
                switch (type) {
                    case "unshareScreen":
                        endPeerConnection(ncSignalingMessage.getFrom(), true);
                        break;
                    case "offer":
                    case "answer":
                        magicPeerConnectionWrapper.setNick(ncSignalingMessage.getPayload().getNick());
                        SessionDescription sessionDescriptionWithPreferredCodec;

                        String sessionDescriptionStringWithPreferredCodec = MagicWebRTCUtils.preferCodec
                            (ncSignalingMessage.getPayload().getSdp(),
                             "H264", false);

                        sessionDescriptionWithPreferredCodec = new SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type),
                            sessionDescriptionStringWithPreferredCodec);

                        if (magicPeerConnectionWrapper.getPeerConnection() != null) {
                            magicPeerConnectionWrapper.getPeerConnection().setRemoteDescription(magicPeerConnectionWrapper
                                                                                                    .getMagicSdpObserver(), sessionDescriptionWithPreferredCodec);
                        }
                        break;
                    case "candidate":
                        NCIceCandidate ncIceCandidate = ncSignalingMessage.getPayload().getIceCandidate();
                        IceCandidate iceCandidate = new IceCandidate(ncIceCandidate.getSdpMid(),
                                                                     ncIceCandidate.getSdpMLineIndex(), ncIceCandidate.getCandidate());
                        magicPeerConnectionWrapper.addCandidate(iceCandidate);
                        break;
                    case "endOfCandidates":
                        magicPeerConnectionWrapper.drainIceCandidates();
                        break;
                    default:
                        break;
                }
            }
        } else {
            Log.e(TAG, "unexpected RoomType while processing NCSignalingMessage");
        }
    }

    public void meetingEnded(){
        binding.callStates.callStateTextView.setTextColor(getResources().getColor(R.color.nc_darkRed));
        Snackbar.make(
            binding.getRoot(),
            R.string.nc_meeting_ended_leaving,
            Snackbar.LENGTH_LONG
                     ).setTextColor(getResources().getColor(R.color.nc_darkRed)).show();
        setCallState(CallStatus.LEAVING);
        hangup(true);
    }

    private void hangup(boolean shutDownView) {
        mCompositeDisposable.dispose();
        if(timer!=null){
            timer.cancel();
        }
        stopCallingSound();
        dispose(null);

        if (shutDownView) {

            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Failed to stop capturing while hanging up");
                }
                videoCapturer.dispose();
                videoCapturer = null;
            }

            if (binding.selfVideoRenderer != null) {
                binding.selfVideoRenderer.release();
            }

            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }

            if (audioManager != null) {
                audioManager.stop();
                audioManager = null;
            }

            if (videoSource != null) {
                videoSource = null;
            }

            if (peerConnectionFactory != null) {
                peerConnectionFactory = null;
            }

            localMediaStream = null;
            localAudioTrack = null;
            localVideoTrack = null;


            if (TextUtils.isEmpty(credentials) && hasExternalSignalingServer) {
                WebSocketConnectionHelper.deleteExternalSignalingInstanceForUserEntity(-1);
            }
        }

        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            endPeerConnection(magicPeerConnectionWrapperList.get(i).getSessionId(), false);
        }

        hangupNetworkCalls(shutDownView);
        ApplicationWideCurrentRoomHolder.getInstance().setInCall(false);
    }

    private void hangupNetworkCalls(boolean shutDownView) {
        int apiVersion = ApiUtils.getCallApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.leaveCall(credentials, ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<GenericOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                    if (shutDownView) {
                        finish();
                    } else if (currentCallStatus == CallStatus.RECONNECTING || currentCallStatus == CallStatus.PUBLISHER_FAILED) {
                        initiateCall();
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void startVideoCapture() {
        if (videoCapturer != null) {
            videoCapturer.startCapture(1280, 720, 30);
        }
    }

    private void processUsersInRoom(List<HashMap<String, Object>> users) {
        List<String> newSessions = new ArrayList<>();
        Set<String> oldSessions = new HashSet<>();

        hasMCU = hasExternalSignalingServer && webSocketClient != null && webSocketClient.hasMCU();


        // The signaling session is the same as the Nextcloud session only when the MCU is not used.
        String currentSessiondId = callSession;
        if (hasMCU) {
            currentSessiondId = webSocketClient.getSessionId();
        }

        for (HashMap<String, Object> participant : users) {
            if (!participant.get("sessionId").equals(currentSessiondId)) {
                Object inCallObject = participant.get("inCall");
                boolean isNewSession;
                if (inCallObject instanceof Boolean) {
                    isNewSession = (boolean) inCallObject;
                } else {
                    isNewSession = ((long) inCallObject) != 0;
                }

                if (isNewSession) {
                    newSessions.add(participant.get("sessionId").toString());
                } else {
                    oldSessions.add(participant.get("sessionId").toString());
                }
            }
        }

        for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
            if (!magicPeerConnectionWrapper.isMCUPublisher()) {
                oldSessions.add(magicPeerConnectionWrapper.getSessionId());
            }
        }

        // Calculate sessions that left the call
        oldSessions.removeAll(newSessions);

        // Calculate sessions that join the call
        newSessions.removeAll(oldSessions);

        if (!isConnectionEstablished() && !currentCallStatus.equals(CallStatus.CONNECTING)) {
            return;
        }

        if (newSessions.size() > 0 && !hasMCU) {
            getPeersForCall();
        }

        if (hasMCU) {
            // Ensure that own publishing peer is set up.
            getPeerConnectionWrapperForSessionIdAndType(webSocketClient.getSessionId(), "video", true);
        }

        for (String sessionId : newSessions) {
            getPeerConnectionWrapperForSessionIdAndType(sessionId, "video", false);
        }

        if (newSessions.size() > 0 && !currentCallStatus.equals(CallStatus.IN_CONVERSATION)) {
            setCallState(CallStatus.IN_CONVERSATION);
        }

        for (String sessionId : oldSessions) {
            endPeerConnection(sessionId, false);
        }
    }

    private void getPeersForCall() {
        Log.d(TAG, "getPeersForCall");
        int apiVersion = ApiUtils.getCallApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForCall(apiVersion, baseUrl, roomToken))
            .subscribeOn(Schedulers.io())
            .subscribe(new Observer<ParticipantsOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull ParticipantsOverall participantsOverall) {
                    participantMap = new HashMap<>();
                    for (Participant participant : participantsOverall.getOcs().getData()) {
                        participantMap.put(participant.getSessionId(), participant);
                    }
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    Log.e(TAG, "error while executing getPeersForCall", e);
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void deleteMagicPeerConnection(MagicPeerConnectionWrapper magicPeerConnectionWrapper) {
        magicPeerConnectionWrapper.removePeerConnection();
        magicPeerConnectionWrapperList.remove(magicPeerConnectionWrapper);
    }

    private MagicPeerConnectionWrapper getPeerConnectionWrapperForSessionId(String sessionId, String type) {
        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            if (magicPeerConnectionWrapperList.get(i).getSessionId().equals(sessionId) && magicPeerConnectionWrapperList.get(i).getVideoStreamType().equals(type)) {
                return magicPeerConnectionWrapperList.get(i);
            }
        }

        return null;
    }

    private MagicPeerConnectionWrapper getPeerConnectionWrapperForSessionIdAndType(String sessionId, String type, boolean publisher) {
        MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        if ((magicPeerConnectionWrapper = getPeerConnectionWrapperForSessionId(sessionId, type)) != null) {
            return magicPeerConnectionWrapper;
        } else {
            if (hasMCU && publisher) {
                magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                                                                            iceServers,
                                                                            sdpConstraintsForMCU,
                                                                            sessionId, callSession,
                                                                            localMediaStream,
                                                                            true,
                                                                            true,
                                                                            type);

            } else if (hasMCU) {
                magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                                                                            iceServers,
                                                                            sdpConstraints,
                                                                            sessionId,
                                                                            callSession,
                                                                            null,
                                                                            false,
                                                                            true,
                                                                            type);
            } else {
                if (!"screen".equals(type)) {
                    magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                                                                                iceServers,
                                                                                sdpConstraints,
                                                                                sessionId,
                                                                                callSession,
                                                                                localMediaStream,
                                                                                false,
                                                                                false,
                                                                                type);
                } else {
                    magicPeerConnectionWrapper = new MagicPeerConnectionWrapper(peerConnectionFactory,
                                                                                iceServers,
                                                                                sdpConstraints,
                                                                                sessionId,
                                                                                callSession,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                type);
                }
            }

            magicPeerConnectionWrapperList.add(magicPeerConnectionWrapper);

            if (publisher) {
                startSendingNick();
            }

            return magicPeerConnectionWrapper;
        }
    }

    private List<MagicPeerConnectionWrapper> getPeerConnectionWrapperListForSessionId(String sessionId) {
        List<MagicPeerConnectionWrapper> internalList = new ArrayList<>();
        for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
            if (magicPeerConnectionWrapper.getSessionId().equals(sessionId)) {
                internalList.add(magicPeerConnectionWrapper);
            }
        }

        return internalList;
    }

    private void endPeerConnection(String sessionId, boolean justScreen) {
        List<MagicPeerConnectionWrapper> magicPeerConnectionWrappers;
        MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        if (!(magicPeerConnectionWrappers = getPeerConnectionWrapperListForSessionId(sessionId)).isEmpty()) {
            for (int i = 0; i < magicPeerConnectionWrappers.size(); i++) {
                magicPeerConnectionWrapper = magicPeerConnectionWrappers.get(i);
                if (magicPeerConnectionWrapper.getSessionId().equals(sessionId)) {
                    if (magicPeerConnectionWrapper.getVideoStreamType().equals("screen") || !justScreen) {
                        runOnUiThread(() -> removeMediaStream(sessionId));
                        deleteMagicPeerConnection(magicPeerConnectionWrapper);
                    }
                }
            }
        }
    }

    private void removeMediaStream(String sessionId) {
        Log.d(TAG, "removeMediaStream");
        participantDisplayItems.remove(sessionId);

        if (!isDestroyed()) {
            initGridAdapter();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ConfigurationChangeEvent configurationChangeEvent) {
        powerManagerUtils.setOrientation(Objects.requireNonNull(getResources()).getConfiguration().orientation);
        initGridAdapter();
        updateSelfVideoViewPosition();
    }

    private void updateSelfVideoViewPosition() {
        Log.d(TAG, "updateSelfVideoViewPosition");
        if (!isInPipMode) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) binding.selfVideoRenderer.getLayoutParams();

            DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
            int screenWidthPx = displayMetrics.widthPixels;

            int screenWidthDp = (int) DisplayUtils.convertPixelToDp(screenWidthPx, getApplicationContext());

            float newXafterRotate = 0;
            float newYafterRotate;
            if (binding.callInfosLinearLayout.getVisibility() == View.VISIBLE) {
                newYafterRotate = 250;
            } else {
                newYafterRotate = 20;
            }

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                layoutParams.height = (int) getResources().getDimension(R.dimen.large_preview_dimension);
                layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
                newXafterRotate = (float) (screenWidthDp - getResources().getDimension(R.dimen.large_preview_dimension) * 0.8);

            } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
                layoutParams.width = (int) getResources().getDimension(R.dimen.large_preview_dimension);
                newXafterRotate = (float) (screenWidthDp - getResources().getDimension(R.dimen.large_preview_dimension) * 0.5);
            }
            binding.selfVideoRenderer.setLayoutParams(layoutParams);

            int newXafterRotatePx = (int) DisplayUtils.convertDpToPixel(newXafterRotate, getApplicationContext());
            binding.selfVideoViewWrapper.setY(newYafterRotate);
            binding.selfVideoViewWrapper.setX(newXafterRotatePx);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(PeerConnectionEvent peerConnectionEvent) {
        String sessionId = peerConnectionEvent.getSessionId();

        if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType
                                                                        .PEER_CLOSED)) {
            endPeerConnection(sessionId, peerConnectionEvent.getVideoStreamType().equals("screen"));
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                                                                               .PeerConnectionEventType.SENSOR_FAR) ||
            peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                                                                        .PeerConnectionEventType.SENSOR_NEAR)) {

            if (!isVoiceOnlyCall) {
                boolean enableVideo = peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent
                                                                                                  .PeerConnectionEventType.SENSOR_FAR) && videoOn;
                if (EffortlessPermissions.hasPermissions(this, PERMISSIONS_CAMERA) &&
                    (currentCallStatus.equals(CallStatus.CONNECTING) || isConnectionEstablished()) && videoOn
                    && enableVideo != localVideoTrack.enabled()) {
                    toggleMedia(enableVideo, true);
                }
            }
        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType.NICK_CHANGE)) {
            if (participantDisplayItems.get(sessionId) != null) {
                participantDisplayItems.get(sessionId).setNick(peerConnectionEvent.getNick());
            }
            participantsAdapter.notifyDataSetChanged();

        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType.VIDEO_CHANGE) && !isVoiceOnlyCall) {
            if (participantDisplayItems.get(sessionId) != null) {
                participantDisplayItems.get(sessionId).setStreamEnabled(peerConnectionEvent.getChangeValue());
            }
            participantsAdapter.notifyDataSetChanged();

        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType.AUDIO_CHANGE)) {
            if (participantDisplayItems.get(sessionId) != null) {
                participantDisplayItems.get(sessionId).setAudioEnabled(peerConnectionEvent.getChangeValue());
            }
            participantsAdapter.notifyDataSetChanged();

        } else if (peerConnectionEvent.getPeerConnectionEventType().equals(PeerConnectionEvent.PeerConnectionEventType.PUBLISHER_FAILED)) {
            currentCallStatus = CallStatus.PUBLISHER_FAILED;
            webSocketClient.clearResumeId();
            hangup(false);
        }
    }

    private void startSendingNick() {
        DataChannelMessageNick dataChannelMessage = new DataChannelMessageNick();
        dataChannelMessage.setType("nickChanged");
        HashMap<String, String> nickChangedPayload = new HashMap<>();
        nickChangedPayload.put("userid", conversationUser.getUserId());
        nickChangedPayload.put("name", conversationUser.getDisplayName());
        dataChannelMessage.setPayload(nickChangedPayload);
        final MagicPeerConnectionWrapper magicPeerConnectionWrapper;
        for (int i = 0; i < magicPeerConnectionWrapperList.size(); i++) {
            if (magicPeerConnectionWrapperList.get(i).isMCUPublisher()) {
                magicPeerConnectionWrapper = magicPeerConnectionWrapperList.get(i);
                Observable
                    .interval(1, TimeUnit.SECONDS)
                    .repeatUntil(() -> (!isConnectionEstablished() || isDestroyed()))
                    .observeOn(Schedulers.io())
                    .subscribe(new Observer<Long>() {
                        @Override
                        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                            // unused atm
                        }

                        @Override
                        public void onNext(@io.reactivex.annotations.NonNull Long aLong) {
                            magicPeerConnectionWrapper.sendNickChannelData(dataChannelMessage);
                        }

                        @Override
                        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                            // unused atm
                        }

                        @Override
                        public void onComplete() {
                            // unused atm
                        }
                    });
                break;
            }

        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MediaStreamEvent mediaStreamEvent) {
        if (mediaStreamEvent.getMediaStream() != null) {
            boolean hasAtLeastOneVideoStream = mediaStreamEvent.getMediaStream().videoTracks != null
                && mediaStreamEvent.getMediaStream().videoTracks.size() > 0;

            setupVideoStreamForLayout(
                mediaStreamEvent.getMediaStream(),
                mediaStreamEvent.getSession(),
                hasAtLeastOneVideoStream,
                mediaStreamEvent.getVideoStreamType());
        } else {
            setupVideoStreamForLayout(
                null,
                mediaStreamEvent.getSession(),
                false,
                mediaStreamEvent.getVideoStreamType());
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(SessionDescriptionSendEvent sessionDescriptionSend) throws IOException {
        NCMessageWrapper ncMessageWrapper = new NCMessageWrapper();
        ncMessageWrapper.setEv("message");
        ncMessageWrapper.setSessionId(callSession);
        NCSignalingMessage ncSignalingMessage = new NCSignalingMessage();
        ncSignalingMessage.setTo(sessionDescriptionSend.getPeerId());
        ncSignalingMessage.setRoomType(sessionDescriptionSend.getVideoStreamType());
        ncSignalingMessage.setType(sessionDescriptionSend.getType());
        NCMessagePayload ncMessagePayload = new NCMessagePayload();
        ncMessagePayload.setType(sessionDescriptionSend.getType());

        if (!"candidate".equals(sessionDescriptionSend.getType())) {
            ncMessagePayload.setSdp(sessionDescriptionSend.getSessionDescription().description);
            ncMessagePayload.setNick(conversationUser.getDisplayName());
        } else {
            ncMessagePayload.setIceCandidate(sessionDescriptionSend.getNcIceCandidate());
        }


        // Set all we need
        ncSignalingMessage.setPayload(ncMessagePayload);
        ncMessageWrapper.setSignalingMessage(ncSignalingMessage);


        if (!hasExternalSignalingServer) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{")
                .append("\"fn\":\"")
                .append(StringEscapeUtils.escapeJson(LoganSquare.serialize(ncMessageWrapper.getSignalingMessage()))).append("\"")
                .append(",")
                .append("\"sessionId\":")
                .append("\"").append(StringEscapeUtils.escapeJson(callSession)).append("\"")
                .append(",")
                .append("\"ev\":\"message\"")
                .append("}");

            List<String> strings = new ArrayList<>();
            String stringToSend = stringBuilder.toString();
            strings.add(stringToSend);

            int apiVersion = ApiUtils.getSignalingApiVersion(conversationUser, new int[]{ApiUtils.APIv3, 2, 1});

            ncApi.sendSignalingMessages(credentials, ApiUtils.getUrlForSignaling(apiVersion, baseUrl, roomToken),
                                        strings.toString())
                .retry(3)
                .subscribeOn(Schedulers.io())
                .subscribe(new Observer<SignalingOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull SignalingOverall signalingOverall) {
                        receivedSignalingMessages(signalingOverall.getOcs().getSignalings());
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, "", e);
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });
        } else {
            webSocketClient.sendCallMessage(ncMessageWrapper);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EffortlessPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults,
                                                         this);
    }

    private void setupVideoStreamForLayout(@Nullable MediaStream mediaStream, String session, boolean videoStreamEnabled, String videoStreamType) {
        String nick;
        if (hasExternalSignalingServer) {
            nick = webSocketClient.getDisplayNameForSession(session);
        } else {
            nick = getPeerConnectionWrapperForSessionIdAndType(session, videoStreamType, false).getNick();
        }

        String userId = "";
        if (hasMCU) {
            userId = webSocketClient.getUserIdForSession(session);
        } else if (participantMap.get(session).getActorType() == Participant.ActorType.USERS) {
            userId = participantMap.get(session).getActorId();
        }

        String urlForAvatar;
        if (!TextUtils.isEmpty(userId)) {
            urlForAvatar = ApiUtils.getUrlForAvatarWithName(baseUrl,
                                                            userId,
                                                            R.dimen.avatar_size_big);
        } else {
            urlForAvatar = ApiUtils.getUrlForAvatarWithNameForGuests(baseUrl,
                                                                     nick,
                                                                     R.dimen.avatar_size_big);
        }

        ParticipantDisplayItem participantDisplayItem = new ParticipantDisplayItem(userId,
                                                                                   session,
                                                                                   nick,
                                                                                   urlForAvatar,
                                                                                   mediaStream,
                                                                                   videoStreamType,
                                                                                   videoStreamEnabled,
                                                                                   rootEglBase);
        participantDisplayItems.put(session, participantDisplayItem);

        initGridAdapter();
    }

    private void setCallState(CallStatus callState) {
        if (currentCallStatus == null || !currentCallStatus.equals(callState)) {
            currentCallStatus = callState;
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            } else {
                handler.removeCallbacksAndMessages(null);
            }

            switch (callState) {
                case CONNECTING:
                    handler.post(() -> {
                        playCallingSound();
                        if (isIncomingCallFromNotification) {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_incoming);
                        } else {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_ringing);
                        }
                        binding.callConversationNameTextView.setText(conversationName);

                        binding.callModeTextView.setText(getDescriptionForCallType());

                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }

                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case CALLING_TIMEOUT:
                    handler.post(() -> {
                        hangup(false);
                        binding.callStates.callStateTextView.setText(R.string.nc_call_timeout);
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.GONE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.GONE);
                        }

                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        binding.callStates.errorImageView.setImageResource(R.drawable.ic_av_timer_timer_24dp);

                        if (binding.callStates.errorImageView.getVisibility() != View.VISIBLE) {
                            binding.callStates.errorImageView.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                case RECONNECTING:
                    handler.post(() -> {
                        playCallingSound();
                        binding.callStates.callStateTextView.setText(R.string.nc_call_reconnecting);
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }
                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }
                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case JOINED:
                    handler.postDelayed(() -> setCallState(CallStatus.CALLING_TIMEOUT), 45000);
                    handler.post(() -> {
                        binding.callModeTextView.setText(getDescriptionForCallType());
                        if (isIncomingCallFromNotification) {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_incoming);
                        } else {
                            binding.callStates.callStateTextView.setText(R.string.nc_call_ringing);
                        }
                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                        }

                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case IN_CONVERSATION:
                    handler.post(() -> {
                        stopCallingSound();
                        binding.callModeTextView.setText(getDescriptionForCallType());

                        if (!isVoiceOnlyCall) {
                            binding.callInfosLinearLayout.setVisibility(View.GONE);
                        }

                        if (!isPTTActive) {
                            animateCallControls(false, 5000);
                        }

                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.INVISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.GONE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.GONE);
                        }

                        if (binding.gridview.getVisibility() != View.VISIBLE) {
                            binding.gridview.setVisibility(View.VISIBLE);
                        }

                        if (binding.callStates.errorImageView.getVisibility() != View.GONE) {
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                case OFFLINE:
                    handler.post(() -> {
                        stopCallingSound();

                        binding.callStates.callStateTextView.setText(R.string.nc_offline);

                        if (binding.callStates.callStateRelativeLayout.getVisibility() != View.VISIBLE) {
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                        }


                        if (binding.gridview.getVisibility() != View.INVISIBLE) {
                            binding.gridview.setVisibility(View.INVISIBLE);
                        }

                        if (binding.callStates.callStateProgressBar.getVisibility() != View.GONE) {
                            binding.callStates.callStateProgressBar.setVisibility(View.GONE);
                        }

                        binding.callStates.errorImageView.setImageResource(R.drawable.ic_signal_wifi_off_white_24dp);
                        if (binding.callStates.errorImageView.getVisibility() != View.VISIBLE) {
                            binding.callStates.errorImageView.setVisibility(View.VISIBLE);
                        }
                    });
                    break;
                case LEAVING:
                    handler.post(() -> {
                        if (!isDestroyed()) {
                            stopCallingSound();
                            binding.callModeTextView.setText(getDescriptionForCallType());
                            binding.callStates.callStateTextView.setText(R.string.nc_leaving_call);
                            binding.callStates.callStateRelativeLayout.setVisibility(View.VISIBLE);
                            binding.gridview.setVisibility(View.INVISIBLE);
                            binding.callStates.callStateProgressBar.setVisibility(View.VISIBLE);
                            binding.callStates.errorImageView.setVisibility(View.GONE);
                        }
                    });
                    break;
                default:
            }
        }
    }

    private String getDescriptionForCallType() {
        String appName = getResources().getString(R.string.nc_app_product_name);
        if (isVoiceOnlyCall) {
            return String.format(getResources().getString(R.string.nc_call_voice), appName);
        } else {
            return String.format(getResources().getString(R.string.nc_call_video), appName);
        }
    }

    private void playCallingSound() {
        stopCallingSound();
        Uri ringtoneUri;
        if (isIncomingCallFromNotification) {
            ringtoneUri = NotificationUtils.INSTANCE.getCallRingtoneUri(getApplicationContext(), appPreferences);
        } else {
            ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() + "/raw" +
                                        "/tr110_1_kap8_3_freiton1");
        }

        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setLooping(true);
            AudioAttributes audioAttributes = new AudioAttributes.Builder().setContentType(
                AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());

            mediaPlayer.prepareAsync();

        } catch (IOException e) {
            Log.e(TAG, "Failed to play sound");
        }
    }

    private void stopCallingSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private class MicrophoneButtonTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            v.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isPTTActive) {
                isPTTActive = false;
                binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
                pulseAnimation.stop();
                toggleMedia(false, false);
                animateCallControls(false, 5000);
            }
            return true;
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(NetworkEvent networkEvent) {
        if (networkEvent.getNetworkConnectionEvent()
            .equals(NetworkEvent.NetworkConnectionEvent.NETWORK_CONNECTED)) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
        } else if (networkEvent.getNetworkConnectionEvent()
            .equals(NetworkEvent.NetworkConnectionEvent.NETWORK_DISCONNECTED)) {
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        Log.d(TAG, "onPictureInPictureModeChanged");
        Log.d(TAG, "isInPictureInPictureMode= " + isInPictureInPictureMode);
        isInPipMode = isInPictureInPictureMode;
        if (isInPictureInPictureMode) {
            mReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent == null || !MICROPHONE_PIP_INTENT_NAME.equals(intent.getAction())) {
                            return;
                        }

                        final int action = intent.getIntExtra(MICROPHONE_PIP_INTENT_EXTRA_ACTION, 0);
                        switch (action) {
                            case MICROPHONE_PIP_REQUEST_MUTE:
                            case MICROPHONE_PIP_REQUEST_UNMUTE:
                                onMicrophoneClick();
                                break;
                        }
                    }
                };
            registerReceiver(mReceiver, new IntentFilter(MICROPHONE_PIP_INTENT_NAME));

            updateUiForPipMode();
        } else {
            unregisterReceiver(mReceiver);
            mReceiver = null;

            updateUiForNormalMode();
        }
    }

    void updatePictureInPictureActions(
        @DrawableRes int iconId,
        String title,
        int requestCode) {

        if (isGreaterEqualOreo() && isPipModePossible()) {
            final ArrayList<RemoteAction> actions = new ArrayList<>();

            final Icon icon = Icon.createWithResource(this, iconId);
            final PendingIntent intent =
                PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    new Intent(MICROPHONE_PIP_INTENT_NAME).putExtra(MICROPHONE_PIP_INTENT_EXTRA_ACTION, requestCode),
                    0);

            actions.add(new RemoteAction(icon, title, title, intent));

            mPictureInPictureParamsBuilder.setActions(actions);
            setPictureInPictureParams(mPictureInPictureParamsBuilder.build());
        }
    }

    public void updateUiForPipMode() {
        Log.d(TAG, "updateUiForPipMode");
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                             ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 0);
        binding.gridview.setLayoutParams(params);


        //binding.callControls.setVisibility(View.GONE);
        binding.requestsAndControlsLinearLayout.setVisibility(View.GONE); //hide this instead of above to cover both
        // request controls and normal controls
        binding.callInfosLinearLayout.setVisibility(View.GONE);
        binding.selfVideoViewWrapper.setVisibility(View.GONE);
        binding.callStates.callStateRelativeLayout.setVisibility(View.GONE);

        if (participantDisplayItems.size() > 1) {
            binding.pipCallConversationNameTextView.setText(conversationName);
            binding.pipGroupCallOverlay.setVisibility(View.VISIBLE);
        } else {
            binding.pipGroupCallOverlay.setVisibility(View.GONE);
        }

        binding.selfVideoRenderer.release();
    }

    public void updateUiForNormalMode() {
        Log.d(TAG, "updateUiForNormalMode");

        /*if (isVoiceOnlyCall) {
            binding.callControls.setVisibility(View.VISIBLE);
        } else {
            binding.callControls.setVisibility(View.INVISIBLE); // animateCallControls needs this to be invisible for a check.
        }*/

        binding.requestsAndControlsLinearLayout.setVisibility(View.VISIBLE); //always be visible
        initViews();

        binding.callInfosLinearLayout.setVisibility(View.VISIBLE);
        binding.selfVideoViewWrapper.setVisibility(View.VISIBLE);

        binding.pipGroupCallOverlay.setVisibility(View.GONE);
    }

    @Override
    void suppressFitsSystemWindows() {
        binding.controllerCallLayout.setFitsSystemWindows(false);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        eventBus.post(new ConfigurationChangeEvent());
    }

    private class SelfVideoTouchListener implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            long duration = event.getEventTime() - event.getDownTime();

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float newY = event.getRawY() - binding.selfVideoViewWrapper.getHeight() / (float) 2;
                float newX = event.getRawX() - binding.selfVideoViewWrapper.getWidth() / (float) 2;
                binding.selfVideoViewWrapper.setY(newY);
                binding.selfVideoViewWrapper.setX(newX);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && duration < 100) {
                switchCamera();
            }
            return true;
        }
    }

    //kikao stuff

    private void initKikaoControls(){
        //save session

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(KikaoUtilitiesConstants.ACTIVE_SESSION_ID, roomToken);
        editor.apply();

        //show controls according to room type

        Log.d(TAG, "The conversation type type is: "+conversationType);
        if (conversationType.equals(Conversation.ConversationType.ROOM_GROUP_CALL)){
            showStaffControls();
        }else if (conversationType.equals(Conversation.ConversationType.ROOM_PLENARY_CALL)){
            showPlenaryControlsDisabled();
            updateKikaoPermissions();
            updateKikaoVotes();
        }else if (conversationType.equals(Conversation.ConversationType.ROOM_COMMITTEE_CALL)){
            showCommitteeControlsDisabled();
            updateKikaoPermissions();
            updateKikaoVotes();
        }else{
            showStaffControls();
        }

        binding.requestToSpeakButton.setOnClickListener(l ->{
            Log.d(TAG, "Request to speak clicked");

            if (binding.requestToSpeakButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.action_cancel))){
                cancelRequestPermissionToSpeakNetworkCall();
            }else{
                requestPermissionToSpeakNetworkCall();
            }
        });

        binding.requestToInterveneButton.setOnClickListener(l ->{
            Log.d(TAG, "Request to intervene clicked");

            if (binding.requestToInterveneButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.action_cancel))){
                cancelRequestPermissionToInterveneNetworkCall();
            }else{
                requestPermissionToInterveneNetworkCall();
            }
        });

        binding.callControlRaiseHand.setOnClickListener(l ->{

            handRaised = !handRaised;

            if (handRaised){
                Log.d(TAG, "Hand raised");

                //show hand raised icon
                binding.callControlRaiseHand.getHierarchy().setPlaceholderImage(R.drawable.ic_hand);
            }else{
                Log.d(TAG, "Hand lowered");

                //show hand lowered icon
                binding.callControlRaiseHand.getHierarchy().setPlaceholderImage(R.drawable.ic_hand_off);
            }

            if (isConnectionEstablished() && magicPeerConnectionWrapperList != null) {
                if (!hasMCU) {
                    for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
                        magicPeerConnectionWrapper.raiseHand(magicPeerConnectionWrapper.getSessionId(),handRaised);
                    }
                } else {
                    for (MagicPeerConnectionWrapper magicPeerConnectionWrapper : magicPeerConnectionWrapperList) {
                        magicPeerConnectionWrapper.raiseHand(magicPeerConnectionWrapper.getSessionId(),handRaised);
                    }
                }
            }
        });
    }

    private void requestToSpeakStartLoading(){

    }

    private void requestToInterveneStartLoading(){

    }

    private void showRequestToSpeakButtonSuccess() {

    }

    private void showRequestToSpeakLoadingError() {

    }

    private void showRequestToInterveneButtonSuccess() {

    }

    private void showRequestToInterveneLoadingError() {

    }

    private void requestPermissionToSpeakNetworkCall() {

        JSONObject json = new JSONObject();
        try {
            json.put("token", roomToken);
            json.put("userId", conversationUser.getUserId());
            json.put("activityType", 0);
            json.put("approved", false);
            json.put("started", false);
            json.put("paused", false);
            json.put("canceled", false);
            json.put("duration", 0);
            json.put("talkingSince", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        requestToSpeakStartLoading();


        Log.d(TAG,"Calling apiservice");
        apiService.requestToSpeak(credentials, RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<RequestToActionGenericResult>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull RequestToActionGenericResult requestToActionGenericResult) {
                    showRequestToSpeakButtonSuccess();
                    binding.requestToSpeakButton.setText(getResources().getString(R.string.action_cancel));
                    binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                   R.color.kikao_danger));
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    showRequestToSpeakLoadingError();
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void requestPermissionToInterveneNetworkCall() {
        JSONObject json = new JSONObject();
        try {
            json.put("token", roomToken);
            json.put("userId", conversationUser.getUserId());
            json.put("activityType", 1);
            json.put("approved", false);
            json.put("started", false);
            json.put("paused", false);
            json.put("canceled", false);
            json.put("duration", 0);
            json.put("talkingSince", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        requestToInterveneStartLoading();

        Log.d(TAG,"Calling apiservice");
        apiService.requestToIntervene(credentials, RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<RequestToActionGenericResult>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull RequestToActionGenericResult requestToActionGenericResult) {
                    binding.requestToInterveneButton.setText(getResources().getString(R.string.action_cancel));
                    binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                       R.color.kikao_danger));
                    showRequestToInterveneButtonSuccess();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    showRequestToInterveneLoadingError();
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void cancelRequestPermissionToSpeakNetworkCall() {

        JSONObject json = new JSONObject();
        try {
            json.put("token", roomToken);
            json.put("userId", conversationUser.getUserId());
            json.put("activityType", 0);
            json.put("approved", false);
            json.put("started", false);
            json.put("paused", false);
            json.put("canceled", true);
            json.put("duration", 0);
            json.put("talkingSince", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        requestToSpeakStartLoading();

        Log.d(TAG,"Calling apiservice");
        apiService.cancelRequestToSpeak(credentials,sharedPref.getInt(KikaoUtilitiesConstants.ACTIVE_SESSION_REQUEST_TO_SPEAK_ID, 0), roomToken, RequestBody.create(MediaType.parse(
            "application/json"), json.toString()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<RequestToActionGenericResult>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull RequestToActionGenericResult requestToActionGenericResult) {
                    showRequestToSpeakButtonSuccess();

                    binding.requestToSpeakButton.setText(getResources().getString(R.string.kikao_request_to_speak));
                    binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                   R.color.colorPrimary));
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    showRequestToSpeakLoadingError();
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void cancelRequestPermissionToInterveneNetworkCall() {

        JSONObject json = new JSONObject();
        try {
            json.put("token", roomToken);
            json.put("userId", conversationUser.getUserId());
            json.put("activityType", 1);
            json.put("approved", false);
            json.put("started", false);
            json.put("paused", false);
            json.put("canceled", true);
            json.put("duration", 0);
            json.put("talkingSince", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        requestToInterveneStartLoading();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Log.d(TAG,"Calling apiservice");
        apiService.cancelRequestToIntervene(credentials,sharedPref.getInt(KikaoUtilitiesConstants.ACTIVE_SESSION_REQUEST_TO_INTERVENE_ID, 0),
                                            roomToken,
                                            RequestBody.create(MediaType.parse(
                                                "application/json"), json.toString()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<RequestToActionGenericResult>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull RequestToActionGenericResult requestToActionGenericResult) {
                    binding.requestToInterveneButton.setText(getResources().getString(R.string.kikao_request_to_intervene));
                    binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                       R.color.colorPrimary));
                    showRequestToInterveneButtonSuccess();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    showRequestToInterveneLoadingError();
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void updateKikaoPermissionsNetworkCall(){

        apiService.getSpeakerActionResponses(credentials,roomToken )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<List<RequestToActionGenericResult>>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    mCompositeDisposable.add(d);
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull List<RequestToActionGenericResult> lists) {
                    RequestToActionGenericResult requestToActionGenericResult = new RequestToActionGenericResult();

                    if (lists.size() > 0) {

                        for (int i= 0; i < lists.size();i++) {
                            RequestToActionGenericResult item = lists.get(i);
                            if (item.getUserId().equalsIgnoreCase(conversationUser.getUserId())) {
                                requestToActionGenericResult = item;

                                allocatedSpeakTime = requestToActionGenericResult.getDuration() / 60; //to get it in minutes
                                talkingSince = requestToActionGenericResult.getTalkingSince();

                                //perform action based on result
                                if (requestToActionGenericResult.getActivityType() == KikaoUtilitiesConstants.ACTION_REQUEST_TO_SPEAK) {
                                    speakerIsApproved = requestToActionGenericResult.getApproved();

                                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                    SharedPreferences.Editor editor = sharedPref.edit();
                                    editor.putInt(KikaoUtilitiesConstants.ACTIVE_SESSION_REQUEST_TO_SPEAK_ID,
                                                  requestToActionGenericResult.getId());
                                    editor.apply();

                                    //permission granted
                                    if (requestToActionGenericResult.getApproved()) {
                                        // minutes
                                        if (requestToActionGenericResult.getPaused()) {
                                            //user paused
                                            manageControls(KikaoUtilitiesConstants.ACTION_PAUSE_USER);

                                        } else if (requestToActionGenericResult.getCanceled()) {
                                            binding.requestToSpeakButton.setText(getResources().getString(R.string.kikao_request_to_speak));
                                            binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                           R.color.colorPrimary));
                                            manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);

                                        } else {
                                            binding.requestToSpeakButton.setText(getResources().getString(R.string.action_cancel));
                                            binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                           R.color.kikao_danger));
                                            manageControls(KikaoUtilitiesConstants.ACTION_RESUME_USER);
                                        }
                                    }else if (requestToActionGenericResult.getCanceled()) {
                                        binding.requestToSpeakButton.setText(getResources().getString(R.string.kikao_request_to_speak));
                                        binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                       R.color.colorPrimary));
                                        manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);

                                    } else {

                                        binding.requestToSpeakButton.setText(getResources().getString(R.string.action_cancel));
                                        binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                       R.color.kikao_danger));
                                        manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);
                                    }
                                } else if (requestToActionGenericResult.getActivityType() == KikaoUtilitiesConstants.ACTION_REQUEST_TO_INTERVENE) {
                                    speakerIsApproved = requestToActionGenericResult.getApproved();
                                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                    SharedPreferences.Editor editor = sharedPref.edit();
                                    editor.putInt(KikaoUtilitiesConstants.ACTIVE_SESSION_REQUEST_TO_INTERVENE_ID,
                                                  requestToActionGenericResult.getId());
                                    editor.apply();
                                    //permission granted
                                    if (requestToActionGenericResult.getApproved()) {
                                        // minutes
                                        if (requestToActionGenericResult.getPaused()) {
                                            //user paused
                                            manageControls(KikaoUtilitiesConstants.ACTION_PAUSE_USER);

                                        } else if (requestToActionGenericResult.getCanceled()) {
                                            binding.requestToInterveneButton.setText(getResources().getString(R.string.kikao_request_to_intervene));
                                            binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                               R.color.colorPrimary));
                                            manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);

                                        } else {
                                            binding.requestToInterveneButton.setText(getResources().getString(R.string.action_cancel));
                                            binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                               R.color.kikao_danger));
                                            manageControls(KikaoUtilitiesConstants.ACTION_RESUME_USER);
                                        }
                                    } else if (requestToActionGenericResult.getCanceled()) {
                                        binding.requestToInterveneButton.setText(getResources().getString(R.string.kikao_request_to_intervene));
                                        binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                           R.color.colorPrimary));
                                        manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);

                                    } else {
                                        binding.requestToInterveneButton.setText(getResources().getString(R.string.action_cancel));
                                        binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                           R.color.kikao_danger));
                                        manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);
                                    }
                                }

                                break;
                            } else {

                                //cancel user only afer looping through all others
                                if (i == lists.size()-1 ) {
                                    binding.requestToSpeakButton.setText(getResources().getString(R.string.kikao_request_to_speak));
                                    binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                           R.color.colorPrimary));
                                    binding.requestToInterveneButton.setText(getResources().getString(R.string.kikao_request_to_intervene));
                                    binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                                               R.color.colorPrimary));
                                    manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);
                                }
                            }
                        }
                    }else{
                        binding.requestToSpeakButton.setText(getResources().getString(R.string.kikao_request_to_speak));
                        binding.requestToSpeakButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                       R.color.colorPrimary));

                        binding.requestToInterveneButton.setText(getResources().getString(R.string.kikao_request_to_intervene));
                        binding.requestToInterveneButton.setBackgroundColor(ContextCompat.getColor(getApplicationContext(),
                                                                                           R.color.colorPrimary));
                        manageControls(KikaoUtilitiesConstants.ACTION_CANCEL_USER);
                    }

                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    //todo show error to user
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void manageControls(String action){
        Log.d(TAG, "Speaker unmuted: "+speakerhasUnmuted);
        Log.d(TAG, "Action called: "+action);
        if (action.equals(KikaoUtilitiesConstants.ACTION_NONE)){

        }else if(action.equals(KikaoUtilitiesConstants.ACTION_PAUSE_USER)){
            if (conversationType.equals(Conversation.ConversationType.ROOM_PLENARY_CALL)) {
                pauseTimer(true);
            }
        }else if(action.equals(KikaoUtilitiesConstants.ACTION_RESUME_USER)){
            //todo unpause timer logic or add another Constant -> ACTION_UNPAUSE_USER
            //if microphone is already visible then no need
            if (binding.microphoneButton.getVisibility() != View.VISIBLE) {
                if (conversationType.equals(Conversation.ConversationType.ROOM_COMMITTEE_CALL)) {
                    showCommitteeControlsEnabled();
                } else if (conversationType.equals(Conversation.ConversationType.ROOM_PLENARY_CALL)) {
                    showPlenaryControlsEnabled();
                }
            }
        }else if(action.equals(KikaoUtilitiesConstants.ACTION_CANCEL_USER)){
            if (conversationType.equals(Conversation.ConversationType.ROOM_COMMITTEE_CALL)) {
                showCommitteeControlsDisabled();
            } else if (conversationType.equals(Conversation.ConversationType.ROOM_PLENARY_CALL)) {
                showPlenaryControlsDisabled();
            }
        }
    }

    private void showStaffControls(){

        //make raise hand visible
        binding.callControlRaiseHand.setVisibility(View.VISIBLE);
    }

    private void showCommitteeControlsDisabled(){
        Log.d(TAG, "Show commitee controls");
        if (binding.requestsLinearLayout!=null){
            binding.requestsLinearLayout.setVisibility(View.VISIBLE);
            if (binding.timeLeftButton!=null){
                binding.timeLeftButton.setVisibility(View.GONE);
            }
        }


        binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
        binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
        binding.microphoneButton.setVisibility(View.GONE);
        binding.cameraButton.setVisibility(View.GONE);
        binding.switchSelfVideoButton.setVisibility(View.GONE);
        binding.selfVideoRenderer.setVisibility(View.GONE);

        //cancel timer
        if(countDownTimer!=null){
            countDownTimer.cancel();
        }

        //mute mic and camera
        audioOn = false;
        videoOn = false;

        //disable audio
        toggleMedia(audioOn, false);
        //disable video
        toggleMedia(videoOn, true);


    }

    private void showPlenaryControlsDisabled(){
        Log.d(TAG, "Show plenary controls");

        //cancel timer
        if(countDownTimer!=null){
            countDownTimer.cancel();
        }

        if (binding.requestsLinearLayout!=null){
            binding.requestsLinearLayout.setVisibility(View.VISIBLE);
            if (binding.timeLeftButton!=null){
                binding.timeLeftButton.setVisibility(View.GONE);
            }
        }

        speakTimerStarted = false;
        speakerhasUnmuted = false;

        binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
        binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
        binding.microphoneButton.setVisibility(View.GONE);
        binding.cameraButton.setVisibility(View.GONE);
        binding.switchSelfVideoButton.setVisibility(View.GONE);
        binding.selfVideoRenderer.setVisibility(View.GONE);

        //mute mic and camera
        audioOn = false;
        videoOn = false;

        //disable audio
        toggleMedia(audioOn, false);
        //disable video
        toggleMedia(videoOn, true);


    }

    private void showCommitteeControlsEnabled(){
        Log.d(TAG, "Show commitee controls");
        if (binding.requestsLinearLayout!=null){
            binding.requestsLinearLayout.setVisibility(View.VISIBLE);
            if (binding.timeLeftButton!=null){
                binding.timeLeftButton.setVisibility(View.GONE);
            }
        }

        binding. microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
        binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
        if (isVoiceOnlyCall) {
            binding.speakerButton.setVisibility(View.VISIBLE);
            binding.microphoneButton.setVisibility(View.VISIBLE);
            binding.cameraButton.setVisibility(View.GONE);
            binding.cameraButton.setVisibility(View.GONE);
            binding.selfVideoRenderer.setVisibility(View.GONE);
        } else {
            binding.speakerButton.setVisibility(View.GONE);
            binding.microphoneButton.setVisibility(View.VISIBLE);
            binding.cameraButton.setVisibility(View.VISIBLE);
            binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
            binding.selfVideoRenderer.setVisibility(View.VISIBLE);
            if (cameraEnumerator.getDeviceNames().length < 2) {
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
        }


    }

    private void showPlenaryControlsEnabled(){
        Log.d(TAG, "Show plenary controls");
        if (binding.requestsLinearLayout!=null){
            binding.requestsLinearLayout.setVisibility(View.VISIBLE);
            if (binding.timeLeftButton!=null){
                binding.timeLeftButton.setVisibility(View.VISIBLE);
            }
        }

        binding.microphoneButton.getHierarchy().setPlaceholderImage(R.drawable.ic_mic_off_white_24px);
        binding.cameraButton.getHierarchy().setPlaceholderImage(R.drawable.ic_videocam_off_white_24px);
        if (isVoiceOnlyCall) {
            binding.speakerButton.setVisibility(View.VISIBLE);
            binding.microphoneButton.setVisibility(View.VISIBLE);
            binding.cameraButton.setVisibility(View.GONE);
            binding.cameraButton.setVisibility(View.GONE);
            binding.selfVideoRenderer.setVisibility(View.GONE);
        } else {
            binding.speakerButton.setVisibility(View.GONE);
            binding.microphoneButton.setVisibility(View.VISIBLE);
            binding.cameraButton.setVisibility(View.VISIBLE);
            binding.switchSelfVideoButton.setVisibility(View.VISIBLE);
            binding.selfVideoRenderer.setVisibility(View.VISIBLE);
            if (cameraEnumerator.getDeviceNames().length < 2) {
                binding.switchSelfVideoButton.setVisibility(View.GONE);
            }
        }

        //show timer and wait to start
        initTimer(allocatedSpeakTime);

    }

    private void initTimer(Integer durationInMinutes){
        String durationInMinutesStr = String.format("%02d:%02d",
                                                    TimeUnit.MILLISECONDS.toMinutes(durationInMinutes*60000),
                                                    TimeUnit.MILLISECONDS.toSeconds(durationInMinutes*60000) -
                                                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationInMinutes*60000))
                                                   );
        if (binding.timeLeftButton!=null) {
            binding.timeLeftButton.setText(durationInMinutesStr);
        }
        countDownTimer =  new CountDownTimer(durationInMinutes*60000, 1000) {
            public void onTick(long millisUntilFinished) {
                String allocatedSpeakTimeStr = String.format("%02d:%02d",
                                                             TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished),
                                                             TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                                                                 TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished))
                                                            );
                if (binding.timeLeftButton!=null) {
                    binding.timeLeftButton.setText(allocatedSpeakTimeStr);
                }

                if (millisUntilFinished/1000 < 30) {
                    binding.timeLeftButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
                        "#" + Integer.toHexString (getResources().getColor(R.color.kikao_danger)))));
                    Animation anim = new AlphaAnimation(0.0f, 1.0f);
                    anim.setDuration(500); //You can manage the blinking time with this parameter
                    anim.setStartOffset(20);
                    anim.setRepeatMode(Animation.REVERSE);
                    anim.setRepeatCount(Animation.INFINITE);
                    binding.timeLeftButton.startAnimation(anim);
                }else if (millisUntilFinished/1000 < 60) {
                    binding.timeLeftButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
                        "#" + Integer.toHexString (getResources().getColor(R.color.kikao_warning)))));
                }


            }
            public void onFinish() {
                binding.timeLeftButton.clearAnimation();
                binding.timeLeftButton.clearFocus();
                binding.timeLeftButton.setText("Done!");
                binding.requestToSpeakButton.setText(R.string.kikao_request_to_speak);
                binding.requestToSpeakButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
                    "#" + Integer.toHexString (getResources().getColor(R.color.colorPrimary)))));
                binding.requestToInterveneButton.setText(R.string.kikao_request_to_intervene);
                binding.requestToInterveneButton.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(
                    "#" + Integer.toHexString (getResources().getColor(R.color.colorPrimary)))));
                showPlenaryControlsDisabled();
                //todo make API call that speaker time is finished
            }
        };
    }

    private void pauseTimer(Boolean pause){
        //check if this incoming request is meant for us
        if (pause) {
            if (countDownTimer != null) {
                countDownTimer.pause();
            }
        } else {
            if (countDownTimer != null) {
                countDownTimer.resume();
            }
        }
    }

    private void startTimer(){
        if (countDownTimer != null) {
            countDownTimer.start();
        }
    }

    private void updateKikaoPermissions(){
        Log.d(TAG, "Calling kikao");
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateKikaoPermissionsNetworkCall();
            }
        }, 0, 10000);//put here time 1000 milliseconds=1 second

    }

    private void updateKikaoVotes(){
        Log.d(TAG, "Calling votes kikao");
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateVotesNetworkCall();
            }
        }, 0, 10000);//put here time 1000 milliseconds=1 second

    }

    private void updateVotesNetworkCall() {
        Log.d(TAG, "User token user"+ new Gson().toJson(conversationUser).toString());
        Log.d(TAG, "User token password"+ new Gson().toJson(conversationPassword).toString());
        ncApi.fetchVotes(credentials, ApiUtils.getUrlForKikaoVotes(baseUrl, roomToken))
            .retry(3)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<PollsResult>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    mCompositeDisposable.add(d);
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull PollsResult pollsResult) {
                   Log.d(TAG, "Votes fetched");
                   //todo show request otp if voting is enabled
                    if (pollsResult.votes.size() > 0){
                        binding.requestOtpLinearLayout.setVisibility(View.VISIBLE);
                    }else{
                        binding.requestOtpLinearLayout.setVisibility(View.GONE);
                    }

                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                    Log.d(TAG, "Votes error occurred");
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void requestOtpNetworkCall(){
        Log.d(TAG, "User token user"+ new Gson().toJson(conversationUser).toString());
        Log.d(TAG, "User token password"+ new Gson().toJson(conversationPassword).toString());

        binding.requestOtpButton.startAnimation();

        JSONObject json = new JSONObject();
        try {
            json.put("token", roomToken);
            json.put("userId", conversationUser.getUserId());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        ncApi.sendOtp(credentials, ApiUtils.getUrlForRequestOtp(baseUrl),  RequestBody.create(MediaType.parse("application/json"), json.toString()))
            .retry(3)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<Response<ResponseBody>>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    mCompositeDisposable.add(d);
                }

                @Override
                public void onNext(@io.reactivex.annotations.NonNull Response<ResponseBody> result) {
                    Log.d(TAG, "Votes fetched");
                    //todo show enter otp and hide sent otp button

                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            //Write whatever to want to do after delay specified (1 sec)
                            Log.d("Handler", "Running Handler");
                            binding.requestOtpButton.revertAnimation();
                        }
                    }, 1000);


                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    // unused atm
                    Log.d(TAG, "Votes error occurred");
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

}
