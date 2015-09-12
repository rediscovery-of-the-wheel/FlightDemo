package com.serenegiant.flightdemo;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import com.serenegiant.media.MediaStoreAdapter;

public class GalleyFragment extends Fragment {
	private static final boolean DEBUG = true;	// FIXME 実働時はfalseにすること
	private static String TAG = GalleyFragment.class.getSimpleName();

	public static GalleyFragment newInstance() {
		GalleyFragment fragment = new GalleyFragment();
		return fragment;
	}

	private GridView mGalleyGridView;
	private MediaStoreAdapter mMediaStoreAdapter;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		final View rootView = inflater.inflate(R.layout.fragment_galley, container, false);
		initView(rootView);
		return rootView;
	}

	/**
	 * Viewを初期化
	 * @param rootView
	 */
	private void initView(final View rootView) {
		mGalleyGridView = (GridView)rootView.findViewById(R.id.media_gridview);
		mMediaStoreAdapter = new MediaStoreAdapter(getActivity(), R.layout.grid_item_media);
		mGalleyGridView.setAdapter(mMediaStoreAdapter);
		mGalleyGridView.setOnItemClickListener(mOnItemClickListener);
	}

	private final AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
			switch (parent.getId()) {
			case R.id.media_gridview:
				doPlay(position, id);
				break;
			}
		}
	};

	private void doPlay(final int position, final long id) {
		final MediaStoreAdapter.MediaInfo info = mMediaStoreAdapter.getMediaInfo(position);
		if (DEBUG) Log.v(TAG, "" + info);
		final Fragment fragment;
		switch (info.mediaType) {
		case MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE:
			fragment = null;	// FIXME 未実装
			break;
		case MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO:
//			fragment = PlayerFragment.newInstance(info.data);
			fragment = PlayerFragment2.newInstance(info.data);
			break;
		default:
			fragment = null;
			break;
		}
		if (fragment != null) {
			getFragmentManager().beginTransaction()
				.addToBackStack(null)
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
				.replace(R.id.container, fragment).commit();
		}
	}
}
