package com.serenegiant.gameengine.v1;
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

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.serenegiant.gameengine.FileIO;
import com.serenegiant.gameengine.IGameViewApplication;
import com.serenegiant.gameengine.IScreen;
import com.serenegiant.gameengine.LoadableInterface;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public abstract class GLModelView extends GLSurfaceView implements IModelView {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static final String TAG = "GLModelView";

	protected static final int IDLE = 0;
	protected static final int INITIALIZE = 1;
	protected static final int RESUME0 = 2;
	protected static final int RESUME1 = 3;
	protected static final int RUNNING = 4;
	protected static final int PAUSE = 5;
	protected static final int FINISH = 6;

	protected final GLGraphics glGraphics;
	protected IScreen mScreen;
	/** UIスレッドからscreenを切り替える際の中継用変数 */
	protected IScreen mNextScreen;
	protected int mState = INITIALIZE;
	protected final Object mStateSyncObj = new Object();
	protected long mPrevTime = System.nanoTime();
	private TimerThread timerThread = null;
	private long mGameThreadID;
	private boolean mIsLandscape;
	//
	protected LoadableInterface mLoadableInterface;
	// 描画関係
	protected final Object mRendererSyncObj = new Object();
	protected boolean mForceRender = false;			// 強制描画フラグ, 画面更新時間になっていなくてもこのフラグがtrueならdrawを呼び出す
	protected float mFpsRequested;
	protected boolean mContinueRendering = false;	// 連続描画モード?
	protected long mUpdateIntervals = 0;			// 連続描画間隔?
	protected boolean glActive = false;

	public GLModelView(final Context context) {
		this(context, null);
	}

	public GLModelView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		if (DEBUG) Log.v(TAG, "コンストラクタ");
		glGraphics = new GLGraphics(this);

//		setEGLContextClientVersion(2);	// GLES2を使う時
		setEGLConfigChooser(8, 8, 8, 8, 16, 1);	 // RGBA8888D16S1 これはsetContentView,　setRendererよりも前に呼び出すこと
		setRenderer(renderer);
//		getHolder().setFormat(PixelFormat.TRANSLUCENT);
		setZOrderOnTop(false);	// これを入れないとadViewが上に表示されない
	}

	@Override
	public void onResume() {
		super.onResume();
		startTimerThread();
	}

	@Override
	public void onPause() {
//		if (DEBUG) Log.v(TAG, "GLGameFragment#onPause:isFinishing=" + getActivity().isFinishing());
		stopTimerThread();
		synchronized (mStateSyncObj) {
			if (mState == RUNNING) {
				mState = PAUSE;
				try {
					mStateSyncObj.wait(50);
				} catch (final InterruptedException e) {
				}
			}
			glActive = false;
		}
		super.onPause();
	}

	@Override
	public void release() {
		stopTimerThread();
		synchronized (mStateSyncObj) {
			if (mState == RUNNING) {
				mState = FINISH;
				try {
					mStateSyncObj.wait(50);
				} catch (final InterruptedException e) {
				}
			}
			glActive = false;
		}
		super.onPause();
	}

	@Override
	public IScreen getCurrentScreen() {
		return mScreen;
	}

	@Override
	public void setScreen(final IScreen screen) {
		// 呼び出し元のスレッドIDに応じて直接呼び出すかrunnable経由で呼び出すかを切り替える
		if (mGameThreadID == Thread.currentThread().getId()) {	// ゲームスレッド内から呼び出された時
			internalSetScreen(screen);							// 直接実行する
		} else {												// 他スレッドから呼び出された時
			mNextScreen = screen;
			queueEvent(mChangeScreenRunnable);					// runnableをゲームスレッドに渡して実行してもらう
		}
	}

	@Override
	public FileIO getAssetIO() {
		final Context app = getContext().getApplicationContext();
		return (app instanceof IGameViewApplication) ? ((IGameViewApplication)app).getAssetIO() : null;
	}

	@Override
	public FileIO getExtFileIO() {
		final Context app = getContext().getApplicationContext();
		return (app instanceof IGameViewApplication) ? ((IGameViewApplication)app).getExtFileIO() : null;
	}

	@Override
	public FileIO getFileIO() {
		final Context app = getContext().getApplicationContext();
		return (app instanceof IGameViewApplication) ? ((IGameViewApplication)app).getFileIO() : null;
	}

	@Override
	public GLGraphics getGLGraphics() {
		return glGraphics;
	}

	private static int mNextPickId = 1;
	@Override
	public int getNextPickId() {
		return mNextPickId++;
	}

	@Override
	public boolean isLandscape() {
		return mIsLandscape;
	}

	@Override
	public void requestRender() {
		// 自前のスレッドで描画タイミングを制御しているのでここではGLSurfaceView#requestRenderは呼び出さない
		synchronized (mRendererSyncObj) {
			mForceRender = true;
			mRendererSyncObj.notifyAll();
		}
	}

	@Override
	public void setFpsRequest(final float fps) {
		synchronized (mRendererSyncObj) {
			mFpsRequested = fps;
			mContinueRendering = (fps > 0);
			if (mContinueRendering)
				mUpdateIntervals = (long)(1000 * 1000000 / fps);	// 更新頻度[ナノ秒]
			else
				mUpdateIntervals = 1000 * 1000000;					// 更新頻度1秒[ナノ秒]
		}
	}

	/**
	 * タイマースレッドが存在しなければ生成する
	 */
	private void startTimerThread() {
		if (DEBUG) Log.v(TAG, "startTimerThread:");
		if (timerThread == null) {
			timerThread = new TimerThread();
			timerThread.start();
		}
	}

	/**
	 * タイマースレッドが存在すれば停止・破棄する
	 */
	private void stopTimerThread() {
		if (DEBUG) Log.v(TAG, "stopTimerThread:");
		if (timerThread != null) {
			timerThread.pause();
			timerThread = null;
		}
	}

	/**
	 * 一定時間毎にGLSurfaceViewのレンダラースレッドを起床させるスレッド
	 * このスレッドが起床要求するかtouchEvent/accelEventが発生するとレンダラースレッドが起床される
	 */
	private class TimerThread extends Thread {
		public volatile boolean isRunning = true;

		@Override
		public void run() {
			if (DEBUG) Log.v(TAG, "TimerThread#run:");
			long prevTime = System.nanoTime();

			setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);	// requestRenderが呼ばれた時だけ描画する
			for ( ; isRunning ; ) {
				final long nano_time = System.nanoTime();
				final long delta = nano_time - prevTime;
				synchronized(mRendererSyncObj) {
					if (mForceRender || (delta > mUpdateIntervals)) {	// 起床要求の時間確認
						prevTime = nano_time;
						GLModelView.super.requestRender();				// レンダラースレッドを起床要求
						mForceRender = false;
					} else {
						try {
							mRendererSyncObj.wait(5);	// 5msecが経過するか、別スレッドからnotifyされるまで待つ。
						} catch (final InterruptedException e) {
						}
					}
				}
			}
			setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);	// 連続描画に戻す
			synchronized (mRendererSyncObj) {
				mRendererSyncObj.notifyAll();
			}
			if (DEBUG) Log.v(TAG, "TimerThread#run:終了");
		}

		public void pause() {
			if (DEBUG) Log.v(TAG, "TimerThread#pause:");
			isRunning = false;
			synchronized (mRendererSyncObj) {
				while (true) {
					try {
						mRendererSyncObj.wait();
						break;
					} catch (final InterruptedException e) {
						// リトライ
					}
				}
			}
			while (isAlive()) {
				try {
					join();
					break;
				} catch (final InterruptedException e) {
					// リトライする
				}
			}
		}
	}

	// GLSurfaceViewのレンダラー
	private final GLSurfaceView.Renderer renderer = new Renderer() {
		float deltaTime;
		@Override
		public void onDrawFrame(final GL10 gl) {
			if ((isInEditMode())) return;

			int localState;
			synchronized (mStateSyncObj) {
				localState = mState;
			}

			if (localState == RUNNING) {	// 実行
				final long t = System.nanoTime();
				deltaTime = (t - mPrevTime) / 1000000000.0f;
				mPrevTime = t;
				mScreen.update(deltaTime);
				mScreen.draw(deltaTime);
				// 出来るだけこのタイミングでガベージコレクションが走って欲しいんだけど、頻度が高すぎる
			}
			else if (localState == PAUSE) {		// 中断処理
				handlePause();
			}
			else if (localState == FINISH) {	// 終了処理
				handleFinish();
			}
		}

		private void handlePause() {
			synchronized (mStateSyncObj) {
				mState = PAUSE;
			}
			mScreen.pause();
			if (mLoadableInterface != null) {
				mLoadableInterface.pause();
			}
			synchronized (mStateSyncObj) {
				mState = IDLE;
				mStateSyncObj.notifyAll();
			}
		}

		private void handleFinish() {
			synchronized (mStateSyncObj) {
				mState = FINISH;
			}
			onRelease();
			if (mScreen != null) {
				mScreen.pause();
				mScreen.release();
				mScreen = null;
			}
			if (mLoadableInterface != null) {
				mLoadableInterface.dispose();
				mLoadableInterface = null;
			}
			synchronized (mStateSyncObj) {
				mState = IDLE;
				mStateSyncObj.notifyAll();
			}
		}

		@Override
		public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
			if (DEBUG) Log.v(TAG, "onSurfaceChanged:width=" + width + ",height=" + height + ",gl=" + gl);
			if ((isInEditMode())) return;

			mIsLandscape = (width > height);
			synchronized (mStateSyncObj) {
				if (mState == RUNNING) {
					setScreenSize(mScreen, width, height);
				} else {
					setScreen(mScreen == null ? createScreen() : mScreen);
					mState = RUNNING;
				}
			}
			gl.glViewport(0, 0, width, height);
		}

		@Override
		public void onSurfaceCreated(final GL10 gl, final EGLConfig config) {
			if (DEBUG) Log.v(TAG, "onSurfaceCreated" + gl);
			if ((isInEditMode())) return;

			int localState;
			glActive = true;
			synchronized (mStateSyncObj) {
				mGameThreadID = Thread.currentThread().getId(); // 2013/07/22
				if (mScreen == null) {	// 2013/06/05
					mState = INITIALIZE;
				}
				localState = mState;
			}
			if (localState == INITIALIZE) {
				onInitialize();
			}
			if (mLoadableInterface != null) {
				if (localState == INITIALIZE) {
					mLoadableInterface.load(getContext());
				} else {
					mLoadableInterface.reload(getContext());
				}
				mLoadableInterface.resume(getContext());
			}
			mPrevTime = System.nanoTime();
/*			setScreen(mScreen == null ? getStartScreen() : mScreen);
			synchronized (mStateSyncObj) {
				mState = GameState.RUNNING;
			} */
			System.gc();
		}
	};

	protected void onInitialize() {
		if (DEBUG) Log.v(TAG, "onInitialize:");
	}

	protected void onRelease() {
		if (DEBUG) Log.v(TAG, "onRelease:");
	}

	/**
	 * サイズ変更時の処理
	 * onSurfaceChangedから呼び出される
	 * @param screen
	 * @param width
	 * @param height
	 */
	protected void setScreenSize(final IScreen screen, final int width, final int height) {
		if (screen != null) {
			screen.setScreenSize(width, height);
			screen.onSizeChanged(width, height);
			requestRender();
		}
	}

	/**
	 * screen切り替え処理の実体(無限ループにならないようにrunnableからsetScreenを再び呼び出さないようにするため別メソッド化)
	 * @param screen
	 */
	protected void internalSetScreen(final IScreen screen) {
		if (DEBUG) Log.v(TAG, "internalSetScreen:" + screen);
		if (screen == null)
			throw new IllegalArgumentException("Screen must not be null");
		if ((mScreen != null) && (mScreen != screen)) {
			mScreen.pause();
			mScreen.release();
		}
		System.gc();	// 2013/05/24
		setScreenSize(screen, getWidth(), getHeight());
		synchronized (mStateSyncObj) {
			mScreen = screen;
		}
		screen.resume();
		requestRender();	// 2013/06/23
//		mNextScreen = null;
	}

	/**
	 * UIスレッドからscreenを切り替える際にゲームスレッドに変更を要求するためのrunnable
	 */
	protected final Runnable mChangeScreenRunnable = new Runnable() {
		@Override
		public void run() {
//			if (DEBUG) Log.v(TAG, "BaseGameFragment#mChangeScreenRunnable#run");
			internalSetScreen(mNextScreen);
		}
	};

	/**
	 * 表示画面を生成
	 * @return
	 */
	protected abstract IScreen createScreen();

}
