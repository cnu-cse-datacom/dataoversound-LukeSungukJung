package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.List;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone(){

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();




    }
    private int findPowerSize(int sound){
            int a = 0;
            while(true) {
                a++;
                if(Math.pow(2,a) >= sound)
                    break;
            }
        return (int)Math.pow(2,a);
    }

    private double findFrequency(double[] toTransform) {
        int len = toTransform.length;
        double[] real =  new double[len];
        double[] img =  new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);
        Double[]freqs = this.fftfreq(complx.length,1);

        for(int i=0;i<complx.length;i++){
            realNum = complx[i].getReal();
            //Log.d("real num:",Double.toString(realNum));
            imgNum = complx[i].getImaginary();
            //Log.d("real num:",Double.toString(imgNum));
            mag[i] = Math.sqrt((realNum*realNum)+imgNum*imgNum);
            //Log.d("real mag:",Double.toString(mag[i]));
        }

        int peak_coeff =0;
        double max_w =0;
        for(int i=0;i<mag.length;i++){
            if(max_w<Math.abs(mag[i])){
                max_w=Math.abs(mag[i]);
                peak_coeff=i;
            }
        }
        double peak_freq = freqs[peak_coeff];
        Log.d("fft peak_freq:",Double.toString(peak_freq));
    return (int)Math.abs(peak_freq*mSampleRate);
    }

    private Double[] fftfreq(int length, int d) {
        //Log.d("fft length:",Integer.toString(length[i]));
        double pr = 1.0 / (length * d);

        int[] children = new int[length];
        Double[] results = new Double[children.length];

        int ele_counter = (length-1)/2 + 1;


        for(int i=0; i<=ele_counter; i++){
            children[i] = i;
            //Log.d("fft chil res:",Integer.toString(children[i]));
        }

        int temp = -(length / 2);

        for(int i=ele_counter+1; i<length; i++) {
            children[i] = temp;
            temp--;
        }

        for(int i = 0; i<length; i++){
            results[i] = children[i] * pr;
            //Log.d("fft res:",Double.toString(results[i]));
        }
        return results;

    }
    boolean match(double freq1, double freq2) {
        return Math.abs(freq1 - freq2) < 20 ;
    }

    private List<Integer> extract_packet(ArrayList<Double> freqs){
        ArrayList<Double>  sampled_freqs = new ArrayList<Double>();
        ArrayList<Integer>  bit_chunks = new ArrayList<Integer>();
        ArrayList<Integer>  real_bit_chunks = new ArrayList<Integer>();
        for(int i=0;i<freqs.size();i++){
            sampled_freqs.add((freqs.get(i)));
        }
        for(int i= 0; i<sampled_freqs.size(); i++){
            int freq = (int)(Math.round((sampled_freqs.get(i) -START_HZ)/STEP_HZ));
            bit_chunks.add(freq);
        }
        for(int i=1; i<bit_chunks.size(); i++) {
            if (bit_chunks.get(i) > 0 && bit_chunks.get(i) < Math.pow(2,BITS)) {
                real_bit_chunks.add(bit_chunks.get(i));
            }
        }

        return decode_bitchunks(BITS,real_bit_chunks);
    }

    private List<Integer> decode_bitchunks(int bits, List<Integer> real_bit_chunks){
        List<Integer> out_bytes = new ArrayList<>();
        int next_read_chunk = 0 ;
        int next_read_bit = 0 ;
        int val_ = 0;
        int bits_left = 8 ;

        while(next_read_chunk < real_bit_chunks.size()) {
            int can_fill = bits - next_read_bit;
            int to_fill = Math.min(bits_left, can_fill);
            int offset = bits - next_read_bit - to_fill;
            val_ <<= to_fill;
            int shifted = real_bit_chunks.get(next_read_chunk) & (((1 << to_fill) - 1) << offset);
            val_ |= shifted >> offset;
            bits_left -= to_fill;
            next_read_bit += to_fill;

            if (bits_left <= 0) {
                out_bytes.add(val_);
                val_ = 0;
                bits_left = 8;
            }

            if (next_read_bit >= bits){
                next_read_chunk += 1;
                next_read_bit -= bits;
            }
        }
        return out_bytes  ;
    }

    public void PreRequest() {
        int blocksize= findPowerSize((int) (long) Math.round(interval / 2 * mSampleRate));
        Log.d("listen num frame:",Integer.toString(blocksize));
        short[] buffer = new short[blocksize];
        double[] chunks = new double[blocksize];

        ArrayList<Double> packet = new ArrayList<Double>();
        List<Integer> byte_stream = new ArrayList<>();
        boolean in_packet = false;

        while(true){
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
            for(int i=0;i<buffer.length;i++){
                chunks[i] = buffer[i];
            }
            double dom = findFrequency(chunks);

            if(in_packet && match(dom,HANDSHAKE_END_HZ)) {
                byte_stream = extract_packet(packet);
                Log.d("byte_stream", byte_stream.toString());
                String disaply_str = "";
                int print_size = byte_stream.size();
                for(int i = 0 ; i < print_size ; i++){
                    disaply_str += Character.toString((char)(int)(byte_stream.get(i)));
                }
                Log.d("display_str : ", disaply_str.toString());
                packet.clear();
                break;
            }else if(in_packet) {
                packet.add(dom);
                Log.d("int dom : ", Integer.toString((int)(dom)));
            }else if(match(dom,HANDSHAKE_START_HZ))
                in_packet =true;
            Log.d("dom", Double.toString(dom));



        }

    }


}