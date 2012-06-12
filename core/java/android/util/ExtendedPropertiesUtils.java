/*
 * Copyright (C) 2012 ParanoidAndroid Team
 */

package android.util;

import android.app.*;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.*;
import android.content.res.Resources;
import android.content.res.CompatibilityInfo;
import android.os.SystemProperties;
import android.util.Log;

import java.io.*;
import java.util.*;
import java.lang.Math;
import java.lang.NumberFormatException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public class ExtendedPropertiesUtils {
 
    public static class ParanoidAppInfo {
        public boolean Active = false;
        public int Pid = 0;
        public ApplicationInfo Info = null;
        public String Name = "";
        public String Path = "";
        public int Mode = 0;
        public int ScreenWidthDp = 0;
        public int ScreenHeightDp = 0;
        public int ScreenLayout = 0;
        public int Dpi = 0;
        public float ScaledDensity = 0;
        public float Density = 0;
        public boolean Force = false;
    
    }

    // STATIC PROPERTIES
    public static final String PARANOID_PROPIERTIES = "/system/pad.prop";
    public static ActivityThread mParanoidMainThread = null;
    public static Context mParanoidContext = null;
    public static PackageManager mParanoidPackageManager = null;
    public static List<PackageInfo> mParanoidPackageList;
    public static ArrayList<String> mExcludedList = new ArrayList <String>();

    public static ParanoidAppInfo mParanoidGlobalHook = new ParanoidAppInfo();
    public static ParanoidAppInfo mParanoidLocalHook  = new ParanoidAppInfo();

    public static String paranoidStatus() {
        return " T:" + (mParanoidMainThread != null) + " CXT:" + (mParanoidContext != null) + " PM:" + (mParanoidPackageManager != null);
    }

    // SET UP HOOK BY READING OUT PAD.PROP
    public static void paranoidConfigure(ParanoidAppInfo Info) {

        if (!getProperty("hybrid_mode", "0").equals("1"))
            return;

        // CONFIGURE LAYOUT
	Info.Mode = Integer.parseInt(getProperty(Info.Name + ".mode", "0"));
	switch (Info.Mode) {
	    case 1:  
	        Info.ScreenWidthDp = Integer.parseInt(getProperty("screen_default_width", "0"));
 		Info.ScreenHeightDp = Integer.parseInt(getProperty("screen_default_height", "0"));
		Info.ScreenLayout = Integer.parseInt(getProperty("screen_default_layout", "0"));
	    break;
	    case 2: 
		Info.ScreenWidthDp = Integer.parseInt(getProperty("screen_opposite_width", "0"));
		Info.ScreenHeightDp = Integer.parseInt(getProperty("screen_opposite_height", "0"));
		Info.ScreenLayout = Integer.parseInt(getProperty("screen_opposite_layout", "0"));
	   break;
        
        }

        // CONFIGURE DPI
	Info.Dpi = Integer.parseInt(getProperty(Info.Name + ".dpi", "0"));

	// CONFIGURE DENSITIES
	Info.Density = Float.parseFloat(getProperty(Info.Name + ".den", "0"));
	Info.ScaledDensity = Float.parseFloat(getProperty(Info.Name + ".sden", "0"));

	// CALCULATE RELATIONS, IF NEEDED
	if (Info.Dpi != 0) {			
	    Info.Density = Info.Density == 0 ? Info.Dpi / (float) 160 : Info.Density;
	    Info.ScaledDensity = Info.ScaledDensity == 0 ? Info.Dpi / (float) 160 : Info.ScaledDensity;
	}

        // FLAG AS READY TO GO
        Info.Active = true;
    
    }

    // INITIALIZE HOOK BY CATCHING A RUNNING THREAD, THIS ALSO FILLS THE GLOBAL-PROCESS HOOK
    public static void paranoidInit(ActivityThread Thread) {
        // INIT PARANOID HYBRID
        // something should be done here to prevent the system from overriding
        if (mParanoidMainThread == null) {  
            try {                
                mParanoidMainThread = Thread;
                ContextImpl context = ContextImpl.createSystemContext(mParanoidMainThread);
                LoadedApk info = new LoadedApk(mParanoidMainThread, "android", context, null,
                    CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO);
                context.init(info, null, mParanoidMainThread);
                mParanoidContext = context;
                mParanoidPackageManager = mParanoidContext.getPackageManager();
                mParanoidPackageList = mParanoidPackageManager.getInstalledPackages(0);	
                mParanoidGlobalHook.Pid = android.os.Process.myPid();
                mParanoidGlobalHook.Info = getAppInfoFromPID(mParanoidGlobalHook.Pid);
                if (mParanoidGlobalHook.Info != null) {
                    mParanoidGlobalHook.Name = mParanoidGlobalHook.Info.packageName;
                    mParanoidGlobalHook.Path = 
                        mParanoidGlobalHook.Info.sourceDir.substring(0, mParanoidGlobalHook.Info.sourceDir.lastIndexOf("/")); 
                    paranoidConfigure(mParanoidGlobalHook);
                    //Log.i("PARANOID:init", "App=" + mParanoidGlobalHook.Name + " Dpi=" + mParanoidGlobalHook.Dpi + 
                    //    " Mode=" + mParanoidGlobalHook.Mode);
                }    
           } catch (Exception e) { 
                Log.i("PARANOID:init", "ERR: init crashed! Status=" + paranoidStatus()); 
                mParanoidMainThread = null;
           } 
       }
    }

    public boolean paranoidOverride(ApplicationInfo Info) {
        if (Info != null) {
            mParanoidLocalHook.Pid  = android.os.Process.myPid();
            mParanoidLocalHook.Info = Info;
            if (mParanoidLocalHook.Info != null) {
                mParanoidLocalHook.Name = mParanoidLocalHook.Info.packageName;
                mParanoidLocalHook.Path = 
                    mParanoidLocalHook.Info.sourceDir.substring(0, mParanoidLocalHook.Info.sourceDir.lastIndexOf("/")); 
                paranoidConfigure(mParanoidLocalHook);
                //Log.i("PARANOID:override", "App=" + mParanoidLocalHook.Name + " RealApp=" + mParanoidGlobalHook.Name + 
                //    " Dpi=" + mParanoidLocalHook.Dpi + " Mode=" + mParanoidLocalHook.Mode);
            
            }
            return true;
        } else
            return false;
    
    }

    // COMPONENTS CAN OVERRIDE THEIR PROCESS-HOOK
    public boolean paranoidOverride(String Fullname) {
        if (Fullname != null) {
            mParanoidLocalHook.Pid  = android.os.Process.myPid();
            mParanoidLocalHook.Info = getAppInfoFromPath(Fullname);
            if (mParanoidLocalHook.Info != null) {
                mParanoidLocalHook.Name = mParanoidLocalHook.Info.packageName;
                mParanoidLocalHook.Path = 
                    mParanoidLocalHook.Info.sourceDir.substring(0, mParanoidLocalHook.Info.sourceDir.lastIndexOf("/")); 
                paranoidConfigure(mParanoidLocalHook);
                //Log.i("PARANOID:override", "App=" + mParanoidLocalHook.Name + " RealApp=" + mParanoidGlobalHook.Name + 
                //    " Dpi=" + mParanoidLocalHook.Dpi + " Mode=" + mParanoidLocalHook.Mode);
            }
            return true;
        } else
            return false;
    }

    // COMPONENTS CAN COPY ANOTHER COMPONENTS HOOK
    public boolean paranoidOverride(ExtendedPropertiesUtils New) {
        if (New != null && New.mParanoidLocalHook.Active) {
            mParanoidLocalHook.Active = New.mParanoidLocalHook.Active;
            mParanoidLocalHook.Pid = New.mParanoidLocalHook.Pid;
            mParanoidLocalHook.Info = New.mParanoidLocalHook.Info;
            mParanoidLocalHook.Name = New.mParanoidLocalHook.Name;
            mParanoidLocalHook.Path = New.mParanoidLocalHook.Path;
            mParanoidLocalHook.Mode = New.mParanoidLocalHook.Mode;
            mParanoidLocalHook.ScreenWidthDp = New.mParanoidLocalHook.ScreenWidthDp;
            mParanoidLocalHook.ScreenHeightDp = New.mParanoidLocalHook.ScreenHeightDp;
            mParanoidLocalHook.ScreenLayout = New.mParanoidLocalHook.ScreenLayout;
            mParanoidLocalHook.Dpi = New.mParanoidLocalHook.Dpi;
            mParanoidLocalHook.ScaledDensity = New.mParanoidLocalHook.ScaledDensity;
            mParanoidLocalHook.Density = New.mParanoidLocalHook.Density;
            mParanoidLocalHook.Force = New.mParanoidLocalHook.Force;
            //Log.i("PARANOID:override", "App=" + mParanoidLocalHook.Name + " RealApp=" + mParanoidGlobalHook.Name + 
            //    " Dpi=" + mParanoidLocalHook.Dpi + " Mode=" + mParanoidLocalHook.Mode);
            return true;
        } else
            return false;
    }

    public static  boolean paranoidGetActive(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Active : mParanoidGlobalHook.Active; 
    }

    public static int paranoidGetPid(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Pid : mParanoidGlobalHook.Pid; 
    }

    public static ApplicationInfo paranoidGetInfo(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Info : mParanoidGlobalHook.Info; 
    }

    public static String paranoidGetName(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Name : mParanoidGlobalHook.Name; 
    }

    public static String paranoidGetPath(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Path : mParanoidGlobalHook.Path; 
    }

    public static int paranoidGetMode(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Mode : mParanoidGlobalHook.Mode; 
    }

    public static int paranoidGetScreenWidthDp(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.ScreenWidthDp : mParanoidGlobalHook.ScreenWidthDp; 
    }

    public static int paranoidGetScreenHeightDp(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.ScreenHeightDp : mParanoidGlobalHook.ScreenHeightDp; 
    }

    public static int paranoidGetScreenLayout(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.ScreenLayout : mParanoidGlobalHook.ScreenLayout; 
    }

    public static int paranoidGetDpi(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Dpi : mParanoidGlobalHook.Dpi; 
    }

    public static float paranoidGetScaledDensity(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.ScaledDensity : mParanoidGlobalHook.ScaledDensity; 
    }

    public static float paranoidGetDensity(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Density : mParanoidGlobalHook.Density; 
    }

    public static boolean paranoidGetForce(){ 
     return mParanoidLocalHook.Active ? mParanoidLocalHook.Force : mParanoidGlobalHook.Force; 
    }

    public static ApplicationInfo getAppInfoFromPath(String Path) {
	for(int i=0; mParanoidPackageList != null && i<mParanoidPackageList.size(); i++) {
		PackageInfo p = mParanoidPackageList.get(i);
		if (p.applicationInfo != null && p.applicationInfo.sourceDir.equals(Path))		
			return p.applicationInfo;
	}
	return null;
    }

    public static ApplicationInfo getAppInfoFromPackageName(String PackageName) {
	for(int i=0; mParanoidPackageList != null && i<mParanoidPackageList.size(); i++) {
		PackageInfo p = mParanoidPackageList.get(i);
		if (p.applicationInfo != null && p.applicationInfo.packageName.equals(PackageName))		
			return p.applicationInfo;
	}
	return null;
    }

    public static ApplicationInfo getAppInfoFromPID(int PID) {
	if (mParanoidContext != null) {
		List mProcessList = ((ActivityManager)mParanoidContext.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();		 
                Iterator mProcessListIt = mProcessList.iterator();
		while(mProcessListIt.hasNext()) {
			ActivityManager.RunningAppProcessInfo mAppInfo = (ActivityManager.RunningAppProcessInfo)(mProcessListIt.next());
			if(mAppInfo.pid == PID)
				return getAppInfoFromPackageName(mAppInfo.processName);
                }
	}
		return null;
    }

    public void paranoidLog(String Message) {
        Log.i("PARANOID:" + Message, "Init=" + (mParanoidMainThread != null && mParanoidContext != null && mParanoidPackageManager != null) + 
            " App=" + paranoidGetName() + " Dpi=" + paranoidGetDpi() + " Mode=" + paranoidGetMode());
    }

    public void paranoidTrace(String Message) {
        StringWriter sw = new StringWriter();
        new Throwable("").printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        Log.i("PARANOID:" + Message, "Trace=" + stackTrace); 
    }

   public static String getFixedProperty(String prop, String orElse) {
        try {
            String[] props = readFile(PARANOID_PROPIERTIES).split("\n");
            for(int i=0; i<props.length; i++){
			if(props[i].contains("=") && props[i].substring(0, props[i].lastIndexOf("=")).equals(prop))
				return props[i].replace(prop+"=", "").trim();
            }
	} catch (Exception e) {
            e.printStackTrace(); 
        }
        return orElse.trim();
    }

    public static String getProperty(String prop){
        return getProperty(prop, null);
    }

    public static String getProperty(String prop, String orElse) {
        try {
            String[] props = readFile(PARANOID_PROPIERTIES).split("\n");
            for(int i=0; i<props.length; i++){
		if(props[i].contains("=")){
			if(props[i].substring(0, props[i].lastIndexOf("=")).equals(prop)){
				String result = props[i].replace(prop+"=", "").trim();	
				if (result.contains("rom_current_base"))
					result = SystemProperties.get("ro.sf.lcd_density", orElse);
                                else if (!isParsableToInt(result))
					result = getProperty(result, orElse);
				return result;	
			}
	         }
	    
            }
         } catch (Exception e) {
              e.printStackTrace();
        }
        return orElse.trim();
    }

    public static boolean isParsableToInt(String toParse){
        try {
            Integer.parseInt(toParse);
            return true;
        } catch(Exception e){
            return false;
        }
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
        
        } catch (Exception e) {
             e.printStackTrace(); 
        }
    
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
                bb.get(barray, 0, nGet);
                char[] theChars = new char[nGet];
                for (int i = 0; i < nGet;) {
                    theChars[i] = (char)(barray[i++] & 0xff);
                }
                text += new String(theChars);           
            }
            bb.clear();   
        }
        removedBadChars = text;
    } catch(Exception e){
        e.printStackTrace();
    }
	return removedBadChars;
    }


}
