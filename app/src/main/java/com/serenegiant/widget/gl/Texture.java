package com.serenegiant.widget.gl;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.opengles.GL10;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLUtils;
import android.support.v4.util.LruCache;
import android.text.TextUtils;

import com.serenegiant.glutils.GLHelper;

public class Texture {
	private static final boolean DEBUG = true;	// FIXME 実働時はfalseにすること
	private static final String TAG = "Vertex";

	private static final int LOAD_NON = -1;
	private static final int LOAD_ASSET = 0;
	private static final int LOAD_FILE = 1;
	private static final int LOAD_EXTFILE = 2;
	
	public int width;
	public int height;
	protected String mFileName;
	
	private GLGraphics glGraphics;
	private FileIO assetIO, fileIO, extFileIO;
	private boolean mMipmapped;
	private int textureID;
	private int mMinFilter;
	private int mMagFilter;
	private int loadType = LOAD_ASSET;
	private int maxMipmappedLevel;
	
	// bitmap用のメモリキャッシュ, keyはファイル名+ミップマップのレベル, 大本のビットマップはキャッシュしない
	// メモリキャッシュは全てのTextureで共有する
	private static LruCache<String, Bitmap> mMemoryCache;
	
	// テクスチャは2の乗数サイズでないとだめ
	// ミップマップするなら正方形でないとだめ
	public Texture(final IModelView modelView, final String fileName) {
		this(modelView, fileName, false);
	}

	/**
	 * TextureのコンストラクタはGLスレッドから呼び出さないとダメみたい
	 * @param modelView
	 * @param fileName
	 * @param mipmapped
	 */
	public Texture(final IModelView modelView, final String fileName, final boolean mipmapped) {
		glGraphics = modelView.getGLGraphics();
		assetIO = modelView.getAssetIO();			// アセットからの読み込み用
		fileIO = modelView.getFileIO();			// 内部ストレージからの読み込み用
		extFileIO = modelView.getExtFileIO();		// 外部ストレージからの読み込み用
		mFileName = fileName;
		mMipmapped = mipmapped;
		if (mMemoryCache == null) {
			// キャッシュ用のメモリサイズを計算する
			final int memClass = ((ActivityManager)modelView.getContext().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
			// Use 1/8th of the available memory for this memory cache.
			final int cacheSize = 1024 * 1024 * memClass / 8;	// [MB]
			// メモリキャッシュを生成
			mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
				@Override
				protected int sizeOf(String key, Bitmap bitmap) {
					// The cache size will be measured in bytes rather than number // of items.
					 return bitmap.getRowBytes() * bitmap.getHeight(); 	// return bitmap.getByteCount();
				}
			};
		}
		if (!TextUtils.isEmpty(fileName))
			load();
	}
	
	public void load(String fileName) {
		mFileName = fileName;
		load();
	}
	
	protected void load() {
		if (!internal_load(LOAD_ASSET)) {
			if (!internal_load(LOAD_FILE)) {
				if (!internal_load(LOAD_EXTFILE)) {
					throw new RuntimeException("couldn't load texture '" + mFileName + "'");
				}
			}
		}		
	}
	
