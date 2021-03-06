package com.serenegiant.aceparrot;
/*
 * By downloading, copying, installing or using the software you agree to this license.
 * If you do not agree to this license, do not download, install,
 * copy or use the software.
 *
 *
 *                           License Agreement
 *                        (3-clause BSD License)
 *
 * Copyright (C) 2015-2017, saki t_saki@serenegiant.com
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *   * Neither the names of the copyright holders nor the names of the contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * This software is provided by the copyright holders and contributors "as is" and
 * any express or implied warranties, including, but not limited to, the implied
 * warranties of merchantability and fitness for a particular purpose are disclaimed.
 * In no event shall copyright holders or contributors be liable for any direct,
 * indirect, incidental, special, exemplary, or consequential damages
 * (including, but not limited to, procurement of substitute goods or services;
 * loss of use, data, or profits; or business interruption) however caused
 * and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of
 * the use of this software, even if advised of the possibility of such damage.
 */

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.serenegiant.dialog.SelectFileDialogFragment;
import com.serenegiant.arflight.drone.AttitudeScreenBase;
import com.serenegiant.gameengine.v1.IModelView;
import com.serenegiant.arflight.BuildConfig;
import com.serenegiant.arflight.R;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.widget.OrientationView;
import com.serenegiant.widget.SideMenuListView;
import com.serenegiant.widget.StickView;
import com.serenegiant.widget.StickView.OnStickMoveListener;
import com.serenegiant.widget.TouchPilotView;
import com.serenegiant.widget.TouchableLinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jp.co.rediscovery.arflight.CameraControllerListener;
import jp.co.rediscovery.arflight.DeviceInfo;
import jp.co.rediscovery.arflight.DroneStatus;
import jp.co.rediscovery.arflight.ICameraController;
import jp.co.rediscovery.arflight.IDeviceController;
import jp.co.rediscovery.arflight.IFlightController;
import jp.co.rediscovery.arflight.ISkyController;
import jp.co.rediscovery.arflight.IVideoStreamController;
import jp.co.rediscovery.arflight.controllers.FlightControllerMambo;

import static com.serenegiant.aceparrot.AppConst.*;

public class PilotFragment extends BasePilotFragment {
//	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static String TAG = PilotFragment.class.getSimpleName();

	private static final long ALPHA_PILOTING_DELAY_MS = 2500;		// アイコン等のアルファを落とすまでの時間[ミリ秒]
	private static final long HIDE_PILOTING_DELAY_MS = 5000;		// アイコン等をすべて隠すまでの時間[ミリ秒]


	public static PilotFragment newInstance(final ARDiscoveryDeviceService device, final DeviceInfo info) {
		if (!BuildConfig.USE_SKYCONTROLLER) throw new RuntimeException("does not support skycontroller now");
		final PilotFragment fragment = new PilotFragment();
		fragment.setDevice(device, info);
		return fragment;
	}

	protected ViewGroup mControllerFrame;		// 操作パネル全体
	private TouchableLinearLayout mPilotFrame;	// 操縦パネル
	private OrientationView mCameraView;		// カメラのPan/Tiltの十字線描画用

	// 上パネル
	protected View mTopPanel;
	private TextView mBatteryLabel;			// バッテリー残量表示
	private ImageButton mFlatTrimBtn;		// フラットトリム
	private TextView mAlertMessage;			// 非常メッセージ
	private String mBatteryFmt;
	// 下パネル
	protected View mBottomPanel;
	protected ImageButton mEmergencyBtn;	// 非常停止ボタン
	protected ImageButton mTakeOnOffBtn;	// 離陸/着陸ボタン
	private ImageButton mRecordBtn;			// 記録ボタン
	private TextView mRecordLabel;
	private ImageButton mPlayBtn;			// 再生ボタン
	private TextView mPlayLabel;
	private ImageButton mLoadBtn;			// 読み込みボタン
	private ImageButton mConfigShowBtn;		// 設定パネル表示ボタン
	private TextView mTimeLabelTv;
	private ImageButton mClearButton;		// クリアボタン(タッチ描画操縦)
	private ImageButton mMoveButton;		// 移動ボタン(タッチ描画操縦)
	// 右サイドパネル
	protected View mRightSidePanel;
	private ImageButton mCopilotBtn;		// コパイロットボタン
	private ImageButton mStillCaptureBtn;
	private ImageButton mVideoRecordingBtn;
	// 左サイドパネル
	protected View mLeftSidePanel;
	// 操縦用
	private StickView mRightStickPanel;		// 右スティックパネル
	private StickView mLeftStickPanel;		// 左スティックパネル
	private TouchPilotView mTouchPilotView;	// タッチ描画パネル
	private View mForwardView, mBackView;
	private View mRightView, mLeftView;
	private View mDownView, mUpView;
	private View mTurnLeftView, mTurnRightView;
	private View mHeartBeatView;
	private View mActionBtn;

	// サイドメニュー
	private SideMenuListView mSideMenuListView;
	/** 操縦に使用するボタン等の一括変更用。操作可・不可に応じてenable/disableを切り替える */
	private final List<View> mActionViews = new ArrayList<View>();
	/** 操縦に使用するボタン等の一括変更用。自動で隠す設定の時に使用  */
	private final List<View> mAlphaHideList = new ArrayList<View>();

	public PilotFragment() {
		super();
	}

//	@Override
//	public void onAttach(Activity activity) {
//		super.onAttach(activity);
//		if (DEBUG) Log.v(TAG, "onAttach:");
//	}

	@Override
	public void onDetach() {
//		if (DEBUG) Log.v(TAG, "onDetach:");
		final IMainActivity activity = (IMainActivity) getActivity();
		activity.setSideMenuEnable(false);
		activity.removeSideMenuView(mSideMenuListView);
		super.onDetach();
	}

	@Override
	protected void internalOnResume() {
		super.internalOnResume();
//		if (DEBUG) Log.v(TAG, "internalOnResume:");
		mControllerFrame.setKeepScreenOn(true);
		cancelAlphaHide();
	}

	@Override
	protected void internalOnPause() {
//		if (DEBUG) Log.v(TAG, "internalOnPause:");
		stopHeartBeat();
		clearAlphaHide();
		removeSideMenu();
		mResetLevelTasks.clear();
		mControllerFrame.setKeepScreenOn(false);
		super.internalOnPause();
	}

