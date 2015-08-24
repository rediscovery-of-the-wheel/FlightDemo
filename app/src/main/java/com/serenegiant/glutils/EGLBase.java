package com.serenegiant.glutils;

/*
 * Copyright (c) 2014 saki t_saki@serenegiant.com
 *
 * File name: EGLBase.java
 *
*/

/**
 * プライベートスレッド上でOpenGL|ESを使って描画するための
 * EGLレンダリングコンテキストを生成＆使用するためのヘルパークラス
 */
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.serenegiant.utils.BuildCheck;

/**
 * EGLを使用してSurfaceおよびオフスクリーン(PBuffer)へOpenGL|ESで描画をするためのクラス
 */
public class EGLBase {
//	private static final boolean DEBUG = false;	// FIXME set false on release
	private static final String TAG = "EGLBase";

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    public static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;
//	private static final int EGL_SWAP_BEHAVIOR_PRESERVED_BIT = 0x0400;

    private EGL10 mEgl = null;
	private EGLDisplay mEglDisplay = null;
    private EGLConfig mEglConfig = null;
    private int mGlVersion = 2;

	private EGLContext mEglContext = EGL10.EGL_NO_CONTEXT;

	/**
	 * EGLレンダリングコンテキストに紐付ける描画オブジェクト
	 */
	public static class EglSurface {
		private final EGLBase mEgl;
		private EGLSurface mEglSurface = EGL10.EGL_NO_SURFACE;

		/**
		 * Surface(Surface/SurfaceTexture/SufaceHolder)に関係付けられたEglSurface
		 * @param egl
		 * @param surface
		 */
		/*package*/EglSurface(final EGLBase egl, final Object surface) {
//			if (DEBUG) Log.v(TAG, "EglSurface:");
			mEgl = egl;
			mEglSurface = mEgl.createWindowSurface(surface);
		}

		/**
		 * 指定した大きさを持つオフスクリーンEglSurface(PBuffer)
		 * @param egl
		 * @param width
		 * @param height
		 */
		/*package*/EglSurface(final EGLBase egl, final int width, final int height) {
//			if (DEBUG) Log.v(TAG, "EglSurface:");
			mEgl = egl;
			mEglSurface = mEgl.createOffscreenSurface(width, height);
		}

		/**
		 * 指定したEGLSurfaceをカレントの描画Surfaceに設定する
		 * Surface全面に描画できるようにViewportも変更するので必要であればswapの後に変更すること
		 */
		public void makeCurrent() {
			mEgl.makeCurrent(mEglSurface);
			GLES20.glViewport(0, 0, mEgl.getSurfaceWidth(mEglSurface), mEgl.getSurfaceHeight(mEglSurface));
		}

		/**
		 * 描画を終了してダブルバッファを切り替える
		 */
		public void swap() {
			mEgl.swap(mEglSurface);
		}

//		public void setPresentationTime(final long nsecs) {
//			EGLExt.eglPresentationTimeANDROID(mEgl.mEglDisplay, mEglSurface, nsecs);
//		}

		/**
		 * 破棄処理
		 */
		public void release() {
//			if (DEBUG) Log.v(TAG, "EglSurface:release:");
			mEgl.makeDefault();
			mEgl.destroyWindowSurface(mEglSurface);
	        mEglSurface = EGL10.EGL_NO_SURFACE;
		}
	}

	/**
	 * コンストラクタ
	 * @param shared_context 共有コンテキストを使用する場合に指定
	 * @param with_depth_buffer
	 * @param isRecordable trueならMediaCodec等の録画用Surfaceを使用する場合に、EGL_RECORDABLE_ANDROIDフラグ付きでコンフィグする
	 */
	public EGLBase(final EGLContext shared_context, final boolean with_depth_buffer, final boolean isRecordable) {
//		if (DEBUG) Log.v(TAG, "EGLBase:");
		init(shared_context, with_depth_buffer, isRecordable);
	}

