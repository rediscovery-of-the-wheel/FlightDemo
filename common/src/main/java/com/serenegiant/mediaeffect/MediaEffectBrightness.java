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


/** 明るさ調整([-1.0f,+1.0f], RGB各成分に単純加算), 0だと無調整 */
public class MediaEffectBrightness extends MediaEffectGLESBase {
	private static final boolean DEBUG = false;
	private static final String TAG = "MediaEffectBrightness";

	private static final String FRAGMENT_SHADER_BASE = MediaEffectDrawer.SHADER_VERSION +
		"%s" +
		"precision highp float;\n" +
		"varying       vec2 vTextureCoord;\n" +
		"uniform %s    sTexture;\n" +
		"uniform float uColorAdjust;\n" +
		"void main() {\n" +
		"    highp vec4 tex = texture2D(sTexture, vTextureCoord);\n" +
		"    gl_FragColor = vec4(tex.rgb + vec3(uColorAdjust, uColorAdjust, uColorAdjust), tex.w);\n" +
		"}\n";
	private static final String FRAGMENT_SHADER
		= String.format(FRAGMENT_SHADER_BASE, MediaEffectDrawer.HEADER_2D, MediaEffectDrawer.SAMPLER_2D);
	private static final String FRAGMENT_SHADER_EXT
		= String.format(FRAGMENT_SHADER_BASE, MediaEffectDrawer.HEADER_OES, MediaEffectDrawer.SAMPLER_OES);

	public MediaEffectBrightness() {
		this(0.0f);
	}

	public MediaEffectBrightness(final float brightness) {
		super(new MediaEffectColorAdjustDrawer(FRAGMENT_SHADER));
		setParameter(brightness);
	}

	/**
	 * 露出調整
	 * @param brightness [-1.0f,+1.0f], RGB各成分に単純加算)
	 * @return
	 */
	public MediaEffectBrightness setParameter(final float brightness) {
		((MediaEffectColorAdjustDrawer)mDrawer).setColorAdjust(brightness);
		return this;
	}
}
