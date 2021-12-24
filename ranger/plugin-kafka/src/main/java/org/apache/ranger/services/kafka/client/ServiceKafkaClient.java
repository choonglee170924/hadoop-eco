/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.services.kafka.client;

import kafka.utils.ZkUtils;
import kafka.utils.ZkUtils$;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.ZkConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.ranger.plugin.client.BaseClient;
import org.apache.ranger.plugin.service.ResourceLookupContext;
import org.apache.ranger.plugin.util.TimedEventUtil;
import scala.collection.Iterator;
import scala.collection.Seq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ServiceKafkaClient {
	private static final Logger LOG = LogManager.getLogger(ServiceKafkaClient.class);
	private static final String errMessage = " You can still save the repository and start creating "
			+ "policies, but you would not be able to use autocomplete for "
			+ "resource names. Check server logs for more info.";
	private static final String TOPIC_KEY = "topic";
	private static final long LOOKUP_TIMEOUT_SEC = 5;
	String serviceName = null;
	String zookeeperConnect = null;

	public ServiceKafkaClient(String serviceName, String zookeeperConnect) {
		this.serviceName = serviceName;
		this.zookeeperConnect = zookeeperConnect;
	}

	public Map<String, Object> connectionTest() throws Exception {
		String errMsg = errMessage;
		Map<String, Object> responseData = new HashMap<String, Object>();
		try {
			getTopicList(null);
			// If it doesn't throw exception, then assume the instance is
			// reachable
			String successMsg = "ConnectionTest Successful";
			BaseClient.generateResponseDataMap(true, successMsg,
					successMsg, null, null, responseData);
		} catch (IOException e) {
			LOG.error("Error connecting to Kafka. kafkaClient=" + this, e);
			String failureMsg = "Unable to connect to Kafka instance."
					+ e.getMessage();
			BaseClient.generateResponseDataMap(false, failureMsg,
					failureMsg + errMsg, null, null, responseData);
		}
		return responseData;
	}

	private List<String> getTopicList(List<String> ignoreTopicList) throws Exception {
		List<String> ret = new ArrayList<String>();

		int sessionTimeout = 5000;
		int connectionTimeout = 10000;
		ZkClient zkClient = null;
		ZkConnection zkConnection = null;

		try {
			zkClient = ZkUtils$.MODULE$.createZkClient(zookeeperConnect, sessionTimeout, connectionTimeout);
			zkConnection = new ZkConnection(zookeeperConnect, sessionTimeout);

			ZkUtils zkUtils = new ZkUtils(zkClient, zkConnection, true);
			Seq<String> topicList = zkUtils.getChildrenParentMayNotExist(ZkUtils.BrokerTopicsPath());

			Iterator<String> iter = topicList.iterator();
			while (iter.hasNext()) {
				String topic = iter.next();
				if (ignoreTopicList == null || !ignoreTopicList.contains(topic)) {
					ret.add(topic);
				}
			}
		} finally {
			try {
				if (zkClient != null) {
					zkClient.close();
				}
			} catch (Exception ex) {
				LOG.error("Error closing zkClient", ex);
			}

			try {
				if (zkConnection != null) {
					zkConnection.close();
				}

			} catch (Exception ex) {
				LOG.error("Error closing zkConnection", ex);
			}
		}
		return ret;
	}

	/**
	 * @param serviceName
	 * @param context
	 * @return
	 */
	public List<String> getResources(ResourceLookupContext context) {

		String userInput = context.getUserInput();
		String resource = context.getResourceName();
		Map<String, List<String>> resourceMap = context.getResources();
		List<String> resultList = null;
		List<String> topicList = null;

		RESOURCE_TYPE lookupResource = RESOURCE_TYPE.TOPIC;

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== getResources()  UserInput: \"" + userInput
					+ "\" resource : " + resource + " resourceMap: "
					+ resourceMap);
		}

		if (userInput != null && resource != null) {
			if (resourceMap != null && !resourceMap.isEmpty()) {
				topicList = resourceMap.get(TOPIC_KEY);
			}
			switch (resource.trim().toLowerCase()) {
				case TOPIC_KEY:
					lookupResource = RESOURCE_TYPE.TOPIC;
					break;
				default:
					break;
			}
		}

		if (userInput != null) {
			try {
				Callable<List<String>> callableObj = null;
				final String userInputFinal = userInput;

				final List<String> finalTopicList = topicList;

				if (lookupResource == RESOURCE_TYPE.TOPIC) {
					// get the topic list for given Input
					callableObj = new Callable<List<String>>() {
						@Override
						public List<String> call() {
							List<String> retList = new ArrayList<String>();
							try {
								List<String> list = getTopicList(finalTopicList);
								if (userInputFinal != null
										&& !userInputFinal.isEmpty()) {
									for (String value : list) {
										if (value.startsWith(userInputFinal)) {
											retList.add(value);
										}
									}
								} else {
									retList.addAll(list);
								}
							} catch (Exception ex) {
								LOG.error("Error getting topic.", ex);
							}
							return retList;
						}

						;
					};
				}
				// If we need to do lookup
				if (callableObj != null) {
					synchronized (this) {
						resultList = TimedEventUtil.timedTask(callableObj,
								LOOKUP_TIMEOUT_SEC, TimeUnit.SECONDS);
					}
				}
			} catch (Exception e) {
				LOG.error("Unable to get hive resources.", e);
			}
		}

		return resultList;
	}

	@Override
	public String toString() {
		return "ServiceKafkaClient [serviceName=" + serviceName
				+ ", zookeeperConnect=" + zookeeperConnect + "]";
	}

	enum RESOURCE_TYPE {
		TOPIC
	}

}