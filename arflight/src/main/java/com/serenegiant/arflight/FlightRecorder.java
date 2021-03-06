package com.serenegiant.arflight;
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

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FlightRecorder implements IAutoFlight {
	private static final boolean DEBUG = false;	// FIXME 実働時はfalseにすること
	private static final String TAG = "FlightRecorder";

	private static class CmdRec {
		public int cmd;
		public int[] value = new int[4];
		public long time;
	}

	private final Object mSync = new Object();
	private final List<String> mRecords = new ArrayList<String>(100);
	private final AutoFlightListener mPlaybackListener;

	private volatile boolean mIsRecording;	// 記録中
	private volatile boolean mIsPlayback;	// 再生中
	private long mStartTime;	// 記録開始時刻[ミリ秒]
	private int mCurrentPos;	// 次の読み込み位置(再生時)


	public FlightRecorder(final AutoFlightListener listener) {
		if (listener == null) {
			throw new NullPointerException("AutoFlightListenerコールバックリスナーが設定されてない");
		}
		mPlaybackListener = listener;
	}

	/**
	 * 指定したパスのファイルにコマンドを書き出す
	 * @param path
	 */
	public void save(final String path) {
		if (DEBUG) Log.v(TAG, "save:" + path);
		int cnt = 0;
		try {
			final List<String> copy = new ArrayList<String>();
			synchronized (mSync) {
				copy.addAll(mRecords);
			}
			if (copy.size() > 0) {	// 1件もコマンドが記録されていない時は保存しない
				final BufferedWriter writer = new BufferedWriter(new FileWriter(path));
				try {
					for (String line : copy) {
						writer.write(line);
						writer.newLine();
						if (DEBUG) Log.v(TAG, String.format("%5d)%s", cnt++, line));
					}
					writer.flush();
				} catch (final IOException e) {
					Log.w(TAG, e);
				} finally {
					writer.close();
				}
			}
		} catch (final FileNotFoundException e) {
			Log.w(TAG, e);
		} catch (final IOException e) {
			Log.w(TAG, e);
		} catch (final Exception e) {
			Log.w(TAG, e);
		}
		if (DEBUG) Log.v(TAG, "save:finished:" + cnt);
	}

	/**
	 * 指定したパスのファイルからコマンドを読み込む
	 * @param path
	 * @throws IllegalStateException 再生中ならIllegalStateExceptionを生成する
	 */
	public void load(final String path) throws IllegalStateException {
		load(new File(path));
	}

	/**
	 * 指定したパスのファイルからコマンドを読み込む
	 * @param path
	 * @throws IllegalStateException 再生中ならIllegalStateExceptionを生成する
	 */
	public void load(final File path) throws IllegalStateException {
		if (mIsPlayback) throw new IllegalStateException("再生中だよ");
		if (DEBUG) Log.v(TAG, "load:" + path);
		int cnt = 0;
		try {
			final List<String> copy = new ArrayList<String>();
			final CmdRec cmd = new CmdRec();
			long lastTime = 0;
			final BufferedReader reader = new BufferedReader(new FileReader(path));
			try {
				for (String line = reader.readLine(); !TextUtils.isEmpty(line); line = reader.readLine()) {
					if (!parseCmd(line, cmd)) {
						copy.add(line);
						if (cmd.time > lastTime) {
							lastTime = cmd.time;
						}
					}
					if (DEBUG) Log.v(TAG, String.format("%5d)%s", cnt++, line));
				}
			} catch (final IOException e) {
				Log.w(TAG, e);
			} finally {
				reader.close();
			}
			synchronized (mSync) {
				mRecords.clear();
				mRecords.addAll(copy);
				mStartTime = System.currentTimeMillis() - lastTime;
			}
		} catch (final FileNotFoundException e) {
			Log.w(TAG, e);
		} catch (final IOException e) {
			Log.w(TAG, e);
		} catch (final Exception e) {
			Log.w(TAG, e);
		}
		if (DEBUG) Log.v(TAG, "load:finished:" + cnt);
	}

	/**
	 * コマンドをクリア
	 */
	public void clear() {
		if (DEBUG) Log.v(TAG, "clear:");
		synchronized (mSync) {
			mRecords.clear();
			mStartTime = System.currentTimeMillis();
			mCurrentPos = 0;
		}
	}

	/**
	 * 記録されているコマンドの数を取得
	 * @return
	 */
	public int size() {
		synchronized (mSync) {
			return mRecords.size();
		}
	}

	/**
	 * 記録開始
	 * @throws IllegalStateException 再生中ならIllegalStateExceptionを生成する
	 */
	public void start() throws IllegalStateException {
		if (DEBUG) Log.v(TAG, "start:");
		synchronized (mSync) {
			if (mIsPlayback) throw new IllegalStateException("再生中だよ");
			if (!mIsRecording) {
				mStartTime = System.currentTimeMillis();
				mIsRecording = true;
			}
		}
	}

	/**
	 * 記録・コマンド再生終了
	 */
	@Override
	public void stop() {
		if (DEBUG) Log.v(TAG, "stop:");
		synchronized (mSync) {
			mIsRecording = false;
			mIsPlayback = false;
			mSync.notifyAll();
		}
	}

	/**
	 * コマンド再生の準備
	 * @param args 未使用, InputStreamとかコマンドリストを渡せるようにするかも
	 * @throws RuntimeException
	 */
	@Override
	public void prepare(Object...args) throws RuntimeException {
		synchronized (mSync) {
			if (mIsRecording) throw new IllegalStateException("記録中だよ");
			if (mRecords.size() == 0) throw new IllegalStateException("データが無い");
		}
		try {
			mPlaybackListener.onPrepared();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
	/**
	 * コマンド再生開始
	 * @throws IllegalStateException 記録中ならIllegalStateExceptionを生成する
	 */
	@Override
	public void play() throws IllegalStateException {
		if (DEBUG) Log.v(TAG, "play:");
		synchronized (mSync) {
			if (mIsRecording) throw new IllegalStateException("記録中だよ");
			if (!mIsPlayback) {
				mIsPlayback = true;
				new Thread(mPlaybackRunnable, "PlaybackTask").start();
			}
		}
	}

	/**
	 * 関係するリソースを破棄する
	 */
	@Override
	public void release() {
		stop();
	}

	/**
	 * 記録中かどうかを取得
	 * @return
	 */
	public boolean isRecording() {
		synchronized (mSync) {
			return mIsRecording;
		}
	}

	/**
	 * 自動フライトの準備ができているかどうか
	 * @return
	 */
	public boolean isPrepared() {
		synchronized (mSync) {
			return !mIsRecording && !mIsPlayback && (mRecords.size() > 0);
		}
	}

	/**
	 * 再生中かどうかを取得
	 * @return
	 */
	@Override
	public boolean isPlaying() {
		synchronized (mSync) {
			return mIsPlayback;
		}
	}

	/**
	 * 指定したコマンドを記録
	 * 記録中でなければ無視する
	 * @param cmd CMD_XXX定数
	 * @param values, -100〜100, 0は移動終了
	 */
	public void record(final int cmd, final int... values) {
		synchronized (mSync) {
			if (mIsRecording) {
				final long t = System.currentTimeMillis() - mStartTime;
				final StringBuilder sb = new StringBuilder();
				for (int i = 0; i < values.length; i++) {
					if (i > 0) {
						sb.append(",");
					}
					sb.append(values[i]);
				}
				// 0:cmd, 1:t, 2:value0, 3:value1,...
				final String cmd_str = String.format("%d,%d,%s", cmd, t, sb.toString());
				mRecords.add(cmd_str);
				mCurrentPos = mRecords.size() - 1;
			}
		}
	}

	/**
	 * 次のコマンドの位置を取得
	 * @return
	 */
	public int pos() {
		synchronized (mSync) {
			return mCurrentPos;
		}
	}

	/**
	 * コマンドの読み込み位置を変更
	 * @param pos
	 */
	public void pos(int pos) {
		if (DEBUG) Log.v(TAG, "pos:" + pos);
		synchronized (mSync) {
			mCurrentPos = pos;
		}
	}

	/**
	 * 指定した時刻にコマンドの読み取り位置を進める
	 * @param time
	 * @return 次の読み込むコマンドの時刻を返す
	 */
	public long seek(final long time) {
		if (DEBUG) Log.v(TAG, "seek:" + time);
		synchronized (mSync) {
			mCurrentPos = 0;
			String[] record;
			int pos = -1;
			for (String line : mRecords) {
				try {
					pos++;
					record = line.split(",");
					// 0:cmd, 1:t, 2:value0, 3:value1,...
					final long t = Long.parseLong(record[1]);
					if (time <= t) {
						mCurrentPos = pos;
						return t;
					}
				} catch (final NumberFormatException e) {
					Log.w(TAG, e);
					break;
				} catch (final NullPointerException e) {
					Log.w(TAG, e);
					break;
				}
			}
			return 0;
		}
	}

	/**
	 * 記録したコマンドを取得して１ステップ進める
	 * @param cmd
	 * @return コマンドを取得できればfalse, 取得できなければtrue
	 */
	private boolean step(final CmdRec cmd) {
		try {
			cmd.cmd = 0;
			String line = null;
			synchronized (mSync) {
				if ((mCurrentPos >= 0) && (mCurrentPos < mRecords.size())) {
					line = mRecords.get(mCurrentPos++);
				}
			}
			return parseCmd(line, cmd);
		} catch (final NullPointerException e) {
			Log.w(TAG, e);
		}
		return true;
	}

	/**
	 * 文字列1レコード分を解析する
	 * @param line
	 * @param cmd
	 * @return
	 */
	private boolean parseCmd(final String line, final CmdRec cmd) {
		if (!TextUtils.isEmpty(line) && (cmd != null)) {
			final String[] record = line.split(",");
			try {
				cmd.cmd = Integer.parseInt(record[0]);
				cmd.time = Long.parseLong(record[1]);
				for (int i = 2; i < record.length; i++)
					cmd.value[i-2] = Integer.parseInt(record[i]);
				return false;
			} catch (final NumberFormatException e) {
				cmd.cmd = CMD_NON;
				Log.w(TAG, e);
			}
		}
		return true;
	}

	/**
	 * コマンド再生スレッドの実行用Runnable
	 */
	private final Runnable mPlaybackRunnable = new Runnable() {
		@Override
		public void run() {
			try {
				mPlaybackListener.onStart();
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
			final CmdRec cmd = new CmdRec();
			final long start_time = System.currentTimeMillis();
			long current_time;
			for ( ; mIsPlayback; ) {
				if (!step(cmd)) {
					// 記録されているコマンドを読み込めた時
					current_time = System.currentTimeMillis() - start_time;
					if (cmd.time > current_time) {
						synchronized (mSync) {
							try {
								mSync.wait(cmd.time - current_time);
							} catch (final InterruptedException e) {
							}
						}
					}
					if (!mIsPlayback) break;
					try {
						if (mPlaybackListener.onStep(cmd.cmd, cmd.value, cmd.time)) {
							// trueが返ってきたので終了する
							break;
						}
					} catch (final Exception e) {
						Log.w(TAG, e);
					}
				} else {
					// コマンドが無かった時
					break;
				}
			}
			mIsPlayback = false;
			try {
				mPlaybackListener.onStop();
			} catch (final Exception e) {
				Log.w(TAG, e);
			}
		}
	};
}
