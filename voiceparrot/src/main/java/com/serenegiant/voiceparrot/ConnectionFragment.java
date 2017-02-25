package com.serenegiant.voiceparrot;

import android.app.Fragment;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.View;

import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.serenegiant.aceparrot.BaseBridgeFragment;
import com.serenegiant.aceparrot.BaseConnectionFragment;
import com.serenegiant.aceparrot.ConfigAppFragment;
import com.serenegiant.aceparrot.GalleyFragment;
import com.serenegiant.aceparrot.ScriptFragment;

import java.io.IOException;

public class ConnectionFragment extends BaseConnectionFragment {

	public static ConnectionFragment newInstance() {
		return new ConnectionFragment();
	}

	public ConnectionFragment() {
		super();
	}

	protected void onClick(final View view, final int position) {
		Fragment fragment = null;
		switch (view.getId()) {
		case R.id.pilot_button:
			if (checkPermissionLocation()) {
				fragment = getFragment(position, true, false);
			}
			break;
		case R.id.voice_pilot_button:
			if (checkPermissionLocation() && checkPermissionAudio()) {
				fragment = getFragment(position, true, true);
			}
			break;
		case R.id.download_button:
			if (checkPermissionWriteExternalStorage()) {
				fragment = getFragment(position, false, false);
			}
			break;
		case R.id.gallery_button:
			if (checkPermissionWriteExternalStorage()) {
				fragment = GalleyFragment.newInstance();
			}
			break;
		case R.id.script_button:
			if (checkPermissionWriteExternalStorage()) {
				fragment = ScriptFragment.newInstance();
			}
			break;
		case R.id.config_show_btn:
			fragment = ConfigAppFragment.newInstance();
			break;
		}
		replace(fragment);
	}

	protected boolean onLongClick(final View view, final int position) {
		return false;
	}

	@Override
	protected BaseBridgeFragment newBridgetFragment(final ARDiscoveryDeviceService device) {
		return BridgeFragment.newInstance(device);
	}

	@Override
	protected void setDataSource(final Context context, final MediaPlayer media_player) throws IOException {
		media_player.setDataSource(context, Uri.parse("android.resource://" + getActivity().getPackageName() + "/" + R.raw.into_the_sky));
	}
}
