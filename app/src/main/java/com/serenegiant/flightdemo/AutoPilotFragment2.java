package com.serenegiant.flightdemo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.serenegiant.arflight.DeviceInfo;
import com.serenegiant.arflight.DroneStatus;
import com.serenegiant.arflight.ICameraController;
import com.serenegiant.arflight.IDeviceController;
import com.serenegiant.arflight.IFlightController;
import com.serenegiant.arflight.VideoStream;
import com.serenegiant.arflight.attribute.AttributeFloat;
import com.serenegiant.dialog.SelectFileDialogFragment;
import com.serenegiant.drone.AttitudeScreenBase;
import com.serenegiant.gameengine1.IModelView;
import com.serenegiant.math.Vector;
import com.serenegiant.opencv.ImageProcessor;
import com.serenegiant.utils.FileUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static com.serenegiant.flightdemo.AppConst.*;

public class AutoPilotFragment2 extends BasePilotFragment {
	private static final boolean DEBUG = true; // FIXME 実働時はfalseにすること
	private static final String TAG = AutoPilotFragment2.class.getSimpleName();

	public static AutoPilotFragment2 newInstance(final ARDiscoveryDeviceService device, final String pref_name) {

		final AutoPilotFragment2 fragment = new AutoPilotFragment2();
		final Bundle args = fragment.setDevice(device);
		fragment.mPrefName =  TextUtils.isEmpty(pref_name) ? TAG : pref_name;
		args.putString(KEY_PREF_NAME_AUTOPILOT, fragment.mPrefName);
		return fragment;
	}

	public static AutoPilotFragment2 newInstance(final ARDiscoveryDeviceService device, final DeviceInfo info, final String pref_name) {

		if (!BuildConfig.USE_SKYCONTROLLER) throw new RuntimeException("does not support skycontroller now");
		final AutoPilotFragment2 fragment = new AutoPilotFragment2();
		final Bundle args = fragment.setBridge(device, info);
		fragment.mPrefName =  TextUtils.isEmpty(pref_name) ? TAG : pref_name;
		args.putString(KEY_PREF_NAME_AUTOPILOT, fragment.mPrefName);
		return fragment;
	}

	private ViewGroup mControllerFrame;			// 操作パネル全体

	// 上パネル
	private View mTopPanel;
	private TextView mBatteryLabel;			// バッテリー残量表示
	private ImageButton mFlatTrimBtn;		// フラットトリム
	private TextView mAlertMessage;			// 非常メッセージ

	// 下パネル
	private View mBottomPanel;
	private ImageButton mEmergencyBtn;		// 非常停止ボタン
	private ImageButton mTakeOnOffBtn;		// 離陸/着陸ボタン
	private ImageButton mRecordBtn;			// 記録ボタン
	private TextView mRecordLabel;
	private ImageButton mConfigShowBtn;		// 設定パネル表示ボタン
	private TextView mTimeLabelTv;

	// 右サイドパネル
	private View mRightSidePanel;
	private ImageButton mStillCaptureBtn;
	private ImageButton mVideoRecordingBtn;
	private ImageButton mTraceButton;

	/** 操縦に使用するボタン等の一括変更用。操作可・不可に応じてenable/disableを切り替える */
	private final List<View> mActionViews = new ArrayList<View>();

	protected SurfaceView mDetectView;
	protected ImageProcessor mImageProcessor;
	protected TraceTask mTraceTask;
	protected Switch mAutoWhiteBlanceSw;
	private TextView mTraceTv1, mTraceTv2;

	// 設定
	protected String mPrefName;
	protected SharedPreferences mPref;

	public AutoPilotFragment2() {
		super();
		// デフォルトコンストラクタが必要
	}

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		if (DEBUG) Log.v(TAG, "onAttach");
		mPref = activity.getSharedPreferences(mPrefName, 0);
	}

	@Override
	public void onDetach() {
		if (DEBUG) Log.v(TAG, "onDetach");
		mPref = null;
		super.onDetach();
	}

	@Override
	protected View internalCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState, final int layout_id) {
		// パラメータの読み込み
		mAutoWhiteBlance = mPref.getBoolean(KEY_AUTO_WHITE_BLANCE, false);
		mExposure = mPref.getFloat(KEY_EXPOSURE, 0.0f);
		mSaturation = mPref.getFloat(KEY_SATURATION, 0.0f);
		mBrightness = mPref.getFloat(KEY_BRIGHTNESS, 0.0f);
		mPosterize = mPref.getFloat(KEY_POSTERIZE, 10);
		mEnablePosterize = mPref.getBoolean(KEY_ENABLE_POSTERIZE, false);
		mBinarizeThreshold = mPref.getFloat(KEY_BINARIZE_THRESHOLD, 0.5f);
		mTrapeziumRate = Double.parseDouble(mPref.getString(KEY_TRAPEZIUM_RATE, "0.0"));
		if (Math.abs(mTrapeziumRate) < 0.01) mTrapeziumRate = 0.0;
		//
		mEnableGLESExtraction = mPref.getBoolean(KEY_ENABLE_EXTRACTION, true);
		mGLESSmoothType = getInt(mPref, KEY_SMOOTH_TYPE, 0);
		mEnableGLESCanny = mPref.getBoolean(KEY_ENABLE_EDGE_DETECTION, false);
		mEnableNativeExtraction = mPref.getBoolean(KEY_ENABLE_NATIVE_EXTRACTION, false);
		mEnableNativeCanny = mPref.getBoolean(KEY_ENABLE_NATIVE_EDGE_DETECTION, false);
		mNativeSmoothType = getInt(mPref, KEY_NATIVE_SMOOTH_TYPE, 0);
		mAreaLimitMin = mPref.getFloat(KEY_AREA_LIMIT_MIN, 1000.0f);
		mAspectLimitMin = mPref.getFloat(KEY_ASPECT_LIMIT_MIN, 3.0f);
		mAreaErrLimit1 = mPref.getFloat(KEY_AREA_ERR_LIMIT1, 1.25f);
		mAreaErrLimit2 = mPref.getFloat(KEY_AREA_ERR_LIMIT2, 1.3f);
		//
		mFlightAttitudeYaw = mPref.getFloat(KEY_TRACE_FLIGHT_ATTITUDE_YAW, 0.0f);
		mFlightSpeed = mPref.getFloat(KEY_TRACE_FLIGHT_SPEED, 100.0f);

		// Viewの取得・初期化
		mActionViews.clear();

		final LayoutInflater local_inflater = getThemedLayoutInflater(inflater);
		final ViewGroup rootView = (ViewGroup) local_inflater.inflate(R.layout.fragment_pilot_auto, container, false);

		mControllerFrame = (ViewGroup) rootView.findViewById(R.id.controller_frame);
		mControllerFrame.setOnClickListener(mOnClickListener);

// 上パネル
		mTopPanel = rootView.findViewById(R.id.top_panel);
		mTopPanel.setOnClickListener(mOnClickListener);
		mTopPanel.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mTopPanel);
		// フラットトリムボタン
		mFlatTrimBtn = (ImageButton) rootView.findViewById(R.id.flat_trim_btn);
		mFlatTrimBtn.setOnClickListener(mOnClickListener);
		mFlatTrimBtn.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mFlatTrimBtn);
		// 設定表示ボタン
		mConfigShowBtn = (ImageButton) rootView.findViewById(R.id.config_show_btn);
		mConfigShowBtn.setOnClickListener(mOnClickListener);
		//
		mBatteryLabel = (TextView) rootView.findViewById(R.id.batteryLabel);
		mAlertMessage = (TextView) rootView.findViewById(R.id.alert_message);
		mAlertMessage.setVisibility(View.INVISIBLE);

// 下パネル
		// 非常停止ボタン
		mBottomPanel = rootView.findViewById(R.id.bottom_panel);
		mEmergencyBtn = (ImageButton) rootView.findViewById(R.id.emergency_btn);
		mEmergencyBtn.setOnClickListener(mOnClickListener);
		// 離着陸指示ボタン
		mTakeOnOffBtn = (ImageButton) rootView.findViewById(R.id.take_onoff_btn);
		mTakeOnOffBtn.setOnClickListener(mOnClickListener);
		mTakeOnOffBtn.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mTakeOnOffBtn);
		// 記録ボタン
		mRecordBtn = (ImageButton) rootView.findViewById(R.id.record_btn);
		mRecordBtn.setOnClickListener(mOnClickListener);
		mRecordBtn.setOnLongClickListener(mOnLongClickListener);
		// 記録ラベル
		mRecordLabel = (TextView) rootView.findViewById(R.id.record_label);
		// 時間ボタン
		mTimeLabelTv = (TextView) rootView.findViewById(R.id.time_label);
		setChildVisibility(mTimeLabelTv, View.INVISIBLE);

// 右サイドパネル
		mRightSidePanel = rootView.findViewById(R.id.right_side_panel);
		mActionViews.add(mRightSidePanel);

		// 静止画撮影
		mStillCaptureBtn = (ImageButton) rootView.findViewById(R.id.still_capture_btn);
		mStillCaptureBtn.setOnClickListener(mOnClickListener);

		// 動画撮影
		mVideoRecordingBtn = (ImageButton) rootView.findViewById(R.id.video_capture_btn);
		mVideoRecordingBtn.setOnClickListener(mOnClickListener);

		// トレース実行
		mTraceButton = (ImageButton)rootView.findViewById(R.id.trace_btn);
		mTraceButton.setOnClickListener(mOnClickListener);
		mTraceButton.setOnLongClickListener(mOnLongClickListener);

		if (mController instanceof ICameraController) {
			((ICameraController)mController).setCameraControllerListener(null);
			((ICameraController)mController).sendCameraOrientation(0, 0);
		}

		mModelView = (IModelView)rootView.findViewById(R.id.drone_view);
		mModelView.setModel(IModelView.MODEL_NON, AttitudeScreenBase.CTRL_ATTITUDE);
		((View)mModelView).setOnClickListener(mOnClickListener);

		mDetectView = (SurfaceView)rootView.findViewById(R.id.detect_view);
		mDetectView.setVisibility(View.VISIBLE);
