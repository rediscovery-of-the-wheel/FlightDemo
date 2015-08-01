package com.serenegiant.flightdemo;

import android.app.ProgressDialog;
import android.graphics.Rect;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.serenegiant.arflight.DeviceController;
import com.serenegiant.arflight.DeviceControllerListener;
import com.serenegiant.arflight.FlightRecorder;
import com.serenegiant.dialog.SelectFileDialogFragment;
import com.serenegiant.utils.FileUtils;

import java.io.File;
import java.sql.Date;
import java.util.Random;

public class PilotFragment extends Fragment implements SelectFileDialogFragment.OnFileSelectListener {
	private static final boolean DEBUG = true;	// FIXME 実働時はfalseにすること
	private static String TAG = PilotFragment.class.getSimpleName();
	private static String EXTRA_DEVICE_SERVICE = "piloting.extra.device.service";

	static {
		FileUtils.DIR_NAME = "FlightDemo";
	}
	/**
	 * Use this factory method to create a new instance of
	 * this fragment using the provided parameters.
	 *
	 * @return A new instance of fragment PilotFragment.
	 */
	public static PilotFragment newInstance(final ARDiscoveryDeviceService service) {
		final PilotFragment fragment = new PilotFragment();
		fragment.service = service;
		final Bundle args = new Bundle();
		args.putParcelable(EXTRA_DEVICE_SERVICE, service);
		fragment.setArguments(args);
		return fragment;
	}

	private final Handler mHandler = new Handler();
	private final long mUIThreadId = Thread.currentThread().getId();
	private DeviceController deviceController;
	private ARDiscoveryDeviceService service;

	// 上パネル
	private TextView batteryLabel;
	private ImageButton mFlatTrimBtn;
	// 下パネル
	private Button mEmergencyBtn;	// 非常停止ボタン
	private Button mTakeOnOffBtn;	// 離陸/着陸ボタン
	private ImageButton mRecordBtn;	// 記録ボタン
	private ImageButton mPlayBtn;	// 再生ボタン
	private ImageButton mLoadBtn;	// 読み込みボタン
	// 右サイドパネル
	private View mRightSidePanel;
	// 左サイドパネル
	private View mLeftSidePanel;
	// 右スティックパネル
	private View mRightStickPanel;
	// 左スティックパネル
	private View mLeftStickPanel;

	private volatile int mFlyingState = -1;
	private volatile int mBattery = -1;
	private boolean mIsFlying = false;	// FIXME mFlyingStateを参照するようにしてmIsFlyingフラグは削除する
	private boolean mIsConnected = false;
	// 画面座標値から移動量(±100)に変換するための係数
	private float mRightScaleX, mRightScaleY;
	private float mLeftScaleX, mLeftScaleY;
	private final FlightRecorder mFlightRecorder = new FlightRecorder();

