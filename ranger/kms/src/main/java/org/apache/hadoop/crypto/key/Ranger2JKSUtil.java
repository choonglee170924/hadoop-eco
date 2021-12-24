/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.crypto.key;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.ranger.kms.dao.DaoManager;

public class Ranger2JKSUtil {

	private static final String DEFAULT_KEYSTORE_TYPE = "jceks";
	private static final String ENCRYPTION_KEY = "ranger.db.encrypt.key.password";
	
	public static void showUsage() {
		System.err.println("USAGE: java " + Ranger2JKSUtil.class.getName() + " <KMS_FileName> [KeyStoreType]");
		System.err.println(" If KeyStoreType is not provided, it will be considered as " + DEFAULT_KEYSTORE_TYPE);
		System.err.println(" When execution of this utility, it will prompt for both keystore password and key password.");
	}
	

	public static void main(String[] args) throws IOException {
			if (args.length == 0) {
				System.err.println("Invalid number of parameters found.");
				showUsage();
				System.exit(1);
			}
			else {
				String keyStoreFileName = args[0];
				File f = new File(keyStoreFileName);
				if (! f.exists()) {					
					boolean ret = f.createNewFile();
					if (!ret) {
						System.err.println("Error creating new keystore file. fileName="+ args[0]);
					}
				}
				String keyStoreType = (args.length == 2 ? args[1] : DEFAULT_KEYSTORE_TYPE);
				try {
					KeyStore.getInstance(keyStoreType);
				} catch (KeyStoreException e) {
					System.err.println("ERROR: Unable to get valid keystore for the type [" + keyStoreType + "]");
					showUsage();
					System.exit(1);
				}
				
				new Ranger2JKSUtil().doExportKeysFromJKS(keyStoreFileName, keyStoreType);
				
				System.out.println("Keys from Ranger KMS Database has been successfully exported into " + keyStoreFileName);
				
				System.exit(0);
				
			}
	}
	
	private void doExportKeysFromJKS(String keyStoreFileName, String keyStoreType) {
		char[] keyStorePassword = null;
		char[] keyPassword = null;
		try {
			keyStorePassword = ConsoleUtil.getPasswordFromConsole("Enter Password for the keystore FILE :");
			keyPassword = ConsoleUtil.getPasswordFromConsole("Enter Password for the KEY(s) stored in the keystore:");
			Configuration conf = RangerKeyStoreProvider.getDBKSConf();
			RangerKMSDB rangerkmsDb = new RangerKMSDB(conf);		
			DaoManager daoManager = rangerkmsDb.getDaoManager();
			RangerKeyStore dbStore = new RangerKeyStore(daoManager);
			String password = conf.get(ENCRYPTION_KEY);
			RangerMasterKey rangerMasterKey = new RangerMasterKey(daoManager);
			char[] masterKey = rangerMasterKey.getMasterKey(password).toCharArray();
			OutputStream out = null;
			try {
				out = new FileOutputStream(new File(keyStoreFileName));
				dbStore.engineLoadToKeyStoreFile(out, keyStorePassword, keyPassword, masterKey, keyStoreType);
			}
			finally {
				if (out != null) {
					try {
						out.close();
					} catch (Exception e) {
						throw new RuntimeException("ERROR:  Unable to close file stream for [" + keyStoreFileName + "]", e);
					}
				}
			}
		}
		catch(Throwable t) {
			throw new RuntimeException("Unable to export keys to [" + keyStoreFileName + "] due to exception.", t);
		}
		finally{
			Arrays.fill(keyStorePassword, ' ');
			Arrays.fill(keyPassword, ' ');
		}
	}
	

}
