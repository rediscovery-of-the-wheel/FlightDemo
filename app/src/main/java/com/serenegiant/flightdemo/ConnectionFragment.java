package com.serenegiant.flightdemo;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.VideoView;

import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.serenegiant.arflight.ARDeviceServiceAdapter;
import com.serenegiant.arflight.ManagerFragment;
import com.serenegiant.gl.IModelView;
import com.serenegiant.widget.PlayerTextureView;

import java.io.IOException;
import java.util.List;

public class ConnectionFragment extends BaseFragment {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static String TAG = ConnectionFragment.class.getSimpleName();

	/**
	 * Use this factory method to create a new instance of
	 * this fragment using the provided parameters.
	 *
	 * @return A new instance of fragment PilotFragment.
	 */
	public static ConnectionFragment newInstance() {
		ConnectionFragment fragment = new ConnectionFragment();
		final Bundle args = new Bundle();
		fragment.setArguments(args);
		return fragment;
	}

	private ListView mDeviceListView;
//	private IModelView mModelView;
//	private VideoView mVideoView;
	private PlayerTextureView mVideoView;
	private ImageButton mDownloadBtn, mPilotBtn;
	private MediaPlayer mMediaPlayer;

	public ConnectionFragment() {
		super();
		// Required empty public constructor
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
//		if (DEBUG) Log.v(TAG, "onCreateView:");
		final View rootView = inflater.inflate(R.layout.fragment_connection, container, false);
		initView(rootView);
		return rootView;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.d(TAG, "onResume:");
		final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
		manager.startDiscovery();
		manager.addCallback(mManagerCallback);
		updateButtons(false);
//		mModelView.onResume();
/*		if (mVideoView != null) {
			mVideoView.resume();
			if (!mVideoView.isPlaying()) {
				mVideoView.start();
			}
		} */
		if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
			mMediaPlayer.start();
		}
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.d(TAG, "onPause:");

