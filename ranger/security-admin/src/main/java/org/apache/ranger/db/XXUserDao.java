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

package org.apache.ranger.db;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.ranger.common.db.BaseDao;
import org.apache.ranger.entity.XXUser;
import org.springframework.stereotype.Service;

import javax.persistence.NoResultException;
import java.util.List;

@Service
public class XXUserDao extends BaseDao<XXUser> {
	private static final Logger logger = LogManager.getLogger(XXResourceDao.class);

	public XXUserDao(RangerDaoManagerBase daoManager) {
		super(daoManager);
	}

	public XXUser findByUserName(String name) {
		if (daoManager.getStringUtil().isEmpty(name)) {
			logger.debug("name is empty");
			return null;
		}
		try {
			return getEntityManager()
					.createNamedQuery("XXUser.findByUserName", XXUser.class)
					.setParameter("name", name.trim())
					.getSingleResult();
		} catch (NoResultException e) {
			// ignore
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public List<String> findByPolicyItemId(Long polItemId) {
		if (polItemId == null) {
			return null;
		}
		try {
			return getEntityManager()
					.createNamedQuery("XXUser.findByPolicyItemId")
					.setParameter("polItemId", polItemId).getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}

	public XXUser findByPortalUserId(Long portalUserId) {
		if (portalUserId == null) {
			return null;
		}
		try {
			return getEntityManager().createNamedQuery("XXUser.findByPortalUserId", tClass)
					.setParameter("portalUserId", portalUserId).getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}
}
