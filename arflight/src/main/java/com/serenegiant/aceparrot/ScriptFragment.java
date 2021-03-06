package com.serenegiant.aceparrot;
/*
 * By downloading, copying, installing or using the software you agree to this license.
 * If you do not agree to this license, do not download, install,
 * copy or use the software.
 *
 *
 *                           License Agreement
 *                        (3-clause BSD License)
 *
 * Copyright (C) 2015-2017, saki t_saki@serenegiant.com
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *   * Neither the names of the copyright holders nor the names of the contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * This software is provided by the copyright holders and contributors "as is" and
 * any express or implied warranties, including, but not limited to, the implied
 * warranties of merchantability and fitness for a particular purpose are disclaimed.
 * In no event shall copyright holders or contributors be liable for any direct,
 * indirect, incidental, special, exemplary, or consequential damages
 * (including, but not limited to, procurement of substitute goods or services;
 * loss of use, data, or profits; or business interruption) however caused
 * and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of
 * the use of this software, even if advised of the possibility of such damage.
 */

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.serenegiant.dialog.ConfirmDialog;
import com.serenegiant.dialog.OnDialogResultIntListener;
import com.serenegiant.dialog.SelectFileDialogFragment;
import com.serenegiant.arflight.R;
import com.serenegiant.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScriptFragment extends BaseFragment implements SelectFileDialogFragment.OnFileSelectListener, OnDialogResultIntListener {
    private static final boolean DEBUG = false;  // FIXME 実働時はfalseにすること
    private static final String TAG = ScriptFragment.class.getSimpleName();

    public static ScriptFragment newInstance() {
        final ScriptFragment fragment = new ScriptFragment();
        return fragment;
    }

	private final List<ScriptHelper.ScriptRec> mScripts = new ArrayList<ScriptHelper.ScriptRec>();
	private ScriptHelper.ScriptListAdapter mScriptListAdapter;
    private SharedPreferences mPref;
	private ViewPager mViewPager;
	private String lastError;

    public ScriptFragment() {
		super();
        // デフォルトコンストラクタが必要:
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mPref = getActivity().getPreferences(0);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
//		if (DEBUG) Log.v(TAG, "onCreateView:");
		final LayoutInflater local_inflater = getThemedLayoutInflater(inflater);
        final View rootView = local_inflater.inflate(R.layout.fragment_script, container, false);
        mViewPager = (ViewPager)rootView.findViewById(R.id.pager);
        mViewPager.setAdapter(new ScriptPagerAdapter(inflater));
        // スクリプトの設定を読み込む
		try {
			ScriptHelper.loadScripts(mPref, mScripts);
		} catch (IOException e) {
			lastError = e.getMessage();
			mViewPager.setCurrentItem(1);
		}
		return rootView;
    }

	@Override
	protected void internalOnPause() {
		ScriptHelper.saveScripts(mPref, mScripts);
		super.internalOnPause();
	}

	/**
	 * ファイル選択ダイアログからのコールバック
	 * @param files
	 */
	@Override
	public void onFileSelect(final File[] files) {
		if (DEBUG) Log.v(TAG, "onFileSelect:");
		if (mScriptListView != null) {
			mScriptListView.clearChoices();
		}
		final int n = files != null ? files.length : 0;
		boolean added = false;
		try {
			added = ScriptHelper.addScripts(files, mScripts);
		} catch (final IOException e) {
			Log.w(TAG, e);
			lastError = e.getMessage();
			// FIXME ここでエラー画面に遷移したい
			mViewPager.setCurrentItem(1);
		}
		if (added && (mScriptListAdapter != null)) {
			mScriptListAdapter.notifyDataSetChanged();
		}
		updateScriptList();
 	}

	private static final int REQUEST_REMOVE = 123;

	/**
	 * 削除確認ダイアログからのコールバック
	 * @param dialog
	 * @param id
	 * @param result
	 */
	@Override
	public void onDialogResult(final DialogInterface dialog, final int id, final int result) {
		if (result == DialogInterface.BUTTON_POSITIVE) {
			switch (id) {
			case REQUEST_REMOVE:
				removeScripts();
				break;
			}
		}
	}

	/**
	 * 選択されているスクリプト定義をリストから取り除く
	 */
	private void removeScripts() {
		final ScriptHelper.ScriptRec[] scripts = getSelectedScripts();
		if (scripts != null && scripts.length > 0) {
			if (mScriptListAdapter != null) {
				for (final ScriptHelper.ScriptRec script: scripts) {
					mScriptListAdapter.remove(script);
				}
			}
			if (mScriptListView != null) {
				mScriptListView.clearChoices();
			}
			updateScriptList();
		}
	}

	/**
	 * 選択中のスクリプトファイル定義を取得
	 * @return
	 */
	private ScriptHelper.ScriptRec[] getSelectedScripts() {
		ScriptHelper.ScriptRec[] result = null;
		if (mScriptListView != null) {
			final SparseBooleanArray ids = mScriptListView.getCheckedItemPositions();
			final int n = ids != null ? ids.size() : 0;
			if (n > 0) {
				final List<ScriptHelper.ScriptRec> list = new ArrayList<ScriptHelper.ScriptRec>();
				for (int i = 0; i < n; i++) {
					if (ids.valueAt(i)) {
						final Object obj = mScriptListView.getItemAtPosition(ids.keyAt(i));
						if (obj instanceof ScriptHelper.ScriptRec) {
							list.add((ScriptHelper.ScriptRec)obj);
						}
					}
				}
				final int m = list.size();
				if (m > 0) {
					result = new ScriptHelper.ScriptRec[m];
					list.toArray(result);
				}
			}
		}
		return result;
	}

	private ListView mScriptListView;
	private ImageButton mRemoveBtn;

	/**
	 * スクリプト選択画面の初期化
	 * @param rootView
	 */
    private void initScriptList(final View rootView) {
		mScriptListAdapter = new ScriptHelper.ScriptListAdapter(getActivity(), R.layout.list_item_script, mScripts);
		mScriptListView = (ListView)rootView.findViewById(R.id.script_listview);
		mScriptListView.setEmptyView(rootView.findViewById(R.id.empty_view));
		mScriptListView.setAdapter(mScriptListAdapter);
		mScriptListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				updateScriptList();
			}
		});
		ImageButton button = (ImageButton)rootView.findViewById(R.id.load_btn);
		button.setOnClickListener(mOnClickListener);
		button = (ImageButton)rootView.findViewById(R.id.help_btn);
		button.setOnClickListener(mOnClickListener);
		mRemoveBtn = (ImageButton)rootView.findViewById(R.id.delete_btn);
		mRemoveBtn.setOnClickListener(mOnClickListener);
		updateScriptList();
    }

	private TextView mErrorTextView;

	/**
	 * エラー画面の初期化
	 * @param rootView
	 */
    private void initScriptError(final View rootView) {
		mErrorTextView = (TextView)rootView.findViewById(R.id.error_textview);
		if (TextUtils.isEmpty(lastError)) {
			mErrorTextView.setText(R.string.script_error_non);
		} else {
			mErrorTextView.setText(lastError);
		}
		ImageButton button = (ImageButton)rootView.findViewById(R.id.save_btn);
		button.setOnClickListener(mOnClickListener);
		button = (ImageButton)rootView.findViewById(R.id.help_btn);
		button.setOnClickListener(mOnClickListener);
    }

	/**
	 * ボタンをタッチした時の処理
	 */
	private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			int i = view.getId();
			if (i == R.id.load_btn) {
				setColorFilter((ImageView) view);
				final File root = FileUtils.getCaptureDir(getActivity(), "Documents", 0);
				SelectFileDialogFragment.showDialog(ScriptFragment.this, root.getAbsolutePath(), false, "script");

			} else if (i == R.id.delete_btn) {// 削除確認ダイアログを表示
				ConfirmDialog.showDialog(ScriptFragment.this, REQUEST_REMOVE, getString(R.string.confirm_remove_script), null);

			} else if (i == R.id.save_btn) {
			} else if (i == R.id.help_btn) {
				replace(HelpFragment.newInstance(HelpFragment.SCRIPT_HELP_SCRIPTS));

			}
		}
	};

	/**
	 * スクリプトリストが更新された時の処理
	 */
	private void updateScriptList() {
		runOnUiThread(mUpdateScriptListTask);
	}

	private final Runnable mUpdateScriptListTask = new Runnable() {
		@Override
		public void run() {
			final boolean selected = mScriptListView != null && mScriptListView.getCheckedItemCount() > 0;
			final int visibility = selected ? View.VISIBLE : View.INVISIBLE;
			if (mRemoveBtn != null) {
				mRemoveBtn.setVisibility(visibility);
			}
		}
	};

	/**
     * 設定画面の各ページ用のViewを提供するためのPagerAdapterクラス
     */
    private class ScriptPagerAdapter extends PagerAdapter {
        private final LayoutInflater mInflater;
        public ScriptPagerAdapter(final LayoutInflater inflater) {
            super();
            mInflater = inflater;
        }

        @Override
        public synchronized Object instantiateItem(final ViewGroup container, final int position) {
            if (DEBUG) Log.v(TAG, "instantiateItem:position=" + position);
            View view = null;
            switch (position) {
            case 0:
                view = mInflater.inflate(R.layout.fragment_script_list, container, false);
                initScriptList(view);
                break;
            case 1:
                view = mInflater.inflate(R.layout.fragment_script_error, container, false);
                initScriptError(view);
                break;
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
            return 2;
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return view.equals(object);
        }

        @Override
        public CharSequence getPageTitle(final int position) {
//          if (DEBUG) Log.v(TAG, "getPageTitle:position=" + position);
            CharSequence result = null;
/*			switch (position) {
            case 0:
                result = getString(R.string.script_list);
                break;
            case 1:
                result = getString(R.string.script_error);
                break;
            } */
            return result;
        }
    }
}
