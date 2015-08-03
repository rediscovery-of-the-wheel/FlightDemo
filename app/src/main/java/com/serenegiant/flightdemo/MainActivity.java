package com.serenegiant.flightdemo;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.parrot.arsdk.arsal.ARSALPrint;
import com.serenegiant.lang.script.ParseException;
import com.serenegiant.lang.script.ScriptParser;

import java.io.IOException;


public class MainActivity extends AppCompatActivity {
	// ActionBarActivityを継承するとPilotFragmentから戻る際にクラッシュする
	// Fragmentが切り替わらずに処理中にもかかわらずActivityが破棄されてしまう
	private static String TAG = MainActivity.class.getSimpleName();

	private static boolean isLoaded = false;

	static {
		if (!isLoaded) {
			try {
				System.loadLibrary("arsal");
				System.loadLibrary("arsal_android");
				System.loadLibrary("arnetworkal");
				System.loadLibrary("arnetworkal_android");
				System.loadLibrary("arnetwork");
				System.loadLibrary("arnetwork_android");
				System.loadLibrary("arcommands");
				System.loadLibrary("arcommands_android");
				System.loadLibrary("json");
				System.loadLibrary("ardiscovery");
				System.loadLibrary("ardiscovery_android");
				System.loadLibrary("arstream");
				System.loadLibrary("arstream_android");
				System.loadLibrary("arcontroller");
				System.loadLibrary("arcontroller_android");

//				ARSALPrint.enableDebugPrints();	// XXX ARライブラリのデバッグメッセージを表示する時
				isLoaded = true;
			} catch (Exception e) {
				Log.e(TAG, "Oops (LoadLibrary)", e);
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		final Toolbar tool_bar = (Toolbar)findViewById(R.id.sample_toolbar);
		this.setSupportActionBar(tool_bar);
		final ManagerFragment manager = ManagerFragment.getInstance(this);
		if (savedInstanceState == null) {
			final Fragment fragment = new ConnectionFragment();
			getFragmentManager().beginTransaction()
				.add(R.id.container, fragment).commit();
		}

/*		try {
			final ScriptParser parser = new ScriptParser(getResources().getAssets().open("control.script"));
			parser.Parse();
//			parser.Parse().jjtAccept(new ScriptParserVisitorImpl(), null);
		} catch (IOException e) {
			Log.w(TAG, e);
		} catch (ParseException e) {
			Log.w(TAG, e);
		} catch (Exception e) {
			Log.w(TAG, e);
		} */
	}

	@Override
	public void onBackPressed() {
		//　ActionBarActivity/AppCompatActivityはバックキーの処理がおかしくて
		// バックスタックの処理が正常にできない事に対するworkaround
		if (getFragmentManager().getBackStackEntryCount() > 0) {
			getFragmentManager().popBackStack();
		} else {
			super.onBackPressed();
		}
	}

}
