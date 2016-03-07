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

import android.opengl.GLES20;
import android.util.Log;

import com.serenegiant.glutils.FullFrameRect;
import com.serenegiant.glutils.Texture2dProgram;
import com.serenegiant.glutils.TextureOffscreen;

/** Cannyエッジ検出フィルタ */
public class MediaEffectCanny implements IEffect {
	private static final boolean DEBUG = true;
	private static final String TAG = "MediaEffectCanny";

	private FullFrameRect mDrawer;
	private TextureOffscreen mOutputOffscreen;

	private static final String FRAGMENT_SHADER_BASE = Texture2dProgram.SHADER_VERSION +
		"%s" +
		"#define KERNEL_SIZE " + Texture2dProgram.KERNEL_SIZE + "\n" +
		"precision highp float;\n" +
		"varying       vec2 vTextureCoord;\n" +
		"uniform %s    sTexture;\n" +
		"uniform float uKernel[18];\n" +
		"uniform vec2  uTexOffset[KERNEL_SIZE];\n" +
		"uniform float uColorAdjust;\n" +
		"const float texelWidth = 1.0 / 640.0;\n" +
		"const float texelHeight = 1.0 / 368.0;\n" +
		"const float lowerThreshold = 0.4;\n" +	// lowerとupperの値を入れ替えると白黒反転する
		"const float upperThreshold = 0.8;\n" +
		"void main() {\n" +
		"    vec3 currentGradientAndDirection = texture2D(sTexture, vTextureCoord).rgb;\n" +
		"    vec2 gradientDirection = ((currentGradientAndDirection.gb * 2.0) - 1.0) * vec2(texelWidth, texelHeight);\n" +
		"    float firstSampledGradientMagnitude = texture2D(sTexture, vTextureCoord + gradientDirection).r;\n" +
		"    float secondSampledGradientMagnitude = texture2D(sTexture, vTextureCoord - gradientDirection).r;\n" +
		"    float multiplier = step(firstSampledGradientMagnitude, currentGradientAndDirection.r);\n" +
		"    multiplier = multiplier * step(secondSampledGradientMagnitude, currentGradientAndDirection.r);\n" +
		"    float thresholdCompliance = smoothstep(lowerThreshold, upperThreshold, currentGradientAndDirection.r);\n" +
		"    multiplier = multiplier * thresholdCompliance;\n" +
		"    gl_FragColor = vec4(multiplier, multiplier, multiplier, 1.0);\n" +
		"}\n";
//		"void main() {\n" +
//		"    vec4 magdir = texture2D(sTexture, vTextureCoord);\n" +
//		"    float a = 0.5 / sin(3.14159 / 8.0); \n" +	// eight directions on grid
//		"    vec2 alpha = vec2(a);\n" +
//		"    vec2 offset = floor(alpha.xx * magdir.xy / magdir.zz);\n" +
//		"    vec4 fwdneighbour, backneighbour;\n" +
//		"    fwdneighbour = texture2D(sTexture, vTextureCoord + offset);\n" +
//		"    backneighbour = texture2D(sTexture, vTextureCoord + offset);\n" +
//		"    vec4 colorO;\n" +
//		"    if (fwdneighbour.z > magdir.z || backneighbour.z > magdir.z)\n" +
//		"        colorO = vec4(0.0, 0.0, 0.0, 0.0);\n" +	// not an edgel
//		"    else\n" +
//		"        colorO = vec4(1.0, 1.0, 1.0, 1.0);\n" +	// is an edgel
//		"    if (magdir.z < uColorAdjust)\n" +
//		"        colorO  = vec4(0.0, 0.0, 0.0, 0.0);\n" +	// thresholding
//		"    gl_FragColor = colorO;\n" +
//		"}\n";
//----
//		"const float texWidth  = 1.0 / 640.0;\n" +
//		"const float texHeight = 1.0 / 368.0;\n" +
//		"const float threshold = 0.2;\n" +
//		"const vec2 unshift = vec2(1.0 / 256.0, 1.0);\n" +
//		"const float atan0   = 0.414213;\n" +
//		"const float atan45  = 2.414213;\n" +
//		"const float atan90  = -2.414213;\n" +
//		"const float atan135 = -0.414213;\n" +
//		"vec2 atanForCanny(float x) {\n" +
//		"    if (x < atan0 && x > atan135) {\n" +
//		"        return vec2(1.0, 0.0);\n" +
//		"    }\n" +
//		"    if (x < atan90 && x > atan45) {\n" +
//		"        return vec2(0.0, 1.0);\n" +
//		"    }\n" +
//		"    if (x > atan135 && x < atan90) {\n" +
//		"        return vec2(-1.0, 1.0);\n" +
//		"    }\n" +
//		"    return vec2(1.0, 1.0);\n" +
//		"}\n" +
//		"vec4 cannyEdge(vec2 coords) {\n" +
//		"    vec4 color = texture2D(sTexture, coords);\n" +
//		"    color.z = dot(color.zw, unshift);\n" +
//		"    if (color.z > threshold) {\n" +
//		"        color.x -= 0.5;\n" +
//		"        color.y -= 0.5;\n" +
//		"        vec2 offset = atanForCanny(color.y / color.x);\n" +
//		"        offset.x *= texWidth;\n" +
//		"        offset.y *= texHeight;\n" +
//		"        vec4 forward  = texture2D(sTexture, coords + offset);\n" +
//		"        vec4 backward = texture2D(sTexture, coords - offset);\n" +
//		"        forward.z  = dot(forward.zw, unshift);\n" +
//		"        backward.z = dot(backward.zw, unshift);\n" +
//		"        if (forward.z >= color.z ||\n" +
//		"            backward.z >= color.z) {\n" +
//		"            return vec4(0.0, 0.0, 0.0, 1.0);\n" +
//		"        } else {\n" +
//		"            color.x += 0.5; color.y += 0.5;\n" +
//		"            return vec4(1.0, color.x, color.y, 1.0);\n" +
//		"        }\n" +
//		"    }\n" +
//		"    return vec4(0.0, 0.0, 0.0, 1.0);\n" +
//		"}\n" +
//		"void main() {\n" +
//		"    gl_FragColor = cannyEdge(vTextureCoord);\n" +
//		"}\n";
	private static final String FRAGMENT_SHADER
		= String.format(FRAGMENT_SHADER_BASE, Texture2dProgram.HEADER_2D, Texture2dProgram.SAMPLER_2D);
	private static final String FRAGMENT_SHADER_EXT
		= String.format(FRAGMENT_SHADER_BASE, Texture2dProgram.HEADER_OES, Texture2dProgram.SAMPLER_OES);