	@SuppressWarnings("deprecation")
	@Override
	protected View internalCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState, final int layout_id) {
		mActionViews.clear();
		mAlphaHideList.clear();

		final ViewGroup rootView = (ViewGroup) inflater.inflate(layout_id, container, false);

//		rootView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
//		rootView.setOnKeyListener(mOnKeyListener);
//		rootView.setFocusable(true);

		mControllerFrame = (ViewGroup) rootView.findViewById(R.id.controller_frame);
		mControllerFrame.setOnClickListener(mOnClickListener);
//		mControllerFrame.setFocusable(true);
//		mControllerFrame.requestFocus();

		mPilotFrame = (TouchableLinearLayout)rootView.findViewById(R.id.pilot_frame);
		mPilotFrame.setOnTouchableListener(mOnTouchableListener);

		mCameraView = (OrientationView)rootView.findViewById(R.id.camera_view);
		if (mCameraView != null) {
			mCameraView.setPanTilt(0, 0);
			mCameraView.setOnClickListener(mOnClickListener);
			mAlphaHideList.add(mCameraView);
		}

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
		mAlphaHideList.add(mFlatTrimBtn);
		// 設定表示ボタン
		mConfigShowBtn = (ImageButton) rootView.findViewById(R.id.config_show_btn);
		mConfigShowBtn.setOnClickListener(mOnClickListener);
		mAlphaHideList.add(mConfigShowBtn);
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
		// ハートビート
		mHeartBeatView = rootView.findViewById(R.id.heart_beat_view);
		// 記録ボタン
		mRecordBtn = (ImageButton) rootView.findViewById(R.id.record_btn);
		mRecordBtn.setOnClickListener(mOnClickListener);
		mRecordBtn.setOnLongClickListener(mOnLongClickListener);
		mAlphaHideList.add(mRecordBtn);
		// 記録ラベル
		mRecordLabel = (TextView) rootView.findViewById(R.id.record_label);
		mAlphaHideList.add(mRecordLabel);
		// 再生ボタン
		mPlayBtn = (ImageButton) rootView.findViewById(R.id.play_btn);
		mPlayBtn.setOnClickListener(mOnClickListener);
		mPlayBtn.setOnLongClickListener(mOnLongClickListener);
		mAlphaHideList.add(mPlayBtn);
		// 再生ラベル
		mPlayLabel = (TextView) rootView.findViewById(R.id.play_label);
		mAlphaHideList.add(mPlayLabel);
		// 読み込みボタン
		mLoadBtn = (ImageButton) rootView.findViewById(R.id.load_btn);
		mLoadBtn.setOnClickListener(mOnClickListener);
		mLoadBtn.setOnLongClickListener(mOnLongClickListener);
		mAlphaHideList.add(mLoadBtn);
		// 時間ボタン
		mTimeLabelTv = (TextView) rootView.findViewById(R.id.time_label);
		setChildVisibility(mTimeLabelTv, View.INVISIBLE);
		mAlphaHideList.add(mTimeLabelTv);

		// クリアボタン(タッチ描画操縦)
		mClearButton = (ImageButton) rootView.findViewById(R.id.clear_btn);
		if (mClearButton != null) {
			mClearButton.setOnClickListener(mOnClickListener);
			mAlphaHideList.add(mClearButton);
		}
		// 移動ボタン(タッチ描画操縦)
		mMoveButton = (ImageButton) rootView.findViewById(R.id.move_btn);
		if (mMoveButton != null) {
			mMoveButton.setOnClickListener(mOnClickListener);
			mAlphaHideList.add(mMoveButton);
		}
// 操縦パネル
		ImageButton button;
// 右サイドパネル
		mRightSidePanel = rootView.findViewById(R.id.right_side_panel);
		mActionViews.add(mRightSidePanel);

		// コパイロットボタン
		mCopilotBtn = (ImageButton) rootView.findViewById(R.id.copilot_btn);
		mCopilotBtn.setOnClickListener(mOnClickListener);
		mCopilotBtn.setVisibility(mController instanceof ISkyController ? View.VISIBLE : View.GONE);

		// 静止画撮影
		mStillCaptureBtn = (ImageButton) rootView.findViewById(R.id.still_capture_btn);
		mStillCaptureBtn.setOnClickListener(mOnClickListener);

		// 動画撮影
		mVideoRecordingBtn = (ImageButton) rootView.findViewById(R.id.video_capture_btn);
		mVideoRecordingBtn.setOnClickListener(mOnClickListener);

		button = (ImageButton) rootView.findViewById(R.id.cap_p45_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton) rootView.findViewById(R.id.cap_m45_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

// 左サイドパネル
		mLeftSidePanel = rootView.findViewById(R.id.left_side_panel);
		mActionViews.add(mLeftSidePanel);

		button = (ImageButton) rootView.findViewById(R.id.flip_right_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton) rootView.findViewById(R.id.flip_left_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton) rootView.findViewById(R.id.flip_front_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		button = (ImageButton) rootView.findViewById(R.id.flip_back_btn);
		button.setOnClickListener(mOnClickListener);
		mActionViews.add(button);

		// 右スティックパネル
		mRightStickPanel = (StickView) rootView.findViewById(R.id.stick_view_right);
		if (mRightStickPanel != null) {
			mRightStickPanel.setOnStickMoveListener(mOnStickMoveListener);
			mActionViews.add(mRightStickPanel);

			final View stickView = mRightStickPanel.getStickView();
			mForwardView = stickView.findViewById(R.id.forward_feedback_view);
			mRightView = stickView.findViewById(R.id.right_feedback_view);
			mBackView = stickView.findViewById(R.id.back_feedback_view);
			mLeftView = stickView.findViewById(R.id.left_feedback_view);
			mDownView = stickView.findViewById(R.id.down_feedback_view);
			mUpView = stickView.findViewById(R.id.up_feedback_view);
			mTurnLeftView = stickView.findViewById(R.id.turn_left_feedback_view);
			mTurnRightView = stickView.findViewById(R.id.turn_right_feedback_view);
		}

		// 左スティックパネル
		mLeftStickPanel = (StickView) rootView.findViewById(R.id.stick_view_left);
		if (mLeftStickPanel != null) {
			mLeftStickPanel.setOnStickMoveListener(mOnStickMoveListener);
			mActionViews.add(mRightStickPanel);

			final View stickView = mLeftStickPanel.getStickView();
			if (mForwardView == null) {
				mForwardView = stickView.findViewById(R.id.forward_feedback_view);
			}
			if (mRightView == null) {
				mRightView = stickView.findViewById(R.id.right_feedback_view);
			}
			if (mBackView == null) {
				mBackView = stickView.findViewById(R.id.back_feedback_view);
			}
			if (mLeftView == null) {
				mLeftView = stickView.findViewById(R.id.left_feedback_view);
			}
			if (mDownView == null) {
				mDownView = stickView.findViewById(R.id.down_feedback_view);
			}
			if (mUpView == null) {
				mUpView = stickView.findViewById(R.id.up_feedback_view);
			}
			if (mTurnLeftView == null) {
				mTurnLeftView = stickView.findViewById(R.id.turn_left_feedback_view);
			}
			if (mTurnRightView == null) {
				mTurnRightView = stickView.findViewById(R.id.turn_right_feedback_view);
			}
		}

		// タッチパイロットView(タッチ描画操縦)
		mTouchPilotView = (TouchPilotView) rootView.findViewById(R.id.touch_pilot_view);
		if (mTouchPilotView != null) {
			mTouchPilotView.setTouchPilotListener(mTouchPilotListener);
			mActionViews.add(mTouchPilotView);
		}

		mActionBtn = rootView.findViewById(R.id.action_button);
		mActionBtn.setOnClickListener(mOnClickListener);

		final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(getProductId());
		// 機体モデル表示
		final int model;
		final int ctrl;
		switch (product) {
		case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
			model = IModelView.MODEL_BEBOP;
			ctrl = AttitudeScreenBase.CTRL_ATTITUDE;
			break;
		case ARDISCOVERY_PRODUCT_BEBOP_2:	// Bebop2
			model = IModelView.MODEL_BEBOP2;
			ctrl = AttitudeScreenBase.CTRL_ATTITUDE;
			break;
		case ARDISCOVERY_PRODUCT_JS:        // JumpingSumo
		case ARDISCOVERY_PRODUCT_JS_EVO_LIGHT:
		case ARDISCOVERY_PRODUCT_JS_EVO_RACE:
			model = IModelView.MODEL_NON;
			ctrl = AttitudeScreenBase.CTRL_PILOT;
			break;
		case ARDISCOVERY_PRODUCT_MINIDRONE:				// DELOS product
			model = IModelView.MODEL_MINIDRONE;
			ctrl = AttitudeScreenBase.CTRL_PILOT;
			break;
		case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_LIGHT:	// Delos EVO Light product
		case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_BRICK:	// Delos EVO Brick product
		case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_HYDROFOIL:// Delos EVO Hydrofoil product これと
			model = IModelView.MODEL_CARGO;
			ctrl = AttitudeScreenBase.CTRL_PILOT;
			break;
		case ARDISCOVERY_PRODUCT_MINIDRONE_DELOS3:		// Delos3 product
		case ARDISCOVERY_PRODUCT_MINIDRONE_WINGX:		// WingX product これはモデルが違うけど
			model = IModelView.MODEL_MAMBO;
			ctrl = AttitudeScreenBase.CTRL_PILOT;
			break;
		case ARDISCOVERY_PRODUCT_SKYCONTROLLER:			// Sky controller product
		case ARDISCOVERY_PRODUCT_SKYCONTROLLER_2:		// Sky controller 2 product
			model = IModelView.MODEL_SKYCONTROLLER;
			ctrl = AttitudeScreenBase.CTRL_PILOT;
			break;
		default:
			model = IModelView.MODEL_BEBOP;
			ctrl = AttitudeScreenBase.CTRL_ATTITUDE;
			break;
		}
		if (mController instanceof ICameraController) {
			((ICameraController)mController).setCameraControllerListener(mCameraControllerListener);
			((ICameraController)mController).sendCameraOrientation(0, 0);
		}
		final SharedPreferences pref = getActivity().getPreferences(0);
		final int color = pref.getInt(KEY_COLOR, getResources().getColor(R.color.RED));
		TextureHelper.genTexture(getActivity(), model, color);
		mModelView = (IModelView)rootView.findViewById(R.id.drone_view);
		mModelView.setModel(model, ctrl);
		return rootView;
	}

//	@Override
//	public void onDestroy() {
//		if (DEBUG) Log.v(TAG, "onDestroy:");
//		super.onDestroy();
//	}

	private final TouchableLinearLayout.OnTouchableListener mOnTouchableListener
		= new TouchableLinearLayout.OnTouchableListener() {

		/** minimum distance between touch positions*/
		private static final float MIN_DISTANCE = 15.f;
		private static final float MIN_DISTANCE_SQUARE = MIN_DISTANCE * MIN_DISTANCE;
		/** コマンドを送る最小間隔[ミリ秒]  */
		private static final long MIN_CMD_INTERVALS_MS = 50;	// 50ミリ秒
		/** pan/tiltをリセットするための長押し時間 */
		private static final long RESET_DURATION_MS = 2000;	// 2秒

		private boolean inited;
		/** マルチタッチ開始時のタッチポインタのインデックス */
		private int mPrimaryId, mSecondaryId;
		/** マルチタッチ開始時のタッチ位置 */
		private float mPrimaryX, mPrimaryY, mSecondX, mSecondY;
		/** マルチタッチ開始時のタッチ中点 */
		private float mPivotX, mPivotY;
		/** マルチタッチ開始時のタッチ距離 */
		private float mTouchDistance;

		private float mPanLen, mTiltLen;
		private int mPan, mTilt;
		private long prevTime;

		@Override
		public boolean onInterceptTouchEvent(final MotionEvent event) {
			final boolean intercept = (mController instanceof ICameraController) && (event.getPointerCount() > 1);	// マルチタッチした時は横取りする
			if (intercept) {
				// マルチタッチ開始時のタッチ位置等を保存
				initTouch(event);
			} else {
				cancelAlphaHide();
			}
			return intercept;
		}

		@Override
		public boolean onTouchEvent(final MotionEvent event) {
			final int action = event.getActionMasked();
			final int n = event.getPointerCount();
			switch (action) {
			case MotionEvent.ACTION_DOWN:
//				if (DEBUG) Log.v(TAG, "ACTION_DOWN:");
				// シングルタッチ
				return n > 1;	// 多分ここにはこない
			case MotionEvent.ACTION_POINTER_DOWN:
//				if (DEBUG) Log.v(TAG, "ACTION_POINTER_DOWN:");
				clearAlphaHide();
				return true;
			case MotionEvent.ACTION_MOVE:
//				if (DEBUG) Log.v(TAG, "ACTION_MOVE:");
				if ((n > 1) && (System.currentTimeMillis() - prevTime > MIN_CMD_INTERVALS_MS) && checkTouchMoved(event)) {
					prevTime = System.currentTimeMillis();
					removeEvent(mResetRunnable);
					if (!inited) {
						initTouch(event);
					}
					// 現在のタッチ座標
					final float x0 = event.getX(0);
					final float y0 = event.getY(0);
					final float x1 = event.getX(1);
					final float y1 = event.getY(1);
					// 現在の中点座標
					final float cx = (x0 + x1) / 2.0f;
					final float cy = (y0 + y1) / 2.0f;
					// 最初のタッチ中点との距離を計算
					final float dx = (mPivotX - cx) * mPanLen + mPan;
					final float dy = (cy - mPivotY) * mTiltLen + mTilt;
					final int pan = dx < -100 ? -100 : (dx > 100 ? 100 : (int)dx);
					final int tilt = dy < -100 ? -100 : (dy > 100 ? 100 : (int)dy);
//					if (DEBUG) Log.v(TAG, String.format("ACTION_MOVE:dx=%5.2f,dy=%5.2f,pan=%d,tilt=%d", dx, dy, pan, tilt));
					if (mController instanceof ICameraController) {
						((ICameraController)mController).sendCameraOrientation(tilt, pan);
					}
				}
				return true;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				cancelAlphaHide();
				break;
			case MotionEvent.ACTION_POINTER_UP:
				break;
			}
			if (n == 0) {
				inited = false;
				removeEvent(mResetRunnable);
				cancelAlphaHide();
			}
			return false;
		}

		/** Pan/TiltをリセットするためのRunnable */
		private Runnable mResetRunnable = new Runnable() {
			@Override
			public void run() {
				inited = false;
				if (mController instanceof ICameraController) {
					((ICameraController)mController).sendCameraOrientation(0, 0);
					if (mCameraView != null) {
						mCameraView.setPanTilt(0, 0);
					}
				}
			}
		};

		private void initTouch(final MotionEvent event) {
			// primary touch
			mPrimaryId = event.getPointerId(0);
			mPrimaryX = event.getX(0);
			mPrimaryY = event.getY(0);
			// secondary touch
			mSecondaryId = event.getPointerId(1);
			mSecondX = event.getX(1);
			mSecondY = event.getY(1);
			// calculate the distance between first and second touch
			final float dx = mSecondX - mPrimaryX;
			final float dy = mSecondY - mPrimaryY;
			mTouchDistance = (float)Math.hypot(dx, dy);
			// set pivot position to the middle coordinate
			mPivotX = (mPrimaryX + mSecondX) / 2.0f;
			mPivotY = (mPrimaryY + mSecondY) / 2.0f;
			prevTime = System.currentTimeMillis() - MIN_CMD_INTERVALS_MS;
			mPanLen = 80.0f / mPilotFrame.getWidth();
			mTiltLen = 80.0f / mPilotFrame.getHeight();
			if (mController instanceof ICameraController) {
				mPan = ((ICameraController)mController).getPan();
				mTilt = ((ICameraController)mController).getTilt();
			}
			inited = true;
			removeEvent(mResetRunnable);
			queueEvent(mResetRunnable, RESET_DURATION_MS);
			clearAlphaHide();
		}

		/** タッチ位置を動かしたかどうかを取得 */
		private final boolean checkTouchMoved(final MotionEvent event) {
			final int ix0 = event.findPointerIndex(mPrimaryId);
			final int ix1 = event.findPointerIndex(mSecondaryId);
			if (ix0 >= 0) {
				// check primary touch
				float x = event.getX(ix0) - mPrimaryX;
				float y = event.getY(ix0) - mPrimaryY;
				if (x * x + y * y < MIN_DISTANCE_SQUARE) {
					// primary touch is at the almost same position
					if (ix1 >= 0) {
						// check secondary touch
						x = event.getX(ix1) - mSecondX;
						y = event.getY(ix1) - mSecondY;
						if (x * x + y * y < MIN_DISTANCE_SQUARE) {
							// secondary touch is also at the almost same position.
							return false;
						}
					} else {
						return false;
					}
				}
			}
			return true;
		}

	};

	private final CameraControllerListener mCameraControllerListener = new CameraControllerListener() {
		@Override
		public void onCameraOrientationChanged(final IDeviceController controller, final int pan, final int tilt) {
			if (mCameraView != null) {
				mCameraView.setPanTilt(-pan, tilt);
			}
		}
	};

	protected boolean onClick(final View view) {
		return false;
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
//			if (DEBUG) Log.v(TAG, "onClick:" + view);
			cancelAlphaHide();
			if (PilotFragment.this.onClick(view)) return;
			int i = view.getId();
			if (i == R.id.flat_trim_btn) {// フラットトリム
				setColorFilter(view);
				if ((mFlightController != null) && (getState() == IFlightController.STATE_STARTED)) {
					mFlightController.requestFlatTrim();
				}

			} else if (i == R.id.load_btn) {// 読み込みボタンの処理
				setColorFilter(view);
				final File root = FileUtils.getCaptureDir(getActivity(), "Documents", 0);
				SelectFileDialogFragment.showDialog(PilotFragment.this, root.getAbsolutePath(), false, "fcr");

			} else if (i == R.id.record_btn) {// 操縦記録ボタンの処理
				if (!mFlightRecorder.isRecording()) {
					startRecord(true);
				} else {
					stopRecord();
				}
				updateButtons();

			} else if (i == R.id.play_btn) {// 再生ボタンの処理
				PilotFragment.super.stopMove();
				if (!mFlightRecorder.isPlaying()) {
					startPlay();
				} else {
					stopPlay();
				}

			} else if (i == R.id.config_show_btn) {// 設定パネル表示処理
				setColorFilter(view);
				if (isStarted()) {
					if ((getState() & IFlightController.STATE_MASK_FLYING) == DroneStatus.STATE_FLYING_LANDED) {
						replace(ConfigFragment.newInstance(getDevice(), getDeviceInfo()));
					} else {
						landing();
					}
				}

			} else if (i == R.id.clear_btn) {// タッチ描画データの消去
				setColorFilter(view);
				if (mTouchPilotView != null) {
					mTouchPilotView.clear();
				}
				updateButtons();

			} else if (i == R.id.move_btn) {// タッチ描画で操縦開始
				setColorFilter(view);
				// 再生ボタンの処理
				PilotFragment.super.stopMove();
				if (!mTouchFlight.isPlaying()) {
					startTouchMove();
				} else {
					stopTouchMove();
				}

			} else if (i == R.id.emergency_btn) {// 非常停止指示ボタンの処理
				setColorFilter(view);
				emergencyStop();

			} else if (i == R.id.copilot_btn) {
				if ((mController instanceof ISkyController) && mController.isConnected()) {
					((ISkyController) mController).setCoPilotingSource(
						((ISkyController) mController).getCoPilotingSource() == 0 ? 1 : 0
					);
					runOnUiThread(mUpdateButtonsTask, 300);
				}

			} else if (i == R.id.take_onoff_btn) {// 離陸指示/着陸指示ボタンの処理
				setColorFilter(view);
				if (!isFlying()) {
//					takeOff();
					Toast.makeText(getActivity(), R.string.notify_takeoff, Toast.LENGTH_SHORT).show();
				} else {
					landing();
				}
				updateButtons();

			} else if (i == R.id.flip_front_btn) {
				setColorFilter(view);
				flip(IFlightController.FLIP_FRONT);

			} else if (i == R.id.flip_back_btn) {
				setColorFilter(view);
				flip(IFlightController.FLIP_BACK);

			} else if (i == R.id.flip_right_btn) {
				setColorFilter(view);
				flip(IFlightController.FLIP_RIGHT);

			} else if (i == R.id.flip_left_btn) {
				setColorFilter(view);
				flip(IFlightController.FLIP_LEFT);

			} else if (i == R.id.still_capture_btn) {// 静止画撮影ボタンの処理
				if (getStillCaptureState() == DroneStatus.MEDIA_READY) {
					setColorFilter(view);
					if (mFlightController != null) {
						mFlightController.requestTakePicture();
					}
				}

			} else if (i == R.id.video_capture_btn) {// 動画撮影ボタンの処理
				setColorFilter(view);
				if (mController instanceof ICameraController) {
					mVideoRecording = !mVideoRecording;
					((ICameraController) mController).sendVideoRecording(mVideoRecording);
				}

			} else if (i == R.id.cap_p45_btn) {
				setColorFilter(view);
				cap(45);
			} else if (i == R.id.cap_m45_btn) {
				setColorFilter(view);
				cap(-45);
			} else if (i == R.id.action_button) {
				setColorFilter(view);
				actionToggle();

/*			case R.id.north_btn:
				setColorFilter(view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mFlightController != null) {
					mFlightController.setHeading(0);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 0);
				}
				break;
			case R.id.south_btn:
				setColorFilter(view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mFlightController != null) {
					mFlightController.setHeading(180);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 180);
				}
				break;
			case R.id.west_btn:
				setColorFilter(view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mFlightController != null) {
					mFlightController.setHeading(-90);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, -90);
				}
				break;
			case R.id.east_btn:
				setColorFilter(view, TOUCH_RESPONSE_COLOR, TOUCH_RESPONSE_TIME_MS);
				if (mFlightController != null) {
					mFlightController.setHeading(90);
					mFlightRecorder.record(FlightRecorder.CMD_COMPASS, 90);
				}
				break; */
			}
		}
	};

	protected void actionToggle() {
		if (mFlightController instanceof FlightControllerMambo) {
			final FlightControllerMambo mambo = (FlightControllerMambo) mFlightController;
			if (mambo.hasClaw()) {
				mambo.toggleClaw();
			} else if (mambo.hasGun()) {
				Log.i(TAG, "requestFireGun");
				mambo.requestFireGun(0);
			} else {
				Log.i(TAG, "なんでやねん");
			}
		}
	}

	private final View.OnLongClickListener mOnLongClickListener = new View.OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
//			if (DEBUG) Log.v(TAG, "onLongClick:" + view);
			mVibrator.vibrate(50);
			int i = view.getId();
			if (i == R.id.record_btn) {
				if (!mFlightRecorder.isRecording()) {
					startRecord(false);
				} else {
					stopRecord();
				}
				return true;
			} else if (i == R.id.flat_trim_btn) {
				setColorFilter(view);
				if ((mFlightController != null) && (getState() == IFlightController.STATE_STARTED)) {
					replace(CalibrationFragment.newInstance(getDevice()));
					return true;
				}

			} else if (i == R.id.take_onoff_btn) {// 離陸/着陸ボタンを長押しした時の処理
				setColorFilter(view);
				if (!isFlying()) {
					takeOff();
					return true;
				}
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
			switch (mOperationType) {
			case 0:	// 通常
			case 1:	// 左右反転
				// レイアウト上でView自体を左右入れ替えているので左右反転モードも通常操作モードと同じ
				stick_normal(view.getId(), mx, my);
				break;
			case 2:	// mode1
				stick_mode1(view.getId(), mx, my);
				break;
			case 3:	// mode2
				stick_mode2(view.getId(), mx, my);
				break;
			}
		}
	};

	private void stick_normal(final int id, final int _mx, final int _my) {
		if (id == R.id.stick_view_right) {
			if ((_mx != mPrevRightMX) || ((_my != mPrevRightMY))) {
				mPrevRightMX = _mx;
				mPrevRightMY = _my;
				sendMove(_mx, -_my, 0, 0);
			}
		} else if (id == R.id.stick_view_left) {
			int mx = _mx;
			if ((Math.abs(_mx) < 20)) mx = 0;
			if (mx != mPrevLeftMX) {    // 左右回転
				mPrevLeftMX = mx;
				sendMove(0, 0, 0, mx);
			}
			if (_my != mPrevLeftMY) {    // 上昇下降
				mPrevLeftMY = _my;
				sendMove(0, 0, -_my, 0);
			}
		}
	}

	private void stick_mode1(final int id, final int _mx, final int _my) {
		// モード1
		// 右スティック: 左右=左右移動, 上下=上昇下降
		// 左スティック: 左右=左右回転, 上下=前後移動
		if (id == R.id.stick_view_right) {
			if (_my != mPrevRightMY) {    // 上昇下降
				mPrevRightMY = _my;
				sendMove(0, 0, -_my, 0);
			}
			int mx = _mx;
			if ((Math.abs(_mx) < 20)) mx = 0;
			if (mx != mPrevRightMX) {    // 左右移動
				mPrevRightMX = mx;
				sendMove(mx, 0, 0, 0);
			}
		} else if (id == R.id.stick_view_left) {
			if (_mx != mPrevLeftMX) {    // 左右回転
				mPrevLeftMX = _mx;
				sendMove(0, 0, 0, _mx);
			}
			if (_my != mPrevLeftMY) {    // 前後移動
				mPrevLeftMY = _my;
				sendMove(0, -_my, 0, 0);
			}
		}
	}

	private void stick_mode2(final int id, final int _mx, final int _my) {
		// モード2
		// 右スティック: 左右=左右移動, 上下=前後移動
		// 左スティック: 左右=左右回転, 上下=上昇下降
		if (id == R.id.stick_view_right) {
			if (_my != mPrevRightMY) {    // 前後移動
				mPrevRightMY = _my;
				sendMove(0, -_my, 0, 0);
			}
			if (_mx != mPrevRightMX) {    // 左右移動
				mPrevRightMX = _mx;
				sendMove(_mx, 0, 0, 0);
			}
		} else if (id == R.id.stick_view_left) {
			int mx = _mx;
			if ((Math.abs(_mx) < 20)) mx = 0;
			if (mx != mPrevLeftMX) {    // 左右回転
				mPrevLeftMX = mx;
				sendMove(0, 0, 0, mx);
			}
			if (_my != mPrevLeftMY) {    // 上昇下降
				mPrevLeftMY = _my;
				sendMove(0, 0, -_my, 0);
			}
		}
	}

	private final TouchPilotView.TouchPilotListener mTouchPilotListener = new  TouchPilotView.TouchPilotListener() {
		@Override
		public void onDrawFinish(final TouchPilotView view,
			final float min_x, final float max_x,
			final float min_y, final float max_y,
			final float min_z, final float max_z,
			final int num_points, final float[] points) {

//			if (DEBUG) Log.v(TAG, "onDrawFinish:" + num_points);
			mTouchFlight.prepare(view.getWidth(), view.getHeight(), min_x, max_x, min_y, max_y, min_z, max_z, num_points, points);
			updateButtons();
		}
	};

	@Override
	protected void onConnect(final IDeviceController controller) {
//		if (DEBUG) Log.v(TAG, "onConnect:");
		super.onConnect(controller);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setChildVisibility(mVideoRecordingBtn, controller instanceof IVideoStreamController ? View.VISIBLE : View.INVISIBLE);
				if (mController instanceof FlightControllerMambo) {
					final FlightControllerMambo mambo = (FlightControllerMambo)mController;
					mActionBtn.setVisibility(
						mambo.hasClaw() || mambo.hasGun() ? View.VISIBLE : View.GONE);
				} else {
					mActionBtn.setVisibility(View.GONE);
				}
			}
		});
		setSideMenu();
		if (mController instanceof ICameraController) {
			((ICameraController)mController).sendExposure(0);
			((ICameraController)mController).sendCameraOrientation(0, 0);
			((ICameraController)mController).sendAutoWhiteBalance(0);	// 自動ホワイトバランス
		}
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
		final boolean isSkyController = mController instanceof ISkyController;
		if (mBatteryFmt == null) {
			mBatteryFmt = getString(isSkyController ? R.string.battery_skycontroller : R.string.battery);
		}
		if (battery >= 0) {
			mBatteryLabel.setText(isSkyController
				? String.format(mBatteryFmt, battery, ((ISkyController)mController).getBatterySkyController())
				: String.format(mBatteryFmt, battery));
		} else {
			mBatteryLabel.setText("---");
		}
	}


	@Override
	protected void updateTimeOnUIThread(final int minutes, final int seconds) {
		mTimeLabelTv.setText(String.format(Locale.US, "%3d:%02d", minutes, seconds));
	}

	/**
	 * ボタン表示の更新(UIスレッドで処理)
	 */
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
			final boolean is_connected = isStarted();
			final boolean is_recording = mFlightRecorder.isRecording();
			final boolean is_playing = mFlightRecorder.isPlaying();
			final boolean can_play = is_connected && !is_recording && !mScriptRunning && !mTouchMoveRunning && (alarm_state == DroneStatus.ALARM_NON) && (mFlightRecorder.size() > 0);
			final boolean can_record = is_connected && !is_playing && !mScriptRunning;
			final boolean can_load = is_connected && !is_playing && !is_recording && !mTouchMoveRunning;
			final boolean can_fly = can_record && (alarm_state == DroneStatus.ALARM_NON);
			final boolean can_flattrim = can_fly && (state == IFlightController.STATE_STARTED);
			final boolean can_config = can_flattrim;
			final boolean can_clear = is_connected && !is_recording && !is_playing && !mScriptRunning && !mTouchMoveRunning && mTouchFlight.isPrepared();
			final boolean can_move = is_connected && !is_recording && !is_playing && !mScriptRunning && (mTouchFlight.isPrepared() || mTouchFlight.isPlaying()) && (alarm_state == DroneStatus.ALARM_NON);
			final boolean is_battery_alarm
				= (alarm_state == DroneStatus.ALARM_BATTERY) || (alarm_state == DroneStatus.ALARM_BATTERY_CRITICAL);

			// 上パネル
			mTopPanel.setEnabled(is_connected);
			mFlatTrimBtn.setEnabled(can_flattrim);	// フラットトリム
			mBatteryLabel.setTextColor(is_battery_alarm ? 0xffff0000 : 0xff9400d3);
			mConfigShowBtn.setEnabled(can_config);
			mConfigShowBtn.setColorFilter(can_config ? 0 : DISABLE_COLOR);

			// 下パネル
			mBottomPanel.setEnabled(is_connected);
			mEmergencyBtn.setEnabled(is_connected);	// 非常停止
			mCopilotBtn.setEnabled(is_connected);	// コパイロット
			mCopilotBtn.setColorFilter(
				(mController instanceof ISkyController)
				&& ((ISkyController)mController).getCoPilotingSource() == 0
					? 0 : 0xffff0000);
			setChildVisibility(mTimeLabelTv, is_recording || is_playing ? View.VISIBLE : View.INVISIBLE);
			mLoadBtn.setEnabled(can_load);            // 読み込み
			mPlayBtn.setEnabled(can_play);            // 再生
			mPlayBtn.setColorFilter(can_play ? (mFlightRecorder.isPlaying() ? 0xffff0000 : 0) : DISABLE_COLOR);
			mPlayLabel.setText(is_recording ? R.string.action_stop : R.string.action_play);
			mRecordBtn.setEnabled(can_record);        // 記録
			mRecordBtn.setColorFilter(can_record ? (is_recording ? 0xffff0000 : 0) : DISABLE_COLOR);
			mRecordLabel.setText(is_recording ? R.string.action_stop : R.string.action_record);
			if (mClearButton != null) {
				mClearButton.setEnabled(can_clear);
				mClearButton.setColorFilter(can_clear ? (mTouchMoveRunning ? 0xffff0000 : 0) : DISABLE_COLOR);
			}
			if (mMoveButton != null) {
				mMoveButton.setEnabled(can_move);
				mMoveButton.setColorFilter(can_move ? (mTouchMoveRunning || mTouchFlight.isPlaying() ? 0xffff0000 : 0) : DISABLE_COLOR);
			}

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

			// 右サイドパネル(とmCapXXXBtn等)
			mRightSidePanel.setEnabled(can_fly);

			mStillCaptureBtn.setEnabled(still_capture_state == DroneStatus.MEDIA_READY);
			setChildVisibility(mStillCaptureBtn, still_capture_state != DroneStatus.MEDIA_UNAVAILABLE ? View.VISIBLE : View.INVISIBLE);

			mVideoRecordingBtn.setEnabled((video_recording_state == DroneStatus.MEDIA_READY) || (video_recording_state == DroneStatus.MEDIA_BUSY));
			setChildVisibility(mStillCaptureBtn, video_recording_state != DroneStatus.MEDIA_UNAVAILABLE ? View.VISIBLE : View.INVISIBLE);
			mVideoRecordingBtn.setColorFilter(mVideoRecording ? 0x7fff0000 : 0);
