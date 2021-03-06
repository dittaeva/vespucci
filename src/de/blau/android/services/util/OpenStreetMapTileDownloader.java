// Created by plusminus on 21:31:36 - 25.09.2008
package de.blau.android.services.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.RemoteException;
import android.util.Log;
import de.blau.android.Application;
import de.blau.android.services.IOpenStreetMapTileProviderCallback;
import de.blau.android.views.util.OpenStreetMapTileServer;

/**
 * The OpenStreetMapTileDownloader loads tiles from a server and passes them to
 * a OpenStreetMapTileFilesystemProvider.<br/>
 * This class was taken from OpenStreetMapViewer (original package org.andnav.osm) in 2010-06
 * by Marcus Wolschon to be integrated into the de.blau.androin
 * OSMEditor. 
 * @author Nicolas Gramlich
 * @author Marcus Wolschon <Marcus@Wolschon.biz
 * @author Manuel Stahl
 *
 */
public class OpenStreetMapTileDownloader extends OpenStreetMapAsyncTileProvider {
	// ===========================================================
	// Constants
	// ===========================================================

	private static final String DEBUGTAG = "OSM_DOWNLOADER";

	// ===========================================================
	// Fields
	// ===========================================================

	protected final Context mCtx;
	protected final OpenStreetMapTileFilesystemProvider mMapTileFSProvider;

	// ===========================================================
	// Constructors
	// ===========================================================

	public OpenStreetMapTileDownloader(final Context ctx, final OpenStreetMapTileFilesystemProvider aMapTileFSProvider){
		mCtx = ctx;
		mMapTileFSProvider = aMapTileFSProvider;
		mThreadPool = Executors.newFixedThreadPool(4);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods from SuperClass/Interfaces
	// ===========================================================

	protected Runnable getTileLoader(OpenStreetMapTile aTile, IOpenStreetMapTileProviderCallback aCallback) {
		return new TileLoader(aTile, aCallback);
	};
	
	// ===========================================================
	// Methodsorg.andnav.osm.services
	// ===========================================================

	private String buildURL(final OpenStreetMapTile tile) {
		OpenStreetMapTileServer renderer = OpenStreetMapTileServer.get(mCtx.getResources(), tile.rendererID, false);
		return renderer.isMetadataLoaded() ? renderer.getTileURLString(tile) : "";
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
	
	private class TileLoader extends OpenStreetMapAsyncTileProvider.TileLoader {
		
		public TileLoader(final OpenStreetMapTile aTile, final IOpenStreetMapTileProviderCallback aCallback) {
			super(aTile, aCallback);
		}
		
		//@Override
		public void run() {
			InputStream in = null;
			OutputStream out = null;
			
			String tileURLString = buildURL(mTile);
			
			try {
				if (tileURLString.length() > 0) {
					if(Log.isLoggable(DEBUGTAG, Log.DEBUG))
						Log.d(DEBUGTAG, "Downloading Maptile from url: " + tileURLString);
					
					URLConnection conn = new URL(tileURLString).openConnection();
					conn.setRequestProperty("User-Agent", Application.userAgent);
					in = new BufferedInputStream(conn.getInputStream(), StreamUtils.IO_BUFFER_SIZE);
					
					final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
					out = new BufferedOutputStream(dataStream, StreamUtils.IO_BUFFER_SIZE);
					StreamUtils.copy(in, out);
					out.flush();
					
					final byte[] data = dataStream.toByteArray();
					
					if (data.length == 0) {
						throw new IOException("no tile data");
					}
					Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
					if (b == null) {
						throw new IOException("decodeByteArray returned null");
					}
					OpenStreetMapTileDownloader.this.mMapTileFSProvider.saveFile(mTile, data);
					if(Log.isLoggable(DEBUGTAG, Log.DEBUG)) {
						Log.d(DEBUGTAG, "Maptile saved to: " + tileURLString);
					}
					mCallback.mapTileLoaded(mTile.rendererID, mTile.zoomLevel, mTile.x, mTile.y, b);
				}
			} catch (IOException e) {
				try {
					mCallback.mapTileFailed(mTile.rendererID, mTile.zoomLevel, mTile.x, mTile.y);
				} catch (RemoteException e1) {
					Log.e(DEBUGTAG, "Error calling mCallback for MapTile. Exception: " + e.getClass().getSimpleName(), e);
				}
				if(Log.isLoggable(DEBUGTAG, Log.ERROR)) {
					Log.e(DEBUGTAG, "Error Downloading MapTile. Exception: " + e.getClass().getSimpleName(), e);
				}
				/* TODO What to do when downloading tile caused an error?
				 * Also remove it from the mPending?
				 * Doing not blocks it for the whole existence of this TileDownloder.
				 * -> we remove it and the application has to re-request it.
				 */
			} catch (RemoteException re) {
			} finally {
				StreamUtils.closeStream(in);
				StreamUtils.closeStream(out);
				finished();
			}
		}
	};
	
}