	public PilotFragment() {
		// デフォルトコンストラクタが必要
		mFlightRecorder.setPlaybackListener(mPlaybackListener);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:" + savedInstanceState);
		if (savedInstanceState == null)
			savedInstanceState = getArguments();
		if (savedInstanceState != null) {
			service = savedInstanceState.getParcelable(EXTRA_DEVICE_SERVICE);
			deviceController = new DeviceController(getActivity(), service);
			deviceController.setListener(mDeviceControllerListener);
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		final View rootView = inflater.inflate(R.layout.fragment_pilot, container, false);
		mEmergencyBtn = (Button)rootView.findViewById(R.id.emergency_btn);
		mEmergencyBtn.setOnClickListener(mOnClickListener);

		mTakeOnOffBtn = (Button)rootView.findViewById(R.id.take_onoff_btn);
		mTakeOnOffBtn.setOnClickListener(mOnClickListener);

		mRecordBtn = (ImageButton)rootView.findViewById(R.id.record_btn);
		mRecordBtn.setOnClickListener(mOnClickListener);
		mRecordBtn.setOnLongClickListener(mOnLongClickListener);

		mPlayBtn = (ImageButton)rootView.findViewById(R.id.play_btn);
		mPlayBtn.setOnClickListener(mOnClickListener);
		mPlayBtn.setOnLongClickListener(mOnLongClickListener);

		mLoadBtn = (ImageButton)rootView.findViewById(R.id.load_btn);
		mLoadBtn.setOnClickListener(mOnClickListener);
		mLoadBtn.setOnLongClickListener(mOnLongClickListener);

		mFlatTrimBtn = (ImageButton)rootView.findViewById(R.id.flat_trim_btn);
		mFlatTrimBtn.setOnLongClickListener(mOnLongClickListener);

		Button button;
		// 右サイドパネル
		mRightSidePanel = rootView.findViewById(R.id.right_side_panel);
		button = (Button)rootView.findViewById(R.id.cap_p15_btn);
		button.setOnClickListener(mOnClickListener);

		button = (Button)rootView.findViewById(R.id.cap_p45_btn);
		button.setOnClickListener(mOnClickListener);

		button = (Button)rootView.findViewById(R.id.cap_m15_btn);
		button.setOnClickListener(mOnClickListener);

		button = (Button)rootView.findViewById(R.id.cap_m45_btn);
		button.setOnClickListener(mOnClickListener);
		// 左サイドパネル
		mLeftSidePanel = rootView.findViewById(R.id.left_side_panel);
		button = (Button)rootView.findViewById(R.id.flip_right_btn);
		button.setOnClickListener(mOnClickListener);

		button = (Button)rootView.findViewById(R.id.flip_left_btn);
		button.setOnClickListener(mOnClickListener);

		button = (Button)rootView.findViewById(R.id.flip_front_btn);
		button.setOnClickListener(mOnClickListener);

		button = (Button)rootView.findViewById(R.id.flip_back_btn);
		button.setOnClickListener(mOnClickListener);

		// 右スティックパネル
		mRightStickPanel = rootView.findViewById(R.id.right_panel);
		mRightStickPanel.setOnTouchListener(mOnTouchListener);

		button = (Button)rootView.findViewById(R.id.west_btn);
		button.setOnClickListener(mOnClickListener);
		button = (Button)rootView.findViewById(R.id.east_btn);
		button.setOnClickListener(mOnClickListener);
//		MovableImageView iv = (MovableImageView)rootView.findViewById(R.id.right_stick_image);
//		iv.setResizable(false);
//		iv.setMovable(false);
		// 左スティックパネル
		mLeftStickPanel = rootView.findViewById(R.id.left_panel);
		mLeftStickPanel.setOnTouchListener(mOnTouchListener);
		button = (Button)rootView.findViewById(R.id.north_btn);
		button.setOnClickListener(mOnClickListener);
		button = (Button)rootView.findViewById(R.id.south_btn);
		button.setOnClickListener(mOnClickListener);
//		iv = (MovableImageView)rootView.findViewById(R.id.left_stick_image);
//		iv.setResizable(false);
//		iv.setMovable(false);

		batteryLabel = (TextView)rootView.findViewById(R.id.batteryLabel);

		return rootView;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		if (mRightStickPanel.getWidth() != 0 && mRightStickPanel.getHeight() != 0) {
			mRightScaleX = 250f / (float) mRightStickPanel.getWidth();
			mRightScaleY = 250f / (float) mRightStickPanel.getHeight();
			mLeftScaleX = 250f / (float) mLeftStickPanel.getWidth();
			mLeftScaleY = 250f / (float) mLeftStickPanel.getHeight();
		} else {
			mRightScaleX = mRightScaleY = mLeftScaleX = mLeftScaleY = 0;
		}
		if (DEBUG) Log.w(TAG, String.format("scale:left(%f,%f)right(%f,%f)", mRightScaleX, mRightScaleY, mLeftScaleX, mLeftScaleY));
		if (DEBUG) Log.w(TAG, String.format("mRightStickPanel:(%d,%d)mLeftStickPanel(%d,%d)",
			mRightStickPanel.getWidth(), mRightStickPanel.getHeight(),
			mLeftStickPanel.getWidth(), mLeftStickPanel.getHeight()));
		final Rect r = new Rect();
		mRightStickPanel.getDrawingRect(r);
		if (DEBUG) Log.w(TAG, "mRightStickPanel:" + r);
		mLeftStickPanel.getDrawingRect(r);
		if (DEBUG) Log.w(TAG, "mLeftStickPanel:" + r);
		startDeviceController();
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		stopRecord();
		stopDeviceController();
		super.onPause();
	}

	@Override
	public void onFileSelect(File[] files) {
		if (DEBUG) Log.v(TAG, "onFileSelect:");
		if ((files != null) && (files.length > 0)
			&& !mFlightRecorder.isPlaying() && !mFlightRecorder.isRecording() ) {
			mFlightRecorder.load(files[0]);
		}
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (DEBUG) Log.v(TAG, "onClick:" + view);
			switch (view.getId()) {
			case R.id.load_btn:
				// 読み込みボタンの処理
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
			case R.id.emergency_btn:
				// 非常停止指示ボタンの処理
				stopMove();
				stopPlay();
				if (deviceController != null) {
					deviceController.sendEmergency();
					mFlightRecorder.record(FlightRecorder.CMD_EMERGENCY);
					mIsFlying = false;
				}
				updateButtons();
				break;
			case R.id.take_onoff_btn:
				// 離陸指示/着陸指示ボタンの処理
				if (deviceController != null) {
					mIsFlying = !mIsFlying;
					if (mIsFlying) {
						// 離陸指示
						deviceController.sendTakeoff();
						mFlightRecorder.record(FlightRecorder.CMD_TAKEOFF);
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								mTakeOnOffBtn.setText(R.string.button_text_landing);
							}
						});
					} else {
						// 着陸指示
						stopMove();
						deviceController.sendLanding();
						mFlightRecorder.record(FlightRecorder.CMD_LANDING);
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								mTakeOnOffBtn.setText(R.string.button_text_takeoff);
							}
						});
					}
				} else {
					mIsFlying = false;
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mTakeOnOffBtn.setText(R.string.button_text_takeoff);
						}
					});
				}
				updateButtons();
				break;
			case R.id.flip_front_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsFlip(DeviceController.FLIP_FRONT);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceController.FLIP_FRONT);
				}
				break;
			case R.id.flip_back_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsFlip(DeviceController.FLIP_BACK);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceController.FLIP_BACK);
				}
				break;
			case R.id.flip_right_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsFlip(DeviceController.FLIP_RIGHT);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceController.FLIP_RIGHT);
				}
				break;
			case R.id.flip_left_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsFlip(DeviceController.FLIP_LEFT);
					mFlightRecorder.record(FlightRecorder.CMD_FLIP, DeviceController.FLIP_LEFT);
				}
				break;
			case R.id.cap_p15_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsCap(15);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, 15);
				}
				break;
			case R.id.cap_p45_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsCap(45);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, 45);
				}
				break;
			case R.id.cap_m15_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsCap(-15);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, -15);
				}
				break;
			case R.id.cap_m45_btn:
				if (deviceController != null) {
					deviceController.sendAnimationsCap(-45);
					mFlightRecorder.record(FlightRecorder.CMD_CAP, -45);
				}
				break;
			case R.id.north_btn:
				if (deviceController != null) {
					deviceController.setPsi(0);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 0);
				}
				break;
			case R.id.south_btn:
				if (deviceController != null) {
					deviceController.setPsi(180);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 180);
				}
				break;
			case R.id.west_btn:
				if (deviceController != null) {
					deviceController.setPsi(-90);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, -90);
				}
				break;
			case R.id.east_btn:
				if (deviceController != null) {
					deviceController.setPsi(90);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 90);
				}
				break;
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
				if ((deviceController != null) && (mFlyingState == 0)) {
					deviceController.sendFlatTrim();
					return true;
				}
				break;
			}
			return false;
		}
	};

	private final View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(final View view, final MotionEvent event) {
			switch (view.getId()) {
			case R.id.right_panel:
				doRightStick(event);
				return true;
			case R.id.left_panel:
				doLeftStick(event);
				return true;
			}
			return false;
		}
	};

	private final DeviceControllerListener mDeviceControllerListener
		= new DeviceControllerListener() {
		@Override
		public void onDisconnect() {
			if (DEBUG) Log.v(TAG, "mDeviceControllerListener#onDisconnect");
			stopRecord();
			stopDeviceController();
			mIsFlying = false;
		}

		@Override
		public void onUpdateBattery(final byte percent) {
			mBattery = percent;
			updateBattery();
		}

		@Override
		public void onFlatTrimUpdate(final boolean success) {
			if (DEBUG) Log.v(TAG, "onFlatTrimUpdate:success=" + success);
		}

		@Override
		public void onFlyingStateChangedUpdate(final int state) {
			if (DEBUG) Log.v(TAG, "onFlyingStateChangedUpdate:state=" + state);
			if (mFlyingState != state) {
				mFlyingState = state;
				updateButtons();
			}
		}
	};

	private static final int CTRL_STEP = 5;

	private float mFirstPtRightX, mFirstPtRightY;
	private int mPrevRightMX, mPrevRightMY;
	private final void doRightStick(final MotionEvent event) {
		final int action = event.getAction();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if (DEBUG) Log.v(TAG, "doRightStick:ACTION_DOWN");
			if ((mRightScaleX == 0) || (mRightScaleY == 0)) {
				mRightScaleX = 250f / (float) mRightStickPanel.getWidth();
				mRightScaleY = 250f / (float) mRightStickPanel.getHeight();
			}

			mFirstPtRightX = event.getX();
			mFirstPtRightY = event.getY();
			mPrevRightMX = mPrevRightMY = 0;
			break;
		case MotionEvent.ACTION_MOVE:
			final float dx = event.getX() - mFirstPtRightX;
			final float dy = event.getY() - mFirstPtRightY;
//			if (DEBUG) Log.v(TAG, String.format("doRightStick:(%5.1f,%5.1f)", dx, dy));

			int mx = (int) (dx * mRightScaleX);
			if (mx < -100) mx = -100;
			else if (mx > 100) mx = 100;
			mx = (mx / CTRL_STEP) * CTRL_STEP;
			if (mx != mPrevRightMX) {
				mPrevRightMX = mx;
				if (deviceController != null) {
					deviceController.setRoll((byte) mx);
					deviceController.setFlag((byte) (mx != 0 ? 1 : 0));
					mFlightRecorder.record(FlightRecorder.CMD_RIGHT_LEFT, mx);
				}
			}
			int my = (int) (dy * mRightScaleY);
			if (my < -100) my = -100;
			else if (my > 100) my = 100;
			my = (my / CTRL_STEP) * CTRL_STEP;
			if (my != mPrevRightMY) {
				mPrevRightMY = my;
				if (deviceController != null) {
					deviceController.setPitch((byte) -my);
					mFlightRecorder.record(FlightRecorder.CMD_FORWARD_BACK, -my);
				}
			}
//			if (DEBUG) Log.v(TAG, String.format("doRightStick:(%d,%d)", mx, my));
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (DEBUG) Log.v(TAG, "doRightStick:ACTION_UP");
			if (deviceController != null) {
				// 左右移動量をクリア, 正:右, 負:左
				deviceController.setRoll((byte) 0);
				deviceController.setFlag((byte) 0);
				mFlightRecorder.record(FlightRecorder.CMD_RIGHT_LEFT, 0);
				// 前後移動量をクリア, 正:前, 負:後
				deviceController.setPitch((byte) 0);
				mFlightRecorder.record(FlightRecorder.CMD_FORWARD_BACK, 0);
			}
			break;
		}
	}

	private float mFirstPtLeftX, mFirstPtLeftY;
	private int mPrevLeftMX, mPrevLeftMY;
	private final void doLeftStick(final MotionEvent event) {
		final int action = event.getAction();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			if (DEBUG) Log.v(TAG, "doLeftStick:ACTION_DOWN");
			if ((mLeftScaleX == 0) || (mLeftScaleY == 0)) {
				mLeftScaleX = 250f / (float) mLeftStickPanel.getWidth();
				mLeftScaleY = 250f / (float) mLeftStickPanel.getHeight();
			}
			mFirstPtLeftX = event.getX();
			mFirstPtLeftY = event.getY();
			mPrevLeftMX = mPrevLeftMY = 0;
			break;
		case MotionEvent.ACTION_MOVE:
			final float dx = event.getX() - mFirstPtLeftX;
			final float dy = event.getY() - mFirstPtLeftY;
//			if (DEBUG) Log.v(TAG, String.format("doLeftStick:(%5.1f,%5.1f)", dx, dy));

			int mx = (int) (dx * mLeftScaleX);
			if (mx < -100) mx = -100;
			else if (mx > 100) mx = 100;
			if ((Math.abs(mx) < 20)) mx = 0;
			mx = (mx / CTRL_STEP) * CTRL_STEP;
			if (mx != mPrevLeftMX) {
				mPrevLeftMX = mx;
				if (deviceController != null) {
					deviceController.setYaw((byte) mx);
					mFlightRecorder.record(FlightRecorder.CMD_TURN, mx);
				}
			}
			int my = (int) (dy * mLeftScaleY);
			if (my < -100) my = -100;
			else if (my > 100) my = 100;
			my = (my / CTRL_STEP) * CTRL_STEP;
			if (my != mPrevLeftMY) {
				mPrevLeftMY = my;
				if (deviceController != null) {
					deviceController.setGaz((byte) -my);
					mFlightRecorder.record(FlightRecorder.CMD_UP_DOWN, -my);
				}
			}
//			if (DEBUG) Log.v(TAG, String.format("doLeftStick:(%d,%d)", mx, my));
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (DEBUG) Log.v(TAG, "doLeftStick:ACTION_DOWN");
			if (deviceController != null) {
				// 上下移動量をクリア, 正:上, 負:下
				deviceController.setGaz((byte) 0);
				mFlightRecorder.record(FlightRecorder.CMD_UP_DOWN, 0);
				// 回転量をクリア, 正:右回り, 負:左回り
				deviceController.setYaw((byte) 0);
				mFlightRecorder.record(FlightRecorder.CMD_TURN, 0);
			}
			break;
		}
	}

	private void startDeviceController() {
		if (DEBUG) Log.v(TAG, "startDeviceController:");
		if ((deviceController != null) && !mIsConnected) {
			mBattery = -1;
			updateBattery();

			final ProgressDialog dialog = new ProgressDialog(getActivity());
			dialog.setTitle(R.string.connecting);
			dialog.setIndeterminate(true);
			dialog.show();

			new Thread(new Runnable() {
				@Override
				public void run() {
					final boolean failed = deviceController.start();

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							dialog.dismiss();
						}
					});

					mIsConnected = !failed;
					if (failed) {
						try {
							getFragmentManager().popBackStack();
						} catch (final Exception e) {
							Log.w(TAG, e);
						}
					} else {
						//only with RollingSpider in version 1.97 : date and time must be sent to permit a reconnection
						final Date currentDate = new Date(System.currentTimeMillis());
						deviceController.sendDate(currentDate);
						deviceController.sendTime(currentDate);
						stopMove();
					}
					updateButtons();
				}
			}).start();

		}
	}

	private void stopDeviceController() {
		if (DEBUG) Log.v(TAG, "stopDeviceController:");
		stopMove();
		if (deviceController != null) {
			final ProgressDialog dialog = new ProgressDialog(getActivity());
			dialog.setTitle(R.string.disconnecting);
			dialog.setIndeterminate(true);
			dialog.show();

			new Thread(new Runnable() {
				@Override
				public void run() {
					mIsConnected = mIsFlying = false;
					mFlyingState = mBattery = -1;
					deviceController.stop();
					updateButtons();
					updateBattery();

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							dialog.dismiss();
						}
					});
				}
			}).start();
		}
	}

	private void stopMove() {
		if (DEBUG) Log.v(TAG, "stopMove:");
		if (deviceController != null) {
			// 上下移動量をクリア, 正:上, 負:下
			deviceController.setGaz((byte) 0);
			mFlightRecorder.record(FlightRecorder.CMD_UP_DOWN, 0);
			// 回転量をクリア, 正:右回り, 負:左回り
			deviceController.setYaw((byte) 0);
			mFlightRecorder.record(FlightRecorder.CMD_TURN, 0);
			// 前後移動量をクリア, 正:前, 負:後
			deviceController.setPitch((byte) 0);
			deviceController.setFlag((byte) 0);
			mFlightRecorder.record(FlightRecorder.CMD_FORWARD_BACK, 0);
			// 左右移動量をクリア, 正:右, 負:左
			deviceController.setRoll((byte) 0);
			deviceController.setFlag((byte) 0);
			mFlightRecorder.record(FlightRecorder.CMD_RIGHT_LEFT, 0);
		}
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
			mFlightRecorder.save(path);
			updateButtons();
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

	private final FlightRecorder.PlaybackListener mPlaybackListener = new FlightRecorder.PlaybackListener() {
		@Override
		public void onStart() {
			if (DEBUG) Log.v(TAG, "mPlaybackListener#onStart:");
			updateButtons();
		}

		@Override
		public boolean onStep(final int cmd, final int value, final long t) {
			if (DEBUG) Log.v(TAG, String.format("mPlaybackListener#onStep:cmd=%d,v=%d,t=%d", cmd, value, t));
			if (deviceController != null) {
				switch (cmd) {
				case FlightRecorder.CMD_EMERGENCY:		// 非常停止
					deviceController.sendEmergency();
					break;
				case FlightRecorder.CMD_TAKEOFF:		// 離陸
					deviceController.sendTakeoff();
					break;
				case FlightRecorder.CMD_LANDING:		// 着陸
					deviceController.sendLanding();
					break;
				case FlightRecorder.CMD_UP_DOWN:		// 上昇:gaz>0, 下降: gaz<0
					deviceController.setGaz((byte) value);
					break;
				case FlightRecorder.CMD_RIGHT_LEFT:		// 右: roll>0,flag=1 左: roll<0,flag=1
					deviceController.setRoll((byte) value);
					deviceController.setFlag((byte) (value != 0 ? 1 : 0));
					break;
				case FlightRecorder.CMD_FORWARD_BACK:	// 前進: pitch>0,flag=1, 後退: pitch<0,flag=1
					deviceController.setPitch((byte) value);
					break;
				case FlightRecorder.CMD_TURN:			// 右回転: yaw>0, 左回転: ywa<0
					deviceController.setYaw((byte) value);
					break;
				case FlightRecorder.CMD_COMPASS:		// 北磁極に対する角度 -360〜360度
					deviceController.setPsi(value);		// 実際は浮動小数点だけど
					break;
				case FlightRecorder.CMD_FLIP:			// フリップ
					deviceController.sendAnimationsFlip(value);
					break;
				case FlightRecorder.CMD_CAP:			// キャップ(指定角度水平回転)
					deviceController.sendAnimationsCap(value);
					break;
				}
				return false;
			} else {
				return true;
			}
		}

		@Override
		public void onStop() {
			if (DEBUG) Log.v(TAG, "mPlaybackListener#onStop:");
			updateButtons();
		}
	};

	/**
	 * ボタン表示の更新(UIスレッドで処理)
	 */
	private void updateButtons() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final int state = mFlyingState;
				final boolean is_connected = mIsConnected;
				final boolean is_recording = mFlightRecorder.isRecording();
				final boolean is_playing = mFlightRecorder.isPlaying();
				final boolean can_play = is_connected && !is_recording && (state != 5) && (mFlightRecorder.size() > 0);
				final boolean can_record = is_connected && !is_playing;
				final boolean can_load = is_connected && !is_playing && !is_recording;
				final boolean can_fly = can_record && (state != 5);
				final boolean can_flattrim = can_fly && (state == 0);
				switch (state) {
				case 0: // Landed state
				case 1:	// Taking off state
				case 2:	// Hovering state
				case 3:	// Flying state
				case 4:	// Landing state
				case 5:	// Emergency state
				case 6: // Rolling state
					break;
				}

				// 上パネル
				mFlatTrimBtn.setEnabled(can_flattrim);	// フラットトリム
				// 下パネル
				mEmergencyBtn.setEnabled(is_connected);	// 非常停止
				mTakeOnOffBtn.setEnabled(can_fly);		// 離陸/着陸
				mLoadBtn.setEnabled(can_load);			// 読み込み
				mPlayBtn.setEnabled(can_play);			// 再生
				mRecordBtn.setEnabled(can_record);		// 記録
				if (is_recording) {
					mRecordBtn.setImageResource(R.drawable.btn_shutter_video_recording);
				} else {
					mRecordBtn.setImageResource(R.drawable.btn_shutter_default);
				}
				// 右サイドパネル(とmCapXXXBtn等)
				mRightSidePanel.setEnabled(can_fly);
				// 左サイドパネル(とmFlipXXXBtn等)
				mLeftSidePanel.setEnabled(can_fly);
				// 右スティックパネル(東/西ボタン)
				mRightStickPanel.setEnabled(can_fly);
				// 左スティックパネル(北/南ボタン)
				mLeftStickPanel.setEnabled(can_fly);

			}
		});
	}

	private void updateBattery() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mBattery >= 0) {
					batteryLabel.setText(String.format("%d%%", mBattery));
				} else {
					batteryLabel.setText("---");
				}
			}
		});
	}

	private void runOnUiThread(final Runnable task) {
		if (task != null) {
			try {
				if (mUIThreadId != Thread.currentThread().getId()) {
					mHandler.post(task);
				} else {
					task.run();
				}
			} catch (Exception e) {
				Log.w(TAG, e);
			}
		}
	}

}