//			mVideoRecordingBtn.setImageResource(mVideoRecording ? android.R.drawable.presence_video_busy : android.R.drawable.presence_video_online);

			// 左サイドパネル(とmFlipXXXBtn等)
			mLeftSidePanel.setEnabled(can_fly);
			// 右スティックパネル(東/西ボタン)
			if (mRightStickPanel != null) {
				mRightStickPanel.setEnabled(can_fly);
			}
			// 左スティックパネル(北/南ボタン)
			if (mLeftStickPanel != null) {
				mLeftStickPanel.setEnabled(can_fly);
			}
			// タッチパイロットView
			if (mTouchPilotView != null) {
				mTouchPilotView.setEnabled(can_fly);
			}

			for (final View view: mActionViews) {
				view.setEnabled(can_fly);
				if (view instanceof ImageView) {
					((ImageView)view).setColorFilter(can_fly ? 0 : DISABLE_COLOR);
				}
			}
		}
	};

	private void setSideMenu() {
//		if (DEBUG) Log.v(TAG, "setSideMenu:");
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final Activity activity = getActivity();
				if (activity == null || activity.isFinishing()) return;

				final List<String> labelList = setupScript();
				if (labelList.size() > 0) {
					if (mSideMenuListView == null) {
						mSideMenuListView = new SideMenuListView(activity);
						((IMainActivity)activity).setSideMenuView(mSideMenuListView);
						mSideMenuListView.setOnItemClickListener(mOnItemClickListener);
					}
					ListAdapter adapter = mSideMenuListView.getAdapter();
					if (adapter instanceof SideMenuAdapter) {
						((SideMenuAdapter)adapter).clear();
						if (labelList.size() > 0) {
							((SideMenuAdapter)adapter).addAll(labelList);
						}
					} else {
						mSideMenuListView.setAdapter(null);
						if (labelList.size() > 0) {
							adapter = new SideMenuAdapter(getActivity(), R.layout.item_sidemenu, labelList);
							mSideMenuListView.setAdapter(adapter);
						}
					}
					((IMainActivity)activity).setSideMenuEnable(labelList.size() > 0);
				}
			}
		});
	}

	private void removeSideMenu() {
//		if (DEBUG) Log.v(TAG, "removeSideMenu:");
		if (mSideMenuListView != null) {
			final SideMenuListView v = mSideMenuListView;
			mSideMenuListView = null;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					final IMainActivity activity = (IMainActivity) getActivity();
					activity.setSideMenuEnable(false);
					activity.removeSideMenuView(v);
				}
			});
		}
	}

	/**
	 * サイドメニューの項目をクリックした時の処理
	 */
	private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