//--------------------------------------------------------------------------------
		final ConfigPagerAdapter adapter = new ConfigPagerAdapter(inflater);
		final ViewPager pager = (ViewPager)rootView.findViewById(R.id.pager);
		pager.setAdapter(adapter);
		//
		mTraceTv1 = (TextView)rootView.findViewById(R.id.trace1_tv);
		mTraceTv2 = (TextView)rootView.findViewById(R.id.trace2_tv);

		return rootView;
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
//			if (DEBUG) Log.v(TAG, "onClick:" + view);
			switch (view.getId()) {
			case R.id.flat_trim_btn:
				// フラットトリム
				setColorFilter((ImageView)view);
				if ((mFlightController != null) && (getState() == IFlightController.STATE_STARTED)) {
					mFlightController.requestFlatTrim();
				}
				break;
			case R.id.load_btn:
				// 読み込みボタンの処理
				setColorFilter((ImageView)view);
				final File root = FileUtils.getCaptureDir(getActivity(), "Documents", false);
				try {
					SelectFileDialogFragment.showDialog(AutoPilotFragment2.this, root.getAbsolutePath(), false, "fcr");
				} catch (final NullPointerException e) {
					Log.w(TAG, e);
				}
				break;
			case R.id.record_btn:
				// 操縦記録ボタンの処理
				if (!mFlightRecorder.isRecording()) {
					startRecord(true);
				} else {
					stopRecord();
				}
				updateButtons();
				break;
			case R.id.config_show_btn:
				// 設定パネル表示処理
				setColorFilter((ImageView)view);
				if (isConnected()) {
					if ((getState() & IFlightController.STATE_MASK_FLYING) == DroneStatus.STATE_FLYING_LANDED) {
						replace(ConfigFragment.newInstance(getDevice()));
					} else {
						landing();
					}
				}
				break;
			case R.id.trace_btn:
				// 自動操縦ボタン
				setColorFilter((ImageView)view);
				mAutoPilot = false;	// 自動操縦解除
				setColorFilter(mTraceButton, 0, 0);
				break;
			case R.id.emergency_btn:
				// 非常停止指示ボタンの処理
				mAutoPilot = false;
				setColorFilter(mTraceButton, 0, 0);
				setColorFilter((ImageView) view);
				emergencyStop();
				break;
			case R.id.take_onoff_btn:
				// 離陸指示/着陸指示ボタンの処理
				mAutoPilot = false;
				setColorFilter(mTraceButton, 0, 0);
				setColorFilter((ImageView)view);
				if (!isFlying()) {
//					takeOff();
					Toast.makeText(getActivity(), R.string.notify_takeoff, Toast.LENGTH_SHORT).show();
				} else {
					landing();
				}
				updateButtons();
				break;
			case R.id.still_capture_btn:
				// 静止画撮影ボタンの処理
				if (getStillCaptureState() == DroneStatus.MEDIA_READY) {
					setColorFilter((ImageView) view);
					if (mFlightController != null) {
						mFlightController.requestTakePicture();
					}
				}
				break;
			case R.id.video_capture_btn:
				// 動画撮影ボタンの処理
				setColorFilter((ImageView)view);
				if (mController instanceof ICameraController) {
					mVideoRecording = !mVideoRecording;
					((ICameraController)mController).sendVideoRecording(mVideoRecording);
				}
				break;
//--------------------------------------------------------------------------------
			case R.id.top_panel:
				// 解析中画像の表示モードを変更
				if (mImageProcessor != null) {
					mImageProcessor.setResultFrameType((mImageProcessor.getResultFrameType() - 2) % 2 + 3);
				}
				break;
			case R.id.drone_view:
				break;
			case R.id.update_extraction_color_btn:
				post(new Runnable() {
					@Override
					public void run() {
						if (mImageProcessor != null) {
							final int[] result = mImageProcessor.requestUpdateExtractionColor();
							// FIXME プレファレンスに保存する
						}
					}
				}, 0);
				break;
			case R.id.reset_extraction_color_btn:
				// 抽出色をリセット
				if (mImageProcessor != null) {
					mImageProcessor.resetExtractionColor();
				}
				break;
			}
		}
	};

	private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
