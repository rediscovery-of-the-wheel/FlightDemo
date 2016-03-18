package com.serenegiant.mediaeffect;
/*
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: MediaEffectKernel.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.util.Log;

import com.serenegiant.glutils.Texture2dProgram;

/** 露出調整, -10〜+10, 0だと無調整 */
public class MediaEffectExposure extends MediaEffectGLESBase {
	private static final boolean DEBUG = false;
	private static final String TAG = "MediaEffectExposure";

	private static final String FRAGMENT_SHADER_BASE = Texture2dProgram.SHADER_VERSION +
		"%s" +
		"precision highp float;\n" +
		"varying       vec2 vTextureCoord;\n" +
		"uniform %s    sTexture;\n" +
		"uniform float uColorAdjust;\n" +
		"void main() {\n" +
		"    highp vec4 tex = texture2D(sTexture, vTextureCoord);\n" +
		"    gl_FragColor = vec4(tex.rgb * pow(2.0, uColorAdjust), tex.w);\n" +
		"}\n";
	private static final String FRAGMENT_SHADER
		= String.format(FRAGMENT_SHADER_BASE, Texture2dProgram.HEADER_2D, Texture2dProgram.SAMPLER_2D);
	private static final String FRAGMENT_SHADER_EXT
		= String.format(FRAGMENT_SHADER_BASE, Texture2dProgram.HEADER_OES, Texture2dProgram.SAMPLER_OES);

	public MediaEffectExposure() {
		super(new MediaEffectColorAdjustDrawer(FRAGMENT_SHADER));
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
	}

	public MediaEffectExposure(final float exposure) {
		this();
		setParameter(exposure);
	}

	/**
	 * 露出調整
	 * @param exposure -10〜+10, 0は無調整
	 * @return
	 */
	public MediaEffectExposure setParameter(final float exposure) {
		synchronized (mSync) {
			((MediaEffectColorAdjustDrawer)mDrawer).setColorAdjust(exposure);
		}
		return this;
	}

}