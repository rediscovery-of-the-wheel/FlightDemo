package com.serenegiant.flightdemo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.serenegiant.arflight.DeviceControllerMiniDrone;
import com.serenegiant.arflight.FlightRecorder;
import com.serenegiant.arflight.IDeviceController;
import com.serenegiant.dialog.SelectFileDialogFragment;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.widget.SideMenuListView;
import com.serenegiant.widget.StickView;
import com.serenegiant.widget.StickView.OnStickMoveListener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PilotFragment extends ControlFragment implements SelectFileDialogFragment.OnFileSelectListener {
	private static final boolean DEBUG = true;	// FIXME 実働時はfalseにすること
	private static String TAG = PilotFragment.class.getSimpleName();

	static {
		FileUtils.DIR_NAME = "FlightDemo";
	}

	public static PilotFragment newInstance(final ARDiscoveryDeviceService device) {
		final PilotFragment fragment = new PilotFragment();
		fragment.setDevice(device);
		return fragment;
	}

	private View mControllerView;	// 操作パネル
	// 上パネル
	private View mTopPanel;
	private TextView mBatteryLabel;
	private ImageButton mFlatTrimBtn;	// フラットトリム
	private TextView mAlertMessage;
	// 下パネル
	private View mBottomPanel;
	private ImageButton mEmergencyBtn;	// 非常停止ボタン
	private ImageButton mTakeOnOffBtn;	// 離陸/着陸ボタン
	private ImageButton mRecordBtn;		// 記録ボタン
	private TextView mRecordLabel;
	private ImageButton mPlayBtn;		// 再生ボタン
	private TextView mPlayLabel;
	private ImageButton mLoadBtn;		// 読み込みボタン
	private ImageButton mConfigShowBtn;	// 設定パネル表示ボタン
	private TextView mTimeLabelTv;
	// 右サイドパネル
	private View mRightSidePanel;
	// 左サイドパネル
	private View mLeftSidePanel;
	// 右スティックパネル
	private StickView mRightStickPanel;
	// 左スティックパネル
	private StickView mLeftStickPanel;

	private final FlightRecorder mFlightRecorder = new FlightRecorder();
	/** 操縦に使用するボタン等。操作可・不可に応じてenable/disableを切り替える */
	private final List<View> mActionViews = new ArrayList<View>();

	private SideMenuListView mSideMenuListView;

	public PilotFragment() {
		super();
		// デフォルトコンストラクタが必要
		mFlightRecorder.setPlaybackListener(mFlightRecorderListener);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if (DEBUG) Log.v(TAG, "onAttach:");
	}

	@Override
	public void onDetach() {
		if (DEBUG) Log.v(TAG, "onDetach:");
		super.onDetach();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		final SharedPreferences pref = getActivity().getPreferences(0);
		final boolean reverse_operation = pref.getBoolean(ConfigFragment.KEY_REVERSE_OPERATION, false);

		final View rootView = inflater.inflate(reverse_operation ?
			R.layout.fragment_pilot_minidrone_reverse : R.layout.fragment_pilot_minidrone,
			container, false);

		mControllerView = rootView.findViewById(R.id.controller_frame);

		mActionViews.clear();
		// 上パネル
		mTopPanel = rootView.findViewById(R.id.top_panel);
		mActionViews.add(mTopPanel);

		mFlatTrimBtn = (ImageButton)rootView.findViewById(R.id.flat_trim_btn);
		mFlatTrimBtn.setOnLongClickListener(mOnLongClickListener);
		mActionViews.add(mFlatTrimBtn);

		mConfigShowBtn = (ImageButton)rootView.findViewById(R.id.config_show_btn);
		mConfigShowBtn.setOnClickListener(mOnClickListener);

		// 下パネル
		mBottomPanel = rootView.findViewById(R.id.bottom_panel);
		mEmergencyBtn = (ImageButton)rootView.findViewById(R.id.emergency_btn);
		mEmergencyBtn.setOnClickListener(mOnClickListener);

		mTakeOnOffBtn = (ImageButton)rootView.findViewById(R.id.take_onoff_btn);
		mTakeOnOffBtn.setOnClickListener(mOnClickListener);
		mActionViews.add(mTakeOnOffBtn);

		mRecordBtn = (ImageButton)rootView.findViewById(R.id.record_btn);
		mRecordBtn.setOnClickListener(mOnClickListener);
		mRecordBtn.setOnLongClickListener(mOnLongClickListener);

		mRecordLabel = (TextView)rootView.findViewById(R.id.record_label);

		mPlayBtn = (ImageButton)rootView.findViewById(R.id.play_btn);
		mPlayBtn.setOnClickListener(mOnClickListener);
		mPlayBtn.setOnLongClickListener(mOnLongClickListener);

		mPlayLabel = (TextView)rootView.findViewById(R.id.play_label);

		mLoadBtn = (ImageButton)rootView.findViewById(R.id.load_btn);
		mLoadBtn.setOnClickListener(mOnClickListener);
		mLoadBtn.setOnLongClickListener(mOnLongClickListener);

		mTimeLabelTv = (TextView)rootView.findViewById(R.id.time_label);
		mTimeLabelTv.setVisibility(View.INVISIBLE);

		ImageButton button;
		// 右サイドパネル
		mRightSidePanel = rootView.findViewById(R.id.right_side_panel);
		mActionViews.add(mRightSidePanel);

		button = (ImageButton)rootView.findViewById(R.id.cap_p15_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton)rootView.findViewById(R.id.cap_p45_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton)rootView.findViewById(R.id.cap_m15_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton)rootView.findViewById(R.id.cap_m45_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		// 左サイドパネル
		mLeftSidePanel = rootView.findViewById(R.id.left_side_panel);
		mActionViews.add(mLeftSidePanel);

		button = (ImageButton)rootView.findViewById(R.id.flip_right_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton)rootView.findViewById(R.id.flip_left_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton)rootView.findViewById(R.id.flip_front_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton)rootView.findViewById(R.id.flip_back_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		// 右スティックパネル
		mRightStickPanel = (StickView)rootView.findViewById(R.id.stick_view_right);
		mRightStickPanel.setOnStickMoveListener(mOnStickMoveListener);
		mActionViews.add(mRightStickPanel);

		// 左スティックパネル
		mLeftStickPanel = (StickView)rootView.findViewById(R.id.stick_view_left);
		mLeftStickPanel.setOnStickMoveListener(mOnStickMoveListener);
		mActionViews.add(mRightStickPanel);

		mBatteryLabel = (TextView)rootView.findViewById(R.id.batteryLabel);
		mAlertMessage = (TextView)rootView.findViewById(R.id.alert_message);
		mAlertMessage.setVisibility(View.INVISIBLE);

		// サイドメニュー
/*		prepareSideMenu(rootView);
		mSideMenuListView = (SideMenuListView)rootView.findViewById(R.id.side_menu_listview);
		mSideMenuListView.setOnItemClickListener(mOnItemClickListener); */

		return rootView;
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		stopDeviceController(false);
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		startDeviceController();
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		removeFromUIThread(mPopBackStackTask);
		stopRecord();
		stopPlay();
		mResetColorFilterTasks.clear();
		super.onPause();
	}

	@Override
	public void onFileSelect(File[] files) {
		if (DEBUG) Log.v(TAG, "onFileSelect:");
		if ((files != null) && (files.length > 0)
			&& !mFlightRecorder.isPlaying() && !mFlightRecorder.isRecording() ) {
			mFlightRecorder.load(files[0]);
			updateButtons();
		}
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (DEBUG) Log.v(TAG, "onClick:" + view);
			switch (view.getId()) {
			case R.id.load_btn:
				// 読み込みボタンの処理
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				final File root = FileUtils.getCaptureDir(getActivity(), "Documents", false);
				SelectFileDialogFragment.showDialog(PilotFragment.this, root.getAbsolutePath(), false, "fcr");
				break;
			case R.id.record_btn:
				// 記録ボタンの処理
				if (!mFlightRecorder.isRecording()) {
					startRecord(true);
				} else {
					stopRecord();
				}
				updateButtons();
				break;
			case R.id.play_btn:
				// 再生ボタンの処理
				stopMove();
				if (!mFlightRecorder.isPlaying()) {
					startPlay();
				} else {
					stopPlay();
				}
				break;
			case R.id.config_show_btn:
				// 設定パネル表示処理
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (isConnected()) {
					if ((mController.getState() & IDeviceController.STATE_MASK_FLYING) == IDeviceController.STATE_FLYING_LANDED) {
						final ConfigFragment fragment = ConfigFragment.newInstance(getDevice());
						getFragmentManager().beginTransaction()
							.addToBackStack(null)
							.replace(R.id.container, fragment)
							.commit();
					} else {
						landing();
					}
				}
				break;
			case R.id.emergency_btn:
				// 非常停止指示ボタンの処理
				setColorFilter((ImageView) view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				emergencyStop();
				break;
			case R.id.take_onoff_btn:
				// 離陸指示/着陸指示ボタンの処理
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				mIsFlying = !mIsFlying;
				if (mIsFlying) {
					takeOff();
				} else {
					landing();
				}
				updateButtons();
				break;
			case R.id.flip_front_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsFlip(DeviceControllerMiniDrone.FLIP_FRONT);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceControllerMiniDrone.FLIP_FRONT);
				}
				break;
			case R.id.flip_back_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsFlip(DeviceControllerMiniDrone.FLIP_BACK);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceControllerMiniDrone.FLIP_BACK);
				}
				break;
			case R.id.flip_right_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsFlip(DeviceControllerMiniDrone.FLIP_RIGHT);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceControllerMiniDrone.FLIP_RIGHT);
				}
				break;
			case R.id.flip_left_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsFlip(DeviceControllerMiniDrone.FLIP_LEFT);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceControllerMiniDrone.FLIP_LEFT);
				}
				break;
			case R.id.cap_p15_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsCap(15);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, 15);
				}
				break;
			case R.id.cap_p45_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsCap(45);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, 45);
				}
				break;
			case R.id.cap_m15_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsCap(-15);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, -15);
				}
				break;
			case R.id.cap_m45_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					((DeviceControllerMiniDrone) mController).sendAnimationsCap(-45);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, -45);
				}
				break;
