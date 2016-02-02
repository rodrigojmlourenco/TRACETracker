package org.trace.tracker.utils;

import android.nfc.NdefRecord;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;


public class NFCRecordParser {

    public final static String UTF8 = "UTF-8", UTF16="UTF-16";

    public static String parseTextRecord(NdefRecord record) {
        try {
            byte[] payload = record.getPayload();
        /*
         * payload[0] contains the "Status Byte Encodings" field, per the
         * NFC Forum "Text Record Type Definition" section 3.2.1.
         *
         * bit7 is the Text Encoding Field.
         *
         * if (Bit_7 == 0): The text is encoded in UTF-8 if (Bit_7 == 1):
         * The text is encoded in UTF16
         *
         * Bit_6 is reserved for future use and must be set to zero.
         *
         * Bits 5 to 0 are the length of the IANA language code.
         */
            String textEncoding = ((payload[0] & 0200) == 0) ? UTF8 : UTF16;
            int languageCodeLength = payload[0] & 0077;
            String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
            String text =
                    new String(payload, languageCodeLength + 1,
                            payload.length - languageCodeLength - 1, textEncoding);
            return text;
        } catch (UnsupportedEncodingException e) {
            // should never happen unless we get a malformed tag.
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean isText(NdefRecord record) {
        return (record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT));
    }

    public static boolean isUnknown(NdefRecord record){
        return record.getTnf() == NdefRecord.TNF_UNKNOWN;
    }

    public static String parseUnknownRecord(NdefRecord record){
        return new String(record.getPayload());
    }

}
