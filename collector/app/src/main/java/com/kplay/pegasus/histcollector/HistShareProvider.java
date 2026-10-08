package com.kplay.pegasus.histcollector;
import android.content.*;import android.database.Cursor;import android.database.MatrixCursor;import android.net.Uri;import android.os.ParcelFileDescriptor;import java.io.*;
public final class HistShareProvider extends ContentProvider{
 public static final String AUTH="com.kplay.pegasus.histcollector.hist";public static final Uri CURRENT=Uri.parse("content://"+AUTH+"/current");
 public boolean onCreate(){return true;}
 public String getType(Uri u){return "application/vnd.sqlite3";}
 public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{
  if(!"r".equals(mode)||u==null||!"/current".equals(u.getPath()))throw new FileNotFoundException("read-only current HIST only");
  File f=getContext().getDatabasePath("pegasus_hist_v1.sqlite");if(!f.exists())throw new FileNotFoundException("HIST DB not found");
  return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
 }
 public Cursor query(Uri u,String[]p,String s,String[]a,String so){File f=getContext().getDatabasePath("pegasus_hist_v1.sqlite");MatrixCursor c=new MatrixCursor(new String[]{"display_name","size"});if(f.exists())c.addRow(new Object[]{"PEGASUS_HIST_V1.sqlite",f.length()});return c;}
 public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException("read only");}
 public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException("read only");}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException("read only");}
}