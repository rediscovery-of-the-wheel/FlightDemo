package com.serenegiant.flightdemo;

import android.content.DialogInterface;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.armedia.ARMediaObject;
import com.serenegiant.arflight.FTPController;
import com.serenegiant.arflight.IDeviceController;
import com.serenegiant.dialog.ConfirmDialog;
import com.serenegiant.dialog.OnDialogResultIntListener;
import com.serenegiant.dialog.TransferProgressDialogFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MediaFragment extends ControlBaseFragment
	implements TransferProgressDialogFragment.TransferProgressDialogListener, OnDialogResultIntListener {
	private static final boolean DEBUG = true;	// FIXME 実働時はfalseにすること
	private static String TAG = MediaFragment.class.getSimpleName();

	private static final int REQUEST_DELETE = 1;
	private static final int REQUEST_FETCH = 2;

	public static MediaFragment newInstance(final ARDiscoveryDeviceService device) {
		final MediaFragment fragment = new MediaFragment();
		fragment.setDevice(device);
		return fragment;
	}

	private FTPController mFTPController;
	private ViewPager mViewPager;
	private MediaPagerAdapter mPagerAdapter;

	public MediaFragment() {
		// デフォルトコンストラクタが必要
	}

/*	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		if (DEBUG) Log.v(TAG, "onAttach:");
	} */

/*	@Override
	public void onDetach() {
		if (DEBUG) Log.v(TAG, "onDetach:");
		super.onDetach();
	} */

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		mPagerAdapter = new MediaPagerAdapter(inflater);

		final View rootView = inflater.inflate(R.layout.fragment_media, container, false);
		mViewPager = (ViewPager)rootView.findViewById(R.id.pager);
		mViewPager.setAdapter(mPagerAdapter);
		return rootView;
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		if (mFTPController != null) {
			mFTPController.release();
			mFTPController = null;
		}
		stopDeviceController(false);
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		startDeviceController();
	}

