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

package org.apache.ranger.plugin.util;


import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.policyengine.RangerPolicyEngine;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import com.google.common.base.Objects;

@JsonAutoDetect(fieldVisibility=Visibility.ANY)
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown=true)
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ServicePolicies implements java.io.Serializable {
	private static final long serialVersionUID = 1L;

	private String             serviceName;
	private Long               serviceId;
	private Long               policyVersion;
	private Date               policyUpdateTime;
	private List<RangerPolicy> policies;
	private RangerServiceDef   serviceDef;
	private String             auditMode = RangerPolicyEngine.AUDIT_DEFAULT;
	private TagPolicies        tagPolicies;

	/**
	 * @return the serviceName
	 */
	public String getServiceName() {
		return serviceName;
	}
	/**
	 * @param serviceName the serviceName to set
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	/**
	 * @return the serviceId
	 */
	public Long getServiceId() {
		return serviceId;
	}
	/**
	 * @param serviceId the serviceId to set
	 */
	public void setServiceId(Long serviceId) {
		this.serviceId = serviceId;
	}
	/**
	 * @return the policyVersion
	 */
	public Long getPolicyVersion() {
		return policyVersion;
	}
	/**
	 * @param policyVersion the policyVersion to set
	 */
	public void setPolicyVersion(Long policyVersion) {
		this.policyVersion = policyVersion;
	}
	/**
	 * @return the policyUpdateTime
	 */
	public Date getPolicyUpdateTime() {
		return policyUpdateTime;
	}
	/**
	 * @param policyUpdateTime the policyUpdateTime to set
	 */
	public void setPolicyUpdateTime(Date policyUpdateTime) {
		this.policyUpdateTime = policyUpdateTime;
	}
	/**
	 * @return the policies
	 */
	public List<RangerPolicy> getPolicies() {
		return policies;
	}
	/**
	 * @param policies the policies to set
	 */
	public void setPolicies(List<RangerPolicy> policies) {
		this.policies = policies;
	}
	/**
	 * @return the serviceDef
	 */
	public RangerServiceDef getServiceDef() {
		return serviceDef;
	}
	/**
	 * @param serviceDef the serviceDef to set
	 */
	public void setServiceDef(RangerServiceDef serviceDef) {
		this.serviceDef = serviceDef;
	}

	public String getAuditMode() {
		return auditMode;
	}

	public void setAuditMode(String auditMode) {
		this.auditMode = auditMode;
	}
	/**
	 * @return the tagPolicies
	 */
	public ServicePolicies.TagPolicies getTagPolicies() {
		return tagPolicies;
	}
	/**
	 * @param tagPolicies the tagPolicies to set
	 */
	public void setTagPolicies(ServicePolicies.TagPolicies tagPolicies) {
		this.tagPolicies = tagPolicies;
	}
	
	@Override
	public String toString() {
		return Objects.toStringHelper(this.getClass())
			.add("serviceName", serviceName)
			.add("serviceId", serviceId)
			.add("policyVersion", policyVersion)
			.add("policyUpdateTime", policyUpdateTime)
			.add("policies", policies)
			.add("serviceDef", serviceDef)
			.add("auditMode", auditMode)
			.add("tagPolicies", tagPolicies)
			.toString();
	}

	@JsonAutoDetect(fieldVisibility=Visibility.ANY)
	@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown=true)
	@XmlRootElement
	@XmlAccessorType(XmlAccessType.FIELD)
	public static class TagPolicies implements java.io.Serializable {
		private static final long serialVersionUID = 1L;

		private String             serviceName;
		private Long               serviceId;
		private Long               policyVersion;
		private Date               policyUpdateTime;
		private List<RangerPolicy> policies;
		private RangerServiceDef   serviceDef;
		private String             auditMode = RangerPolicyEngine.AUDIT_DEFAULT;
		/**
		 * @return the serviceName
		 */
		public String getServiceName() {
			return serviceName;
		}
		/**
		 * @param serviceName the serviceName to set
		 */
		public void setServiceName(String serviceName) {
			this.serviceName = serviceName;
		}
		/**
		 * @return the serviceId
		 */
		public Long getServiceId() {
			return serviceId;
		}
		/**
		 * @param serviceId the serviceId to set
		 */
		public void setServiceId(Long serviceId) {
			this.serviceId = serviceId;
		}
		/**
		 * @return the policyVersion
		 */
		public Long getPolicyVersion() {
			return policyVersion;
		}
		/**
		 * @param policyVersion the policyVersion to set
		 */
		public void setPolicyVersion(Long policyVersion) {
			this.policyVersion = policyVersion;
		}
		/**
		 * @return the policyUpdateTime
		 */
		public Date getPolicyUpdateTime() {
			return policyUpdateTime;
		}
		/**
		 * @param policyUpdateTime the policyUpdateTime to set
		 */
		public void setPolicyUpdateTime(Date policyUpdateTime) {
			this.policyUpdateTime = policyUpdateTime;
		}
		/**
		 * @return the policies
		 */
		public List<RangerPolicy> getPolicies() {
			return policies;
		}
		/**
		 * @param policies the policies to set
		 */
		public void setPolicies(List<RangerPolicy> policies) {
			this.policies = policies;
		}
		/**
		 * @return the serviceDef
		 */
		public RangerServiceDef getServiceDef() {
			return serviceDef;
		}
		/**
		 * @param serviceDef the serviceDef to set
		 */
		public void setServiceDef(RangerServiceDef serviceDef) {
			this.serviceDef = serviceDef;
		}

		public String getAuditMode() {
			return auditMode;
		}

		public void setAuditMode(String auditMode) {
			this.auditMode = auditMode;
		}

		@Override
		public String toString() {
			return Objects.toStringHelper(this.getClass())
					.add("serviceName", serviceName)
					.add("serviceId", serviceId)
					.add("policyVersion", policyVersion)
					.add("policyUpdateTime", policyUpdateTime)
					.add("policies", policies)
					.add("serviceDef", serviceDef)
					.add("auditMode", auditMode)
					.toString();
		}
	}
}
