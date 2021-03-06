/*******************************************************************************
 * Copyright (c) 2017 Chen Chao(cnfree2000@hotmail.com).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Chen Chao  - initial API and implementation
 *******************************************************************************/

package org.sf.feeling.decompiler.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sf.feeling.decompiler.JavaDecompilerPlugin;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class UserUtil
{

	public static final File DecompilerCacheDir = new File( String.valueOf( System.getProperty( "user.home" ) ) //$NON-NLS-1$
			+ File.separatorChar
			+ ".decompiler" ); //$NON-NLS-1$
	private static final File UserJsonFile = new File( DecompilerCacheDir, "user.json" ); //$NON-NLS-1$

	public static String unicodeToString( String str )
	{
		Pattern pattern = Pattern.compile( "(\\\\u(\\p{XDigit}{4}))" ); //$NON-NLS-1$
		Matcher matcher = pattern.matcher( str );
		char ch;
		while ( matcher.find( ) )
		{
			ch = (char) Integer.parseInt( matcher.group( 2 ), 16 );
			str = str.replace( matcher.group( 1 ), ch + "" ); //$NON-NLS-1$
		}
		return str;
	}

	public static JsonObject collectUserIp( )
	{
		InputStream is = null;
		try
		{
			URLConnection connection = new URL( "http://pv.sohu.com/cityjson" ) //$NON-NLS-1$
					.openConnection( );
			connection.setConnectTimeout( 30000 );
			connection.setReadTimeout( 30000 );
			is = connection.getInputStream( );
			String content = FileUtil.getContent( is );
			content = unicodeToString( content.substring( content.indexOf( "=" ) + 1, //$NON-NLS-1$
					content.length( ) - 1 ) );
			JsonObject json = Json.parse( content ).asObject( );
			String ip = json.get( "cip" ).asString( ); //$NON-NLS-1$

			is.close( );

			connection = new URL( "http://ip.taobao.com/service/getIpInfo.php?ip=" + ip ) //$NON-NLS-1$
					.openConnection( );
			connection.setConnectTimeout( 30000 );
			connection.setReadTimeout( 30000 );
			is = connection.getInputStream( );
			content = unicodeToString( FileUtil.getContent( is ) );
			json = Json.parse( content ).asObject( );
			if ( json.getInt( "code", -1 ) == 0 && json.get( "data" ).isObject( ) ) //$NON-NLS-1$ //$NON-NLS-2$
			{
				return json.get( "data" ).asObject( ); //$NON-NLS-1$
			}
		}
		catch ( Exception e )
		{
			Logger.debug( e );
		}
		finally
		{
			if ( is != null )
			{
				try
				{
					is.close( );
				}
				catch ( IOException e )
				{
					Logger.debug( e );
				}
			}
		}
		return null;
	}

	private static synchronized String generateUserMacAddress( )
	{
		List<String> macList = new ArrayList<String>( );
		try
		{
			Enumeration<NetworkInterface> ni = NetworkInterface.getNetworkInterfaces( );
			while ( ni.hasMoreElements( ) )
			{
				NetworkInterface netI = ni.nextElement( );
				byte[] bytes = (byte[]) ReflectionUtils.invokeMethod( netI,
						"getHardwareAddress", //$NON-NLS-1$
						new Class[0],
						new Object[0] );
				Boolean isUp = (Boolean) ReflectionUtils.invokeMethod( netI,
						"isUp", //$NON-NLS-1$
						new Class[0],
						new Object[0] );
				if ( Boolean.TRUE.equals( isUp ) && netI != null && bytes != null && bytes.length == 6 )
				{
					StringBuffer sb = new StringBuffer( );
					for ( byte b : bytes )
					{
						sb.append( Integer.toHexString( ( b & 240 ) >> 4 ) );
						sb.append( Integer.toHexString( b & 15 ) );
						sb.append( "-" ); //$NON-NLS-1$
					}
					sb.deleteCharAt( sb.length( ) - 1 );
					macList.add( sb.toString( ).toUpperCase( ) );
				}
			}
		}
		catch ( Exception e )
		{
			Logger.debug( e );
		}

		if ( macList.isEmpty( ) )
		{
			return null;
		}
		else
		{
			Collections.sort( macList, new Comparator<String>( ) {

				@Override
				public int compare( String o1, String o2 )
				{
					return o1.split( "00" ).length - o2.split( "00" ).length; //$NON-NLS-1$ //$NON-NLS-2$
				}
			} );

			String mac = macList.get( 0 );
			if ( mac.split( "00" ).length > 2 ) //$NON-NLS-1$
			{
				return null;
			}
			return mac;
		}
	}

	private static synchronized JsonObject loadSourceBindingJson( )
	{
		if ( UserJsonFile.exists( ) )
		{
			try
			{
				return Json.parse( FileUtil.getContent( UserJsonFile, "utf-8" ) ) //$NON-NLS-1$
						.asObject( );
			}
			catch ( Exception e )
			{
				Logger.error( "Load source attach binding configuration failed.", //$NON-NLS-1$
						e );
			}
		}
		return saveUser( generateUserMacAddress( ) );
	}

	private static synchronized JsonObject saveUser( String user )
	{
		if ( user == null )
		{
			user = generateUUID( );
		}

		JsonObject userObject = new JsonObject( );
		userObject.set( "user", user ); //$NON-NLS-1$

		if ( user.split( "-" ).length == 6 ) //$NON-NLS-1$
		{
			userObject.set( "uuid", generateUUID( ) ); //$NON-NLS-1$
		}
		else
		{
			userObject.set( "uuid", user ); //$NON-NLS-1$
		}

		userObject.set( "version", VersionUtil.getDecompilerVersion( ) ); //$NON-NLS-1$
		userObject.set( "date", //$NON-NLS-1$
				new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" ) //$NON-NLS-1$
						.format( new Date( System.currentTimeMillis( ) ) ) );
		userObject.set( "count", 0 ); //$NON-NLS-1$
		userObject.set( "adclick", 0 ); //$NON-NLS-1$
		saveSourceBindingJson( userObject );

		return userObject;
	}

	private static String generateUUID( )
	{
		if ( JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).contains( "uuid" ) ) //$NON-NLS-1$
		{
			return JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).getString( "uuid" ); //$NON-NLS-1$
		}
		else
		{
			String uuid = UUID.randomUUID( ).toString( );
			JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).setValue( "uuid", uuid ); //$NON-NLS-1$
			return uuid;
		}
	}

	private static synchronized void saveSourceBindingJson( JsonObject json )
	{
		StringWriter sw = new StringWriter( );
		try
		{
			json.writeTo( sw );
			String jsonString = sw.toString( );
			FileUtil.writeToFile( UserJsonFile, jsonString, "utf-8" ); //$NON-NLS-1$
		}
		catch ( IOException e )
		{
			Logger.debug( e );
		}
		finally
		{
			try
			{
				sw.close( );
			}
			catch ( IOException e )
			{
				Logger.debug( e );
			}
		}
	}

	public static synchronized String[] getUserIds( )
	{
		JsonObject userObject = loadSourceBindingJson( );
		if ( userObject != null )
		{
			String uuid = userObject.getString( "uuid", null ); //$NON-NLS-1$
			if ( uuid != null && uuid.trim( ).length( ) > 0 )
			{
				JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).setValue( "uuid", uuid ); //$NON-NLS-1$
			}
			return new String[]{
					userObject.getString( "user", null ), uuid //$NON-NLS-1$
			};
		}
		return null;
	}

	public static synchronized String getUserUUID( )
	{
		JsonObject userObject = loadSourceBindingJson( );
		if ( userObject != null )
		{
			String uuid = userObject.getString( "uuid", null ); //$NON-NLS-1$
			if ( uuid != null && uuid.trim( ).length( ) > 0 )
			{
				JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).setValue( "uuid", uuid ); //$NON-NLS-1$
			}
			return uuid;
		}
		return null;
	}

	public static synchronized JsonObject updateCount( )
	{
		JsonObject userObject = loadSourceBindingJson( );
		if ( userObject != null )
		{
			long count = userObject.getLong( "count", 0 ); //$NON-NLS-1$
			count += JavaDecompilerPlugin.getDefault( ).getDecompileCount( ).get( );
			userObject.set( "count", count ); //$NON-NLS-1$

			long adclick = userObject.getLong( "adclick", 0 ); //$NON-NLS-1$
			adclick += JavaDecompilerPlugin.getDefault( ).getAdClickCount( ).get( );
			userObject.set( "adclick", adclick ); //$NON-NLS-1$

			saveSourceBindingJson( userObject );
		}
		return userObject;
	}

	public static JsonObject setUserIpInfo( JsonObject userIpInfo )
	{
		JsonObject userObject = loadSourceBindingJson( );
		if ( userObject != null && userIpInfo != null )
		{
			JsonValue ip = userIpInfo.get( "ip" ); //$NON-NLS-1$
			if ( ip != null && ip.isString( ) )
			{
				if ( "".equals( ip.asString( ) ) ) //$NON-NLS-1$
				{
					userObject.remove( "ip" ); //$NON-NLS-1$
				}
				else
				{
					userObject.set( "ip", ip.asString( ) ); //$NON-NLS-1$
				}
			}
			JsonValue country = userIpInfo.get( "country" ); //$NON-NLS-1$
			if ( country != null && country.isString( ) )
			{
				if ( "".equals( country.asString( ) ) ) //$NON-NLS-1$
				{
					userObject.remove( "country" ); //$NON-NLS-1$
				}
				else
				{
					userObject.set( "country", country.asString( ) ); //$NON-NLS-1$
				}
			}
			JsonValue country_code = userIpInfo.get( "country_id" ); //$NON-NLS-1$
			if ( country != null && country.isString( ) )
			{
				if ( "".equals( country.asString( ) ) ) //$NON-NLS-1$
				{
					userObject.remove( "country_code" ); //$NON-NLS-1$
				}
				else
				{
					userObject.set( "country_code", country_code.asString( ) ); //$NON-NLS-1$
				}
			}
			JsonValue province = userIpInfo.get( "region" ); //$NON-NLS-1$
			if ( province != null && province.isString( ) )
			{
				if ( "".equals( province.asString( ) ) ) //$NON-NLS-1$
				{
					userObject.remove( "province" ); //$NON-NLS-1$
				}
				else
				{
					userObject.set( "province", province.asString( ) ); //$NON-NLS-1$
				}
			}
			JsonValue city = userIpInfo.get( "city" ); //$NON-NLS-1$
			if ( province != null && city.isString( ) )
			{
				if ( "".equals( city.asString( ) ) ) //$NON-NLS-1$
				{
					userObject.remove( "city" ); //$NON-NLS-1$
				}
				else
				{
					userObject.set( "city", city.asString( ) ); //$NON-NLS-1$
				}
			}
			saveSourceBindingJson( userObject );
		}
		return userObject;
	}

	public static String getUserIp( )
	{
		JsonObject userObject = loadSourceBindingJson( );
		if ( userObject == null )
			return null;

		JsonValue ipValue = userObject.get( "ip" ); //$NON-NLS-1$
		if ( ipValue != null && ipValue.isString( ) )
		{
			return ipValue.asString( );
		}
		return null;
	}

	public static String getUserProvince( )
	{
		JsonObject userObject = loadSourceBindingJson( );

		if ( userObject == null )
			return null;

		JsonValue provinceValue = userObject.get( "province" ); //$NON-NLS-1$
		if ( provinceValue != null && provinceValue.isString( ) )
		{
			return provinceValue.asString( );
		}

		return null;
	}

	public static String getUserCity( )
	{
		JsonObject userObject = loadSourceBindingJson( );

		if ( userObject == null )
			return null;

		JsonValue cityValue = userObject.get( "city" ); //$NON-NLS-1$
		if ( cityValue != null && cityValue.isString( ) )
		{
			return cityValue.asString( );
		}

		return null;
	}

	public static String getUserCountry( )
	{
		JsonObject userObject = loadSourceBindingJson( );

		if ( userObject == null )
			return null;

		JsonValue countryValue = userObject.get( "country" ); //$NON-NLS-1$
		if ( countryValue != null && countryValue.isString( ) )
		{
			return countryValue.asString( );
		}

		return null;
	}

	public static String getUserCountryCode( )
	{
		JsonObject userObject = loadSourceBindingJson( );

		if ( userObject == null )
			return null;

		JsonValue countryValue = userObject.get( "country_code" ); //$NON-NLS-1$
		if ( countryValue != null && countryValue.isString( ) )
		{
			return countryValue.asString( );
		}

		return null;
	}

	public static Long getUserCount( )
	{
		JsonObject userObject = loadSourceBindingJson( );

		if ( userObject == null )
			return null;

		JsonValue countValue = userObject.get( "count" ); //$NON-NLS-1$
		if ( countValue != null && countValue.isNumber( ) )
		{
			return countValue.asLong( );
		}

		return 0L;
	}

	public static Long getAdClickCount( )
	{
		JsonObject userObject = loadSourceBindingJson( );

		if ( userObject == null )
			return null;

		JsonValue adclickValue = userObject.get( "adclick" ); //$NON-NLS-1$
		if ( adclickValue != null && adclickValue.isNumber( ) )
		{
			return adclickValue.asLong( );
		}

		return 0L;
	}

	public static boolean matchAdCondition( )
	{
		int adCondition = 100;
		if ( JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).contains( "adCondition" ) ) //$NON-NLS-1$
		{
			adCondition = JavaDecompilerPlugin.getDefault( ).getPreferenceStore( ).getInt( "adCondition" ); //$NON-NLS-1$
		}
		if ( UserUtil.getUserCount( ) < 0 )
			return true;
		return UserUtil.getUserCount( ) > adCondition;
	}

	public static void main( String[] args )
	{
		System.out.println( setUserIpInfo( collectUserIp( ) ) );
	}

}
