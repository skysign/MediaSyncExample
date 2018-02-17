package org.mediasyncexample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaSync;
import android.media.PlaybackParams;
import android.media.SyncParams;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback{
    final static String TAG = "MainActivity";
    protected static final int REQUEST_PERMISSION = 100;

    private SurfaceView msvPlay;
    private MediaExtractor mExtractor;
    private MediaCodec mVideoDecoder;
    private int miVideoTrack;
    private MediaCodec mAudioDecoder;
    private int miAudioTrack;
    private int mnSampleRate;
    private int mnChannel;
    private MainActivity mThis;

    private MediaSync mMediaSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mThis = this;

        msvPlay = (SurfaceView)findViewById(R.id.svPlay);
        SurfaceHolder sh = msvPlay.getHolder();
        sh.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged "+format+" "+width+" "+height);

        AssetFileDescriptor file = this.getResources().openRawResourceFd(R.raw.a712989584);

        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaSync = new MediaSync();
        mMediaSync.setSurface(surfaceHolder.getSurface());

        for (int i=0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat mediaFormat = mExtractor.getTrackFormat(i);

            String strMime = mediaFormat.getString(MediaFormat.KEY_MIME);

            if (strMime.startsWith("video/")) {
                miVideoTrack = i;
                try {
                    mVideoDecoder = MediaCodec.createDecoderByType(strMime);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Surface surface = mMediaSync.createInputSurface();

                mVideoDecoder.configure(mediaFormat, surface, null, 0);
                mVideoDecoder.setCallback(new MediaCodec.Callback() {
                    @Override
                    public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
                        ByteBuffer byteBuffer = mVideoDecoder.getInputBuffer(i);
                        mExtractor.selectTrack(miVideoTrack);
                        int nRead = mExtractor.readSampleData(byteBuffer, 0);

                        Log.d("Video", "onInputBufferAvailable i "+i+" nRead " + nRead);

                        if (nRead < 0) {
                            mVideoDecoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                        else {
                            mVideoDecoder.queueInputBuffer(i, 0, nRead, mExtractor.getSampleTime(), 0);
                            mExtractor.advance();
                        }
                    }

                    @Override
                    public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
                        if (0 != (MediaCodec.BUFFER_FLAG_END_OF_STREAM & bufferInfo.flags)) {
                            Log.d("Video", "onOutputBufferAvailable BUFFER_FLAG_END_OF_STREAM");
//                            return;
                        }

                        mVideoDecoder.releaseOutputBuffer(i, bufferInfo.presentationTimeUs * 1000);
                        Log.d("Video", "onOutputBufferAvailable i "+ i +" presentationTimeUs " + bufferInfo.presentationTimeUs);
                    }

                    @Override
                    public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
                        Log.d("Video", "onError");
                        e.printStackTrace();
                    }

                    @Override
                    public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
                        Log.d("Video", "onOutputFormatChanged");
                    }
                });
            }

            if (strMime.startsWith("audio/")) {
                miAudioTrack = i;
                try {
                    mAudioDecoder = MediaCodec.createDecoderByType(strMime);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                mnSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                mnChannel = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                mAudioDecoder.configure(mediaFormat, null, null, 0);
                mAudioDecoder.setCallback(new MediaCodec.Callback() {
                    @Override
                    public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
                        mExtractor.selectTrack(miAudioTrack);
                        ByteBuffer byteBuffer = mAudioDecoder.getInputBuffer(i);
                        int nRead = mExtractor.readSampleData(byteBuffer, 0);

                        Log.d("Audio", "onInputBufferAvailable i "+i+" nRead " + nRead);

                        if (nRead < 0) {
                            mAudioDecoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                        else {
                            mAudioDecoder.queueInputBuffer(i, 0, nRead, mExtractor.getSampleTime(), 0);
                            mExtractor.advance();
                        }
                    }

                    @Override
                    public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
                        ByteBuffer byteBuffer = mAudioDecoder.getOutputBuffer(i);
                        mMediaSync.queueAudio(byteBuffer, i, bufferInfo.presentationTimeUs);

                        Log.d("Audio", "onOutputBufferAvailable i "+i+" presentationTimeUs " + bufferInfo.presentationTimeUs);
                    }

                    @Override
                    public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
                        Log.d("Audio", "onError");
                        e.printStackTrace();
                    }

                    @Override
                    public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
                        Log.d("Audio", "onOutputFormatChanged");
                    }
                });

                int buffsize = AudioTrack.getMinBufferSize(mnSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mnSampleRate,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        buffsize,
                        AudioTrack.MODE_STREAM);
                mMediaSync.setAudioTrack(audioTrack);

                mMediaSync.setCallback(new MediaSync.Callback() {
                    @Override
                    public void onAudioBufferConsumed(@NonNull MediaSync mediaSync, @NonNull ByteBuffer byteBuffer, int i) {
                        mAudioDecoder.releaseOutputBuffer(i, false);
                        Log.d("MediaSync", "onAudioBufferConsumed i " + i);
                    }
                }, null);
            }
        }

        mMediaSync.setOnErrorListener(new MediaSync.OnErrorListener() {
            @Override
            public void onError(@NonNull MediaSync mediaSync, int i, int i1) {
                Log.d("MediaSync", "onError "+i +" " + i1);
            }
        }, null);
        mMediaSync.setSyncParams(new SyncParams().allowDefaults());
        mMediaSync.setPlaybackParams(new PlaybackParams().setSpeed(1.0f));
        Log.d("MediaSync", "start");
//        mAudioDecoder.start();
//        Log.d("mAudioDecoder", "start");
        mVideoDecoder.start();
        Log.d("mVideoDecoder", "start");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed");
    }
}