/*	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		super.onPause();
	} */

	@Override
	protected void onConnect(final IDeviceController controller) {
		if (DEBUG) Log.v(TAG, "#onConnect");
		super.onConnect(controller);
		mFTPController = FTPController.newInstance(getActivity().getApplicationContext(), mController);
		mFTPController.setCallback(mCallback);
		post(mConnectCheckTask, 500);
	}

	@Override
	protected void updateStorageState(int mass_storage_id, int size, int used_size, boolean plugged, boolean full, boolean internal) {
		if (mFreeSpaceProgressbar != null) {
			mFreeSpaceProgressbar.setProgress((int)(used_size / (float)size * 100f));
		}
	}

	/** 切断された時に前のフラグメントに戻るまでの遅延時間[ミリ秒] */
	private static final long POP_BACK_STACK_DELAY = 2000;
	@Override
	protected void onDisconnect(final IDeviceController controller) {
		if (DEBUG) Log.v(TAG, "#onDisconnect");
		requestPopBackStack(POP_BACK_STACK_DELAY);
		super.onDisconnect(controller);
	}

	/**
	 * 接続後機体のストレージ状態を受信するまで待機するためのRunnable
	 */
	private final Runnable mConnectCheckTask = new Runnable() {
		@Override
		public void run() {
			final String mass_storage_id = mController.getMassStorageName();
			if (TextUtils.isEmpty(mass_storage_id)) {
				post(this, 1000);	// まだ準備出来てないので1秒後に再実行
			} else {
				mFTPController.connect();
			}
		}
	};

	/**
	 * FTPControllerからのコールバック
	 */
	private final FTPController.FTPControllerCallback mCallback = new FTPController.FTPControllerCallback() {
		@Override
		public boolean onError(final int requestCode, final Exception e) {
			Log.w(TAG, e);
			return false;
		}

		@Override
		public void onMediaListUpdated(final int requestCode, final List<ARMediaObject> medias) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					if (mARMediaObjectListAdapter != null) {
						mARMediaObjectListAdapter.clear();
						mARMediaObjectListAdapter.addAll(medias);
						mARMediaObjectListAdapter.notifyDataSetChanged();
					}
				}
			});
		}

		@Override
		public void onProgress(final int requestCode, final float progress, final int current, final int total) {
//			if (DEBUG) Log.v(TAG, String.format("onProgress:%d,%f,%d/%d", cmd, progress, current, total));
			if (mTransferProgressDialogFragment != null) {
				mTransferProgressDialogFragment.setProgress(current, total, progress);
			}
		}

		@Override
		public void onFinished(final int requestCode, final int error, final ARMediaObject[] medias) {
			if (DEBUG) Log.v(TAG, String.format("onFinished:%d,%d", requestCode, error));
			hideTransferProgress();
			if ((error == 0) && (requestCode == REQUEST_FETCH)) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						final int n = medias != null ? medias.length : 0;
						if (n > 0) {
							final List<String> list = new ArrayList<String>();
							for (final ARMediaObject mediaObject: medias) {
								try {
									final File file = new File(mediaObject.getFilePath());
									if (file.exists() && (file.length() == mediaObject.getSize())) {
										list.add(mediaObject.getFilePath());
									}
								} catch (final Exception e) {
								}
							}
							final int m = list.size();
							if (m > 0) {
								final String[] paths = new String[m];
								list.toArray(paths);
								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										try {
											MediaScannerConnection.scanFile(getActivity().getApplicationContext(), paths, null, null);
										} catch (final Exception e) {
											Log.w(TAG, e);
										}
									}
								});
							}
						}
					}
				}).start();
			}
		}
	};

	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			switch (view.getId()) {
			case R.id.delete_btn:
				// 削除確認ダイアログを表示
				ConfirmDialog.showDialog(MediaFragment.this, REQUEST_DELETE, getString(R.string.confirm_delete), null);
				break;
			case R.id.fetch_btn:
				// FIXME 確認ダイアログを表示する?
				transferMedias();
				break;
			}
		}
	};

	/**
	 * 選択されているファイルを削除する
	 */
	private void deleteMedias() {
		final ARMediaObject[] medias = getSelectedMedias();
		if (medias != null && medias.length > 0) {
			mFTPController.delete(REQUEST_DELETE, medias);
			if (mARMediaObjectListAdapter != null) {
				for (final ARMediaObject mediaObject: medias) {
					mARMediaObjectListAdapter.remove(mediaObject);
				}
			}
		}
	}

	private TransferProgressDialogFragment mTransferProgressDialogFragment;

	/**
	 * 選択されているファイルを取得する
	 */
	private void transferMedias() {
		final boolean needDelete = mDeleteAfterFetchCheckBox != null ? mDeleteAfterFetchCheckBox.isChecked() : false;
		final ARMediaObject[] medias = getSelectedMedias();
		if (medias != null) {
			mTransferProgressDialogFragment = TransferProgressDialogFragment.showDialog(this, getString(R.string.loading), null);
			mFTPController.transfer(REQUEST_FETCH, medias, needDelete);
		}
	}

	/**
	 * ファイル転送進捗ダイアログを閉じる
	 */
	private void hideTransferProgress() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mTransferProgressDialogFragment != null) {
					mTransferProgressDialogFragment.dismiss();
					mTransferProgressDialogFragment = null;
				}
				if (mMediaListView != null) {
					mMediaListView.clearChoices();
					mARMediaObjectListAdapter.notifyDataSetChanged();
				}
				updateMediaList();
			}
		});
	}

	private ARMediaObjectListAdapter mARMediaObjectListAdapter;	// 取得したメディアファイルの一覧アクセス用Adapter
	private ListView mMediaListView;
	private Button mDeleteBtn;
	private Button mFetchBtn;
	private CheckBox mDeleteAfterFetchCheckBox;
	private ProgressBar mFreeSpaceProgressbar;
	/**
	 * メディアファイル一覧画面の準備
	 * @param rootView
	 */
	private void initMediaList(final View rootView) {
		mMediaListView = (ListView)rootView.findViewById(R.id.listView);
		if (mMediaListView != null) {
			mARMediaObjectListAdapter = new ARMediaObjectListAdapter(getActivity(), R.layout.list_item_media);
			final View empty_view = rootView.findViewById(R.id.empty_view);
			mMediaListView.setEmptyView(empty_view);
			mMediaListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
//					if (DEBUG) Log.v(TAG, "onItemClick:");
					updateMediaList();
				}
			});
			mMediaListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
			mMediaListView.setAdapter(mARMediaObjectListAdapter);
		}
		mFreeSpaceProgressbar = (ProgressBar)rootView.findViewById(R.id.frees_pace_progress);
		mDeleteBtn = (Button)rootView.findViewById(R.id.delete_btn);
		mDeleteBtn.setOnClickListener(mOnClickListener);
		mFetchBtn = (Button)rootView.findViewById(R.id.fetch_btn);
		mFetchBtn.setOnClickListener(mOnClickListener);
		mDeleteAfterFetchCheckBox = (CheckBox)rootView.findViewById(R.id.delete_after_fetch_checkbox);
		updateMediaList();
	}

	/**
	 * メディア一覧表示を更新(実際の処理はUIスレッドで実行する)
	 */
	private void updateMediaList() {
		runOnUiThread(mUpdateMediaListTask);
	}

	/**
	 * メディア一覧表示の更新処理on UIスレッド
	 */
	private final Runnable mUpdateMediaListTask = new Runnable() {
		@Override
		public void run() {
			if (DEBUG) Log.v(TAG, "mUpdateMediaListTask:count=" + mMediaListView.getCheckedItemCount());
			final boolean selected = mMediaListView != null ? mMediaListView.getCheckedItemCount() > 0 : false;
			final int visibility = selected ? View.VISIBLE : View.INVISIBLE;
			if (mDeleteBtn != null) {
				mDeleteBtn.setVisibility(visibility);
			}
			if (mFetchBtn != null) {
				mFetchBtn.setVisibility(visibility);
			}
			if (mDeleteAfterFetchCheckBox != null) {
				if (visibility != mDeleteAfterFetchCheckBox.getVisibility()) {
					mDeleteAfterFetchCheckBox.setChecked(false);
				}
				mDeleteAfterFetchCheckBox.setVisibility(visibility);
			}
		}
	};

	/**
	 * 選択中のメディアファイルを取得
	 * @return
	 */
	private ARMediaObject[] getSelectedMedias() {
		ARMediaObject[] result = null;
		if (mMediaListView != null) {
			final SparseBooleanArray ids = mMediaListView.getCheckedItemPositions();
			final int n = ids != null ? ids.size() : 0;
			if (n > 0) {
				final List<ARMediaObject> list = new ArrayList<ARMediaObject>();
				for (int i = 0; i < n; i++) {
					if (ids.valueAt(i)) {
						final Object obj = mMediaListView.getItemAtPosition(ids.keyAt(i));
						if (obj instanceof ARMediaObject) {
							list.add((ARMediaObject)obj);
						}
					}
				}
				final int m = list.size();
				if (m > 0) {
					result = new ARMediaObject[m];
					list.toArray(result);
				}
			}
		}
		return result;
	}

	@Override
	public void onCancel(final int requestID) {
		mFTPController.cancel();
		hideTransferProgress();
	}

	@Override
	public void onDialogResult(final DialogInterface dialog, final int id, final int result) {
		if (result == DialogInterface.BUTTON_POSITIVE) {
			// OKボタンを押した時
			switch (id) {
			case REQUEST_DELETE:
				deleteMedias();
				break;
			}
		}
	}

	private static interface AdapterItemHandler {
		public void initialize(final MediaFragment parent, final View view);
	}

	private static final class PagerAdapterConfig {
		public final int title_id;
		public final int layout_id;
		public final AdapterItemHandler handler;

		public PagerAdapterConfig(final int _title_id, final int _layout_id, final AdapterItemHandler _handler) {
			title_id = _title_id;
			layout_id = _layout_id;
			handler = _handler;
		}
	}

	private static PagerAdapterConfig[] PAGER_MEDIA;
	static {
		PAGER_MEDIA = new PagerAdapterConfig[1];
		PAGER_MEDIA[0] = new PagerAdapterConfig(R.string.media_title_list, R.layout.media_list, new AdapterItemHandler() {
			@Override
			public void initialize(final MediaFragment parent, final View view) {
				parent.initMediaList(view);
			}
		});
	};

	/**
	 * メディア画面の各ページ用のViewを提供するためのPagerAdapterクラス
	 */
	private class MediaPagerAdapter extends PagerAdapter {
		private final LayoutInflater mInflater;
		public MediaPagerAdapter(final LayoutInflater inflater) {
			super();
			mInflater = inflater;
		}

		@Override
		public synchronized Object instantiateItem(final ViewGroup container, final int position) {
			if (DEBUG) Log.v(TAG, "instantiateItem:position=" + position);
			View view = null;
			if ((position >= 0) && (position < PAGER_MEDIA.length)) {
				final PagerAdapterConfig config = PAGER_MEDIA[position];
				view = mInflater.inflate(config.layout_id, container, false);
				config.handler.initialize(MediaFragment.this, view);
			}
			if (view != null) {
				container.addView(view);
			}
			return view;
		}

		@Override
		public synchronized void destroyItem(final ViewGroup container, final int position, final Object object) {
			if (DEBUG) Log.v(TAG, "destroyItem:position=" + position);
			if (object instanceof View) {
				container.removeView((View)object);
			}
		}

		@Override
		public int getCount() {
			return PAGER_MEDIA.length;
		}

		@Override
		public boolean isViewFromObject(final View view, final Object object) {
			return view.equals(object);
		}

		@Override
		public CharSequence getPageTitle(final int position) {
			if (DEBUG) Log.v(TAG, "getPageTitle:position=" + position);
			CharSequence result = null;
			if ((position >= 0) && (position < PAGER_MEDIA.length)) {
				result = getString(PAGER_MEDIA[position].title_id);
			}
			return result;
		}
	}
}
