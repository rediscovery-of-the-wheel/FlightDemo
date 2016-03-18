package com.serenegiant.glutils;

/*
 * Copyright (c) 2014 saki t_saki@serenegiant.com
 *
 * File name: GLDrawer2D.java
 *
*/

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 描画領域全面にテクスチャを2D描画するためのヘルパークラス
 */
public class GLDrawer2D {
//	private static final boolean DEBUG = false; // FIXME set false on release
//	private static final String TAG = "GLDrawer2D";

    public static final int GL_TEXTURE_EXTERNAL_OES	= 0x8D65;
    public static final int GL_TEXTURE_2D           = 0x0DE1;

	private static final String VERTEX_SHADER
		= "uniform mat4 uMVPMatrix;\n"
		+ "uniform mat4 uTexMatrix;\n"
		+ "attribute highp vec4 aPosition;\n"
		+ "attribute highp vec4 aTextureCoord;\n"
		+ "varying highp vec2 vTextureCoord;\n"
		+ "\n"
		+ "void main() {\n"
		+ "	gl_Position = uMVPMatrix * aPosition;\n"
		+ "	vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n"
		+ "}\n";
	private static final String FRAGMENT_SHADER_OES
		= "#extension GL_OES_EGL_image_external : require\n"
		+ "precision mediump float;\n"
		+ "uniform samplerExternalOES sTexture;\n"
		+ "varying highp vec2 vTextureCoord;\n"
		+ "void main() {\n"
		+ "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
		+ "}";
	private static final String FRAGMENT_SHADER
		= "precision mediump float;\n"
		+ "uniform sampler2D sTexture;\n"
		+ "varying highp vec2 vTextureCoord;\n"
		+ "void main() {\n"
		+ "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
		+ "}";
	private static final float[] VERTICES = { 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f };
	private static final float[] TEXCOORD = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };

	private final FloatBuffer pVertex;
	private final FloatBuffer pTexCoord;
	private final int mTexTarget;
	private int hProgram;
    int maPositionLoc;
    int maTextureCoordLoc;
    int muMVPMatrixLoc;
    int muTexMatrixLoc;
	private final float[] mMvpMatrix = new float[16];

	private static final int FLOAT_SZ = Float.SIZE / 8;
	private static final int VERTEX_NUM = 4;
	private static final int VERTEX_SZ = VERTEX_NUM * 2;
	/**
	 * コンストラクタ
	 * GLコンテキスト/EGLレンダリングコンテキストが有効な状態で呼ばないとダメ
	 * @param isOES 外部テクスチャ(GL_TEXTURE_EXTERNAL_OES)を使う場合はtrue。通常の2Dテキスチャならfalse
	 */
	public GLDrawer2D(final boolean isOES) {
		mTexTarget = isOES ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
		pVertex = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pVertex.put(VERTICES);
		pVertex.flip();
		pTexCoord = ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		pTexCoord.put(TEXCOORD);
		pTexCoord.flip();

		if (isOES)
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER_OES);
		else
			hProgram = GLHelper.loadShader(VERTEX_SHADER, FRAGMENT_SHADER);
		GLES20.glUseProgram(hProgram);
        maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uTexMatrix");
        // モデルビュー変換行列を初期化
		Matrix.setIdentityM(mMvpMatrix, 0);
		//
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mMvpMatrix, 0);
		GLES20.glVertexAttribPointer(maPositionLoc, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, pVertex);
		GLES20.glVertexAttribPointer(maTextureCoordLoc, 2, GLES20.GL_FLOAT, false, VERTEX_SZ, pTexCoord);
		GLES20.glEnableVertexAttribArray(maPositionLoc);
		GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
	}

	/**
	 * 破棄処理。GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 */
	public void release() {
		if (hProgram >= 0)
			GLES20.glDeleteProgram(hProgram);
		hProgram = -1;
	}

	/**
	 * モデルビュー変換行列を取得(内部配列を直接返すので変更時は要注意)
	 * @return
	 */
	public float[] getMvpMatrix() {
		return mMvpMatrix;
	}

	/**
	 * モデルビュー変換行列に行列を割り当てる
	 * @param matrix 領域チェックしていないのでoffsetから16個以上必須
	 * @param offset
	 * @return
	 */
	public GLDrawer2D setMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(matrix, offset, mMvpMatrix, 0, mMvpMatrix.length);
		return this;
	}

	/**
	 * モデルビュー変換行列のコピーを取得
	 * @param matrix 領域チェックしていないのでoffsetから16個以上必須
	 * @param offset
	 */
	public void getMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mMvpMatrix, 0, matrix, offset, mMvpMatrix.length);
	}

	/**
	 * 指定したテクスチャを指定したテクスチャ変換行列を使って描画領域全面に描画するためのヘルパーメソッド
	 * このクラスインスタンスのモデルビュー変換行列が設定されていればそれも適用された状態で描画する
	 * @param tex_id texture ID
	 * @param tex_matrix テクスチャ変換行列、nullならば以前に適用したものが再利用される.領域チェックしていないのでoffsetから16個以上確保しておくこと
	 */
	public void draw(final int tex_id, final float[] tex_matrix, final int offset) {
//		if (DEBUG) Log.v(TAG, "draw");
		GLES20.glUseProgram(hProgram);
		if (tex_matrix != null)
			GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, offset);
		GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0);
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(mTexTarget, tex_id);
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_NUM);
		GLES20.glBindTexture(mTexTarget, 0);
        GLES20.glUseProgram(0);
	}

	/**
	 * Textureオブジェクトを描画するためのヘルパーメソッド
	 * Textureオブジェクトで管理しているテクスチャ名とテクスチャ座標変換行列を使って描画する
	 * @param texture
	 */
	public void draw(final GLTexture texture) {
		draw(texture.mTextureId, texture.mTexMatrix, 0);
	}

	/**
	 * TextureOffscreenオブジェクトを描画するためのヘルパーメソッド
	 * @param offscreen
	 */
	public void draw(final TextureOffscreen offscreen) {
		draw(offscreen.getTexture(), offscreen.getTexMatrix(), 0);
	}

	/**
	 * テクスチャ名生成のヘルパーメソッド。GLHelper#initTexを呼び出す
	 * @return texture ID
	 */
	public int initTex() {
		return GLHelper.initTex(mTexTarget, GLES20.GL_NEAREST);
	}

	public void deleteTex(final int htex) {
		GLHelper.deleteTex(htex);
	}
}