//			if (DEBUG) Log.v(TAG, "onLongClick:" + view);
			switch (view.getId()) {
			case R.id.record_btn:
				if (!mFlightRecorder.isRecording()) {
					startRecord(false);
				} else {
					stopRecord();
				}
				return true;
			case R.id.flat_trim_btn:
				mAutoPilot = false;
				setColorFilter(mTraceButton, 0, 0);
				setColorFilter((ImageView)view);
				if ((mFlightController != null) && (getState() == IFlightController.STATE_STARTED)) {
					replace(CalibrationFragment.newInstance(getDevice()));
					return true;
				}
				break;
			case R.id.take_onoff_btn:
				// 離陸/着陸ボタンを長押しした時の処理
				mAutoPilot = false;
				setColorFilter(mTraceButton, 0, 0);
				setColorFilter((ImageView)view);
				if (!isFlying()) {
					takeOff();
					return true;
				}
			case R.id.trace_btn:
				mAutoPilot = true;
				setColorFilter(mTraceButton, TOUCH_RESPONSE_COLOR, 0);
				if (!isFlying()) {
					takeOff();
					return true;
				}
			}
			return false;
		}
	};

	private int mImageProcessorSurfaceId;
	@Override
	protected void onConnect(final IDeviceController controller) {
		super.onConnect(controller);
		if (DEBUG) Log.v(TAG, "onConnect");
		if (controller instanceof ICameraController) {
			((ICameraController)controller).sendExposure(3);
			((ICameraController)controller).sendCameraOrientation(-100, 0);
			((ICameraController)controller).sendAutoWhiteBalance(mAutoWhiteBlance ? 0 : -1);	// 自動ホワイトバランス
		} else {
			mAutoWhiteBlanceSw.setVisibility(View.GONE);
		}
	}

	@Override
	protected void onDisconnect(final IDeviceController controller) {
		if (DEBUG) Log.v(TAG, "onDisconnect");
		stopImageProcessor();
		super.onDisconnect(controller);
	}

	@Override
	protected void startVideoStreaming() {
		super.startVideoStreaming();
		startImageProcessor();
	}

	@Override
	protected void stopVideoStreaming() {
		stopImageProcessor();
		super.stopVideoStreaming();
	}

	@Override
	protected void updateAlarmMessageOnUIThread(final int alarm) {
		switch (alarm) {
		case DroneStatus.ALARM_NON:					// No alert
			break;
		case DroneStatus.ALARM_USER_EMERGENCY:		// User emergency alert
			mAlertMessage.setText(R.string.alarm_user_emergency);
			break;
		case DroneStatus.ALARM_CUTOUT:				// Cut out alert
			mAlertMessage.setText(R.string.alarm_motor_cut_out);
			break;
		case DroneStatus.ALARM_BATTERY_CRITICAL:	// Critical battery alert
			mAlertMessage.setText(R.string.alarm_low_battery_critical);
			break;
		case DroneStatus.ALARM_BATTERY:				// Low battery alert
			mAlertMessage.setText(R.string.alarm_low_battery);
			break;
		case DroneStatus.ALARM_DISCONNECTED:		// 切断された
			mAlertMessage.setText(R.string.alarm_disconnected);
			break;
		default:
			Log.w(TAG, "unexpected alarm state:" + alarm);
			break;
		}
		mAlertMessage.setVisibility(alarm != 0 ? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	protected void updateBatteryOnUIThread(final int battery) {
		if (battery >= 0) {
			mBatteryLabel.setText(String.format("%d%%", battery));
		} else {
			mBatteryLabel.setText("---");
		}
	}

	@Override
	protected void updateTimeOnUIThread(final int minutes, final int seconds) {
		mTimeLabelTv.setText(String.format("%3d:%02d", minutes, seconds));
	}

	@Override
	protected void updateButtons() {
		runOnUiThread(mUpdateButtonsTask);
	}

	/**
	 *　ボタンの表示更新をUIスレッドで行うためのRunnable
	 */
	private final Runnable mUpdateButtonsTask = new Runnable() {
		@Override
		public void run() {
			final int state = getState();
			final int alarm_state = getAlarm();
			final int still_capture_state = getStillCaptureState();
			final int video_recording_state = getVideoRecordingState();
			final boolean is_connected = isConnected();
			final boolean is_recording = mFlightRecorder.isRecording();
			final boolean is_playing = mFlightRecorder.isPlaying();
			final boolean can_play = is_connected && !is_recording && !mScriptRunning && !mTouchMoveRunning && (alarm_state == DroneStatus.ALARM_NON) && (mFlightRecorder.size() > 0);
			final boolean can_record = is_connected && !is_playing && !mScriptRunning;
			final boolean can_load = is_connected && !is_playing && !is_recording && !mTouchMoveRunning;
			final boolean can_fly = can_record && (alarm_state == DroneStatus.ALARM_NON);
			final boolean can_flattrim = can_fly && (state == IFlightController.STATE_STARTED);
			final boolean can_clear = is_connected && !is_recording && !is_playing && !mScriptRunning && !mTouchMoveRunning && mTouchFlight.isPrepared();
			final boolean can_move = is_connected && !is_recording && !is_playing && !mScriptRunning && (mTouchFlight.isPrepared() || mTouchFlight.isPlaying()) && (alarm_state == DroneStatus.ALARM_NON);
			final boolean is_battery_alarm
				= (alarm_state == DroneStatus.ALARM_BATTERY) || (alarm_state == DroneStatus.ALARM_BATTERY_CRITICAL);

			// 上パネル
			mTopPanel.setEnabled(is_connected);
			mFlatTrimBtn.setEnabled(can_flattrim);	// フラットトリム
			mBatteryLabel.setTextColor(is_battery_alarm ? 0xffff0000 : 0xff9400d3);
			mConfigShowBtn.setEnabled(can_flattrim);
			mConfigShowBtn.setColorFilter(can_flattrim ? 0 : DISABLE_COLOR);

			// 下パネル
			mBottomPanel.setEnabled(is_connected);
			mEmergencyBtn.setEnabled(is_connected);	// 非常停止
			setChildVisibility(mTimeLabelTv, is_recording || is_playing ? View.VISIBLE : View.INVISIBLE);
			mRecordBtn.setEnabled(can_record);        // 記録
			mRecordBtn.setColorFilter(can_record ? (is_recording ? 0xffff0000 : 0) : DISABLE_COLOR);
			mRecordLabel.setText(is_recording ? R.string.action_stop : R.string.action_record);

			// 離陸/着陸
			switch (state & IFlightController.STATE_MASK_FLYING) {
			case DroneStatus.STATE_FLYING_LANDED:		// 0x0000;		// FlyingState=0
				mModelView.stopEngine();
			case DroneStatus.STATE_FLYING_LANDING:		// 0x0400;		// FlyingState=4
				mTakeOnOffBtn.setImageResource(R.mipmap.ic_takeoff);
				break;
			case DroneStatus.STATE_FLYING_TAKEOFF:		// 0x0100;		// FlyingState=1
			case DroneStatus.STATE_FLYING_HOVERING:		// 0x0200;		// FlyingState=2
			case DroneStatus.STATE_FLYING_FLYING:		// 0x0300;		// FlyingState=3
			case DroneStatus.STATE_FLYING_ROLLING:		// 0x0600;		// FlyingState=6
				mTakeOnOffBtn.setImageResource(R.mipmap.ic_landing);
				mModelView.startEngine();
				break;
			case DroneStatus.STATE_FLYING_EMERGENCY:	// 0x0500;		// FlyingState=5
				mModelView.stopEngine();
				break;
			}

			// 右サイドパネル
			mRightSidePanel.setEnabled(can_fly);

			mStillCaptureBtn.setEnabled(still_capture_state == DroneStatus.MEDIA_READY);
			setChildVisibility(mStillCaptureBtn, still_capture_state != DroneStatus.MEDIA_UNAVAILABLE ? View.VISIBLE : View.INVISIBLE);

			mVideoRecordingBtn.setEnabled((video_recording_state == DroneStatus.MEDIA_READY) || (video_recording_state == DroneStatus.MEDIA_BUSY));
			setChildVisibility(mStillCaptureBtn, video_recording_state != DroneStatus.MEDIA_UNAVAILABLE ? View.VISIBLE : View.INVISIBLE);
			mVideoRecordingBtn.setColorFilter(mVideoRecording ? 0x7fff0000 : 0);

			for (final View view: mActionViews) {
				view.setEnabled(can_fly);
				if (view instanceof ImageView) {
					((ImageView)view).setColorFilter(can_fly ? 0 : DISABLE_COLOR);
				}
			}
		}
	};

	private void startImageProcessor() {
		setColorFilter(mTraceButton, 0, 0);
		if (mTraceTask == null) {
			mTraceTask = new TraceTask();
			new Thread(mTraceTask, TAG).start();
		}
		if (mImageProcessor == null) {
			mImageProcessor = new ImageProcessor(mImageProcessorCallback);
			mImageProcessor.setExposure(mExposure);
			mImageProcessor.setSaturation(mSaturation);
			mImageProcessor.setBrightness(mBrightness);
			mImageProcessor.setExtractionColor(
				EXTRACT_COLOR_HSV_LIMIT[0], EXTRACT_COLOR_HSV_LIMIT[1],
				EXTRACT_COLOR_HSV_LIMIT[2], EXTRACT_COLOR_HSV_LIMIT[3],
				EXTRACT_COLOR_HSV_LIMIT[4], EXTRACT_COLOR_HSV_LIMIT[5]);
			mImageProcessor.enableExtraction(mEnableGLESExtraction);
			mImageProcessor.enableNativeExtract(mEnableNativeExtraction);
			mImageProcessor.enableNativeCanny(mEnableNativeCanny);
			mImageProcessor.trapeziumRate(mTrapeziumRate);
			mImageProcessor.setAreaLimit(mAreaLimitMin, AREA_LIMIT_MAX);
			mImageProcessor.setAreaErrLimit(mAreaErrLimit1, mAreaErrLimit2);
			mImageProcessor.setAspectLimit(mAspectLimitMin);
			mImageProcessor.start();
			final Surface surface = mImageProcessor.getSurface();
			mImageProcessorSurfaceId = surface != null ? surface.hashCode() : 0;
			if (mImageProcessorSurfaceId != 0) {
				mVideoStream.addSurface(mImageProcessorSurfaceId, surface);
			}
		}
	}

	private void stopImageProcessor() {
		if ((mVideoStream != null) && (mImageProcessorSurfaceId != 0)) {
			mVideoStream.removeSurface(mImageProcessorSurfaceId);
		}
		mImageProcessorSurfaceId = 0;
		if (mImageProcessor != null) {
			mImageProcessor.release();
			mImageProcessor = null;
		}
		mTraceTask = null;
		synchronized (mQueue) {
			mIsRunning = false;
			mQueue.notifyAll();
		}
		setColorFilter(mTraceButton, 0, 0);
	}

	/**
	 * TraceTaskが停止するときのコールバック
	 * @param isError
	 */
	private void onStopAutoPilot(final boolean isError) {
		if (DEBUG) Log.v(TAG, "onStopAutoPilot:");
		post(new Runnable() {
			@Override
			public void run() {
				stopImageProcessor();
			}
		}, 0);
	}

	/** 解析データキューの最大サイズ */
	private static final int MAX_QUEUE = 4;
	/** 解析データレコードの再利用のためのプール */
	private final List<LineRec> mPool = new ArrayList<LineRec>();
	/** 解析データキュー */
	private final List<LineRec> mQueue = new ArrayList<LineRec>();
	private volatile boolean mIsRunning;
	/** トレース飛行中 */
	private volatile boolean mAutoPilot;
	/** パラメータ変更指示 */
	private boolean mReqUpdateParams;
	/** パラメータの排他制御用 */
	private final Object mParamSync = new Object();

	/** トレース飛行タスク */
	private class TraceTask implements Runnable {
		private static final float EPS_CURVATURE = 1.0e-4f;
		private static final float MAX_PILOT_ANGLE = 45.0f;	// 一度に修正するyaw角の最大絶対値
		private static final float MIN_PILOT_ANGLE = 3.0f;	// 0とみなすyaw角のずれの絶対値
		public TraceTask() {
		}

		@Override
		public void run() {
			synchronized (mQueue) {
				for (int i = 0; i < MAX_QUEUE; i++) {
					mPool.add(new LineRec());
				}
			}
			mIsRunning = mReqUpdateParams = true;
			mAutoPilot = false;
			float flightAngleYaw = 0.0f;	// カメラの上方向に対する移動方向の角度
			float flightSpeed = 50.0f;		// 前進速度の1/2(負なら後進)
			final Vector scale = new Vector((float)mScaleX, (float)mScaleY, (float)mScaleZ);
			float scaleR = (float)mScaleR;
			final Vector factor = new Vector(1.0f, 1.0f, 1.0f);
			//
			long lostTime = -1;
			final Vector work = new Vector();
			final Vector prev = new Vector();
			float pilotAngle = 0.0f;
			final Vector mPilotValue = new Vector();		// roll,pitch,gaz制御量
			final Vector mPrevPilotValue = new Vector();	// roll,pitch,gazの前回制御量
			LineRec rec = null;
			for ( ; mIsRunning ; ) {
				synchronized (mParamSync) {
					if (mReqUpdateParams) {	// パラメータ変更指示?
						mReqUpdateParams = false;
						flightAngleYaw = mFlightAttitudeYaw;
						// factorが最大で2になるのでmFlightSpeedは[-100,+100]なのを[-50,+50]にする
						flightSpeed = mFlightSpeed / 2.0f * (float)(mMaxControlValue / 100.0);
						scale.set((float)mScaleX, (float)mScaleY, (float)mScaleZ);
						scaleR = (float)mScaleR;
					}
				}
				synchronized (mQueue) {
					try {
						// 解析データ待ち
						mQueue.wait(500);
					} catch (InterruptedException e) {
						break;
					}
					if (!mIsRunning) break;
					if (mQueue.size() > 0) {
						rec = mQueue.remove(0);
					}
				}
				if (rec != null) {
					// 解析データを取得できた＼(^o^)／
					final String msg1;
					if (rec.type >= 0) {	// 0:TYPE_LINE, 1:TYPE_CIRCLE, 2:TYPE_CORNER
						// ラインを検出出来た時
						lostTime = -1;
						//--------------------------------------------------------------------------------
						// 前回の位置とコマンドから想定する現在位置とラインの位置が大きく違う時は制限をする
						//--------------------------------------------------------------------------------
						prev.sub(rec.mLinePos);
						final boolean limited = prev.lenSquared() > 20000;
						factor.mult(
							limited ? (Math.abs(prev.x) > 150 ? 0.9f : 1.0f) : 1.01f,
							limited ? (Math.abs(prev.y) > 92 ? 0.9f : 1.0f) : 1.011f,
							limited ? (Math.abs(prev.z) > 100 ? 0.9f : 1.0f) : 1.011f)
							.limit(0.1f, 2.0f);	// 最小0.1, 最大2.0に制限
						//--------------------------------------------------------------------------------
						// 制御量を計算
						// 機体からの角度はカメラ映像の真上が0で反時計回りが負、時計回りが正(Bebopのyaw軸回転角と同じ)
						// 解析画像のラインに対する角度は機体が時計回りすれば正
						// この時機体自体のラインに対する角度は符号反転
						// mCurvatureがゼロでない時にmAngleが正ならラインは左へ曲がっている、mAngleが負なら右へ曲がっている
						// Vectorクラスは反時計回りが正, 時計回りが負
						//--------------------------------------------------------------------------------
						// 画像中心からの距離を計算
						work.set(VideoStream.VIDEO_WIDTH, VideoStream.VIDEO_HEIGHT).div(2.0f).sub(rec.mLinePos);
						// 解析データ(画像中心からのオフセット,距離,回転角)
						msg1 = String.format("v(%5.2f,%5.2f)=%5.1f,θ=%5.2f,r=%6.4e)", work.x, work.y, work.len(), rec.mAngle, rec.mCurvature);
						// カメラ映像の真上に向かって進む, 高度制御無し
						mPilotValue.set(0.f, flightSpeed, 0.0f);
						mPilotValue.sub(work.x, -work.y, 0.0f).mult(factor);
						// 実際の機体の進行方向に合わせて回転, これで機体の実際の進行方向に対する制御量になる
						mPilotValue.rotate(0, 0, flightAngleYaw);
						// 自走操縦スケールを適用
						mPilotValue.mult(scale);
						// 最大最小値を制限
						mPilotValue.limit(-100.0f, +100.0f);
						//--------------------------------------------------------------------------------
						// 機体のyaw角を計算, MAX_PILOT_ANGLE以上は一度に回転させない
						pilotAngle = -(rec.mAngle < -MAX_PILOT_ANGLE ? -MAX_PILOT_ANGLE : (rec.mAngle > MAX_PILOT_ANGLE ? MAX_PILOT_ANGLE : rec.mAngle));
						// 曲率による機体yaw角の補正
						if (Math.abs(rec.mCurvature) > EPS_CURVATURE) {
							// mCurvatureは10e-4〜10e-3ぐらい, log10で-4〜-3ぐらい
							pilotAngle *= 1.05f; // 5%上乗せする
						}
						// 機体の進行方向の傾きを差し引く
						pilotAngle += flightAngleYaw;
						// 一定角度以下は0に丸める
						pilotAngle = (pilotAngle < -MIN_PILOT_ANGLE) || (pilotAngle > MIN_PILOT_ANGLE) ? pilotAngle : 0.0f;
						//--------------------------------------------------------------------------------
						// 今回の位置を保存
						prev.set(rec.mLinePos);
					} else {
						// ラインを見失った時
						msg1 = null;
						factor.clear(0.1f);
						pilotAngle = 0.0f;
						rec.mAngle = 0;
						if (mAutoPilot) {
							if (lostTime < 0) {
								lostTime = System.currentTimeMillis();
							}
							final long t = System.currentTimeMillis() - lostTime;
							if (t < 500) {
								// 一定時間は逆向きに動かす
								mPilotValue.set(mPrevPilotValue).mult(-1.0f);
							} else {
								// 一定時間以上ラインを見失ったらその場で静止
								mPilotValue.clear(0.0f);
							}
							if (t > 10000) {	// 10秒以上ラインを見失ったらライントレース解除
								onStopAutoPilot(true);
								mAutoPilot = false;
							}
						} else {
							mPilotValue.clear(0.0f);
						}
					}
					//--------------------------------------------------------------------------------
					// トレース飛行中なら制御コマンド送信
					//--------------------------------------------------------------------------------
					if (mAutoPilot) {
						// 制御コマンド送信
						mFlightController.requestAnimationsCap((int)pilotAngle);
						mFlightController.setMove(mPilotValue.x, mPilotValue.y, 0.0f);	// FIXME 高度は制御しない
						// 今回の制御量を保存
						mPrevPilotValue.set(mPilotValue);
					}
					final String msg2 = String.format("p(%5.1f,%5.1f,%5.1f,%5.1f)", mPilotValue.x, mPilotValue.y, 0.0f, pilotAngle);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mTraceTv1.setText(msg1);
							mTraceTv2.setText(msg2);
						}
					});
					synchronized (mQueue) {
						mPool.add(rec);
					}
				}
			}
			onStopAutoPilot(!mAutoPilot);
			synchronized (mQueue) {
				mIsRunning = mAutoPilot = false;
				mQueue.clear();
				mPool.clear();
			}
			System.gc();
		}
	}

	/** ImageProcessorからのコールバックリスナー */
	private final ImageProcessor.ImageProcessorCallback mImageProcessorCallback
		= new ImageProcessor.ImageProcessorCallback() {

		private Bitmap mFrame;
		private final Matrix matrix = new Matrix();
		@Override
		public void onFrame(final ByteBuffer frame) {
			if (mDetectView != null) {
				final SurfaceHolder holder = mDetectView.getHolder();
				if ((holder == null) || (holder.getSurface() == null)) return;
				if (mFrame == null) {
					mFrame = Bitmap.createBitmap(VideoStream.VIDEO_WIDTH, VideoStream.VIDEO_HEIGHT, Bitmap.Config.ARGB_8888);
					final float scaleX = mDetectView.getWidth() / (float)VideoStream.VIDEO_WIDTH;
					final float scaleY = mDetectView.getHeight() / (float)VideoStream.VIDEO_HEIGHT;
					matrix.reset();
					matrix.postScale(scaleX, scaleY);
				}
				frame.clear();
				mFrame.copyPixelsFromBuffer(frame);
				final Canvas canvas = holder.lockCanvas();
				if (canvas != null) {
					try {
						canvas.drawBitmap(mFrame, matrix, null);
					} catch (final Exception e) {
						Log.w(TAG, e);
					} finally {
						holder.unlockCanvasAndPost(canvas);
					}
				}
			}
		}

		@Override
		public void onResult(final int type, final float[] result) {
			synchronized (mQueue) {
				if (!mIsRunning) return;
				LineRec rec = mPool.size() > 0 ? mPool.remove(0) : null;
				if (rec == null) {
					rec = new LineRec();
				}
				rec.type = type;
				// ラインの中心座標(位置ベクトル,cv::RotatedRect#center)
				rec.mLinePos.set(result[0], result[1], 0.0f);
				// ラインの長さ(長軸長さ=length)
				rec.mLineLen = result[2];
				// ライン幅(短軸長さ)
				rec.mLineWidth = result[3];
				// ラインの方向(cv::RotatedRect#angle)
				rec.mAngle = result[4];
				// 最小矩形面積に対する輪郭面積の比
				rec.mAreaRate = result[5];
				// 円フィッティングの曲率
				rec.mCurvature = result[6];
				// キュー内に最大数入っていたらプールに戻す
				for ( ; mQueue.size() > MAX_QUEUE ; ) {
					mPool.add(mQueue.remove(0));
				}
				// キューの最後に追加
				mQueue.add(rec);
				mQueue.notify();
			}
		}

	};

