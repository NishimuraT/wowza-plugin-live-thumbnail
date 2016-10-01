package co.nisimura;

import java.awt.image.*;
import javax.imageio.*;
import java.io.*;

import com.wowza.wms.amf.*;
import com.wowza.wms.client.*;
import com.wowza.wms.request.*;
import com.wowza.wms.module.*;
import com.wowza.wms.application.*;
import com.wowza.wms.stream.*;
import com.wowza.wms.transcoder.model.*;
import com.wowza.wms.transcoder.util.*;

import com.wowza.wms.stream.livetranscoder.ILiveStreamTranscoder;
import com.wowza.wms.stream.livetranscoder.ILiveStreamTranscoderNotify;
import com.wowza.wms.transcoder.model.LiveStreamTranscoder;
import com.wowza.wms.transcoder.model.LiveStreamTranscoderActionNotifyBase;
import com.wowza.wms.transcoder.model.TranscoderSession;
import com.wowza.wms.transcoder.model.TranscoderSessionVideo;
import com.wowza.wms.transcoder.model.TranscoderSessionVideoEncode;
import com.wowza.wms.transcoder.model.TranscoderStream;
import com.wowza.wms.transcoder.model.TranscoderStreamDestination;
import com.wowza.wms.transcoder.model.TranscoderStreamDestinationVideo;
import com.wowza.wms.transcoder.model.TranscoderStreamSourceVideo;
import com.wowza.wms.transcoder.model.TranscoderVideoDecoderNotifyBase;
import com.wowza.wms.transcoder.model.TranscoderVideoOverlayFrame;

import java.util.ArrayList;
import java.util.List;
import java.awt.Graphics;

public class SeekPreview extends ModuleBase {

private IApplicationInstance appInstance = null;
public static int count = 0;

	class GrabResult implements ITranscoderFrameGrabResult
	{
		String id;
		int count = 0;
		List<BufferedImage> bufferedImage = new ArrayList<>();
		
		public GrabResult(String id) {
			this.id = id;
		}
		
		synchronized public void onGrabFrame(TranscoderNativeVideoFrame videoFrame)
		{
			BufferedImage image = TranscoderStreamUtils.nativeImageToBufferedImage(videoFrame);
			getLogger().info("SeekPreview#GrabResult.onGrabFrame: "+image.getWidth()+"x"+image.getHeight());
			
			if (image != null) {
				this.bufferedImage.add(image);
			}
			
			if (this.bufferedImage.size() >= 9) {
				getLogger().info("SeekPreview#GrabResult.onGrabFrame complete");
				int w = this.bufferedImage.get(0).getWidth();
				int h = this.bufferedImage.get(0).getHeight();
				BufferedImage img = new BufferedImage(w * 3, h * 3, BufferedImage.TYPE_INT_ARGB);
				Graphics g = img.getGraphics();
				int index = 0;
				for (int i = 0;i < 3;i++) {
					for (int l = 0;l < 3;l++) {
						g.drawImage(this.bufferedImage.get(index), w * l, h * i, null);
						index++;
					}
				}
				
				String storageDir = appInstance.getStreamStoragePath();
				String filePath = storageDir + "/thumbnail_"+this.id+"_" + this.count + ".png";
				File pngFile = new File(filePath);
				
				try {
					if (pngFile.exists())
						pngFile.delete();
					
					ImageIO.write(img, "png", pngFile);
					getLogger().info("SeekPreview#GrabResult.onGrabFrame: Save image: " + pngFile);
				} catch(Exception e) {
					getLogger().error("SeekPreview.grabFrame: File write error: "+pngFile);
				}
				
				this.count++;
				this.bufferedImage = new ArrayList<>();
			}
		}
	}
	
	public void onAppStart(IApplicationInstance appInstance)
	{
		getLogger().info("SeekPreview.onAppStart["+appInstance.getContextStr()+"]");
		this.appInstance = appInstance;
		this.appInstance.addLiveStreamTranscoderListener(new TranscoderCreateNotifierExample());
	}
	
	class TranscoderCreateNotifierExample implements ILiveStreamTranscoderNotify
	{
		public void onLiveStreamTranscoderCreate(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
			getLogger().info("SeekPreview#TranscoderCreateNotifierExample.onLiveStreamTranscoderCreate["+appInstance.getContextStr()+"]: "+stream.getName());
			((LiveStreamTranscoder)liveStreamTranscoder).addActionListener(new TranscoderActionNotifierExample());
		}

		public void onLiveStreamTranscoderDestroy(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
		}

		public void onLiveStreamTranscoderInit(ILiveStreamTranscoder liveStreamTranscoder, IMediaStream stream)
		{
		}	
	}
	
	class TranscoderActionNotifierExample extends LiveStreamTranscoderActionNotifyBase
	{
		TranscoderVideoDecoderNotifyExample transcoder=null;

		public void onSessionVideoEncodeSetup(LiveStreamTranscoder liveStreamTranscoder, TranscoderSessionVideoEncode sessionVideoEncode)
		{
			getLogger().info("SeekPreview#TranscoderActionNotifierExample.onSessionVideoEncodeSetup["+appInstance.getContextStr()+"]");
			TranscoderStream transcoderStream = liveStreamTranscoder.getTranscodingStream();
			if (transcoderStream != null && transcoder==null)
			{
//				List<TranscoderStreamDestination> alltrans = transcoderStream.getDestinations();
//				for(TranscoderStreamDestination destination:alltrans)
//				{
//					//TranscoderSessionVideoEncode sessionVideoEncode = transcoderVideoSession.getEncode(destination.getName());
//					TranscoderStreamDestinationVideo videoDestination = destination.getVideo();
//					videoDestination.grabFrame(new GrabResult());
//				}
				
				TranscoderSession transcoderSession = liveStreamTranscoder.getTranscodingSession();
				TranscoderSessionVideo transcoderVideoSession = transcoderSession.getSessionVideo();
				transcoder = new TranscoderVideoDecoderNotifyExample();
				transcoderVideoSession.addFrameListener(transcoder);
			}
			return;
		}
	}
	
	class TranscoderVideoDecoderNotifyExample extends TranscoderVideoDecoderNotifyBase 
	{
		private GrabResult grabResult;
		
		public TranscoderVideoDecoderNotifyExample () {
			String id = ""+SeekPreview.count;
			SeekPreview.count++;
			this.grabResult = new GrabResult(id);
		}
		
		public void onAfterDecodeFrame(TranscoderSessionVideo sessionVideo, TranscoderStreamSourceVideo sourceVideo, long frameCount) {
			getLogger().info("nishi getDecoderCurrentFrame: "+sessionVideo.getDecoderCurrentFrame());
			getLogger().info("nishi getDecoderFrameRate: "+sessionVideo.getDecoderFrameRate());
			getLogger().info("nishi frameCount: "+frameCount);
			
			if ((int)sessionVideo.getDecoderCurrentFrame() % (int)sessionVideo.getDecoderFrameRate() * 6 == 0) {
				sourceVideo.grabFrame(this.grabResult, 160, 90);
			}
		}
	}

}
