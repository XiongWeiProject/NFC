package com.example.nfcdome;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.nfcdome.databinding.ActivityMainBinding;

import java.nio.charset.Charset;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    ActivityMainBinding mainBinding;
    String fmtUID = "";
    NfcAdapter adapter;
    PendingIntent mPendingIntent;
    IntentFilter[]mIntentFilter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        mainBinding.btnReadId.setOnClickListener(this);
        mainBinding.btnWriteId.setOnClickListener(this);
        setContentView(mainBinding.getRoot());
        adapter = NfcAdapter.getDefaultAdapter(this);
        if (null == adapter) {
            Toast.makeText(this, "不支持NFC功能", Toast.LENGTH_SHORT).show();
        } else if (!adapter.isEnabled()) {
            Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
            // 根据包名打开对应的设置界面
            startActivity(intent);
        }
        mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter filter2 = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        try {
            filter.addDataType("*/*");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            e.printStackTrace();
        }
       mIntentFilter = new IntentFilter[]{filter, filter2};
//一单截获NFC的消息，就调用PendingIntent来激活窗口
//        mPendingIntent = PendingIntent.getActivity(this , 0 , new Intent(this , getClass()) , 0);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_readId:
                mainBinding.tvShow.setText("读取的IC卡ID:".toLowerCase() + " (" + fmtUID + ")");
                break;
            case R.id.btn_writeId:
                mainBinding.tvShow.setText("写入NFC数据:");
                break;
        }

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            tag = MCReader.patchTag(tag);
            //检测到新的标签，请求接口
            fmtUID = byte2HexString(tag.getId());
            Toast.makeText(this,
                    "读取的IC卡ID"
                            .toLowerCase() + " (" + fmtUID + ")",
                    Toast.LENGTH_SHORT).show();
        }
        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        writeNFCTag(detectedTag);

    }
    /*
     * 往NFC标签中写数据主要在该方法中体现
     */
    private void writeNFCTag(Tag tag){
        if(tag == null){
            return;
        }

        NdefMessage ndefMessage = new NdefMessage( new NdefRecord[]{NdefRecord.createUri(Uri.parse("http://www.baidu.com"))} );
        int size = ndefMessage.toByteArray().length;

        try {
            Ndef ndef = Ndef.get(tag);
            //先判断一下这个标签是不是NDEF的
            if(ndef != null){ //如果是NDEF格式的
                ndef.connect();
                //再来判断这个标签是否是可写的
                if( ! ndef.isWritable()){ //如果是不可写的，直接就可以结束了
                    Toast.makeText(this , "该NFC标签不可写!" , Toast.LENGTH_SHORT).show();
                    return;
                }
                //再来判断当前标签的最大容量是否能装下我们要写入的信息
                if(ndef.getMaxSize() < size){
                    Toast.makeText(this , "该NFC标签的最大可写容量太小!" , Toast.LENGTH_SHORT).show();
                    return;
                }
                //到此为止，就可以放心的把东西写入NFC标签中了
                ndef.writeNdefMessage(ndefMessage);
                Toast.makeText(this , "NFC标签写入内容成功" , Toast.LENGTH_SHORT).show();
            }
            else{ //如果不是NDEF格式的
                //尝试将这个非NDEF标签格式化成NDEF格式的
                NdefFormatable format = NdefFormatable.get(tag);
                //因为有些标签是只读的，所以这里需要判断一下
                //如果format不为null，表示这个标签是可以接受格式化的
                if(format != null){
                    format.connect();
                    format.format(ndefMessage); //同时完成了格式化和写入信息的操作
                    Toast.makeText(this , "NFC标签格式化写入成功" , Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(this , "该NFC标签无法被格式化" , Toast.LENGTH_SHORT).show();
                }
            }
        }
        catch (Exception e) {
            Toast.makeText(this , "无法读取该NFC标签" , Toast.LENGTH_SHORT).show();
        }
    }


    public static boolean writeData(Intent intent) {
        String mText = "NFC-DengB-123";
        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        NdefMessage ndefMessage = new NdefMessage(
                new NdefRecord[]{createTextRecord(mText)});
        boolean result = writeTag(ndefMessage, detectedTag);

        return result;
    }
    /**
     * 创建NDEF文本数据
     *
     * @param text
     * @return
     */
    private static NdefRecord createTextRecord(String text) {
        byte[] langBytes = Locale.CHINA.getLanguage().getBytes(Charset.forName("US-ASCII"));
        Charset utfEncoding = Charset.forName("UTF-8");
        //将文本转换为UTF-8格式
        byte[] textBytes = text.getBytes(utfEncoding);
        //设置状态字节编码最高位数为0
        int utfBit = 0;
        //定义状态字节
        char status = (char) (utfBit + langBytes.length);
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        //设置第一个状态字节，先将状态码转换成字节
        data[0] = (byte) status;
        //设置语言编码，使用数组拷贝方法，从0开始拷贝到data中，拷贝到data的1到langBytes.length的位置
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        //设置文本字节，使用数组拷贝方法，从0开始拷贝到data中，拷贝到data的1 + langBytes.length
        //到textBytes.length的位置
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
        //通过字节传入NdefRecord对象
        //NdefRecord.RTD_TEXT：传入类型 读写
        NdefRecord ndefRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
                NdefRecord.RTD_TEXT, new byte[0], data);
        return ndefRecord;
    }
    /**
     * 写数据
     *
     * @param ndefMessage 创建好的NDEF文本数据
     * @param tag         标签
     * @return
     */
    private static boolean writeTag(NdefMessage ndefMessage, Tag tag) {
        try {
            Ndef ndef = Ndef.get(tag);
            ndef.connect();
            ndef.writeNdefMessage(ndefMessage);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String byte2HexString(byte[] bytes) {
        StringBuilder ret = new StringBuilder();
        if (bytes != null) {
            for (Byte b : bytes) {
                ret.append(String.format("%02X", b.intValue() & 0xFF));
            }
        }
        return ret.toString();
    }
    @Override
    protected void onResume() {
        super.onResume();

        adapter.enableForegroundDispatch(this, mPendingIntent, mIntentFilter, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //关闭前台调度系统
        adapter.disableForegroundDispatch(this);
    }
}