		updateButtons(false);
//		mModelView.onPause();
/*		if (mVideoView != null) {
			mVideoView.suspend();
		} */
		if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
			mMediaPlayer.stop();
		}
		final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
		if (manager != null) {
			manager.removeCallback(mManagerCallback);
			manager.stopDiscovery();
		}
		super.onPause();
	}

	/**
	 * Viewを初期化
	 * @param rootView
	 */
	private void initView(final View rootView) {

		final ARDeviceServiceAdapter adapter = new ARDeviceServiceAdapter(getActivity(), R.layout.list_item_deviceservice);

		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
		mMediaPlayer.setOnCompletionListener(mOnCompletionListener);

		mDeviceListView = (ListView)rootView.findViewById(R.id.list);
		final View empty_view = rootView.findViewById(R.id.empty_view);
		mDeviceListView.setEmptyView(empty_view);
		mDeviceListView.setAdapter(adapter);
		mDeviceListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
//		mModelView = (IModelView)rootView.findViewById(R.id.drone_view);
/*		mVideoView = (VideoView)rootView.findViewById(R.id.videoView);
		mVideoView.setOnPreparedListener(mOnPreparedListener);
		mVideoView.setOnCompletionListener(mOnCompletionListener);
		mVideoView.setVideoURI(Uri.parse("android.resource://" + getActivity().getPackageName() + "/" + R.raw.into_the_sky)); */
		mVideoView = (PlayerTextureView)rootView.findViewById(R.id.videoView);
		mVideoView.setScaleMode(PlayerTextureView.SCALE_MODE_CROP);
		mVideoView.setSurfaceTextureListener(mSurfaceTextureListener);

		mDownloadBtn = (ImageButton)rootView.findViewById(R.id.download_button);
		mDownloadBtn.setOnClickListener(mOnClickListener);
		mPilotBtn = (ImageButton)rootView.findViewById(R.id.pilot_button);
		mPilotBtn.setOnClickListener(mOnClickListener);
		ImageButton button = (ImageButton)rootView.findViewById(R.id.gallery_button);
		button.setOnClickListener(mOnClickListener);
		button = (ImageButton)rootView.findViewById(R.id.script_button);
		button.setOnClickListener(mOnClickListener);
	}

	private void updateButtons(final boolean visible) {
		final Activity activity = getActivity();
		if ((activity != null) && !activity.isFinishing()) {
			getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (!visible) {
						try {
							final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter)mDeviceListView.getAdapter();
							adapter.clear();
						} catch (final Exception e) {
							Log.w(TAG, e);
						}
					}
					final int visibility = visible ? View.VISIBLE : View.INVISIBLE;
					mDownloadBtn.setVisibility(visibility);
					mPilotBtn.setVisibility(visibility);
				}
			});
		}
	}

	/**
	 * 検出したデバイスのリストが更新された時のコールバック
	 */
	private ManagerFragment.ManagerCallback mManagerCallback = new ManagerFragment.ManagerCallback() {
		@Override
		public void onServicesDevicesListUpdated(final List<ARDiscoveryDeviceService> devices) {
			final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter) mDeviceListView.getAdapter();
			adapter.clear();
			for (final ARDiscoveryDeviceService service : devices) {
				Log.d(TAG, "service :  " + service);
				final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());
				switch (product) {
				case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
					adapter.add(service);
					break;
				case ARDISCOVERY_PRODUCT_JS:		// JumpingSumo
					// FIXME JumpingSumoは未実装
					break;
				case ARDISCOVERY_PRODUCT_MINIDRONE:	// RollingSpider
					adapter.add(service);
					break;
				}
/*				// ブルートゥース接続の時だけ追加する
				if (service.getDevice() instanceof ARDiscoveryDeviceBLEService) {
					adapter.add(service.getName());
				} */
			}
			adapter.notifyDataSetChanged();
			mDeviceListView.setItemChecked(0, true);	// 先頭を選択
			updateButtons(devices.size() > 0);
		}
	};

	private void clearCheck(final ViewGroup parent) {
		final int n = parent.getChildCount();
		for (int i = 0; i < n; i++) {
			final View v = parent.getChildAt(i);
			if (v instanceof Checkable) {
				((Checkable) v).setChecked(false);
			}
		}
	}

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
		Fragment fragment = null;
		switch (view.getId()) {
		case R.id.pilot_button:
			fragment = getFragment(mDeviceListView.getCheckedItemPosition(), true);
			break;
		case R.id.download_button:
			fragment = getFragment(mDeviceListView.getCheckedItemPosition(), false);
			break;
		case R.id.gallery_button:
			fragment = GalleyFragment.newInstance();
			break;
		case R.id.script_button:
			fragment = ScriptFragment.newInstance();
			break;
		}
		replace(fragment);
		}
	};

	private Fragment getFragment(final int position, final boolean isPiloting) {
		final ManagerFragment manager = ManagerFragment.getInstance(getActivity());
		final ARDeviceServiceAdapter adapter = (ARDeviceServiceAdapter)mDeviceListView.getAdapter();
		final String itemValue = adapter.getItemName(position);
		final ARDiscoveryDeviceService service = manager.getDevice(itemValue);
		Fragment fragment = null;
		if (service != null) {
			// 製品名を取得
			final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(service.getProductID());

			switch (product) {
			case ARDISCOVERY_PRODUCT_ARDRONE:    // Bebop
				fragment = isPiloting ? PilotFragment.newInstance(service) : MediaFragment.newInstance(service);
				break;
			case ARDISCOVERY_PRODUCT_JS:        // JumpingSumo
				//FIXME JumpingSumoは未実装
				break;
			case ARDISCOVERY_PRODUCT_MINIDRONE:    // RollingSpider
				fragment = isPiloting ? PilotFragment.newInstance(service) : MediaFragment.newInstance(service);
				break;
			}
		}
		return fragment;
	}

	private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

		@Override
		public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
			mVideoView.reset();
			try {
				mMediaPlayer.setDataSource(getActivity(), Uri.parse("android.resource://" + getActivity().getPackageName() + "/" + R.raw.into_the_sky));
				mMediaPlayer.prepareAsync();
			} catch (final IOException e) {
				Log.w(TAG, e);
			}
		}

		@Override
		public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
			mVideoView.reset();
		}

		@Override
		public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
			if (mMediaPlayer != null) {
				if (mMediaPlayer.isPlaying()) {
					mMediaPlayer.stop();
				}
				mMediaPlayer.release();
				mMediaPlayer = null;
			}
			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
		}
	};

	private final MediaPlayer.OnPreparedListener mOnPreparedListener = new MediaPlayer.OnPreparedListener() {
		@Override
		public void onPrepared(final MediaPlayer mp) {
			mVideoView.setAspectRatio(mp.getVideoWidth() / (double)mp.getVideoHeight());
			mp.setSurface(new Surface(mVideoView.getSurfaceTexture()));
			mp.setLooping(true);
			mp.start();
		}
	};

	private final MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
		@Override
		public void onCompletion(final MediaPlayer mp) {
			// 再生が終了したら最初に戻って再度再生する
			// 全体を再生し直すなら#onPreparedでMediaPlayer#setLooping(true);を呼ぶ方が簡単
//			mVideoView.seekTo(0);
//			mVideoView.start();
		}
	};

}