//================================================================================
// ここから下はパラメータ関係
//================================================================================
	private int getInt(final SharedPreferences pref, final String key, final int default_value) {
		int result = default_value;
		try {
			result = pref.getInt(key, default_value);
		} catch (final Exception e) {
			try {
				result = Integer.parseInt(pref.getString(key, Integer.toString(default_value)));
			} catch (final Exception e1) {
			}
		}
		return result;
	}

	private static class SmoothTypeAdapter extends ArrayAdapter<String> {
		private final String[] values;
		public SmoothTypeAdapter(final Context context) {
			super(context, android.R.layout.simple_dropdown_item_1line);
			values = context.getResources().getStringArray(R.array.trace_smooth_value2);
			final String[] entries = context.getResources().getStringArray(R.array.trace_smooth_entries);
			addAll(entries);
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			final View rootView = super.getView(position, convertView, parent);
			changeColor(rootView, getContext().getResources().getColor(R.color.WHITE));
			return rootView;
		}

		private void changeColor(final View view, final int cl) {
			if (view instanceof TextView) {
				((TextView)view).setTextColor(cl);
			} else if (view instanceof ViewGroup) {
				final ViewGroup parent = (ViewGroup)view;
				final int n = parent.getChildCount();
				for (int i = 0; i < n; i++) {
					changeColor(parent.getChildAt(i), cl);
				}
			}
		}
	}

	private final AdapterView.OnItemSelectedListener mOnItemSelectedListener
		= new AdapterView.OnItemSelectedListener() {
		@Override
		public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
			switch (parent.getId()) {
			case R.id.use_native_smooth_spinner:
				if (mImageProcessor != null) {
					mImageProcessor.nativeSmoothType(position % 4);
				}
				break;
			}
		}

		@Override
		public void onNothingSelected(final AdapterView<?> parent) {
			switch (parent.getId()) {
			case R.id.use_native_smooth_spinner:
				if (mImageProcessor != null) {
					mImageProcessor.nativeSmoothType(0);
				}
				break;
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			switch (buttonView.getId()) {
			case R.id.white_balance_sw:
				((ICameraController)mController).sendAutoWhiteBalance(isChecked ? 0 : -1);
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_AUTO_WHITE_BLANCE, isChecked).apply();
				}
				break;
			case R.id.use_extract_sw:
				if (mImageProcessor != null) {
					mEnableGLESExtraction = isChecked;
					mImageProcessor.enableExtraction(isChecked);
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_ENABLE_EXTRACTION, isChecked).apply();
				}
				break;
			case R.id.use_canny_sw:
				if (mImageProcessor != null) {
					mEnableGLESCanny = isChecked;
					mImageProcessor.enableCanny(isChecked);
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_ENABLE_EDGE_DETECTION, isChecked).apply();
				}
				break;
			case R.id.use_native_extract_sw:
				if (mImageProcessor != null) {
					mEnableNativeExtraction = isChecked;
					mImageProcessor.enableNativeExtract(isChecked);
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_ENABLE_NATIVE_EXTRACTION, isChecked).apply();
				}
				break;
			case R.id.use_native_canny_sw:
				if (mImageProcessor != null) {
					mEnableNativeCanny = isChecked;
					mImageProcessor.enableNativeCanny(isChecked);
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_ENABLE_NATIVE_EDGE_DETECTION, isChecked).apply();
				}
				break;
			case R.id.use_posterize_sw:
				if (mImageProcessor != null) {
					mEnablePosterize = isChecked;
					mImageProcessor.enablePosterize(isChecked);
				}
				if (mPref != null) {
					mPref.edit().putBoolean(KEY_ENABLE_POSTERIZE, isChecked).apply();
				}
				break;
			}
		}
	};

	private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener
		= new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
			if (!fromUser) return;
			switch (seekBar.getId()) {
			case R.id.exposure_seekbar:
				final float exposure = progressToExposure(progress);	// [0,2000] => [-10.0f, +10.0f]
				if (mExposure != exposure) {
					mExposure = exposure;
					if (mImageProcessor != null) {
						mImageProcessor.setExposure(exposure);
					}
					updateExposure(exposure);
				}
				break;
			case R.id.saturation_seekbar:
				final float saturation = (progress - 100) / 100.0f;	// [0,200] => [-1.0f, +1.0f]
				if (mSaturation != saturation) {
					mSaturation = saturation;
					if (mImageProcessor != null) {
						mImageProcessor.setSaturation(saturation);
					}
					updateSaturation(saturation);
				}
				break;
			case R.id.brightness_seekbar:
				final float brightness = (progress - 100) / 100.0f;	// [0,200] => [-1.0f, +1.0f]
				if (mBrightness != brightness) {
					mBrightness = brightness;
					if (mImageProcessor != null) {
						mImageProcessor.setBrightness(brightness);
					}
					updateBrightness(brightness);
				}
				break;
			case R.id.posterize_seekbar:
				final float posterize = progress + 1;
				if (mPosterize != posterize) {
					mPosterize = posterize;
					if (mImageProcessor != null) {
						mImageProcessor.setPosterize(posterize);
					}
					updatePosterize(posterize);
				}
				break;
			case R.id.binarize_threshold_seekbar:
				final float threshold = progress / 100.0f;
				if (mBinarizeThreshold != threshold) {
					mBinarizeThreshold = threshold;
					if (mImageProcessor != null) {
						mImageProcessor.setBinarizeThreshold(threshold);
					}
					updateBinarizeThreshold(threshold);
				}
				break;
			case R.id.trapezium_rate_seekbar:
				final double trapezium_rate = progressToTrapeziumRate(progress);
				if (mTrapeziumRate != trapezium_rate) {
					mTrapeziumRate = trapezium_rate;
					if (mImageProcessor != null) {
						mImageProcessor.trapeziumRate(trapezium_rate);
					}
					updateTrapeziumRate(trapezium_rate);
				}
				break;
			case R.id.area_limit_min_seekbar:
				final float area_limit_min = progressToAreaLimitMin(progress);
				if (mAreaLimitMin != area_limit_min) {
					mAreaLimitMin = area_limit_min;
					if (mImageProcessor != null) {
						mImageProcessor.setAreaLimit(area_limit_min, AREA_LIMIT_MAX);
					}
					updateAreaLimitMin(area_limit_min);
				}
				break;
			case R.id.area_err_limit1_seekbar:
				final float area_err_limit1 = (progress / 100.0f) + 1.0f;
				if (mAreaErrLimit1 != area_err_limit1) {
					mAreaErrLimit1 = area_err_limit1;
					if (mImageProcessor != null) {
						mImageProcessor.setAreaErrLimit(area_err_limit1, mAreaErrLimit2);
					}
					updateAreaErrLimit1(area_err_limit1);
				}
				break;
			case R.id.area_err_limit2_seekbar:
				final float area_err_limit2 = (progress / 100.0f) + 1.0f;
				if (mAreaErrLimit2 != area_err_limit2) {
					mAreaErrLimit2 = area_err_limit2;
					if (mImageProcessor != null) {
						mImageProcessor.setAreaErrLimit(mAreaErrLimit1, area_err_limit2);
					}
					updateAreaErrLimit2(area_err_limit2);
				}
				break;
			case R.id.aspect_limit_min_seekbar:
				final float aspect = (progress / 10.0f) + 1.0f;
				if (mAspectLimitMin != aspect) {
					mAspectLimitMin = aspect;
					if (mImageProcessor != null) {
						mImageProcessor.setAspectLimit(aspect);
					}
					updateAspectLimitMin(aspect);
				}
				break;
			case R.id.max_altitude_seekbar:
				final float altitude = (int) (progress / 100f * (mMaxAltitude.max() - mMaxAltitude.min())) / 10f + mMaxAltitude.min();
				updateMaxAltitude(altitude);
				break;
			case R.id.max_tilt_seekbar:
				final float tilt = (int) (progress / 100f * (mMaxTilt.max() - mMaxTilt.min())) / 10f + mMaxTilt.min();
				updateMaxTilt(tilt);
				break;
			case R.id.max_vertical_speed_seekbar:
				final float vertical = (int) (progress / 100f * (mMaxVerticalSpeed.max() - mMaxVerticalSpeed.min())) / 10f + mMaxVerticalSpeed.min();
				updateMaxVerticalSpeed(vertical);
				break;
			case R.id.max_rotation_speed_seekbar:
				final float rotation = (int) (progress / 1000f * (mMaxRotationSpeed.max() - mMaxRotationSpeed.min())) + mMaxRotationSpeed.min();
				updateMaxRotationSpeed(rotation);
				break;
			case R.id.max_control_value_seekbar:	// -500〜+500
				final float max_control_value = progress - SCALE_OFFSET;
				updateAutopilotMaxControlValue(max_control_value);
				break;
			case R.id.scale_seekbar_x:
				final float scale_x = (progress - SCALE_OFFSET) / SCALE_FACTOR;
				updateAutopilotScaleX(scale_x);
				break;
			case R.id.scale_seekbar_y:
				final float scale_y = (progress - SCALE_OFFSET) / SCALE_FACTOR;
				updateAutopilotScaleY(scale_y);
				break;
			case R.id.scale_seekbar_z:
				final float scale_z = (progress - SCALE_OFFSET) / SCALE_FACTOR;
				updateAutopilotScaleZ(scale_z);
				break;
			case R.id.scale_seekbar_r:
				final float scale_r = (progress - SCALE_OFFSET) / SCALE_FACTOR;
				updateAutopilotScaleR(scale_r);
				break;
			case R.id.trace_flight_attitude_yaw_seekbar:
				final float attitude_yaw = progress - 90;
				updateFlightAttitudeYaw(attitude_yaw);
				break;
			case R.id.trace_flight_speed_seekbar:
				final float speed = progress - 100;
				updateFlightSpeed(speed);
				break;
			}
		}

		@Override
		public void onStartTrackingTouch(final SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(final SeekBar seekBar) {
			switch (seekBar.getId()) {
			case R.id.exposure_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_EXPOSURE, mExposure).apply();
				}
				break;
			case R.id.saturation_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_SATURATION, mSaturation).apply();
				}
				break;
			case R.id.brightness_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_BRIGHTNESS, mBrightness).apply();
				}
				break;
			case R.id.posterize_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_POSTERIZE, mPosterize).apply();
				}
				break;
			case R.id.binarize_threshold_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_BINARIZE_THRESHOLD, mBinarizeThreshold).apply();
				}
				break;
			case R.id.trapezium_rate_seekbar:
				if (mPref != null) {
					mPref.edit().putString(KEY_TRAPEZIUM_RATE, Double.toString(mTrapeziumRate)).apply();
				}
				break;
			case R.id.area_limit_min_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_AREA_LIMIT_MIN, mAreaLimitMin).apply();
				}
				break;
			case R.id.area_err_limit1_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_AREA_ERR_LIMIT1, mAreaErrLimit1).apply();
				}
				break;
			case R.id.area_err_limit2_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_AREA_ERR_LIMIT2, mAreaErrLimit2).apply();
				}
				break;
			case R.id.aspect_limit_min_seekbar:
				if (mPref != null) {
					mPref.edit().putFloat(KEY_ASPECT_LIMIT_MIN, mAspectLimitMin).apply();
				}
				break;
			case R.id.max_altitude_seekbar:
				final float altitude = (int)(seekBar.getProgress() / 100f * (mMaxAltitude.max() - mMaxAltitude.min())) / 10f + mMaxAltitude.min();
				if (altitude != mMaxAltitude.current()) {
					mFlightController.setMaxAltitude(altitude);
				}
				break;
			case R.id.max_tilt_seekbar:
				final float tilt = (int)(seekBar.getProgress() / 100f * (mMaxTilt.max() - mMaxTilt.min())) / 10f + mMaxTilt.min();
				if (tilt != mMaxTilt.current()) {
					mFlightController.setMaxTilt(tilt);
				}
				break;
			case R.id.max_vertical_speed_seekbar:
				final float vertical = (int)(seekBar.getProgress() / 100f * (mMaxVerticalSpeed.max() - mMaxVerticalSpeed.min())) / 10f + mMaxVerticalSpeed.min();
				if (vertical != mMaxVerticalSpeed.current()) {
					mFlightController.setMaxVerticalSpeed(vertical);
				}
				break;
			case R.id.max_rotation_speed_seekbar:
				final float rotation = (int)(seekBar.getProgress() / 1000f * (mMaxRotationSpeed.max() - mMaxRotationSpeed.min())) + mMaxRotationSpeed.min();
				if (rotation != mMaxRotationSpeed.current()) {
					mFlightController.setMaxRotationSpeed(rotation);
				}
				break;
			// 自動操縦
			case R.id.max_control_value_seekbar:
				final float max_control_value = seekBar.getProgress() - SCALE_OFFSET;
				if (max_control_value != mMaxControlValue) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mMaxControlValue = max_control_value;
					}
					mPref.edit().putFloat(KEY_AUTOPILOT_MAX_CONTROL_VALUE, max_control_value).apply();
				}
				break;
			case R.id.scale_seekbar_x:
				final float scale_x = (seekBar.getProgress() - SCALE_OFFSET) / SCALE_FACTOR;
				if (scale_x != mScaleX) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleX = scale_x;
					}
					mPref.edit().putFloat(KEY_AUTOPILOT_SCALE_X, scale_x).apply();
				}
				break;
			case R.id.scale_seekbar_y:
				final float scale_y = (seekBar.getProgress() - SCALE_OFFSET) / SCALE_FACTOR;
				if (scale_y != mScaleY) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleY = scale_y;
					}
					mPref.edit().putFloat(KEY_AUTOPILOT_SCALE_Y, scale_y).apply();
				}
				break;
			case R.id.scale_seekbar_z:
				final float scale_z = (seekBar.getProgress() - SCALE_OFFSET) / SCALE_FACTOR;
				if (scale_z != mScaleZ) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleZ = scale_z;
					}
					mPref.edit().putFloat(KEY_AUTOPILOT_SCALE_Z, scale_z).apply();
				}
				break;
			case R.id.scale_seekbar_r:
				final float scale_r = (seekBar.getProgress() - SCALE_OFFSET) / SCALE_FACTOR;
				if (scale_r != mScaleR) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mScaleR = scale_r;
					}
					mPref.edit().putFloat(KEY_AUTOPILOT_SCALE_R, scale_r).apply();
				}
				break;
			case R.id.trace_flight_attitude_yaw_seekbar:
				final float attitude_yaw = seekBar.getProgress() - 90;
				if (attitude_yaw != mFlightAttitudeYaw) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mFlightAttitudeYaw = attitude_yaw;
					}
					mPref.edit().putFloat(KEY_TRACE_FLIGHT_ATTITUDE_YAW, attitude_yaw).apply();
				}
				break;
			case R.id.trace_flight_speed_seekbar:
				final float speed = seekBar.getProgress() - 100;
				if (speed != mFlightSpeed) {
					synchronized (mParamSync) {
						mReqUpdateParams = true;
						mFlightSpeed = speed;
					}
					mPref.edit().putFloat(KEY_TRACE_FLIGHT_SPEED, speed).apply();
				}
				break;
			}
		}
	};

