package com.serenegiant.gamepad;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.util.Log;

import com.serenegiant.usb.USBMonitor.UsbControlBlock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HIDGamepadDriver {
	private static final boolean DEBUG = true;	// FIXME 実同時はfalseにすること
	private static final String TAG = HIDGamepadDriver.class.getSimpleName();

	private final Object mSync = new Object();
	private UsbControlBlock mCtrlBlock;
	private volatile boolean mIsRunning;

	public interface HIDGamepadCallback {
		/**
		 * ゲームパッドからのデータ受信時の処理
		 * @param n
		 * @param data
		 * @return true: 処理済み, onEventは呼ばれない, false:onEventの処理を行う
		 */
		public boolean onRawdataChanged(final int n, final byte[] data);
		public void onEvent(final HIDGamepadDriver gamepad, final HIDGamePad data);
	}

	private final HIDGamepadCallback mCallback;
	public HIDGamepadDriver(final HIDGamepadCallback callback) throws NullPointerException {
		if (callback == null) {
			throw new NullPointerException("callback should not be a null");
		}
		mCallback = callback;
	}

	public void open(final UsbControlBlock ctrlBlock) throws RuntimeException {
		synchronized (mSync) {
			mCtrlBlock = ctrlBlock;
			// ここでインターフェースとエンドポイントを探す
			final UsbDevice device = ctrlBlock.getDevice();
			final int num_interface = device.getInterfaceCount();
			if (DEBUG) Log.v(TAG, "num_interface:" + num_interface);
			for (int j = 0; j < num_interface; j++) {
				final UsbInterface intf = device.getInterface(j);
				final int num_endpoint = intf.getEndpointCount();
				if (DEBUG) Log.v(TAG, "num_endpoint:" + num_endpoint);
				if (num_endpoint > 0) {
					UsbEndpoint ep_in = null;
					UsbEndpoint ep_out = null;
					for (int i = 0; i < num_endpoint; i++) {
						final UsbEndpoint ep = intf.getEndpoint(i);
						if (DEBUG)
							Log.v(TAG, "type=" + ep.getType() + ", dir=" + ep.getDirection());
						if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {    // インタラプト転送
							switch (ep.getDirection()) {
							case UsbConstants.USB_DIR_IN:
								if (ep_in == null) {
									ep_in = ep;
								}
								break;
							case UsbConstants.USB_DIR_OUT:
								if (ep_out == null) {
									ep_out = ep;
								}
								break;
							}
						}
						if ((ep_in != null) && (ep_out != null)) break;
					}
					if (ep_in != null) {
						// HID入力インターフェースのエンドポイントが見つかった
						new Thread(new GamepadInTask(intf, ep_in), "GamepadInTask").start();
						if (!mIsRunning) {
							try {
								mSync.wait();
								if (mIsRunning) {
									new Thread(new CallbackTask(device), "CallbackTask").start();
								}
								return;
							} catch (final InterruptedException e) {
								break;
							}
						}
						mIsRunning = false;
						mSync.notifyAll();
					}
					if (ep_out != null) {
						// HID出力インターフェースのエンドポイントが見つかった
						// FIXME 出力は未サポートなので何もしない
					}
				}
			}
			throw new RuntimeException("could not find endpoint");
		}
	}

	public void close() {
		synchronized (mSync) {
			if (!mIsRunning) {
				if (mCtrlBlock != null) {
					mCtrlBlock.close();
				}
			}
			mIsRunning = false;
			mCtrlBlock = null;
			mSync.notifyAll();
		}
	}

	/**
	 * release all related resources
	 */
	public void release() {
		close();
	}

	public UsbDevice getDevice() {
		return mCtrlBlock != null ? mCtrlBlock.getDevice() : null;
	}

	public String getDeviceName(){
		return mCtrlBlock != null ? mCtrlBlock.getDeviceName() : null;
	}

	public UsbControlBlock getUsbControlBlock() {
		return mCtrlBlock;
	}

	private byte[] mValues;

	/**
	 * コールバックメソッドをプライベートスレッド上で呼び出すためのRunnable
	 */
	private class CallbackTask implements Runnable {
		private final HIDGamePad mParser;
		public CallbackTask(final UsbDevice device) {
			mParser = HIDGamePad.getGamepad(device);
		}

		@Override
		public void run() {
			synchronized (mSync) {
				if (!mIsRunning) {
					try {
						mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
			}
			int cnt = 0;
			final int n = mValues != null ? mValues.length : 0;
			final byte[] values = new byte[n];
			final byte[] prev = new byte[n];
			long prev_time = -1;
			for (; mIsRunning ;) {
				synchronized (mSync) {
					try {
						// ゲームパッドの値更新通知が来るまで待機
						mSync.wait();
						if (!mIsRunning) break;
						// ローカルにコピーする
						System.arraycopy(mValues, 0, values, 0, n);
					} catch (final InterruptedException e) {
						break;
					}
				}
				// 値が変更されているかどうかをチェック
				boolean b = false;
				for (int i = 0; i < n; i++) {
					b |= prev[i] != values[i];
					prev[i] = values[i];
				}
				if (b && (System.currentTimeMillis() - prev_time > 20)) {
					// 値が変更されていて前回のコールバック呼び出しから２０ミリ秒以上経過していたらコールバックを呼び出す
					prev_time = System.currentTimeMillis();
					try {
						if (!mCallback.onRawdataChanged(n, values)) {
							mParser.parse(n, values);
							mCallback.onEvent(HIDGamepadDriver.this, mParser);
						}
					} catch (final Exception e) {
						Log.w(TAG, e);
					}
				}
			}
		}
	};


	/**
	 * 非同期でHID入力エンドポイントからデータを読み込んでバッファにセットするためのRunnable
	 */
	private class GamepadInTask implements Runnable {
		private final String TAG_THREAD = GamepadInTask.class.getSimpleName();

		private final UsbInterface mIntf;
		private final UsbEndpoint mEp;
		public GamepadInTask(final UsbInterface intf, final UsbEndpoint ep) {
			mIntf = intf;
			mEp = ep;
		}

		@Override
		public void run() {
			if (DEBUG) Log.v(TAG_THREAD, "#run:");
			final UsbDeviceConnection connection;
			final int intervals;
			final int max_packets;
			final int n;
			synchronized (mSync) {
				mValues = null;
				mIsRunning = false;
				connection = mCtrlBlock.getUsbDeviceConnection();
				if (connection != null) {
					if (DEBUG) Log.v(TAG_THREAD, "claimInterface:");
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
						connection.setInterface(mIntf);
					}
					connection.claimInterface(mIntf, true);	// true: 必要ならカーネルドライバをdisconnectさせる
					intervals = mEp.getInterval();
					max_packets = mEp.getMaxPacketSize();

					if (DEBUG) Log.v(TAG_THREAD, "intervals=" + intervals + ", max_packets=" + max_packets);
					if (max_packets > 0) {
						mValues = new byte[max_packets];
						mIsRunning = true;
					}
				} else {
					intervals = max_packets = 0;
				}
				mSync.notifyAll();
			}
			if (connection != null) {
				try {
					if (mIsRunning) {
						final ByteBuffer buffer = ByteBuffer.allocateDirect(max_packets);
//						buffer.order(ByteOrder.LITTLE_ENDIAN);	// バイトアクセスしかしないから不要
						final UsbRequest request = new UsbRequest();
						request.initialize(connection, mEp);
						UsbRequest req;
						for (; mIsRunning; ) {
							buffer.clear();
							request.queue(buffer, max_packets);
							try {
								req = connection.requestWait();
							} catch (final Exception e) {
								Log.w(TAG, e);
								req = null;
							}
							if (request.equals(req)) {
//								if (DEBUG) Log.v(TAG_THREAD, "got data:" + buffer);
								buffer.clear();
								synchronized (mSync) {
									buffer.get(mValues);
									mSync.notifyAll();
								}
							}
							try {
								Thread.sleep(intervals);
							} catch (final InterruptedException e) {
							}
						}
						request.close();
					}
				} finally {
					if (DEBUG) Log.v(TAG_THREAD, "releaseInterface:");
					connection.releaseInterface(mIntf);
					connection.close();
				}
			}
		}
	};

}
