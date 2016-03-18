package com.serenegiant.mediaeffect;

import com.serenegiant.glutils.TextureOffscreen;

public class MediaEffectGLESTwoPassBase extends MediaEffectGLESBase {

	protected final MediaEffectKernelDrawer mDrawer2;
	protected TextureOffscreen mOutputOffscreen2;

	public MediaEffectGLESTwoPassBase(final boolean isOES, final String fss) {
		super(isOES, fss);
		mDrawer2 = null;
	}

	public MediaEffectGLESTwoPassBase(final String vss, final String fss) {
		super(false, vss, fss);
		mDrawer2 = null;
	}

	public MediaEffectGLESTwoPassBase(final boolean isOES, final String vss, final String fss) {
		super(isOES, vss, fss);
		mDrawer2 = null;
	}

	public MediaEffectGLESTwoPassBase(final boolean isOES, final String vss1, final String fss1, final String vss2, final String fss2) {
		super(isOES, vss1, fss1);
		if (!vss1.equals(vss2) || !fss1.equals(fss2)) {
			mDrawer2 = new MediaEffectKernelDrawer(isOES, vss2, fss2);
		} else {
			mDrawer2 = null;
		}
	}

	@Override
	public void release() {
		if (mDrawer2 != null) {
			mDrawer2.release();
		}
		if (mOutputOffscreen2 != null) {
			mOutputOffscreen2.release();
			mOutputOffscreen2 = null;
		}
		super.release();
	}

	@Override
	public MediaEffectGLESBase resize(final int width, final int height) {
		super.resize(width, height);
		if ((mOutputOffscreen2 == null) || (width != mOutputOffscreen2.getWidth())
			|| (height != mOutputOffscreen2.getHeight())) {
			if (mOutputOffscreen2 != null)
				mOutputOffscreen2.release();
			mOutputOffscreen2 = new TextureOffscreen(width, height, false);
		}
		if (mDrawer2 != null) {
			mDrawer2.setTexSize(width, height);
		}
		return this;
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
		if (!mEnabled) return;
		// パス1
		if (mOutputOffscreen == null) {
			mOutputOffscreen = new TextureOffscreen(width, height, false);
		}
		mOutputOffscreen.bind();
		internal_apply(mDrawer, src_tex_ids[0], mOutputOffscreen.getTexMatrix(), 0);
		mOutputOffscreen.unbind();

		if (mOutputOffscreen2 == null) {
			mOutputOffscreen2 = new TextureOffscreen(width, height, false);
		}
		// パス2
		if ((out_tex_id != mOutputOffscreen2.getTexture())
			|| (width != mOutputOffscreen2.getWidth())
			|| (height != mOutputOffscreen2.getHeight())) {
			mOutputOffscreen2.assignTexture(out_tex_id, width, height);
		}
		mOutputOffscreen2.bind();
		if (mDrawer2 != null) {
			internal_apply(mDrawer2, mOutputOffscreen.getTexture(), mOutputOffscreen2.getTexMatrix(), 0);
		} else {
			internal_apply(mDrawer, mOutputOffscreen.getTexture(), mOutputOffscreen2.getTexMatrix(), 0);
		}
		mOutputOffscreen2.unbind();
	}

	@Override
	public void apply(final ISource src) {
		if (!mEnabled) return;
		if (src instanceof MediaSource) {
			final TextureOffscreen output_tex = ((MediaSource)src).getOutputTexture();
			final int[] src_tex_ids = src.getSourceTexId();
			final int width = src.getWidth();
			final int height = src.getHeight();
			// パス1
			if (mOutputOffscreen == null) {
				mOutputOffscreen = new TextureOffscreen(width, height, false);
			}
			mOutputOffscreen.bind();
			internal_apply(mDrawer, src_tex_ids[0], mOutputOffscreen.getTexMatrix(), 0);
			mOutputOffscreen.unbind();
			// パス2
			output_tex.bind();
			if (mDrawer2 != null) {
				internal_apply(mDrawer2, mOutputOffscreen.getTexture(), output_tex.getTexMatrix(), 0);
			} else {
				internal_apply(mDrawer, mOutputOffscreen.getTexture(), output_tex.getTexMatrix(), 0);
			}
			output_tex.unbind();
		} else {
			apply(src.getSourceTexId(), src.getWidth(), src.getHeight(), src.getOutputTexId());
		}
	}
}