	public MediaEffectCanny() {
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		mDrawer = new FullFrameRect(new Texture2dProgram(GLES20.GL_TEXTURE_2D, FRAGMENT_SHADER));
	}

	public MediaEffectCanny(final float threshold) {
		this();
		setParameter(threshold);
	}

	/**
	 * If you know the source texture came from MediaSource,
	 * using #apply(MediaSource) is much efficient instead of this
	 * @param src_tex_ids
	 * @param width
	 * @param height
	 * @param out_tex_id
	 */
	@Override
	public void apply(final int [] src_tex_ids, final int width, final int height, final int out_tex_id) {
		if (mOutputOffscreen == null) {
			mOutputOffscreen = new TextureOffscreen(width, height, false);
		}
		if ((out_tex_id != mOutputOffscreen.getTexture())
			|| (width != mOutputOffscreen.getWidth())
			|| (height != mOutputOffscreen.getHeight())) {
			mOutputOffscreen.assignTexture(out_tex_id, width, height);
		}
		mOutputOffscreen.bind();
		mDrawer.draw(src_tex_ids[0], mOutputOffscreen.getTexMatrix(), 0);
		mOutputOffscreen.unbind();
	}

	@Override
	public void apply(final ISource src) {
		if (src instanceof MediaSource) {
			final TextureOffscreen output_tex = ((MediaSource)src).getOutputTexture();
			final int[] src_tex_ids = src.getSourceTexId();
			output_tex.bind();
			mDrawer.draw(src_tex_ids[0], output_tex.getTexMatrix(), 0);
			output_tex.unbind();
		} else {
			apply(src.getSourceTexId(), src.getWidth(), src.getHeight(), src.getOutputTexId());
		}
	}

	@Override
	public void release() {
		if (DEBUG) Log.v(TAG, "release:");
		if (mDrawer != null) {
			mDrawer.release();
			mDrawer = null;
		}
		if (mOutputOffscreen != null) {
			mOutputOffscreen.release();
			mOutputOffscreen = null;
		}
	}

	public MediaEffectCanny setParameter(final float threshold) {
		mDrawer.getProgram().setColorAdjust(threshold);
		return this;
	}

	public MediaEffectCanny resize(final int width, final int height) {
		if ((mOutputOffscreen == null) || (width != mOutputOffscreen.getWidth())
			|| (height != mOutputOffscreen.getHeight())) {
			if (mOutputOffscreen != null)
				mOutputOffscreen.release();
			mOutputOffscreen = new TextureOffscreen(width, height, false);
		}
		mDrawer.getProgram().setTexSize(width, height);
		return this;
	}
}
