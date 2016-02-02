package org.trace.tracker.location.modules;


import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.trace.tracker.ModuleInterface;
import org.trace.tracker.Profile;
import org.trace.tracker.utils.MyUtil;
import org.trace.tracker.utils.NFCRecordParser;

public class NFCModule implements ModuleInterface{

    private static final String TAG = NFCModule.class.getSimpleName();

    private static Context context;
    private static HashMap<String, Profile.SecurityLevel> registeredApps;


    public NFCModule(){}
    public NFCModule(Context ctx){
        context = ctx;
        registeredApps = new HashMap<String, Profile.SecurityLevel>();
    }

    public void setNewContext(Context ctx) {
        context = ctx;
    }

    public void writeTag(Tag tag, String tagText) {
        Log.d(TAG, "write the tag...");
        MifareUltralight ultralight = MifareUltralight.get(tag);
        try {
            ultralight.connect();
            ultralight.writePage(4, "abcd".getBytes(Charset.forName("US-ASCII")));
            ultralight.writePage(5, "efgh".getBytes(Charset.forName("US-ASCII")));
            ultralight.writePage(6, "ijkl".getBytes(Charset.forName("US-ASCII")));
            ultralight.writePage(7, "mnop".getBytes(Charset.forName("US-ASCII")));
        } catch (IOException e) {
            Log.e(TAG, "IOException while closing MifareUltralight...", e);
        } finally {

            try {
                ultralight.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException while closing MifareUltralight...", e);
            }
        }
    }

    public void readTag(Tag tag) {
        Log.d(TAG, "read the tag...");
        new NdefReaderTask().execute(tag);
    }


    // Background task for reading the data. Do not block the UI thread while reading.
    private class NdefReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            Log.d(TAG, "NDEF reader: " + tag.toString());

            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                Log.d(TAG, "NDEF not supported by this tag");
                // NDEF is not supported by this Tag.
                return null;
            }

            NdefMessage ndefMessage = ndef.getCachedNdefMessage();

            NdefRecord[] records = ndefMessage.getRecords();
            for (NdefRecord ndefRecord : records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported Encoding", e);
                    }
                }
            }

            return null;
        }


        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */


        private String readText(NdefRecord record) throws UnsupportedEncodingException {
            byte[] payload = record.getPayload();

            // Get the Text Encoding
            String textEncoding = ((payload[0] & 128) == 0) ? NFCRecordParser.UTF8 : NFCRecordParser.UTF16;

            // Get the Language Code
            int languageCodeLength = payload[0] & 0063;

            // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            // e.g. "en"

            // Get the Text
            return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }

        @Override
        protected void onPostExecute(String result) {
            //TODO notify activity with result
            Log.d(TAG, "postExecute, tag read: " + result);

//            if (result != null) {
//                mTextView.setText("Read content: " + result);
//            }
        }
    }

    public String readTagFromIntent(Intent intent){
        Log.d(TAG, "Reading tag from intent");
        String result = "";

        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
                Log.d(TAG, "NDEF messages present!");
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            } else {
                // Unknown tag type
                byte[] empty = new byte[0];
                byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                Parcelable tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                byte[] payload = getTagNDEFMessage(tag).getBytes();
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
                NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
                msgs = new NdefMessage[] { msg };
            }

            result = buildStringFromMsgs(msgs);
        }
        Log.d(TAG, result);
        return result;
    }

    public Location StringToLocation(String tagContent){
        //Break the string by 'Record Text:' and take the last element
        String[] parts = tagContent.split("Record Text:");
        tagContent = parts[parts.length-1].replaceAll("\\s+","");
        //Log.d("StringToLocation", tagContent);

        if(MyUtil.isLatLong(tagContent)){
            parts = tagContent.split(",");
            Location loc = new Location("nfc");
            loc.setLatitude(Double.parseDouble(parts[0]));
            loc.setLongitude(Double.parseDouble(parts[1]));
            loc.setAccuracy(0);
            return loc;
        }
        return null;
    }

    //Never worked. Cannot capture events of empty tags.
    public void writeTagFromIntent(Intent intent){
        Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        writeTag(tagFromIntent, "");
        return;
    }

    private String getTagNDEFMessage(Parcelable p) {
        StringBuilder sb = new StringBuilder();
        Tag tag = (Tag) p;
        byte[] id = tag.getId();
        sb.append("Tag ID (hex): ").append(getHex(id)).append("\n");
        sb.append("Tag ID (dec): ").append(getDec(id)).append("\n");
        sb.append("ID (reversed): ").append(getReversed(id)).append("\n");

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : tag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        for (String tech : tag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                MifareClassic mifareTag = MifareClassic.get(tag);
                String type = "Unknown";
                switch (mifareTag.getType()) {
                    case MifareClassic.TYPE_CLASSIC:
                        type = "Classic";
                        break;
                    case MifareClassic.TYPE_PLUS:
                        type = "Plus";
                        break;
                    case MifareClassic.TYPE_PRO:
                        type = "Pro";
                        break;
                }
                sb.append("Mifare Classic type: ");
                sb.append(type);
                sb.append('\n');

                sb.append("Mifare size: ");
                sb.append(mifareTag.getSize() + " bytes");
                sb.append('\n');

                sb.append("Mifare sectors: ");
                sb.append(mifareTag.getSectorCount());
                sb.append('\n');

                sb.append("Mifare blocks: ");
                sb.append(mifareTag.getBlockCount());
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }
        }

        return sb.toString();
    }

    private String getHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private long getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private long getReversed(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = bytes.length - 1; i >= 0; --i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    String buildStringFromMsgs(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0) {
            return "No messages\n";
        }
        String result = "";

        final int sizeMsgs = msgs.length;
        for(int j=0; j<sizeMsgs; j++) {
            NdefMessage message = msgs[0];
            result += message.toString() + "\n";
            NdefRecord[] records = message.getRecords();
            int sizeRecords = records.length;
            for (int i = 0; i < sizeRecords; i++) {
                NdefRecord record = records[i];
                if(NFCRecordParser.isText(record)) {
                    result += "Record Text: " + NFCRecordParser.parseTextRecord(record);
                    Log.d(TAG, "Record Text: " + NFCRecordParser.parseTextRecord(record));
                }else if(NFCRecordParser.isUnknown(record)){
                    result += "Record Unknown: " + NFCRecordParser.parseUnknownRecord(record);
                    Log.d(TAG, "Record Unknown: " + NFCRecordParser.parseUnknownRecord(record));
                }
            }
        }
        return result;
    }


    //ModuleInterface methods
    @Override
    public void registerApp(Profile profile) {
        String cls = profile.getCls();
        Profile.SecurityLevel level = profile.getSecurityLevel();

        //If already exists, it may be a security level update, anyway its always a put
        registeredApps.put(cls, level);
    }

    @Override
    public void unregisterApp(String cls) {
        registeredApps.remove(cls);
    }

    @Override
    public boolean isSecuritySensitive() {
        return false;
    }

    @Override
    public boolean noAppsRegistered() {
        return registeredApps.isEmpty();
    }


    //TODO: make this part of a TriggerInterface
    public static void sendLocationToCollector(Location location){
        //TODO: make MainActivity independent
        /*((MainActivity) context).sendNewLocation(location, new ArrayList<String>(registeredApps.keySet()));
        ((MainActivity) context).updateLocationTextView("NFC update!", location.toString());*/
    }
}
