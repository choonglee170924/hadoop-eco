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
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.ranger.kms.dao.DaoManager;

public class JKS2RangerUtil {
	
	private static final String DEFAULT_KEYSTORE_TYPE = "jceks";
	private static final String ENCRYPTION_KEY = "ranger.db.encrypt.key.password";
	
	public static void showUsage() {
		System.err.println("USAGE: java " + JKS2RangerUtil.class.getName() + " <KMS_FileName> [KeyStoreType]");
		System.err.println(" If KeyStoreType is not provided, it will be considered as " + DEFAULT_KEYSTORE_TYPE);
		System.err.println(" When execution of this utility, it will prompt for both keystore password and key password.");
	}
	

	public static void main(String[] args) {
			if (args.length == 0) {
				System.err.println("Invalid number of parameters found.");
				showUsage();
				System.exit(1);
			}
			else {
				String keyStoreFileName = args[0];
				File f = new File(keyStoreFileName);
				if (! f.exists()) {
					System.err.println("File: [" + f.getAbsolutePath() + "] does not exists.");
					showUsage();
					System.exit(1);
				}
				String keyStoreType = (args.length == 2 ? args[1] : DEFAULT_KEYSTORE_TYPE);
				try {
					KeyStore.getInstance(keyStoreType);
				} catch (KeyStoreException e) {
					System.err.println("ERROR: Unable to get valid keystore for the type [" + keyStoreType + "]");
					showUsage();
					System.exit(1);
				}
				
				new JKS2RangerUtil().doImportKeysFromJKS(keyStoreFileName, keyStoreType);
				
				System.out.println("Keys from " + keyStoreFileName + " has been successfully imported into RangerDB.");
				
				System.exit(0);
				
			}
	}
	
	private void doImportKeysFromJKS(String keyStoreFileName, String keyStoreType) {
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
			rangerMasterKey.generateMasterKey(password);		
			char[] masterKey = rangerMasterKey.getMasterKey(password).toCharArray();
			InputStream in = null;
			try {
				in = new FileInputStream(new File(keyStoreFileName));
				dbStore.engineLoadKeyStoreFile(in, keyStorePassword, keyPassword, masterKey, keyStoreType);
				dbStore.engineStore(null,masterKey);	
			}
			finally {
				if (in != null) {
					try {
						in.close();
					} catch (Exception e) {
						throw new RuntimeException("ERROR:  Unable to close file stream for [" + keyStoreFileName + "]", e);
					}
				}
			}
		}
		catch(Throwable t) {
			throw new RuntimeException("Unable to import keys from [" + keyStoreFileName + "] due to exception.", t);
		}
		finally{
			Arrays.fill(keyStorePassword, ' ');
			Arrays.fill(keyPassword, ' ');
		}
	}

}
