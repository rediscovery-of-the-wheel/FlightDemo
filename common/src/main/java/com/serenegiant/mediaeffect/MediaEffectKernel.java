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

import com.serenegiant.glutils.FullFrameRect;
import com.serenegiant.glutils.Texture2dProgram;
import com.serenegiant.glutils.TextureOffscreen;

public class MediaEffectKernel implements IEffect {
	private static final boolean DEBUG = true;
	private static final String TAG = "MediaEffectKernel";

	private FullFrameRect mDrawer;
	private TextureOffscreen mOutputOffscreen;

	public MediaEffectKernel() {
		mDrawer = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_FILT3x3));
	}

	public MediaEffectKernel(final float[] kernel) {
		this();
		setParameter(kernel, 0.0f);
	}

	public MediaEffectKernel(final float[] kernel, final float color_adjust) {
		this();
		setParameter(kernel, color_adjust);
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
		if (mDrawer != null) {
			mDrawer.release();
			mDrawer = null;
		}
		if (mOutputOffscreen != null) {
			mOutputOffscreen.release();
			mOutputOffscreen = null;
		}
	}

	public MediaEffectKernel setParameter(final float[] kernel, final float color_adjust) {
		if ((kernel == null) || (kernel.length < 9))
			throw new IllegalArgumentException("kernel should be 3x3");
		mDrawer.getProgram().setKernel(kernel, color_adjust);
		return this;
	}

	public MediaEffectKernel resize(final int width, final int height) {
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