//			if (DEBUG) Log.v(TAG, "onItemClick:" + position);
			final IMainActivity activity = (IMainActivity)getActivity();
			activity.closeSideMenu();
			startScript(position);
/*			if ((position >= 0) && (position < SCRIPT_NUM)) {
				startScript(position);
			} else {
				startAnimationAction(position - SCRIPT_NUM);	// これは新minidrone以外だと動かないのでコメントアウト
			} */
		}
	};

	private int mSurfaceId = 0;
	private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
//			if (DEBUG) Log.v(TAG, "onSurfaceTextureAvailable:");
			if ((mVideoStream != null) && (mSurfaceId == 0)) {
				final Surface _surface = new Surface(surface);
				mSurfaceId = _surface.hashCode();
				mVideoStream.addSurface(mSurfaceId, _surface);
			}
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
//			if (DEBUG) Log.v(TAG, "onSurfaceTextureSizeChanged:");
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
//			if (DEBUG) Log.v(TAG, "onSurfaceTextureDestroyed:");
			if (mVideoStream != null) {
				mVideoStream.removeSurface(mSurfaceId);
			}
			mSurfaceId = 0;
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		}
	};

	private final Runnable mAlphaTask = new Runnable() {
		@Override
		public void run() {
			if (!mAutoHide) return;
			if (mPilotFrame != null) {
				for (final View target: mAlphaHideList) {
					alphaAnimation(target, 1, 1.0f, 0.3f, -1, 0, null);
				}
				mModelView.setAlpha(0.6f);
				alphaAnimation(mPilotFrame, 1, 1.0f, 0.3f, -1, 0, new AnimationCallback() {
					@Override
					public void onAnimationEnd(final View target, final int type) {
						mModelView.setAlpha(0.3f);
						runOnUiThread(mHideTask, HIDE_PILOTING_DELAY_MS);
					}
				});
			}
		}
	};

	private final Runnable mHideTask = new Runnable() {
		private final AnimationCallback mAnimationCallback = new AnimationCallback() {
			@Override
			public void onAnimationEnd(final View target, final int type) {
				if (!mAutoHide) return;
//				target.setVisibility(View.INVISIBLE);
				mModelView.setAlpha(0.0f);
			}
		};
		@Override
		public void run() {
			if (!mAutoHide) return;
			if (mPilotFrame != null) {
				for (final View target: mAlphaHideList) {
					alphaAnimation(target, 1, 0.3f, 0.0f, -1, 0, null);
				}
				mModelView.setAlpha(0.2f);
				alphaAnimation(mPilotFrame, 1, 0.3f, 0.0f, -1, 0, mAnimationCallback);
			}
		}
	};

	@SuppressWarnings("WrongConstant")
	private void cancelAlphaHide() {
		removeFromUIThread(mAlphaTask);
		removeFromUIThread(mHideTask);
		for (final View target: mAlphaHideList) {
			target.clearAnimation();
			target.setAlpha(1.0f);
			int visibility = View.VISIBLE;
			try {
				visibility = (Integer)target.getTag(R.id.anim_visibility);
			} catch (final Exception e) {
			}
			target.setVisibility(visibility);
		}
		if (mPilotFrame !=  null) {
			mPilotFrame.clearAnimation();
			mPilotFrame.setAlpha(1.0f);
			mPilotFrame.setVisibility(View.VISIBLE);
			if (mAutoHide) {
				runOnUiThread(mAlphaTask, ALPHA_PILOTING_DELAY_MS);
			}
		}
		mModelView.setAlpha(1.0f);
	}

	@SuppressWarnings("WrongConstant")
	private void clearAlphaHide() {
		removeFromUIThread(mAlphaTask);
		removeFromUIThread(mHideTask);
		for (final View target: mAlphaHideList) {
			target.clearAnimation();
			target.setAlpha(1.0f);
			int visibility = View.VISIBLE;
			try {
				visibility = (Integer)target.getTag(R.id.anim_visibility);
			} catch (final Exception e) {
			}
			target.setVisibility(visibility);
		}
		if (mPilotFrame !=  null) {
			mPilotFrame.clearAnimation();
			mPilotFrame.setAlpha(1.0f);
			mPilotFrame.setVisibility(View.VISIBLE);
		}
		mModelView.setAlpha(1.0f);
	}

	private final Map<View, ResetLevelTask> mResetLevelTasks = new HashMap<View, ResetLevelTask>();

	/**
	 *
	 * @param view
	 * @param level
	 */
	private void setLevel(@NonNull final View view, final int level) {
		setLevel(view, level, TOUCH_RESPONSE_TIME_MS);
	}

	/**
	 * 指定したViewの背景Drawableにレベルをセットする
	 * reset_delayが正ならば指定時間後に0リセットする
	 * @param view
	 * @param level
	 * @param reset_delay ミリ秒
	 */
	private void setLevel(@NonNull final View view, final int level, final long reset_delay) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final Drawable bk = view.getBackground();
				if (bk != null) {
					bk.setLevel(level);
				}
			}
		});
		if (reset_delay > 0) {
			ResetLevelTask task = mResetLevelTasks.get(view);
			if (task == null) {
				task = new ResetLevelTask(view);
				mResetLevelTasks.put(view, task);
			}
			removeFromUIThread(task);
			runOnUiThread(task, reset_delay);	// UIスレッド上で遅延実行
		}
	}

	/**
	 * 一定時間後にlevelをリセット(0)するためのRunnable
	 */
	private static class ResetLevelTask implements Runnable {
		private final View mView;
		ResetLevelTask(final View view) {
			mView = view;
		}
		@Override
		public void run() {
			final Drawable bk = mView.getBackground();
			if (bk != null) {
				bk.setLevel(0);
			}
		}
	}

	@Override
	protected void onSendMove(final float roll, final float pitch, final float gaz, final float yaw) {
		// 左右
		if ((mLeftView!= null) && (mRightView != null)) {
			if (roll < 0.0f) {
				setLevel(mLeftView, 1);
				setLevel(mRightView, 0, 0);
			} else if (roll > 0.0f) {
				setLevel(mRightView, 1);
				setLevel(mLeftView, 0, 0);
			}
		}
		// 前後
		if ((mForwardView != null) && (mBackView != null)) {
			if (pitch > 0.0f) {
				setLevel(mForwardView, 1);
				setLevel(mBackView, 0, 0);
			} else if (pitch < 0.0f) {
				setLevel(mBackView, 1);
				setLevel(mForwardView, 0, 0);
			}
		}
		// 上下
		if ((mDownView != null) && (mUpView != null)) {
			if (gaz < 0.0f) {
				setLevel(mDownView, 1);
				setLevel(mUpView, 0, 0);
			} else if (gaz > 0.0f) {
				setLevel(mUpView, 1);
				setLevel(mDownView, 0, 0);
			}
		}
		// 左右回転
		if ((mTurnLeftView != null) && (mTurnRightView != null)) {
			if (yaw > 0.0f) {
				setLevel(mTurnLeftView, 1);
				setLevel(mTurnRightView, 0, 0);
			} else if (yaw < 0.0f) {
				setLevel(mTurnRightView, 1);
				setLevel(mTurnLeftView, 0, 0);
			}
		}
	}

	protected void startHeartBeat() {
		if (mHeartBeatView != null) {
			runOnUiThread(mHeartBeatShowTask);
		}
	}

	protected void stopHeartBeat() {
		removeFromUIThread(mHeartBeatShowTask);
		removeFromUIThread(mHearBeatHideTask);
		if (mHeartBeatView != null) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mHeartBeatView.setVisibility(View.INVISIBLE);
				}
			});
		}
	}

	private final Runnable mHeartBeatShowTask = new Runnable() {
		@Override
		public void run() {
			mHeartBeatView.setVisibility(View.VISIBLE);
			runOnUiThread(mHearBeatHideTask, 800);
		}
	};

	private final Runnable mHearBeatHideTask = new Runnable() {
		@Override
		public void run() {
			mHeartBeatView.setVisibility(View.INVISIBLE);
			runOnUiThread(mHeartBeatShowTask, 200);
		}
	};


}
