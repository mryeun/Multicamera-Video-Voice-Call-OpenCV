package teaonly.droideye;

import java.text.AttributedCharacterIterator.Attribute;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class HttpCameraPreview extends SurfaceView implements
		SurfaceHolder.Callback {

	private Context context;

	private CanvasThread canvasThread;
	private String url;

	private SurfaceHolder holder;
	private HttpCamera camera;

	private int viewWidth;
	private int viewHeight;

	public HttpCameraPreview(Context context) {
		super(context);
		// HttpCameraPreview asd = new HttpCameraPreview(context, 100, 100);
		// TODO Auto-generated constructor stub
		// holder = getHolder();
		// holder.addCallback(this);
		// holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
		// this.viewWidth = 100;
		// this.viewHeight = 100;
		// canvasThread = new CanvasThread();
	}

	public HttpCameraPreview(Context context, AttributeSet attr) {
		super(context, attr);
		this.context = context;

		holder = getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
		canvasThread = new CanvasThread();
	}

	public HttpCameraPreview(Context context, int viewWidth, int viewHeight,
			String url) {
		super(context);
		this.url = url;
		holder = getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;

		camera = null;
		canvasThread = null;
		camera = new HttpCamera(url, viewWidth, viewHeight, true);
		canvasThread = new CanvasThread();
		canvasThread.setRunning(true);
		canvasThread.start();
	}

	// private void calculateDisplayDimensions() {
	// Display display = getWindowManager().getDefaultDisplay();
	// viewWidth = display.getWidth();
	// viewHeight = display.getHeight();
	// }

	public void setDimension(int viewWidth, int viewHeight) {
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void surfaceCreated(SurfaceHolder holder) {
		camera = new HttpCamera(url, viewWidth, viewHeight, true);
		canvasThread.setRunning(true);
		canvasThread.start();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		try {
			Canvas c = holder.lockCanvas(null);
			if (camera != null)
				camera.captureAndDraw(c);

			if (c != null && camera != null)
				holder.unlockCanvasAndPost(c);
		} catch (Exception e) {
			Log.e(getClass().getSimpleName(), "Error when surface changed", e);
			camera = null;
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		camera = null;
		boolean retry = true;
		canvasThread.setRunning(false);
		while (retry) {
			try {
				canvasThread.join();
				retry = false;
			} catch (InterruptedException e) {

			}
		}
	}

	private class CanvasThread extends Thread {
		private boolean running;

		public void setRunning(boolean running) {
			this.running = running;
		}

		public void run() {
			while (running) {
				Canvas c = null;
				try {
					c = holder.lockCanvas(null);
					synchronized (holder) {
						camera.captureAndDraw(c);
					}
				} catch (Exception e) {

				} finally {
					if (c != null)
						holder.unlockCanvasAndPost(c);
				}
			}
		}
	}
}
