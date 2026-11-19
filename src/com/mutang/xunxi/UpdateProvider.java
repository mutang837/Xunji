package com.mutang.xunxi;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Narrow, read-only content URI for the single verified update APK. */
public final class UpdateProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private boolean valid(Uri uri){return uri!=null&&"/latest.apk".equals(uri.getPath())&&getContext()!=null&&UpdateClient.apk(getContext()).isFile();}
    @Override public String getType(Uri uri){return valid(uri)?"application/vnd.android.package-archive":null;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!valid(uri)||!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(UpdateClient.apk(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){if(!valid(uri))return null;String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="Xunji-update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=UpdateClient.apk(getContext()).length();}cursor.addRow(row);return cursor;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
