package com.serenegiant.arflight;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;

import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;

import java.util.ArrayList;

public class ARDeviceServiceAdapter extends ArrayAdapter<ARDiscoveryDeviceService> {

	private final LayoutInflater mInflater;
	private final int mLayoutId;

	public ARDeviceServiceAdapter(final Context context, final int resource) {
		super(context, resource, new ArrayList<ARDiscoveryDeviceService>());
		mInflater = LayoutInflater.from(context);
		mLayoutId = resource;
	}

	@Override
	public View getView(final int position, final View convertView, final ViewGroup parent) {
		View rootView = convertView;
		if (rootView == null) {
			rootView = mInflater.inflate(mLayoutId, parent, false);
		}
		ViewHolder holder = (ViewHolder)rootView.getTag(R.id.ardeviceserviceadapter);
		if (holder == null) {
			holder = new ViewHolder();
			holder.title = (TextView)rootView.findViewById(R.id.title);
			holder.state = (TextView)rootView.findViewById(R.id.state);
			holder.thumbnail = (ImageView)rootView.findViewById(R.id.thumbnail);
		}
		final ARDiscoveryDeviceService device = getItem(position);
		if (holder.title != null) {
			holder.title.setText(device.getName());
		}
		if (holder.state != null) {
			// FIXME 接続状態の更新処理
			if (rootView instanceof Checkable) {
				holder.state.setText(((Checkable)rootView).isChecked() ? "選択中" : "---");
			}
		}
		if (holder.thumbnail != null) {
			// FIXME 機体アイコンの更新処理。今はアプリのアイコンと同じまま
			final ARDISCOVERY_PRODUCT_ENUM product = ARDiscoveryService.getProductFromProductID(device.getProductID());
			switch (product) {
			case ARDISCOVERY_PRODUCT_ARDRONE:	// Bebop
			case ARDISCOVERY_PRODUCT_BEBOP_2:	// Bebop2
//				holder.thumbnail.setImageResource(R.drawable.ic_ardrone);
				break;
			case ARDISCOVERY_PRODUCT_MINIDRONE:	// RollingSpider
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_LIGHT:
			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_BRICK:
//			case ARDISCOVERY_PRODUCT_MINIDRONE_EVO_HYDROFOIL: // ハイドロフォイルもいる?
//				holder.thumbnail.setImageResource(R.drawable.ic_minidrone);
				break;
			case ARDISCOVERY_PRODUCT_SKYCONTROLLER:	// SkyControllerNewAPI
				break;
			}
		}
		return rootView;
	}

	public String getItemName(final int position) {
		ARDiscoveryDeviceService device = null;
		try {
			device = getItem(position);
		} catch (final Exception e) {
		}
		return device != null ? device.getName() : null;
	}

	private static final class ViewHolder {
		TextView title;
		TextView state;
		ImageView thumbnail;
	}
}
