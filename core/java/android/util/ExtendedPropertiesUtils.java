/*
 * Copyright (C) 2012 ParanoidAndroid Team
 */
package android.util;

import android.os.SystemProperties;
import android.util.Log;

import java.io.*;
import java.lang.Math;
import java.lang.NumberFormatException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class ExtendedPropertiesUtils {
 
    /** @hide */
    public static final String PARANOID_PROPIERTIES = "/system/pad.prop";
    public static final ArrayList<String> mExcludedList = new ArrayList <String>();
    
   public static String getFixedProperty(String prop, String orElse) {
        try {
            String[] props = readFile(PARANOID_PROPIERTIES).split("\n");
            for(int i=0; i<props.length; i++)
				if(props[i].contains("=") && props[i].substring(0, props[i].lastIndexOf("=")).equals(prop) )
					return props[i].replace(prop+"=", "").trim();	
		} catch (Exception e) { e.printStackTrace(); }
        return orElse.trim();
    }

    public static String getProperty(String prop, String orElse) {
        try {
            String[] props = readFile(PARANOID_PROPIERTIES).split("\n");
            for(int i=0; i<props.length; i++){
		if(props[i].contains("=")){
			if(props[i].substring(0, props[i].lastIndexOf("=")).equals(prop)){
				String result = props[i].replace(prop+"=", "").trim();	
				if (result.contains("rom_tablet_base") || result.contains("rom_phone_base"))
					result = getProperty(result, orElse);
				else if (result.contains("rom_current_base"))
					result = SystemProperties.get("ro.sf.lcd_density", orElse);
				return result;	
			}
		}
	}
        } catch (Exception e) { e.printStackTrace(); }
        return orElse.trim();
    }

    public static void fillArray(){
        try {
		FileInputStream fstream = new FileInputStream(PARANOID_PROPIERTIES);
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine = "";
		while ((strLine = br.readLine()) != null){
			if(strLine.contains("=") && strLine.contains(".")){					
				String mLeft  = strLine.substring(0, strLine.lastIndexOf("="));
				String mRight = strLine.replace(mLeft+"=", "").trim();
				if (mLeft.substring(mLeft.lastIndexOf(".") + 1).equals("force") && Integer.parseInt(mRight) == 1)
					mExcludedList.add(mLeft.substring(0, mLeft.lastIndexOf(".")).trim());
			}
		}
		in.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static String readFile(String file) {
    String text = "";
    String removedBadChars = "";
    try{
        FileInputStream f = new FileInputStream(file);
        FileChannel ch = f.getChannel();
        ByteBuffer bb = ByteBuffer.allocateDirect(8192);
        byte[] barray = new byte[8192];

        int nRead, nGet;
        while ((nRead=ch.read(bb)) != -1){
            if (nRead == 0)
                continue;
            bb.position(0);
            bb.limit(nRead);
            while(bb.hasRemaining()){
                nGet = Math.min(bb.remaining(), 8192);
                bb.get( barray, 0, nGet );
                char[] theChars = new char[nGet];
                for (int i = 0; i < nGet;) {
                    theChars[i] = (char)(barray[i++] & 0xff);
                }
                text += new String(theChars);

            }

            bb.clear( );
        }
        removedBadChars = text;
    }
    catch(Exception e){
        e.printStackTrace();
    }
	return removedBadChars;
    }


}
