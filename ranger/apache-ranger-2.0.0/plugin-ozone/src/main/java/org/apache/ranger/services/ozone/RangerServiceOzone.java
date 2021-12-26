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

package org.apache.ranger.services.ozone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.plugin.client.HadoopException;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerService;
import org.apache.ranger.plugin.model.RangerServiceDef;
import org.apache.ranger.plugin.service.RangerBaseService;
import org.apache.ranger.plugin.service.ResourceLookupContext;
import org.apache.ranger.services.ozone.client.OzoneResourceMgr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RangerServiceOzone extends RangerBaseService {

    private static final Log LOG = LogFactory.getLog(RangerServiceOzone.class);

    public RangerServiceOzone() {
        super();
    }

    @Override
    public void init(RangerServiceDef serviceDef, RangerService service) {
        super.init(serviceDef, service);
    }

    @Override
    public Map<String,Object> validateConfig() throws Exception {
        Map<String, Object> ret = new HashMap<String, Object>();
        String serviceName = getServiceName();
        if(LOG.isDebugEnabled()) {
            LOG.debug("<== RangerServiceOzone.validateConfig Service: (" + serviceName + " )");
        }

        if ( configs != null) {
            try  {
                ret = OzoneResourceMgr.connectionTest(serviceName, configs);
            } catch (HadoopException e) {
                LOG.error("<== RangerServiceOzone.validateConfig Error: " + e.getMessage(),e);
                throw e;
            }
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("<== RangerServiceOzone.validateConfig Response : (" + ret + " )");
        }

        return ret;
    }

    @Override
    public List<String> lookupResource(ResourceLookupContext context) throws Exception {
        List<String> ret           = new ArrayList<String>();
        String  serviceName        = getServiceName();
        String  serviceType		   = getServiceType();
        Map<String,String> configs = getConfigs();
        if(LOG.isDebugEnabled()) {
            LOG.debug("==> RangerServiceOzone.lookupResource Context: (" + context + ")");
        }
        if (context != null) {
            try {
                ret  = OzoneResourceMgr.getOzoneResources(serviceName, serviceType, configs,context);
            } catch (Exception e) {
                LOG.error( "<==RangerServiceOzone.lookupResource Error : " + e);
                throw e;
            }
        }
        if(LOG.isDebugEnabled()) {
            LOG.debug("<== RangerServiceOzone.lookupResource Response: (" + ret + ")");
        }
        return ret;
    }

    @Override
    public List<RangerPolicy> getDefaultRangerPolicies() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("==> RangerServiceOzone.getDefaultRangerPolicies() ");
        }

        List<RangerPolicy> ret = super.getDefaultRangerPolicies();

        if (LOG.isDebugEnabled()) {
            LOG.debug("<== RangerServiceOzone.getDefaultRangerPolicies() : " + ret);
        }
        return ret;
    }

}