//--------------------------------------------------------------------------------
	private String mExposureFormat;
	private String mSaturationFormat;
	private String mBrightnessFormat;
	private String mPosterizeFormat;
	private String mBinarizeThresholdFormat;
	private String mTrapeziumRateFormat;
	private TextView mExposureLabel;
	private TextView mSaturationLabel;
	private TextView mBrightnessLabel;
	private TextView mPosterizeLabel;
	private TextView mBinarizeThresholdLabel;
	private TextView mTrapeziumRateLabel;
	/** ホワイトバランス */
	protected boolean mAutoWhiteBlance;
	/** 露出 */
	protected float mExposure;
	/** 彩度 */
	protected float mSaturation;
	/** 明るさ */
	protected float mBrightness;
	/** ポスタライズ */
	protected boolean mEnablePosterize;
	protected float mPosterize;
	/** 2値化閾値 */
	protected float mBinarizeThreshold;
	/** 台形補正係数 */
	protected double mTrapeziumRate;

	private void initPreprocess(final View rootView) {
		mExposureFormat = getString(R.string.trace_use_exposure);
		mSaturationFormat = getString(R.string.trace_use_saturation);
		mBrightnessFormat = getString(R.string.trace_use_brightness);
		mPosterizeFormat = getString(R.string.trace_use_posterize);
		mBinarizeThresholdFormat = getString(R.string.trace_binarize_threshold);
		mTrapeziumRateFormat = getString(R.string.trace_trapezium_rate);

		Switch sw;
		SeekBar sb;
		Button btn;
		// ホワイトバランス
		mAutoWhiteBlance = mPref.getBoolean(KEY_AUTO_WHITE_BLANCE, true);
		mAutoWhiteBlanceSw = (Switch)rootView.findViewById(R.id.white_balance_sw);
		mAutoWhiteBlanceSw.setChecked(mAutoWhiteBlance);
		mAutoWhiteBlanceSw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// 露出
		mExposure = mPref.getFloat(KEY_EXPOSURE, 0.0f);
		mExposureLabel = (TextView)rootView.findViewById(R.id.exposure_textview);
		sb = (SeekBar)rootView.findViewById(R.id.exposure_seekbar);
		sb.setMax(2000);
		sb.setProgress(exposureToProgress(mExposure));	// [-10,+ 10] => [0, 2000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateExposure(mExposure);
		// 彩度
		mSaturation = mPref.getFloat(KEY_SATURATION, 0.0f);
		mSaturationLabel = (TextView)rootView.findViewById(R.id.saturation_textview);
		sb = (SeekBar)rootView.findViewById(R.id.saturation_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mSaturation * 100.0f) + 100);	// [-1.0f, +1.0f] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateSaturation(mSaturation);
		// 明るさ
		mBrightness = mPref.getFloat(KEY_BRIGHTNESS, 0.0f);
		mBrightnessLabel = (TextView)rootView.findViewById(R.id.brightness_textview);
		sb = (SeekBar)rootView.findViewById(R.id.brightness_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mBrightness * 100.0f) + 100);	// [-1.0f, +1.0f] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateBrightness(mBrightness);
		// ポスタライズ
		mPosterize = mPref.getFloat(KEY_POSTERIZE, 10);
		mPosterizeLabel = (TextView)rootView.findViewById(R.id.posterize_textview);
		sb = (SeekBar)rootView.findViewById(R.id.posterize_seekbar);
		sb.setMax(255);
		sb.setProgress((int)(mPosterize - 1));	// [1, 256] => [0, 255]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		mEnablePosterize = mPref.getBoolean(KEY_ENABLE_POSTERIZE, false);
		sw = (Switch)rootView.findViewById(R.id.use_posterize_sw);
		sw.setChecked(mEnablePosterize);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		updatePosterize(mPosterize);
		// 二値化閾値
		mBinarizeThreshold = mPref.getFloat(KEY_BINARIZE_THRESHOLD, 0.5f);
		mBinarizeThresholdLabel = (TextView)rootView.findViewById(R.id.binarize_threshold_textview);
		sb = (SeekBar)rootView.findViewById(R.id.binarize_threshold_seekbar);
		sb.setMax(100);
		sb.setProgress((int)(mBinarizeThreshold * 100.0f));	// [0.0f, +1.0f] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateBinarizeThreshold(mBinarizeThreshold);
		// 台形補正係数
		mTrapeziumRate = Double.parseDouble(mPref.getString(KEY_TRAPEZIUM_RATE, "0.0"));
		if (Math.abs(mTrapeziumRate) < 0.01) mTrapeziumRate = 0.0;
		mTrapeziumRateLabel = (TextView)rootView.findViewById(R.id.trapezium_rate_textview);
		sb = (SeekBar)rootView.findViewById(R.id.trapezium_rate_seekbar);
		sb.setMax(4000);
		sb.setProgress(trapeziumRateToProgress(mTrapeziumRate));	// [-2.0f, +2.0f] => [0, 4000]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateTrapeziumRate(mTrapeziumRate);
	}

	private int exposureToProgress(final float exposure) {
		return (int)(Math.signum(exposure) * (Math.sqrt(Math.abs(exposure * 100000)))) + 1000;
	}

	private float progressToExposure(final int progress) {
		final int p = progress - 1000;
		return Math.signum(p) * (p * p / 100000.0f);
	}

	private void updateExposure(final float exposure) {
		if (mExposureLabel != null) {
			mExposureLabel.setText(String.format(mExposureFormat, exposure));
		}
	}

	private void updateSaturation(final float saturation) {
		if (mSaturationLabel != null) {
			mSaturationLabel.setText(String.format(mSaturationFormat, saturation));
		}
	}

	private void updateBrightness(final float brightness) {
		if (mBrightnessLabel != null) {
			mBrightnessLabel.setText(String.format(mBrightnessFormat, brightness));
		}
	}

	private void updatePosterize(final float posterize) {
		if (mPosterizeLabel != null) {
			mPosterizeLabel.setText(String.format(mPosterizeFormat, posterize));
		}
	}

	private void updateBinarizeThreshold(final float threshold) {
		if (mBinarizeThresholdLabel != null) {
			mBinarizeThresholdLabel.setText(String.format(mBinarizeThresholdFormat, threshold));
		}
	}

	private int trapeziumRateToProgress(final double trapezium_rate) {
		return (int)(trapezium_rate * 1000.0) + 2000;
	}

	private double progressToTrapeziumRate(final int progress) {
		double trapezium_rate = (progress - 2000) / 1000.0;
		if (Math.abs(trapezium_rate) < 0.01) trapezium_rate = 0.0;
		return trapezium_rate;
	}

	private void updateTrapeziumRate(final double trapezium_rate) {
		if (mTrapeziumRateLabel != null) {
			mTrapeziumRateLabel.setText(String.format(mTrapeziumRateFormat, trapezium_rate));
		}
	}

