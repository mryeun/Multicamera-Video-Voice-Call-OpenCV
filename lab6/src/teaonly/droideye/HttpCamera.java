package teaonly.droideye;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

public class HttpCamera {

	private static final int CONNECT_TIMEOUT = 1000;
	private static final int SOCKET_TIMEOUT = 1000;

	private String url;
	private Rect bounds;
	private boolean preserveAspectRatio;
	private Paint paint = new Paint();
	private int width,height;

	public HttpCamera(String url, int width, int height,
			boolean preserveAspectRatio) {
		this.url = url;
		this.width = width;
		this.height = height;
		bounds = new Rect(0, 0, width, height);
		this.preserveAspectRatio = preserveAspectRatio;

		paint.setFilterBitmap(true);
		paint.setAntiAlias(true);
	}

	private Bitmap retrieveBitmap() throws IOException {
		Bitmap bitmap = null;
		InputStream in = null;
		int response = -1;
		URL myFileUrl;
		try {
			myFileUrl = new URL(url); // 파라미터로 넘어온 Url을 myFileUrl에 대입합니다.

			// 실질적인 통신이 이루어지는 부분입니다.
			// myFileUrl 로 접속을 시도합니다.
			HttpURLConnection conn = (HttpURLConnection) myFileUrl
					.openConnection();

			conn.setDoInput(true);
			conn.connect();
			response = conn.getResponseCode();

			in = conn.getInputStream(); 
			bitmap = BitmapFactory.decodeStream(in); 
			Matrix matrix = new Matrix();
			matrix.postRotate(180);
			bitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix, true);
			return bitmap;
		} catch (IOException e) // 예외처리를 해줍니다.
		{
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {

				}
			}
		}
	}

	public boolean captureAndDraw(Canvas canvas) throws IOException {
		Bitmap bitmap = retrieveBitmap();

		if (bitmap == null)
			throw new IOException("Response Code : ");

		if (bounds.right == bitmap.getWidth()
				&& bounds.bottom == bitmap.getHeight()) {
			canvas.drawBitmap(bitmap, 0, 0, null);
		} else {
			Rect dest;
			if (preserveAspectRatio) {
				dest = new Rect(bounds);
				dest.bottom = bitmap.getHeight() * bounds.right
						/ bitmap.getWidth();
				dest.offset(0, (bounds.bottom - dest.bottom) / 2);
			} else {
				dest = bounds;
			}
			canvas.drawBitmap(bitmap, null, dest, paint);
		}
		return true;
	}
}