	/**
	 * 破棄処理
	 */
    public void release() {
//		if (DEBUG) Log.v(TAG, "release:");
    	destroyContext();
        mEglContext = EGL10.EGL_NO_CONTEXT;
   		if (mEgl == null) return;
   		mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
//		mEgl.eglReleaseThread();	// XXX これを入れるとハングアップする機種がある
   		mEgl.eglTerminate(mEglDisplay);
    	mEglDisplay = null;
		mEglConfig = null;
   		mEgl = null;
    }

    /**
     * 指定したSurfaceからEglSurfaceを生成する
     * 生成したEglSurfaceをmakeCurrentした状態で戻る
     * @param surface(Surface/SurfaceTexture/SurfaceHolder)
     * @return
     */
	public EglSurface createFromSurface(final Object surface) {
//		if (DEBUG) Log.v(TAG, "createFromSurface:");
		final EglSurface eglSurface = new EglSurface(this, surface);
		eglSurface.makeCurrent();
		return eglSurface;
	}

	/**
	 * 指定した大きさのオフスクリーンEglSurfaceを生成する
     * 生成したEglSurfaceをmakeCurrentした状態で戻る
	 * @param width PBufferオフスクリーンのサイズ(0以下はだめ)
	 * @param height
	 * @return
	 */
	public EglSurface createOffscreen(final int width, final int height) {
//		if (DEBUG) Log.v(TAG, "createOffscreen:");
		final EglSurface eglSurface = new EglSurface(this, width, height);
		eglSurface.makeCurrent();
		return eglSurface;
	}

	/**
	 * EGLレンダリングコンテキストを取得する
	 * このEGLBaseインスタンスを使って生成したEglSurfaceをmakeCurrentした状態で
	 * eglGetCurrentContextを呼び出すのと一緒
	 * @return
	 */
	public EGLContext getContext() {
		return mEglContext;
	}

	/**
	 * EGLレンダリングコンテキストとスレッドの紐付けを解除する
	 */
	public void makeDefault() {
//		if (DEBUG) Log.v(TAG, "makeDefault:");
	    if (!mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)) {
	        Log.w("TAG", "makeDefault" + mEgl.eglGetError());
	    }
	}

    /**
     * Queries a string value.
     */
    public String queryString(final int what) {
        return mEgl.eglQueryString(mEglDisplay, what);
    }

    /**
     * Returns the GLES version this context is configured for (2 or 3).
     */
    public int getGlVersion() {
        return mGlVersion;
    }

	/**
	 * 初期化の下請け
	 * @param shared_context
	 * @param with_depth_buffer
	 * @param isRecordable
	 */
	private final void init(EGLContext shared_context, final boolean with_depth_buffer, final boolean isRecordable) {
//		if (DEBUG) Log.v(TAG, "init:");
		shared_context = shared_context != null ? shared_context : EGL10.EGL_NO_CONTEXT;
		if (mEgl == null) {
			mEgl = (EGL10)EGLContext.getEGL();
	        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
	        if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
	            throw new RuntimeException("eglGetDisplay failed");
	        }
			final int[] version = new int[2];
	        if (!mEgl.eglInitialize(mEglDisplay, version)) {
	        	mEglDisplay = null;
	            throw new RuntimeException("eglInitialize failed");
	        }
		}
        // GLES3で取得できるかどうか試してみる
		EGLConfig config = getConfig(3, with_depth_buffer, isRecordable);
        if (config != null) {
            final EGLContext context = createContext(shared_context, config, 3);
            if ((mEgl.eglGetError()) == EGL10.EGL_SUCCESS) {
                //Log.d(TAG, "Got GLES 3 config");
            	mEglConfig = config;
            	mEglContext = context;
                mGlVersion = 3;
            }
        }
        if (mEglContext == EGL10.EGL_NO_CONTEXT) {
            config = getConfig(2, with_depth_buffer, isRecordable);
            if (mEglConfig == null) {
               	throw new RuntimeException("chooseConfig failed");
            }
            // create EGL rendering context
            final EGLContext context = createContext(shared_context, config, 2);
			checkEglError("eglCreateContext");
			mEglConfig = config;
			mEglContext = context;
            mGlVersion = 2;
        }
        // confirm whether the EGL rendering context is successfully created
		final int[] values = new int[1];
		mEgl.eglQueryContext(mEglDisplay, mEglContext, EGL_CONTEXT_CLIENT_VERSION, values);