//--------------------------------------------------------------------------------
	/** OpenGL|ESでのエッジ検出前平滑化 */
	protected int mGLESSmoothType = 0;
	/** OpenGL|ESでエッジ検出(Canny)を行うかどうか */
	protected boolean mEnableGLESCanny = false;
	/** native側のエッジ検出前平滑化 */
	protected int mNativeSmoothType = 0;
	/** native側のエッジ検出(Canny)を使うかどうか */
	protected boolean mEnableNativeCanny = true;
	private void initPreprocess2(final View rootView) {
		Switch sw;
		Spinner spinner;

		// OpenGL|ESのエッジ検出前平滑化
		mGLESSmoothType = getInt(mPref, KEY_SMOOTH_TYPE, 0);
		spinner = (Spinner)rootView.findViewById(R.id.use_smooth_spinner);
		spinner.setAdapter(new SmoothTypeAdapter(getActivity()));
		spinner.setOnItemSelectedListener(mOnItemSelectedListener);
		// OpenGL|ESでエッジ検出を行うかどうか
		mEnableGLESCanny = mPref.getBoolean(KEY_ENABLE_EDGE_DETECTION, false);
		sw = (Switch)rootView.findViewById(R.id.use_canny_sw);
		sw.setChecked(mEnableGLESCanny);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// Native側のCannyを使うかどうか
		mEnableNativeCanny = mPref.getBoolean(KEY_ENABLE_NATIVE_EDGE_DETECTION, false);
		sw = (Switch)rootView.findViewById(R.id.use_native_canny_sw);
		sw.setChecked(mEnableNativeCanny);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// native側のエッジ検出前フィルタ
		mNativeSmoothType = getInt(mPref, KEY_NATIVE_SMOOTH_TYPE, 0);
		spinner = (Spinner)rootView.findViewById(R.id.use_native_smooth_spinner);
		spinner.setAdapter(new SmoothTypeAdapter(getActivity()));
		spinner.setOnItemSelectedListener(mOnItemSelectedListener);
	}

//--------------------------------------------------------------------------------
	/** OpenGL|ESで色抽出を行うかどうか  */
	protected boolean mEnableGLESExtraction = false;
	/** 色抽出範囲設定(HSV上下限) */
	protected final int[] EXTRACT_COLOR_HSV_LIMIT = new int[] {0, 180, 0, 50, 120, 255};
	/** native側の色抽出を使うかどうか */
	protected boolean mEnableNativeExtraction = false;

	private void initColorExtraction(final View rootView) {
		Switch sw;
		Button btn;
		// OpenGL|ESで色抽出を使うかどうか
		mEnableGLESExtraction = mPref.getBoolean(KEY_ENABLE_EXTRACTION, true);
		sw = (Switch)rootView.findViewById(R.id.use_extract_sw);
		sw.setChecked(mEnableGLESExtraction);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// Native側の色抽出を使うかどうか
		mEnableNativeExtraction = mPref.getBoolean(KEY_ENABLE_NATIVE_EXTRACTION, false);
		sw = (Switch)rootView.findViewById(R.id.use_native_extract_sw);
		sw.setChecked(mEnableNativeExtraction);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		// 抽出色取得
		btn = (Button)rootView.findViewById(R.id.update_extraction_color_btn);
		btn.setOnClickListener(mOnClickListener);
		// 抽出色リセット
		btn = (Button)rootView.findViewById(R.id.reset_extraction_color_btn);
		btn.setOnClickListener(mOnClickListener);

	}

