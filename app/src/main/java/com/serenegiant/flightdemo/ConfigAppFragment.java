package com.serenegiant.flightdemo;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.serenegiant.widget.ColorPickerView;
import com.serenegiant.widget.RelativeRadioGroup;
import static com.serenegiant.flightdemo.AppConst.*;

public class ConfigAppFragment extends BaseFragment {

	public static ConfigAppFragment newInstance() {
		final ConfigAppFragment fragment = new ConfigAppFragment();
		return fragment;
	}

	private SharedPreferences mPref;
	private int mColor;
	private boolean mAutoHide;

	public ConfigAppFragment() {
		super();
		// デフォルトコンストラクタが必要
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mPref = activity.getPreferences(0);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
//		if (DEBUG) Log.v(TAG, "onCreateView:");
		final LayoutInflater local_inflater = getThemedLayoutInflater(inflater);
     	final View rootView = local_inflater.inflate(R.layout.fragment_config_app, container, false);
		final RelativeRadioGroup group = (RelativeRadioGroup)rootView.findViewById(R.id.icon_radiogroup);

		switch (mPref.getInt(KEY_ICON_TYPE, 100)) {
		case 1:		// 001
			group.check(R.id.icon_001_radiobutton);
			break;
		case 2:		// 002
			group.check(R.id.icon_002_radiobutton);
			break;
//		case 0:
		default:	// 通常
			group.check(R.id.icon_000_radiobutton);
			break;
		}
		group.setOnCheckedChangeListener(mOnRadioButtonCheckedChangeListener);
// 機体色設定
		mColor = mPref.getInt(KEY_COLOR, getResources().getColor(R.color.RED));
		final ColorPickerView picker = (ColorPickerView)rootView.findViewById(R.id.color_picker);
		picker.setColor(mColor);
		picker.showAlpha(false);
		picker.setColorPickerListener(mColorPickerListener);
// アイコンを自動的に隠す設定
		mAutoHide = mPref.getBoolean(KEY_AUTO_HIDE, false);
		final Switch sw = (Switch)rootView.findViewById(R.id.icon_auto_hide_switch);
		sw.setChecked(mAutoHide);
		sw.setOnCheckedChangeListener(mOnCheckedChangeListener);
		return rootView;
	}

	private final RelativeRadioGroup.OnCheckedChangeListener mOnRadioButtonCheckedChangeListener
		= new RelativeRadioGroup.OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(final RelativeRadioGroup group, final int checkedId) {
			switch (checkedId) {
			case R.id.icon_000_radiobutton:
				mPref.edit().putInt(KEY_ICON_TYPE, 0).apply();
				break;
			case R.id.icon_001_radiobutton:
				mPref.edit().putInt(KEY_ICON_TYPE, 1).apply();
				break;
			case R.id.icon_002_radiobutton:
				mPref.edit().putInt(KEY_ICON_TYPE, 2).apply();
				break;
			}
		}
	};

	private final ColorPickerView.ColorPickerListener mColorPickerListener
		= new ColorPickerView.ColorPickerListener() {
		@Override
		public void onColorChanged(final ColorPickerView view, final int color) {
			if (mColor != color) {
				mColor = color;
				mPref.edit().putInt(KEY_COLOR, color).apply();
				TextureHelper.clearTexture(getActivity());
			}
		}
	};

	private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			switch (buttonView.getId()) {
			case R.id.icon_auto_hide_switch:
			{
				if (mAutoHide != isChecked) {
					mAutoHide = isChecked;
					mPref.edit().putBoolean(KEY_AUTO_HIDE, isChecked).apply();
				}
				break;
			}
			}
		}
	};
}