//		if (DEBUG) Log.d(TAG, "EGLContext created, client version " + values[0]);
        makeDefault();
	}

	/**
	 * change context to draw this window surface
	 * @return
	 */
	private final boolean makeCurrent(final EGLSurface surface) {
//		if (DEBUG) Log.v(TAG, "makeCurrent:");
/*		if (mEglDisplay == null) {
            if (DEBUG) Log.d(TAG, "makeCurrent:eglDisplay not initialized");
        } */
        if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
            final int error = mEgl.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "makeCurrent:EGL_BAD_NATIVE_WINDOW");
            }
            return false;
        }
        // attach EGL renderring context to specific EGL window surface
        if (!mEgl.eglMakeCurrent(mEglDisplay, surface, surface, mEglContext)) {
            Log.w("TAG", "eglMakeCurrent" + mEgl.eglGetError());
            return false;
        }
        return true;
	}

	private final int swap(final EGLSurface surface) {
//		if (DEBUG) Log.v(TAG, "swap:");
        if (!mEgl.eglSwapBuffers(mEglDisplay, surface)) {
        	final int err = mEgl.eglGetError();
//        	if (DEBUG) Log.w(TAG, "swap:err=" + err);
            return err;
        }
        return EGL10.EGL_SUCCESS;
    }

    private final EGLContext createContext(final EGLContext shared_context, final EGLConfig config, final int version) {
//		if (DEBUG) Log.v(TAG, "createContext:");

        final int[] attrib_list = {
        	EGL_CONTEXT_CLIENT_VERSION, version,
        	EGL10.EGL_NONE
        };
        final EGLContext context = mEgl.eglCreateContext(mEglDisplay, config, shared_context, attrib_list);
//		checkEglError("eglCreateContext");
        return context;
    }

    private final void destroyContext() {
//		if (DEBUG) Log.v(TAG, "destroyContext:");

        if (!mEgl.eglDestroyContext(mEglDisplay, mEglContext)) {
            Log.e("destroyContext", "display:" + mEglDisplay + " context: " + mEglContext);
            Log.e(TAG, "eglDestroyContex:" + mEgl.eglGetError());
        }
        mEglContext = EGL10.EGL_NO_CONTEXT;
    }

    private final EGLSurface createWindowSurface(final Object nativeWindow) {
//		if (DEBUG) Log.v(TAG, "createWindowSurface:nativeWindow=" + nativeWindow);

		if (!(nativeWindow instanceof Surface) && !(nativeWindow instanceof SurfaceTexture) && !(nativeWindow instanceof SurfaceHolder))
			throw new RuntimeException("unsupported window type");

		final int[] surfaceAttribs = {
                EGL10.EGL_NONE
        };
		EGLSurface result = null;
		try {
			result = mEgl.eglCreateWindowSurface(mEglDisplay, mEglConfig, nativeWindow, surfaceAttribs);
            if (result == null || result == EGL10.EGL_NO_SURFACE) {
                final int error = mEgl.eglGetError();
                if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                    Log.e(TAG, "createWindowSurface returned EGL_BAD_NATIVE_WINDOW.");
                }
                throw new RuntimeException("createWindowSurface failed error=" + error);
            }
            makeCurrent(result);
			// 画面サイズ・フォーマットの取得
		} catch (final IllegalArgumentException e) {
			Log.e(TAG, "eglCreateWindowSurface", e);
		}
		return result;
	}

    private final int getSurfaceWidth(final EGLSurface surface) {
        final int[] value = new int[1];
    	final boolean ret = mEgl.eglQuerySurface(mEglDisplay, surface, EGL10.EGL_WIDTH, value);
    	if (!ret) value[0] = 0;
   		return value[0];
    }

    private final int getSurfaceHeight(final EGLSurface surface) {
        final int[] value = new int[1];
    	final boolean ret = mEgl.eglQuerySurface(mEglDisplay, surface, EGL10.EGL_HEIGHT, value);
    	if (!ret) value[0] = 0;
   		return value[0];
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    private final EGLSurface createOffscreenSurface(final int width, final int height) {
//		if (DEBUG) Log.v(TAG, "createOffscreenSurface:");
        final int[] surfaceAttribs = {
                EGL10.EGL_WIDTH, width,
                EGL10.EGL_HEIGHT, height,
                EGL10.EGL_NONE
        };
        mEgl.eglWaitGL();
		EGLSurface result = null;
		try {
			result = mEgl.eglCreatePbufferSurface(mEglDisplay, mEglConfig, surfaceAttribs);
	        checkEglError("eglCreatePbufferSurface");
	        if (result == null) {
	            throw new RuntimeException("surface was null");
	        }
		} catch (final IllegalArgumentException e) {
			Log.e(TAG, "createOffscreenSurface", e);
		} catch (final RuntimeException e) {
			Log.e(TAG, "createOffscreenSurface", e);
		}
		return result;
    }

	private final void destroyWindowSurface(EGLSurface surface) {
//		if (DEBUG) Log.v(TAG, "destroySurface:");

        if (surface != EGL10.EGL_NO_SURFACE) {
        	mEgl.eglMakeCurrent(mEglDisplay,
        		EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        	mEgl.eglDestroySurface(mEglDisplay, surface);
        }
        surface = EGL10.EGL_NO_SURFACE;
//        if (DEBUG) Log.v(TAG, "destroySurface:finished");
	}

    private final void checkEglError(final String msg) {
        int error;
        if ((error = mEgl.eglGetError()) != EGL10.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    @SuppressWarnings("unused")
	private final EGLConfig getConfig(final int version, final boolean has_depth_buffer, final boolean isRecordable/*, boolean dirtyRegions*/) {
        int renderableType = EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGL_OPENGL_ES3_BIT_KHR;
        }
//		final int swapBehavior = dirtyRegions ? EGL_SWAP_BEHAVIOR_PRESERVED_BIT : 0;
        final int[] attribList = {
        	EGL10.EGL_RENDERABLE_TYPE, renderableType,
			EGL10.EGL_RED_SIZE, 8,
			EGL10.EGL_GREEN_SIZE, 8,
        	EGL10.EGL_BLUE_SIZE, 8,
        	EGL10.EGL_ALPHA_SIZE, 8,
//        	EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT | swapBehavior,
        	EGL10.EGL_NONE, EGL10.EGL_NONE,	//EGL10.EGL_STENCIL_SIZE, 8,
        	EGL10.EGL_NONE, EGL10.EGL_NONE,	// EGL_RECORDABLE_ANDROID, 1,	// this flag need to recording of MediaCodec
        	EGL10.EGL_NONE,	EGL10.EGL_NONE,	// with_depth_buffer ? EGL10.EGL_DEPTH_SIZE : EGL10.EGL_NONE,
											// with_depth_buffer ? 16 : 0,
			EGL10.EGL_NONE
        };
        int offset = 10;
        if (false) {				// ステンシルバッファ(常時未使用)
        	attribList[offset++] = EGL10.EGL_STENCIL_SIZE;
        	attribList[offset++] = 8;
        }
        if (has_depth_buffer) {	// デプスバッファ
        	attribList[offset++] = EGL10.EGL_DEPTH_SIZE;
        	attribList[offset++] = 16;
        }
        if (isRecordable && (BuildCheck.isAndroid4_3())) {	// MediaCodecの入力用Surfaceの場合
        	attribList[offset++] = EGL_RECORDABLE_ANDROID;	// A-1000F(Android4.1.2)はこのフラグをつけるとうまく動かない
        	attribList[offset++] = 1;
        }
        for (int i = attribList.length - 1; i >= offset; i--) {
        	attribList[i] = EGL10.EGL_NONE;
        }
        final EGLConfig[] configs = new EGLConfig[1];
        final int[] numConfigs = new int[1];
        if (!mEgl.eglChooseConfig(mEglDisplay, attribList, configs, configs.length, numConfigs)) {
        	// XXX it will be better to fallback to RGB565
            Log.w(TAG, "unable to find RGBA8888:");
            return null;
        }
        return configs[0];
    }
}