//--------------------------------------------------------------------------------
	private String mAreaLimitMinFormat;
	private String mAspectLimitMinFormat;
	private String mAreaErrLimit1Format;
	private String mAreaErrLimit2Format;
	private TextView mAreaLimitMinLabel;
	private TextView mAspectLimitMinLabel;
	private TextView mAreaErrLimit1Label;
	private TextView mAreaErrLimit2Label;
	/** 輪郭検出時の最小面積 */
	protected float mAreaLimitMin = 1000.0f;
	protected static final float AREA_LIMIT_MAX = 120000.0f;
	// ラインe検出時の最小アスペクト比
	protected float mAspectLimitMin = 3.0f;
	// 輪郭検出時の面積誤差1, 2
	protected float mAreaErrLimit1 = 1.25f;
	protected float mAreaErrLimit2 = 1.3f;

	private void initDetect(final View rootView) {
		Button btn;
		SeekBar sb;

		mAreaLimitMinFormat = getString(R.string.trace_area_limit_min);
		mAspectLimitMinFormat = getString(R.string.trace_aspect_limit_min);
		mAreaErrLimit1Format = getString(R.string.trace_area_err_limit1);
		mAreaErrLimit2Format = getString(R.string.trace_area_err_limit2);

		// 輪郭検出時の最小面積
		mAreaLimitMin = mPref.getFloat(KEY_AREA_LIMIT_MIN, 1000.0f);
		mAreaLimitMinLabel = (TextView)rootView.findViewById(R.id.area_limit_min_textview);
		sb =(SeekBar)rootView.findViewById(R.id.area_limit_min_seekbar);
		sb.setMax(9700);
		sb.setProgress(areaLimitMinToProgress(mAreaLimitMin)); 	   // [300,10000] => [0, 9700]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAreaLimitMin(mAreaLimitMin);
		// ライン検出時の面積誤差1
		mAreaErrLimit1 = mPref.getFloat(KEY_AREA_ERR_LIMIT1, 1.25f);
		mAreaErrLimit1Label = (TextView)rootView.findViewById(R.id.area_err_limit1_textview);
		sb =(SeekBar)rootView.findViewById(R.id.area_err_limit1_seekbar);
		sb.setMax(100);
		sb.setProgress((int)((mAreaErrLimit1 - 1.0f) * 100.0f)); 	   // [1,2] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAreaErrLimit1(mAreaErrLimit1);
		// ライン検出時の面積誤差2
		mAreaErrLimit2 = mPref.getFloat(KEY_AREA_ERR_LIMIT2, 1.30f);
		mAreaErrLimit2Label = (TextView)rootView.findViewById(R.id.area_err_limit2_textview);
		sb =(SeekBar)rootView.findViewById(R.id.area_err_limit2_seekbar);
		sb.setMax(100);
		sb.setProgress((int)((mAreaErrLimit2 - 1.0f) * 100.0f)); 	   // [1,2] => [0, 100]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAreaErrLimit2(mAreaErrLimit2);
		// ライン検出時の最小アスペクト比
		mAspectLimitMin = mPref.getFloat(KEY_ASPECT_LIMIT_MIN, 3.0f);
		mAspectLimitMinLabel = (TextView)rootView.findViewById(R.id.aspect_limit_min_textview);
		sb =(SeekBar)rootView.findViewById(R.id.aspect_limit_min_seekbar);
		sb.setMax(190);
		sb.setProgress((int)((mAspectLimitMin - 1.0f) * 10)); 	   // [1,20] => [0, 190]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAspectLimitMin(mAspectLimitMin);
	}

	private int areaLimitMinToProgress(final float area_limit_min) {
		return (int)(area_limit_min - 300);
	}

	private float progressToAreaLimitMin(final int progress) {
		return progress + 300;
	}

	private void updateAreaLimitMin(final float area_limit_min) {
		if (mAreaLimitMinLabel != null) {
			mAreaLimitMinLabel.setText(String.format(mAreaLimitMinFormat, area_limit_min));
		}
	}

	private void updateAspectLimitMin(final float aspect) {
		if (mAspectLimitMinLabel != null) {
			mAspectLimitMinLabel.setText(String.format(mAspectLimitMinFormat, aspect));
		}
	}

	private void updateAreaErrLimit1(final float limit) {
		if (mAreaErrLimit1Label != null) {
			mAreaErrLimit1Label.setText(String.format(mAreaErrLimit1Format, limit));
		}
	}

	private void updateAreaErrLimit2(final float limit) {
		if (mAreaErrLimit2Label != null) {
			mAreaErrLimit2Label.setText(String.format(mAreaErrLimit2Format, limit));
		}
	}
//--------------------------------------------------------------------------------
	private TextView mFlightAttitudeYawLabel;
	private TextView mFlightSpeedLabel;
	private String mFlightAttitudeYawFormat;
	private String mFlightSpeedFormat;
	private float mFlightAttitudeYaw = 0;
	private float mFlightSpeed = 100;

	private void initAutoTrace(final View rootView) {
		SeekBar sb;
		//
		mFlightAttitudeYawFormat = getString(R.string.trace_config_flight_attitude_yaw);
		mFlightSpeedFormat = getString(R.string.trace_config_flight_speed);
		// 飛行姿勢(yaw)
		mFlightAttitudeYaw = mPref.getFloat(KEY_TRACE_FLIGHT_ATTITUDE_YAW, 0.0f);
		mFlightAttitudeYawLabel = (TextView)rootView.findViewById(R.id.trace_flight_attitude_yaw_textview);
		sb =(SeekBar)rootView.findViewById(R.id.trace_flight_attitude_yaw_seekbar);
		sb.setMax(180);
		sb.setProgress((int)(mFlightAttitudeYaw + 90));	// [-90,+90] => [0, 180]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateFlightAttitudeYaw(mFlightAttitudeYaw);
		// 飛行速度
		mFlightSpeed = mPref.getFloat(KEY_TRACE_FLIGHT_SPEED, 100.0f);
		mFlightSpeedLabel = (TextView)rootView.findViewById(R.id.trace_flight_speed_textview);
		sb =(SeekBar)rootView.findViewById(R.id.trace_flight_speed_seekbar);
		sb.setMax(200);
		sb.setProgress((int)(mFlightSpeed + 100));	// [-100,+100] => [0, 200]
		sb.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateFlightSpeed(mFlightSpeed);
	}

	private void updateFlightAttitudeYaw(final float attitude_yaw) {
		if (mFlightAttitudeYawLabel != null) {
			mFlightAttitudeYawLabel.setText(String.format(mFlightAttitudeYawFormat, attitude_yaw));
		}
	}

	private void updateFlightSpeed(final float speed) {
		if (mFlightSpeedLabel != null) {
			mFlightSpeedLabel.setText(String.format(mFlightSpeedFormat, speed));
		}
	}