	private boolean internal_load(int loadType) {	
		InputStream in = null;
		try {
//			Bitmap bitmap = getMemoryCache(mFileName);
//			if (bitmap == null) {
//				Log.i(TAG, "cache miss");
				switch (loadType) {
				case LOAD_ASSET:
					if (!assetIO.fileExists(mFileName)) return false;
					in = assetIO.readFile(mFileName);
					break;
				case LOAD_FILE:
					if (!fileIO.fileExists(mFileName)) return false;
					in = fileIO.readFile(mFileName);
					break;
				case LOAD_EXTFILE:
					// TODO パーミッションが無い時は試みないようにする?
					if (!extFileIO.fileExists(mFileName)) return false;
					in = extFileIO.readFile(mFileName);
					break;
				default:
					return false;
				}
				Bitmap bitmap = BitmapFactory.decodeStream(in);
//				putMemoryCache(mFileName, bitmap);	// 元ファイルは大きいのでキャッシュしない
//			}
			width = bitmap.getWidth();
			height = bitmap.getHeight();
			
			final GL10 gl = glGraphics.getGL();
			final int[] textureIDs = new int[1];
			gl.glGenTextures(1, textureIDs, 0);
			textureID = textureIDs[0];
			
			if (mMipmapped) {	// ミップマップする時
				createMipmaps(gl, bitmap);
			} else {			// ミップマップしない時
				gl.glBindTexture(GL10.GL_TEXTURE_2D, textureID);
				GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
				GLHelper.checkGlError(gl, "Texture#texImage2D");
				setFilters(gl, GL10.GL_NEAREST, GL10.GL_NEAREST);
				gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);
				GLHelper.checkGlError(gl, "Texture#glBindTexture");
			}
			bitmap.recycle();
			bitmap = null;	// 2013/05/24
//			System.gc();	// 2013/05/24
			this.loadType = loadType;
			return true;
		} catch (IOException e) {
			this.loadType = LOAD_NON;
			return false;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// 何もしない
				}
			}
		}
	}
	
	/**
	 * ミップマップ用のビットマップを生成してテクスチャとして割り当てる
	 * @param gl
	 * @param bitmap
	 */
	private void createMipmaps(final GL10 gl, Bitmap bitmap) {
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textureID);
		setFilters(gl, GL10.GL_LINEAR_MIPMAP_NEAREST, GL10.GL_LINEAR);
		
		int level = 0;
		int newWidth = width;
		int newHeight = height;
		Bitmap newBitmap;
		while (true) {
			GLUtils.texImage2D(GL10.GL_TEXTURE_2D, level, bitmap, 0);
			GLHelper.checkGlError(gl, "Texture#texImage2D");
			newWidth /= 2;
			newHeight /= 2;
			if ((newWidth <= 0) || (newHeight <= 0))
				break;
			newBitmap = getMemoryCache(mFileName + level);
			if (newBitmap == null) {
//				Log.i(TAG, "cache miss");
				newBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
				putMemoryCache(mFileName + level, newBitmap);	// ミップマップ用のビットマップはメモリキャッシュに入れる
			}
/*			final Bitmap newBitmap = Bitmap.createBitmap(newWidth, newHeight, bitmap.getConfig());
			final Canvas canvas = new Canvas(newBitmap);
			canvas.drawBitmap(bitmap,
				new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
				new Rect(0, 0, newWidth, newHeight), null); */
//			bitmap.recycle();	// メモリキャッシュに入れたのでリサイクルしちゃダメ
			bitmap = newBitmap;
			level++;
		}
		maxMipmappedLevel = level;
		gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);
		GLHelper.checkGlError(gl, "Texture#glBindTexture");
	}
	
	public void reload() {
		internal_load(loadType);
		final GL10 gl = glGraphics.getGL();
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textureID);
		GLHelper.checkGlError(gl, "Texture#glBindTexture");
		setFilters(gl, mMinFilter, mMagFilter);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);
		GLHelper.checkGlError(gl, "Texture#glBindTexture");
	}
	
	private void setFilters(GL10 gl, int minFilter, int magFilter) {
		this.mMinFilter = minFilter;
		this.mMagFilter = magFilter;
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, minFilter);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, magFilter);
		GLHelper.checkGlError(gl, "Texture#glTexParameterf");
	}
	
	public void bind() {
		glGraphics.getGL().glBindTexture(GL10.GL_TEXTURE_2D, textureID);
	}
	
	public void dispose() {
		final GL10 gl = glGraphics.getGL();
		gl.glBindTexture(GL10.GL_TEXTURE_2D, 0);	// gl.glBindTexture(GL10.GL_TEXTURE_2D, textureID);
		final int[] textureIDs = { textureID };
		gl.glDeleteTextures(1, textureIDs, 0);
		removeMemoryCache();
		glGraphics = null;
		assetIO = null;
		fileIO = null;
		extFileIO = null;
	}

	/**
	 * ビットマップをメモリキャッシュに入れる
	 * @param key
	 * @param bitmap
	 */
	private void putMemoryCache(String key, Bitmap bitmap) {
		mMemoryCache.put(key, bitmap);
	}
	
	/**
	 * メモリキャッシュからビットマップを取得する
	 * メモリキャッシュに存在しなければnullが返る
	 * @param key
	 * @return
	 */
	private Bitmap getMemoryCache(String key) {
		return mMemoryCache.get(key);
	}
	
	/**
	 * メモリキャッシュからこのインスタンスに関係するビットマップを除去する
	 */
	private void removeMemoryCache() {
		Bitmap bitmap;
		if (mMipmapped) {
			for (int i = 0; i < maxMipmappedLevel; i++) {
				bitmap = mMemoryCache.remove(mFileName + i);
				try {
					if (bitmap != null)
						bitmap.recycle();
				} catch (Exception e) {
				}
			}
		}
	}
}