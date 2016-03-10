package com.serenegiant.mediaeffect;
/*
 * Copyright (c) 2015 saki t_saki@serenegiant.com
 *
 * File name: MediaEffectPosterize.java
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

import com.serenegiant.glutils.Texture2dProgram;

/**
 * FIXME ポスタライズ, うまく動かない
 */
public class MediaEffectPosterize extends MediaEffectGLESBase {
	private static final boolean DEBUG = false;
	private static final String TAG = "MediaEffectBrightness";

	private static final String FRAGMENT_SHADER_BASE = Texture2dProgram.SHADER_VERSION +
		"%s" +
		"precision highp float;\n" +
		"varying       vec2 vTextureCoord;\n" +
		"uniform %s    sTexture;\n" +
		"uniform float uColorAdjust;\n" +
		"void main() {\n" +
		"    vec4 tex = texture2D(sTexture, vTextureCoord);\n" +
		"    gl_FragColor = floor((tex * uColorAdjust) + vec4(0.5)) / uColorAdjust;\n" +
		"}\n";
	private static final String FRAGMENT_SHADER
		= String.format(FRAGMENT_SHADER_BASE, Texture2dProgram.HEADER_2D, Texture2dProgram.SAMPLER_2D);
	private static final String FRAGMENT_SHADER_EXT
		= String.format(FRAGMENT_SHADER_BASE, Texture2dProgram.HEADER_OES, Texture2dProgram.SAMPLER_OES);

	public MediaEffectPosterize() {
		this(10.0f);
	}

	public MediaEffectPosterize(final float posterize) {
		super(FRAGMENT_SHADER);
		setParameter(posterize);
	}

	/**
	 * 階調レベルをセット
	 * @param posterize [1,256]
	 * @return
	 */
	public MediaEffectPosterize setParameter(final float posterize) {
		mDrawer.getProgram().setColorAdjust(posterize);
		return this;
	}
}