/*			case R.id.north_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					mController.setHeading(0);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 0);
				}
				break;
			case R.id.south_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					mController.setHeading(180);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 180);
				}
				break;
			case R.id.west_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					mController.setHeading(-90);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, -90);
				}
				break;
			case R.id.east_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mController != null) {
					mController.setHeading(90);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 90);
				}
				break; */
			}
		}
	};

	private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
			if (DEBUG) Log.v(TAG, "onLongClick:" + view);
			switch (view.getId()) {
			case R.id.record_btn:
				if (!mFlightRecorder.isRecording()) {
					startRecord(false);
				} else {
					stopRecord();
				}
				return true;
			case R.id.flat_trim_btn:
				setColorFilter((ImageView)view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if ((mController != null) && (mController.getState() == IDeviceController.STATE_STARTED)) {
					mController.sendFlatTrim();
					return true;
				}
				break;
			}
			return false;
		}
	};

	private static final int CTRL_STEP = 5;
	private float mFirstPtRightX, mFirstPtRightY;
	private int mPrevRightMX, mPrevRightMY;
	private float mFirstPtLeftX, mFirstPtLeftY;
	private int mPrevLeftMX, mPrevLeftMY;
	private final OnStickMoveListener mOnStickMoveListener = new OnStickMoveListener() {
		@Override
		public void onStickMove(final View view, final float dx, final float dy) {
			int mx = (int) (dx * 100);
			if (mx < -100) mx = -100;
			else if (mx > 100) mx = 100;
			mx = (mx / CTRL_STEP) * CTRL_STEP;
			int my = (int) (dy * 100);
			if (my < -100) my = -100;
			else if (my > 100) my = 100;
			my = (my / CTRL_STEP) * CTRL_STEP;
			switch (view.getId()) {
			case R.id.stick_view_right: {
				if (mx != mPrevRightMX) {
					mPrevRightMX = mx;
					if (mController != null) {
						mController.setRoll((byte) mx);
						mController.setFlag((byte) (mx != 0 ? 1 : 0));
						mFlightRecorder.record(FlightRecorder.CMD_RIGHT_LEFT, mx);
					}
				}
				if (my != mPrevRightMY) {
					mPrevRightMY = my;
					if (mController != null) {
						mController.setPitch((byte) -my);
						mFlightRecorder.record(FlightRecorder.CMD_FORWARD_BACK, -my);
					}
				}
				break;
			}
			case R.id.stick_view_left: {
				if ((Math.abs(mx) < 20)) mx = 0;
				if (mx != mPrevLeftMX) {
					mPrevLeftMX = mx;
					if (mController != null) {
						mController.setYaw((byte) mx);
						mFlightRecorder.record(FlightRecorder.CMD_TURN, mx);
					}
				}
				if (my != mPrevLeftMY) {
					mPrevLeftMY = my;
					if (mController != null) {
						mController.setGaz((byte) -my);
						mFlightRecorder.record(FlightRecorder.CMD_UP_DOWN, -my);
					}
				}
				break;
			}
			}
		}
	};

	@Override
	protected void onConnect(final IDeviceController controller) {
		if (DEBUG) Log.v(TAG, "#onConnect");
//		setSideMenuEnable(true);
		updateButtons();
	}

	private static final long POP_BACK_STACK_DELAY = 2000;
	@Override
	protected void onDisconnect(final IDeviceController controller) {
		if (DEBUG) Log.v(TAG, "#onDisconnect");
//		setSideMenuEnable(false);
		stopRecord();
		stopPlay();
		removeFromUIThread(mPopBackStackTask);
		postUIThread(mPopBackStackTask, POP_BACK_STACK_DELAY);
		super.onDisconnect(controller);
	}

	@Override
	protected void updateFlyingState(final int state) {
		updateButtons();
	}

	@Override
	protected void updateAlarmState(final int alert_state) {
		runOnUiThread(mUpdateAlarmMessageTask);
		updateButtons();
	}

	@Override
	protected void updateBattery() {
		runOnUiThread(mUpdateBatteryTask);
	}

	/**
	 * 移動停止
	 */
	@Override
	protected void stopMove() {
		if (DEBUG) Log.v(TAG, "stopMove:");
		super.stopMove();
		if (mController != null) {
			mFlightRecorder.record(FlightRecorder.CMD_UP_DOWN, 0);
			mFlightRecorder.record(FlightRecorder.CMD_TURN, 0);
			mFlightRecorder.record(FlightRecorder.CMD_FORWARD_BACK, 0);
			mFlightRecorder.record(FlightRecorder.CMD_RIGHT_LEFT, 0);
		}
	}

	@Override
	protected void emergencyStop() {
		super.emergencyStop();
		stopPlay();
	}

	/**
	 * 離陸指示
	 */
	private void takeOff() {
		// 離陸指示
		if (mController != null) {
			mController.sendTakeoff();
			mFlightRecorder.record(FlightRecorder.CMD_TAKEOFF);
		} else {
			mIsFlying = false;
		}
	}

	/**
	 * 着陸指示
	 */
	private void landing() {
		// 着陸指示
		stopMove();
		if (mController != null) {
			mController.sendLanding();
			mFlightRecorder.record(FlightRecorder.CMD_LANDING);
		}
		mIsFlying = false;
	}

	/**
	 * 記録開始
	 * @param needClear 既存の記録を破棄してから記録開始するかどうか
	 */
	private void startRecord(final boolean needClear) {
		if (DEBUG) Log.v(TAG, "startRecord:");
		if (!mFlightRecorder.isRecording() && !mFlightRecorder.isPlaying()) {
			if (needClear) {
				mFlightRecorder.clear();
			}
			mFlightRecorder.start();
			updateTime(0);
			updateButtons();
		}
	}

	/**
	 * 記録終了
	 */
	private void stopRecord() {
		if (DEBUG) Log.v(TAG, "stopRecord:");
		if (mFlightRecorder.isRecording()) {
			mFlightRecorder.stop();
			// ファイルへ保存
			final String path = FileUtils.getCaptureFile(getActivity(), "Documents", ".fcr", false).getAbsolutePath();
			if (!TextUtils.isEmpty(path)) {
				mFlightRecorder.save(path);
				updateButtons();
			}
			updateTime(-1);
		}
	}

	/**
	 * 再生開始
	 */
	private void startPlay() {
		if (DEBUG) Log.v(TAG, "startPlay:");
		if (!mFlightRecorder.isRecording() && !mFlightRecorder.isPlaying() && (mFlightRecorder.size() > 0)) {
			mFlightRecorder.pos(0);
			mFlightRecorder.play();
			updateTime(0);
			updateButtons();
		}
	}

	/**
	 * 再生終了
	 */
	private void stopPlay() {
		if (DEBUG) Log.v(TAG, "stopPlay:");
		if (mFlightRecorder.isPlaying()) {
			mFlightRecorder.stop();
			updateButtons();
		}
	}

	/**
	 * 飛行記録再生時のコールバックリスナー
	 */
	private final FlightRecorder.FlightRecorderListener mFlightRecorderListener = new FlightRecorder.FlightRecorderListener() {
		@Override
		public void onStart() {
			if (DEBUG) Log.v(TAG, "mFlightRecorderListener#onStart:");
			updateTime(0);
			updateButtons();
		}

		@Override
		public boolean onStep(final int cmd, final int value, final long t) {
//			if (DEBUG) Log.v(TAG, String.format("mFlightRecorderListener#onStep:cmd=%d,v=%d,t=%d", cmd, value, t));
			updateTime(t);
			if (mController != null) {
				switch (cmd) {
				case FlightRecorder.CMD_EMERGENCY:		// 非常停止
					mController.sendEmergency();
					break;
				case FlightRecorder.CMD_TAKEOFF:		// 離陸
					mController.sendTakeoff();
					break;
				case FlightRecorder.CMD_LANDING:		// 着陸
					mController.sendLanding();
					break;
				case FlightRecorder.CMD_UP_DOWN:		// 上昇:gaz>0, 下降: gaz<0
					mController.setGaz((byte) value);
					break;
				case FlightRecorder.CMD_RIGHT_LEFT:		// 右: roll>0,flag=1 左: roll<0,flag=1
					mController.setRoll((byte) value);
					mController.setFlag((byte) (value != 0 ? 1 : 0));
					break;
				case FlightRecorder.CMD_FORWARD_BACK:	// 前進: pitch>0,flag=1, 後退: pitch<0,flag=1
					mController.setPitch((byte) value);
					break;
				case FlightRecorder.CMD_TURN:			// 右回転: yaw>0, 左回転: ywa<0
					mController.setYaw((byte) value);
					break;
				case FlightRecorder.CMD_COMPASS:		// 北磁極に対する角度 -360〜360度
					mController.setHeading(value);		// 実際は浮動小数点だけど
					break;
				case FlightRecorder.CMD_FLIP:			// フリップ
					((DeviceControllerMiniDrone) mController).sendAnimationsFlip(value);
					break;
				case FlightRecorder.CMD_CAP:			// キャップ(指定角度水平回転)
					((DeviceControllerMiniDrone) mController).sendAnimationsCap(value);
					break;
				}
				return false;
			} else {
				return true;
			}
		}

		@Override
		public void onStop() {
			if (DEBUG) Log.v(TAG, "mFlightRecorderListener#onStop:");
			updateTime(-1);
			updateButtons();
		}

		@Override
		public void onRecord(final int cmd, final int value, final long t) {
		}
	};

	/**
	 * アラート表示の更新処理をUIスレッドで実行するためのRunnable
	 */
	private final Runnable mUpdateAlarmMessageTask = new Runnable() {
		@Override
		public void run() {
			final int alarm = getAlarm();
			if (DEBUG) Log.w(TAG, "mUpdateAlarmMessageTask:alarm=" + alarm);
			switch (alarm) {
			case IDeviceController.ALARM_NON:				// No alert
				break;
			case IDeviceController.ALARM_USER_EMERGENCY:	// User emergency alert
				mAlertMessage.setText(R.string.alarm_user_emergency);
				break;
			case IDeviceController.ALARM_CUTOUT:			// Cut out alert
				mAlertMessage.setText(R.string.alarm_motor_cut_out);
				break;
			case IDeviceController.ALARM_BATTERY_CRITICAL:	// Critical battery alert
				mAlertMessage.setText(R.string.alarm_low_battery_critical);
				break;
			case IDeviceController.ALARM_BATTERY:			// Low battery alert
				mAlertMessage.setText(R.string.alarm_low_battery);
				break;
			case IDeviceController.ALARM_DISCONNECTED:		// 切断された
				mAlertMessage.setText(R.string.alarm_disconnected);
				break;
			default:
				Log.w(TAG, "unexpected alarm state:" + alarm);
				break;
			}
			mAlertMessage.setVisibility(alarm != 0 ? View.VISIBLE : View.INVISIBLE);
		}
	};

	/**
	 * バッテリー残量表示の更新処理をUIスレッドでするためのRunnable
	 */
	private final Runnable mUpdateBatteryTask = new Runnable() {
		@Override
		public void run() {
			final int battery = mController != null ? mController.getBattery() : -1;
			if (battery >= 0) {
				mBatteryLabel.setText(String.format("%d%%", battery));
			} else {
				mBatteryLabel.setText("---");
			}
		}
	};

	private volatile long lastCall = -1;
	private void updateTime(final long t) {
//		if (DEBUG) Log.v(TAG, "updateTime:" + t);
		mCurrentTime = t;
		lastCall = System.currentTimeMillis();
		runOnUiThread(mUpdateTimeTask);
	}

	private volatile long mCurrentTime;	// 現在の経過時間[ミリ秒]
	private final Runnable mIntervalUpdateTimeTask = new Runnable() {
		@Override
		public void run() {
			if (mCurrentTime >= 0) {
				runOnUiThread(mUpdateTimeTask);
			}
		}
	};

	private final Runnable mUpdateTimeTask = new Runnable() {
		@Override
		public void run() {
			remove(mIntervalUpdateTimeTask);
			long t = mCurrentTime;
			if (t >= 0) {
				t +=  System.currentTimeMillis() - lastCall;
				final int m = (int)(t / 60000);
				final int s = (int)(t - m * 60000) / 1000;
				mTimeLabelTv.setText(String.format("%3d:%02d", m, s));
				post(mIntervalUpdateTimeTask, 500);
			}
		}
	};

	/**
	 * ボタン表示の更新(UIスレッドで処理)
	 */
	private void updateButtons() {
		runOnUiThread(mUpdateButtonsTask);
	}

	private static final int DISABLE_COLOR = 0xcf777777;
	/**
	 *　ボタンの表示更新をUIスレッドで行うためのRunnable
	 */
	private final Runnable mUpdateButtonsTask = new Runnable() {
		@Override
		public void run() {
			final int state = getState();
			final int alarm_state = getAlarm();
			final boolean is_connected = isConnected();
			final boolean is_recording = mFlightRecorder.isRecording();
			final boolean is_playing = mFlightRecorder.isPlaying();
			final boolean can_play = is_connected && !is_recording && (alarm_state == IDeviceController.ALARM_NON) && (mFlightRecorder.size() > 0);
			final boolean can_record = is_connected && !is_playing;
			final boolean can_load = is_connected && !is_playing && !is_recording;
			final boolean can_fly = can_record && (alarm_state == IDeviceController.ALARM_NON);
			final boolean can_flattrim = can_fly && (state == IDeviceController.STATE_STARTED);
			final boolean can_config = can_flattrim;
			final boolean is_battery_alarm
				= (alarm_state == IDeviceController.ALARM_BATTERY)
					|| (alarm_state == IDeviceController.ALARM_BATTERY_CRITICAL);

			// 上パネル
			mTopPanel.setEnabled(is_connected);
			mFlatTrimBtn.setEnabled(can_flattrim);	// フラットトリム
			mBatteryLabel.setTextColor(is_battery_alarm ? 0xffff0000 : 0xff000000);
			mConfigShowBtn.setEnabled(can_config);
			mConfigShowBtn.setColorFilter(can_config ? 0: DISABLE_COLOR);

			// 下パネル
			mBottomPanel.setEnabled(is_connected);
			mEmergencyBtn.setEnabled(is_connected);	// 非常停止
			mTimeLabelTv.setVisibility(is_recording || is_playing ? View.VISIBLE : View.INVISIBLE);
			mLoadBtn.setEnabled(can_load);            // 読み込み
			mPlayBtn.setEnabled(can_play);            // 再生
			mPlayBtn.setColorFilter(can_play ? (mFlightRecorder.isPlaying() ? 0xffff0000 : 0) : DISABLE_COLOR);
			mPlayLabel.setText(is_recording ? R.string.action_stop : R.string.action_play);
			mRecordBtn.setEnabled(can_record);        // 記録
			mRecordBtn.setColorFilter(can_record ? (is_recording ? 0xffff0000 : 0) : DISABLE_COLOR);
			mRecordLabel.setText(is_recording ? R.string.action_stop : R.string.action_record);

//			mTakeOnOffBtn.setEnabled(can_fly);		// 離陸/着陸
			switch (state & IDeviceController.STATE_MASK_FLYING) {
			case IDeviceController.STATE_FLYING_LANDED:		// 0x0000;		// FlyingState=0
			case IDeviceController.STATE_FLYING_LANDING:	// 0x0400;		// FlyingState=4
				mTakeOnOffBtn.setImageResource(R.drawable.takeoff72x72);
				break;
			case IDeviceController.STATE_FLYING_TAKEOFF:	// 0x0100;		// FlyingState=1
			case IDeviceController.STATE_FLYING_HOVERING:	// 0x0200;		// FlyingState=2
			case IDeviceController.STATE_FLYING_FLYING:		// 0x0300;		// FlyingState=3
			case IDeviceController.STATE_FLYING_ROLLING:	// 0x0600;		// FlyingState=6
				mTakeOnOffBtn.setImageResource(R.drawable.landing72x72);
				break;
			case IDeviceController.STATE_FLYING_EMERGENCY:	// 0x0500;		// FlyingState=5
				break;
			}

			// 右サイドパネル(とmCapXXXBtn等)
			mRightSidePanel.setEnabled(can_fly);
			// 左サイドパネル(とmFlipXXXBtn等)
			mLeftSidePanel.setEnabled(can_fly);
			// 右スティックパネル(東/西ボタン)
			mRightStickPanel.setEnabled(can_fly);
			// 左スティックパネル(北/南ボタン)
			mLeftStickPanel.setEnabled(can_fly);
			for (View view: mActionViews) {
				view.setEnabled(can_fly);
				if (view instanceof ImageView) {
					((ImageView)view).setColorFilter(can_fly ? 0 : DISABLE_COLOR);
				}
			}
		}
	};

	/**
	 * 一定時間後にフラグメントを終了するためのRunnable
	 * 切断された時に使用
	 */
	private final Runnable mPopBackStackTask = new Runnable() {
		@Override
		public void run() {
			try {
				getFragmentManager().popBackStack();
			} catch (Exception e) {
				Log.w(TAG, e);
			}
		}
	};

	/**
	 * タッチレスポンス用にカラーフィルターを適用する時間
	 */
	private static final long TOUCH_RESPONSE_TIME_MS = 100;	// 200ミリ秒
	/**
	 * タッチレスポンス時のカラーフィルター色
	 */
	private static final int TOUCH_RESPONSE_COLOR = 0x7f331133;

	/**
	 * カラーフィルタクリア用のRunnableのキャッシュ
	 */
	private final Map<ImageView, ResetColorFilterTask> mResetColorFilterTasks = new HashMap<ImageView, ResetColorFilterTask>();

	/**
	 * 指定したImageViewに指定した色でカラーフィルターを適用する。
	 * reset_delayが0より大きければその時間経過後にカラーフィルターをクリアする
	 * @param image
	 * @param color
	 * @param reset_delay ミリ秒
	 */
	private void setColorFilter(final ImageView image, final int color, final long reset_delay) {
		if (image != null) {
			image.setColorFilter(color);
			if (reset_delay > 0) {
				ResetColorFilterTask task = mResetColorFilterTasks.get(image);
				if (task == null) {
					task = new ResetColorFilterTask(image);
				}
				removeFromUIThread(task);
				postUIThread(task, reset_delay);
			}
		}
	}

	/**
	 * 一定時間後にImageView(とImageButton)のカラーフィルターをクリアするためのRunnable
	 */
	private static class ResetColorFilterTask implements Runnable {
		private final ImageView mImage;
		public ResetColorFilterTask(final ImageView image) {
			mImage = image;
		}
		@Override
		public void run() {
			mImage.setColorFilter(0);
		}
	}

	/**
	 * サイドメニューを更新
	 */
/*	@Override
	protected void updateSideMenu() {
		if (DEBUG) Log.v(TAG, "updateSideMenu:");
		final List<String> labelList = new ArrayList<String>();
		for (int i = 0; i < 5; i++)
			labelList.add(TAG + i);
        boolean needOpen = false;
    	ListAdapter adapter = mSideMenuListView.getAdapter();
    	if (adapter instanceof SideMenuAdapter) {
    		((SideMenuAdapter) adapter).clear();
    		if ((labelList != null) && (labelList.size() > 0)) {
    			((SideMenuAdapter) adapter).addAll(labelList);
	    		needOpen = true;
    		}
    	} else {
    		mSideMenuListView.setAdapter(null);
    		if ((labelList != null) && (labelList.size() > 0)) {
	    		adapter = new SideMenuAdapter(getActivity(), R.layout.item_sidemenu, labelList);
	    		mSideMenuListView.setAdapter(adapter);
	    		needOpen = true;
    		}
    	}
		super.updateSideMenu();
	} */

	/**
	 * サイドメニューの項目をクリックした時の処理
	 */
/*	private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
			closeSideMenu();
			switch (position) {
			// FIXME 未実装
			}
		}
	}; */

}
