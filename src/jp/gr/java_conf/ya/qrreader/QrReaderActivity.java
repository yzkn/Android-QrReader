package jp.gr.java_conf.ya.qrreader; // Copyright (c) 2013-2016 YA <ya.androidapp@gmail.com> All rights reserved.

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.android.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

public class QrReaderActivity extends Activity implements SurfaceHolder.Callback,
		Camera.PreviewCallback, Camera.AutoFocusCallback {
	private static final String TAG = "ZXingBase";

	private static final int MIN_PREVIEW_PIXCELS = 320 * 240;
	private static final int MAX_PREVIEW_PIXCELS = 800 * 480;
	private Button button1;
	private Button button2;
	private EditText editText1;
	private Camera myCamera;
	private MediaPlayer mediaPlayer;
	private SurfaceView surfaceView;

	private Boolean hasSurface;
	private Boolean initialized;

	private Point screenPoint;
	private Point previewPoint;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		hasSurface = false;
		initialized = false;

		setContentView(R.layout.main);

		button1 = (Button) this.findViewById(R.id.button1);
		button2 = (Button) this.findViewById(R.id.button2);
		editText1 = (EditText) this.findViewById(R.id.editText1);
		surfaceView = (SurfaceView) findViewById(R.id.preview_view);

		button1.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				if (editText1.getText().toString().equals("") == false) {
					showDialog(editText1.getText().toString());
				}
			}
		});
		button1.setOnLongClickListener(new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				sendToS4A(editText1.getText().toString());

				return false;
			}
		});

		button2.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent();
				intent.setClassName("jp.gr.java_conf.ya.qrreader", "jp.gr.java_conf.ya.qrreader.PrefActivity");
				startActivity(intent);
			}
		});
		button2.setOnLongClickListener(new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				new AlertDialog.Builder(QrReaderActivity.this)
						.setTitle(R.string.app_name)
						.setMessage(
								getString(R.string.copyright) + "\n\n" + getString(R.string.license) + "\n"
										+ getString(R.string.zxing) + "\n")
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
							}
						}).create().show();

				return false;
			}
		});

		surfaceView.setOnTouchListener(new View.OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent arg1) {
				if (myCamera != null) {
					Camera.Parameters parameters = myCamera.getParameters();
					if (!parameters.getFocusMode().equals(Camera.Parameters.FOCUS_MODE_FIXED)) {
						myCamera.autoFocus(QrReaderActivity.this);
					}
				}
				return false;
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();

		SurfaceHolder holder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(holder);
		} else {
			holder.addCallback(this);
			holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
	}

	@Override
	protected void onPause() {
		closeCamera();
		if (!hasSurface) {
			SurfaceHolder holder = surfaceView.getHolder();
			holder.removeCallback(this);
		}
		super.onPause();
	}

	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}

	public void onAutoFocus(boolean success, Camera camera) {
		if (success)
			camera.setOneShotPreviewCallback(this);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent msg) {
		switch (keyCode) {
		case android.view.KeyEvent.KEYCODE_MENU:
		case android.view.KeyEvent.KEYCODE_CAMERA:
		case android.view.KeyEvent.KEYCODE_FOCUS:
		case android.view.KeyEvent.KEYCODE_VOLUME_UP:
			if (myCamera != null) {
				Camera.Parameters parameters = myCamera.getParameters();
				if (!parameters.getFocusMode().equals(Camera.Parameters.FOCUS_MODE_FIXED)) {
					myCamera.autoFocus(this);
				}
			}
			return true;
		case android.view.KeyEvent.KEYCODE_BACK:
			finish();
			return true;
		}
		return false;
	}

	public void playRingtone(String key) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		String ringtoneName = pref.getString(key, "");
		if (ringtoneName.equals("") == false) {
			if (mediaPlayer != null) {
				if (mediaPlayer.isPlaying()) {
					mediaPlayer.stop();
				}
			}
			mediaPlayer = MediaPlayer.create(this, Uri.parse(ringtoneName));
			mediaPlayer.setLooping(false);
			mediaPlayer.seekTo(0);
			mediaPlayer.start();
		}
	}

	public void onPreviewFrame(byte[] data, Camera camera) {
		playRingtone("focused");

		View finderView = (View) findViewById(R.id.viewfinder_view);
		int left = finderView.getLeft() * previewPoint.x / screenPoint.x;
		int top = finderView.getTop() * previewPoint.y / screenPoint.y;
		int width = finderView.getWidth() * previewPoint.x / screenPoint.x;
		int height = finderView.getHeight() * previewPoint.y / screenPoint.y;
		PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data,
				previewPoint.x, previewPoint.y, left, top, width, height, false);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		MultiFormatReader reader = new MultiFormatReader();

		String resultStr = "";
		try {
			Result result = reader.decode(bitmap);
			resultStr = result.getText();
		} catch (Exception e) {
			Toast.makeText(this, getString(R.string.cannot_read) + ": " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
		}

		if (resultStr.equals("") == false) {
			long time = 500;
			try {
				Thread.sleep(time);
			} catch (InterruptedException e) {
			}

			playRingtone("scanned");

			editText1.setText(resultStr);
			showDialog(resultStr);
		}
	}

	// ---------------------------------------------------------------------------------

	private void sendIntentText(String str) {
		Pattern EmailPtn = Pattern
				.compile(
						"[a-zA-Z0-9!#$%&'_`/=~\\*\\+\\-\\?\\^\\{\\|\\}]+"
								+ "(\\.[a-zA-Z0-9!#$%&'_`/=~\\*\\+\\-\\?\\^\\{\\|\\}]+)*"
								+ "@"
								+ "[a-zA-Z0-9][a-zA-Z0-9\\-]*(\\.[a-zA-Z0-9\\-]+)*",
						Pattern.CASE_INSENSITIVE);
		Matcher matcher_email = EmailPtn.matcher(str);
		if (matcher_email.find()) {
			String matcher_email_group = matcher_email
					.group(0);
			try {
				Uri uri = Uri.parse("mailto:"
						+ matcher_email_group);
				Intent intent_text = new Intent(
						Intent.ACTION_SENDTO, uri);
				// intent_text.putExtra(Intent.EXTRA_SUBJECT,
				// "");
				intent_text.putExtra(Intent.EXTRA_TEXT, str);
				intent_text
						.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent_text);
				// finish();
			} catch (ActivityNotFoundException e) {
				Log.d("SQRA",
						"send_intent_text_1 ActivityNotFoundException");
			} catch (Exception e) {
				Log.d("SQRA",
						"send_intent_text_1 Exception");
			}
		} else {
			try {
				Intent intent_text = new Intent();
				intent_text.setAction(Intent.ACTION_SEND);
				intent_text.setType("text/plain");
				intent_text.putExtra(Intent.EXTRA_TEXT, str);
				startActivity(intent_text);
				// finish();
			} catch (ActivityNotFoundException e) {
				Log.d("SQRA",
						"send_intent_text_2 ActivityNotFoundException");
			} catch (Exception e) {
				Log.d("SQRA",
						"send_intent_text_2 Exception");
			}
		}

	}

	private void sendIntentUri(String str) {
		try {
			Intent intent_uri = new Intent(
					Intent.ACTION_VIEW, Uri.parse(str));
			startActivity(intent_uri);
			// finish();
		} catch (ActivityNotFoundException e) {
			Log.d("SQRA",
					"send_intent_uri ActivityNotFoundException");
		} catch (Exception e) {
			Log.d("SQRA", "send_intent_uri Exception");
		}
	}

	private void sendIntentDial(String str) {
		try {
			Intent intent_dial = new Intent(
					Intent.ACTION_DIAL, Uri.parse("tel:" + str));
			startActivity(intent_dial);
			// finish();
		} catch (ActivityNotFoundException e) {
			Log.d("SQRA",
					"send_intent_dial ActivityNotFoundException");
		} catch (Exception e) {
			Log.d("SQRA", "send_intent_dial Exception");
		}

	}

	private void sendToS4A(String str) {
		try {
			Intent intent = new Intent();
			intent.setClassName(
					"jp.gr.java_conf.ya.shiobeforandroid",
					"jp.gr.java_conf.ya.shiobeforandroid.UpdateTweet");
			intent.setAction(Intent.ACTION_VIEW);
			intent.putExtra("str2", str);
			startActivity(intent);
			// finish();
		} catch (ActivityNotFoundException e) {
			sendIntentText(str);

			Log.d("SQRA",
					"send_intent_dial ActivityNotFoundException");
		} catch (Exception e) {
			Log.d("SQRA", "send_intent_dial Exception");
		}

	}

	private void showDialog(String resultStr) {
		final EditText editView = new EditText(QrReaderActivity.this);
		editView.setText(resultStr);
		final String[] ITEM = new String[] {
				getString(R.string.send_intent_dial),
				getString(R.string.send_intent_uri),
				getString(R.string.send_intent_text),
				getString(R.string.send_to_s4a) };
		new AlertDialog.Builder(this)
				.setTitle(R.string.send_intent)
				.setView(editView)
				.setItems(ITEM, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (ITEM[which].equals(getString(R.string.send_to_s4a))) {
							sendToS4A(editView.getText().toString());
						} else if (ITEM[which].equals(getString(R.string.send_intent_text))) {
							sendIntentText(editView.getText().toString());
						} else if (ITEM[which].equals(getString(R.string.send_intent_uri))) {
							sendIntentUri(editView.getText().toString());
						} else if (ITEM[which].equals(getString(R.string.send_intent_dial))) {
							sendIntentDial(editView.getText().toString());
						}
					}
				}).create().show();
	}

	private void initCamera(SurfaceHolder holder) {
		try {
			openCamera(holder);
		} catch (Exception e) {
			Log.w(TAG, e);
		}
	}

	private void openCamera(SurfaceHolder holder) throws IOException {
		if (myCamera == null) {
			myCamera = Camera.open();
			if (myCamera == null) {
				throw new IOException();
			}
		}
		myCamera.setPreviewDisplay(holder);

		if (!initialized) {
			initialized = true;
			initFromCameraParameters(myCamera);
		}

		setCameraParameters(myCamera);
		myCamera.startPreview();
	}

	private void closeCamera() {
		if (myCamera != null) {
			myCamera.stopPreview();
			myCamera.release();
			myCamera = null;
		}
	}

	private void setCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();

		parameters.setPreviewSize(previewPoint.x, previewPoint.y);
		camera.setParameters(parameters);
	}

	private void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager) getApplication().getSystemService(
				Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();

		if (width < height) {
			int tmp = width;
			width = height;
			height = tmp;
		}

		screenPoint = new Point(width, height);
		Log.d(TAG, "screenPoint = " + screenPoint);
		previewPoint = findPreviewPoint(parameters, screenPoint, false);
		Log.d(TAG, "previewPoint = " + previewPoint);
	}

	private Point findPreviewPoint(Camera.Parameters parameters, Point screenPoint,
			boolean portrait) {
		Point previewPoint = null;
		int diff = Integer.MAX_VALUE;

		for (Camera.Size supportPreviewSize : parameters.getSupportedPreviewSizes()) {
			int pixels = supportPreviewSize.width * supportPreviewSize.height;
			if (pixels < MIN_PREVIEW_PIXCELS || pixels > MAX_PREVIEW_PIXCELS) {
				continue;
			}

			int supportedWidth = portrait ? supportPreviewSize.height
					: supportPreviewSize.width;
			int supportedHeight = portrait ? supportPreviewSize.width
					: supportPreviewSize.height;
			int newDiff = Math.abs(screenPoint.x * supportedHeight - supportedWidth
					* screenPoint.y);

			if (newDiff == 0) {
				previewPoint = new Point(supportedWidth, supportedHeight);
				break;
			}

			if (newDiff < diff) {
				previewPoint = new Point(supportedWidth, supportedHeight);
				diff = newDiff;
			}
		}
		if (previewPoint == null) {
			Camera.Size defaultPreviewSize = parameters.getPreviewSize();
			previewPoint = new Point(defaultPreviewSize.width, defaultPreviewSize.height);
		}

		return previewPoint;
	}
}