//--------------------------------------------------------------------------------
	private String mMaxAltitudeFormat;
	private String mMaxTiltFormat;
	private String mMaxVerticalSpeedFormat;
	private String mMaxRotationSpeedFormat;
	private TextView mMaxAltitudeLabel;
	private TextView mMaxTiltLabel;
	private TextView mMaxVerticalSpeedLabel;
	private TextView mMaxRotationSpeedLabel;
	private AttributeFloat mMaxAltitude;
	private AttributeFloat mMaxTilt;
	private AttributeFloat mMaxVerticalSpeed;
	private AttributeFloat mMaxRotationSpeed;

	/**
	 * 飛行設定画面の準備
	 * @param root
	 */
	private void initConfigFlight(final View root) {
		if (DEBUG) Log.v(TAG, "initConfigFlight:");
		mMaxAltitudeFormat = getString(R.string.config_max_altitude);
		mMaxTiltFormat = getString(R.string.config_max_tilt);
		mMaxVerticalSpeedFormat = getString(R.string.config_max_vertical_speed);
		mMaxRotationSpeedFormat = getString(R.string.config_max_rotating_speed);
		// 最大高度設定
		mMaxAltitudeLabel = (TextView)root.findViewById(R.id.max_altitude_textview);
		SeekBar seekbar = (SeekBar)root.findViewById(R.id.max_altitude_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxAltitude = mFlightController.getMaxAltitude();
		try {
			seekbar.setProgress((int) ((mMaxAltitude.current() - mMaxAltitude.min()) / (mMaxAltitude.max() - mMaxAltitude.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxAltitude(mMaxAltitude.current());
		// 最大傾斜設定
		// bebopは5-30度。最大時速約50km/hrからすると13.9m/s/30度≒0.46[m/s/度]
		mMaxTiltLabel = (TextView)root.findViewById(R.id.max_tilt_textview);
		seekbar = (SeekBar)root.findViewById(R.id.max_tilt_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxTilt = mFlightController.getMaxTilt();
		try {
			seekbar.setProgress((int) ((mMaxTilt.current() - mMaxTilt.min()) / (mMaxTilt.max() - mMaxTilt.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxTilt(mMaxTilt.current());
		// 最大上昇/降下速度設定
		mMaxVerticalSpeedLabel = (TextView)root.findViewById(R.id.max_vertical_speed_textview);
		seekbar = (SeekBar)root.findViewById(R.id.max_vertical_speed_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxVerticalSpeed = mFlightController.getMaxVerticalSpeed();
		try {
			seekbar.setProgress((int) ((mMaxVerticalSpeed.current() - mMaxVerticalSpeed.min()) / (mMaxVerticalSpeed.max() - mMaxVerticalSpeed.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxVerticalSpeed(mMaxVerticalSpeed.current());
		// 最大回転速度
		mMaxRotationSpeedLabel = (TextView)root.findViewById(R.id.max_rotation_speed_textview);
		seekbar = (SeekBar)root.findViewById(R.id.max_rotation_speed_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxRotationSpeed = mFlightController.getMaxRotationSpeed();
		try {
			seekbar.setProgress((int) ((mMaxRotationSpeed.current() - mMaxRotationSpeed.min()) / (mMaxRotationSpeed.max() - mMaxRotationSpeed.min()) * 1000));
		} catch (final Exception e) {
			seekbar.setProgress(0);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateMaxRotationSpeed(mMaxRotationSpeed.current());
	}

	/**
	 * 最大高度設定値表示を更新
	 * @param max_altitude
	 */
	private void updateMaxAltitude(final float max_altitude) {
		if (mMaxAltitudeLabel != null) {
			mMaxAltitudeLabel.setText(String.format(mMaxAltitudeFormat, max_altitude));
		}
	}

	/**
	 * 最大傾斜設定表示を更新
	 * @param max_tilt
	 */
	private void updateMaxTilt(final float max_tilt) {
		if (mMaxTiltLabel != null) {
			mMaxTiltLabel.setText(String.format(mMaxTiltFormat, max_tilt));
		}
	}

	/**
	 * 最大上昇/降下速度設定表示を更新
	 * @param max_vertical_speed
	 */
	private void updateMaxVerticalSpeed(final float max_vertical_speed) {
		if (mMaxVerticalSpeedLabel != null) {
			mMaxVerticalSpeedLabel.setText(String.format(mMaxVerticalSpeedFormat, max_vertical_speed));
		}
	}

	/**
	 * 最大回転速度設定表示を更新
	 * @param max_rotation_speed
	 */
	private void updateMaxRotationSpeed(final float max_rotation_speed) {
		if (mMaxRotationSpeedLabel != null) {
			mMaxRotationSpeedLabel.setText(String.format(mMaxRotationSpeedFormat, max_rotation_speed));
		}
	}

//----------------------------------------------------------------------
	private TextView mAutopilotScaleXLabel;
	private TextView mAutopilotScaleYLabel;
	private TextView mAutopilotScaleZLabel;
	private TextView mAutopilotScaleRLabel;
	private TextView mAutopilotMaxControlValueLabel;
	private String mAutopilotScaleXFormat;
	private String mAutopilotScaleYFormat;
	private String mAutopilotScaleZFormat;
	private String mAutopilotScaleRFormat;
	private String mAutopilotMaxControlValueFormat;
	/**
	 * 自動操縦設定画面の準備
	 * @param root
	 */
	private void initConfigAutopilot(final View root) {
		mAutopilotScaleXFormat = getString(R.string.config_scale_x);
		mAutopilotScaleYFormat = getString(R.string.config_scale_y);
		mAutopilotScaleZFormat = getString(R.string.config_scale_z);
		mAutopilotScaleRFormat = getString(R.string.config_scale_r);
		mAutopilotMaxControlValueFormat = getString(R.string.config_control_max);
		// 最大制御値設定
		mAutopilotMaxControlValueLabel = (TextView)root.findViewById(R.id.max_control_value_textview);
		SeekBar seekbar = (SeekBar)root.findViewById(R.id.max_control_value_seekbar);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mMaxControlValue = mPref.getFloat(KEY_AUTOPILOT_MAX_CONTROL_VALUE, 100.0f);
		try {
			seekbar.setProgress((int) (mMaxControlValue + SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotMaxControlValue(mMaxControlValue);
		// スケールX設定
		mAutopilotScaleXLabel = (TextView)root.findViewById(R.id.scale_x_textview);
		seekbar = (SeekBar)root.findViewById(R.id.scale_seekbar_x);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleX = mPref.getFloat(KEY_AUTOPILOT_SCALE_X, 1.0f);
		try {
			seekbar.setProgress((int) (mScaleX * SCALE_FACTOR + SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleX(mScaleX);
		// スケールY設定
		mAutopilotScaleYLabel = (TextView)root.findViewById(R.id.scale_y_textview);
		seekbar = (SeekBar)root.findViewById(R.id.scale_seekbar_y);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleY = mPref.getFloat(KEY_AUTOPILOT_SCALE_Y, 1.0f);
		try {
			seekbar.setProgress((int) (mScaleY * SCALE_FACTOR + SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleY(mScaleY);
		// スケールZ設定
		mAutopilotScaleZLabel = (TextView)root.findViewById(R.id.scale_z_textview);
		seekbar = (SeekBar)root.findViewById(R.id.scale_seekbar_z);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleZ = mPref.getFloat(KEY_AUTOPILOT_SCALE_Z, 1.0f);
		try {
			seekbar.setProgress((int) (mScaleZ * SCALE_FACTOR + SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleZ(mScaleZ);
		// スケールR設定
		mAutopilotScaleRLabel = (TextView)root.findViewById(R.id.scale_r_textview);
		seekbar = (SeekBar)root.findViewById(R.id.scale_seekbar_r);
		seekbar.setOnSeekBarChangeListener(null);
		seekbar.setMax(1000);
		mScaleR = mPref.getFloat(KEY_AUTOPILOT_SCALE_R, 1.0f);
		try {
			seekbar.setProgress((int) (mScaleR * SCALE_FACTOR + SCALE_OFFSET));
		} catch (final Exception e) {
			seekbar.setProgress(SCALE_OFFSET);
		}
		seekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
		updateAutopilotScaleR(mScaleR);
	}

	/**
	 * 自動操縦:最大制御設定値表示を更新
	 * @param max_control_value
	 */
	private void updateAutopilotMaxControlValue(final double max_control_value) {
		if (mAutopilotMaxControlValueLabel != null) {
			mAutopilotMaxControlValueLabel.setText(String.format(mAutopilotMaxControlValueFormat, max_control_value));
		}
	}

	/**
	 * 自動操縦:スケールZ設定表示を更新
	 * @param scale_x
	 */
	private void updateAutopilotScaleX(final double scale_x) {
		if (mAutopilotScaleXLabel != null) {
			mAutopilotScaleXLabel.setText(String.format(mAutopilotScaleXFormat, scale_x));
		}
	}

	/**
	 * 自動操縦:スケールY設定表示を更新
	 * @param scale_y
	 */
	private void updateAutopilotScaleY(final double scale_y) {
		if (mAutopilotScaleYLabel != null) {
			mAutopilotScaleYLabel.setText(String.format(mAutopilotScaleYFormat, scale_y));
		}
	}

	/**
	 * 自動操縦:スケールZ設定表示を更新
	 * @param scale_z
	 */
	private void updateAutopilotScaleZ(final double scale_z) {
		if (mAutopilotScaleZLabel != null) {
			mAutopilotScaleZLabel.setText(String.format(mAutopilotScaleZFormat, scale_z));
		}
	}

	/**
	 * 自動操縦:スケールR設定表示を更新
	 * @param scale_r
	 */
	private void updateAutopilotScaleR(final double scale_r) {
		if (mAutopilotScaleRLabel != null) {
			mAutopilotScaleRLabel.setText(String.format(mAutopilotScaleRFormat, scale_r));
		}
	}

//================================================================================
//================================================================================
	private static interface AdapterItemHandler {
		public void initialize(final AutoPilotFragment2 parent, final View view);
	}

	private static final class PagerAdapterConfig {
		public final int title_id;
		public final int layout_id;
		public final AdapterItemHandler handler;

		public PagerAdapterConfig(final int _title_id, final int _layout_id, final AdapterItemHandler _handler) {
			title_id = _title_id;
			layout_id = _layout_id;
			handler = _handler;
		}
	}

	private static PagerAdapterConfig[] PAGER_CONFIG_TRACE;
	static {
		//
		PAGER_CONFIG_TRACE = new PagerAdapterConfig[7];
		PAGER_CONFIG_TRACE[0] = new PagerAdapterConfig(R.string.trace_config_title_preprocess, R.layout.trace_config_preprocess, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initPreprocess(view);
			}
		});
		PAGER_CONFIG_TRACE[1] = new PagerAdapterConfig(R.string.trace_config_title_preprocess2, R.layout.trace_config_preprocess2, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initPreprocess2(view);
			}
		});
		PAGER_CONFIG_TRACE[2] = new PagerAdapterConfig(R.string.trace_config_title_color_extract, R.layout.trace_config_color_extraction, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initColorExtraction(view);
			}
		});
		PAGER_CONFIG_TRACE[3] = new PagerAdapterConfig(R.string.trace_config_title_detect, R.layout.trace_config_detect, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initDetect(view);
			}
		});
		PAGER_CONFIG_TRACE[4] = new PagerAdapterConfig(R.string.trace_config_title_auto_trace, R.layout.trace_config_auto_trace, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initAutoTrace(view);
			}
		});
		PAGER_CONFIG_TRACE[5] = new PagerAdapterConfig(R.string.config_title_flight, R.layout.trace_config_flight, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initConfigFlight(view);
			}
		});
		PAGER_CONFIG_TRACE[6] = new PagerAdapterConfig(R.string.config_title_autopilot, R.layout.trace_config_autopilot, new AdapterItemHandler() {
			@Override
			public void initialize(final AutoPilotFragment2 parent, final View view) {
				parent.initConfigAutopilot(view);
			}
		});
	}

	/**
	 * 設定画面の各ページ用のViewを提供するためのPagerAdapterクラス
	 */
	private class ConfigPagerAdapter extends PagerAdapter {
		private final LayoutInflater mInflater;
		private final PagerAdapterConfig[] mConfigs;
		public ConfigPagerAdapter(final LayoutInflater inflater) {
			super();
			mInflater = inflater;
			mConfigs = PAGER_CONFIG_TRACE;
		}

		@Override
		public synchronized Object instantiateItem(final ViewGroup container, final int position) {
//			if (DEBUG) Log.v(TAG, "instantiateItem:position=" + position);
			View view = null;
			final int n = mConfigs != null ? mConfigs.length : 0;
			if ((position >= 0) && (position < n)) {
				final PagerAdapterConfig config = mConfigs[position];
				view = mInflater.inflate(config.layout_id, container, false);
				config.handler.initialize(AutoPilotFragment2.this, view);
			}
			if (view != null) {
				container.addView(view);
			}
			return view;
		}

		@Override
		public synchronized void destroyItem(final ViewGroup container, final int position, final Object object) {
//			if (DEBUG) Log.v(TAG, "destroyItem:position=" + position);
			if (object instanceof View) {
				container.removeView((View)object);
			}
		}

		@Override
		public int getCount() {
			return mConfigs != null ? mConfigs.length : 0;
		}

		@Override
		public boolean isViewFromObject(final View view, final Object object) {
			return view.equals(object);
		}

		@Override
		public CharSequence getPageTitle(final int position) {
//			if (DEBUG) Log.v(TAG, "getPageTitle:position=" + position);
			CharSequence result = null;
			final int n = mConfigs != null ? mConfigs.length : 0;
			if ((position >= 0) && (position < n)) {
				result = getString(mConfigs[position].title_id);
			}
			return result;
		}
	}
}