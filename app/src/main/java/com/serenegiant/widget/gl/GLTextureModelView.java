package com.serenegiant.widget.gl;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public abstract class GLTextureModelView extends GLTextureView implements IModelView {
	private static final boolean DEBUG = true;	// FIXME 実働時はfalseにすること
	private static final String TAG = "GLTextureModelView";

	protected static final int IDLE = 0;
	protected static final int INITIALIZE = 1;
	protected static final int RESUME0 = 2;
	protected static final int RESUME1 = 3;
	protected static final int RUNNING = 4;
	protected static final int PAUSE = 5;
	protected static final int FINISH = 6;


	protected final GLGraphics glGraphics;
	protected Screen mScreen;
	/** UIスレッドからscreenを切り替える際の中継用変数 */
	protected Screen mNextScreen;
	protected int mState = INITIALIZE;
	protected final Object mStateSyncObj = new Object();
	protected long mPrevTime;
	private long mRendererThreadID;
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

	public GLTextureModelView(final Context context) {
		this(context, null);
	}

	public GLTextureModelView(final Context context, final  AttributeSet attrs) {
		super(context, attrs);
		if (DEBUG) Log.v(TAG, "コンストラクタ");
		glGraphics = new GLGraphics(this);
		setRenderer(renderer);
	}

/*	@Override
	public void onResume() {
		super.onResume();
	} */

	@Override
	public void onPause() {
//		if (DEBUG) Log.v(TAG, "GLGameFragment#onPause:isFinishing=" + getActivity().isFinishing());
		synchronized (mStateSyncObj) {
			if (mState == RUNNING) {
				mState = PAUSE;
				try {
					mStateSyncObj.wait(50);
				} catch (final InterruptedException e) {
				}
			}
		}
		super.onPause();
	}

	@Override
	public void release() {
		synchronized (mStateSyncObj) {
			if (mState != IDLE) {
				mState = FINISH;
				try {
					mStateSyncObj.wait(50);
				} catch (final InterruptedException e) {
				}
			}
		}
		super.release();
	}

	// GLTextureViewのレンダラー
	private final Renderer renderer = new Renderer() {
		float deltaTime;
		@Override
		public void onDrawFrame(final GL10 gl) {
			if ((isInEditMode())) return;

			int localState;
			synchronized (mStateSyncObj) {
				localState = mState;
			}

			switch (localState) {
			case RUNNING:	// 実行中
				final long t = System.nanoTime();
				deltaTime = (t - mPrevTime) / 1000000000.0f;
				mPrevTime = t;
				mScreen.update(deltaTime);
				mScreen.draw(deltaTime);
				break;
			case PAUSE:		// 中断処理
				handlePause();
				break;
			case FINISH:	// 終了処理
				handleFinish();
				break;
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
				mScreen.dispose();
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
		public void onSurfaceDestroyed(final GL10 gl) {
			if (DEBUG) Log.v(TAG, "onSurfaceDestroyed:gl=" + gl);
			int localState;
			synchronized (mStateSyncObj) {
				localState = mState;
			}
			if (localState == RUNNING) {    // 実行中
				handleFinish();
			}
			glActive = false;
		}

		@Override
		public void onSurfaceChanged(final GL10 gl, final int width, final int height) {
			if (DEBUG) Log.v(TAG, "onSurfaceChanged:width=" + width + ",height=" + height + ",gl=" + gl);
			if ((isInEditMode())) return;

			mIsLandscape = (width > height);
			glGraphics.setGL(gl);
			synchronized (mStateSyncObj) {
				if (mState == RUNNING) {
					setScreenSize(mScreen, width, height);
				} else {
					setScreen(mScreen == null ? getScreen() : mScreen);
					mState = RUNNING;
				}
			}
			gl.glViewport(0, 0, width, height);
		}

		@Override
		public void onSurfaceCreated(final GL10 gl, final EGLConfig config) {
			if (DEBUG) Log.v(TAG, "onSurfaceCreated:gl=" + gl);
			if ((isInEditMode())) return;

			int localState;
			glActive = true;
			glGraphics.setGL(gl);
			synchronized (mStateSyncObj) {
				mRendererThreadID = Thread.currentThread().getId();
				if (mScreen == null) {
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
			System.gc();
			mPrevTime = System.nanoTime();
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
	protected void setScreenSize(final Screen screen, final int width, final int height) {
		if (screen != null) {
			screen.setScreenSize(width, height);
			screen.onSizeChanged(width, height);
			requestRender();
		}
	}

	public void setScreen(final Screen screen) {
		// 呼び出し元のスレッドIDに応じて直接呼び出すかrunnable経由で呼び出すかを切り替える
		if (mRendererThreadID == Thread.currentThread().getId()) {	// ゲームスレッド内から呼び出された時
			internalSetScreen(screen);							// 直接実行する
		} else {												// 他スレッドから呼び出された時
			mNextScreen = screen;
			queueEvent(mChangeScreenRunnable);					// runnableをゲームスレッドに渡して実行してもらう
		}
	}

	/**
	 * screen切り替え処理の実体(無限ループにならないようにrunnableからsetScreenを再び呼び出さないようにするため別メソッド化)
	 * @param screen
	 */
	protected void internalSetScreen(final Screen screen) {
		if (DEBUG) Log.v(TAG, "internalSetScreen:" + screen);
		if (screen == null)
			throw new IllegalArgumentException("Screen must not be null");
		if ((mScreen != null) && (mScreen != screen)) {
			mScreen.pause();
			mScreen.dispose();
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
	protected abstract Screen getScreen();

	@Override
	public FileIO getAssetIO() {
		final Context app = getContext().getApplicationContext();
		return (app instanceof IModelViewApplication) ? ((IModelViewApplication)app).getAssetIO() : null;
	}

	@Override
	public FileIO getExtFileIO() {
		final Context app = getContext().getApplicationContext();
		return (app instanceof IModelViewApplication) ? ((IModelViewApplication)app).getExtFileIO() : null;
	}

	@Override
	public FileIO getFileIO() {
		final Context app = getContext().getApplicationContext();
		return (app instanceof IModelViewApplication) ? ((IModelViewApplication)app).getFileIO() : null;
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
		// 自前のスレッドで描画タイミングを制御しているのでここではGLTextureView#requestRenderは呼び出さない
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

}
