/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.config.update;

import com.thoughtworks.go.config.CruiseConfig;
import com.thoughtworks.go.config.PipelineTemplateConfig;
import com.thoughtworks.go.config.TemplatesConfig;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.EntityHashingService;
import com.thoughtworks.go.server.service.SecurityService;
import com.thoughtworks.go.server.service.result.LocalizedOperationResult;

import static com.thoughtworks.go.i18n.LocalizedMessage.staleResourceConfig;
import static com.thoughtworks.go.i18n.LocalizedMessage.unauthorizedToEdit;
import static com.thoughtworks.go.serverhealth.HealthStateType.unauthorised;


public class UpdateTemplateConfigCommand extends TemplateConfigCommand {
    private SecurityService securityService;
    private String md5;
    private EntityHashingService entityHashingService;

    public UpdateTemplateConfigCommand(PipelineTemplateConfig templateConfig, Username currentUser, SecurityService securityService, LocalizedOperationResult result, String md5, EntityHashingService entityHashingService) {
        super(templateConfig, result, currentUser);
        this.securityService = securityService;
        this.md5 = md5;
        this.entityHashingService = entityHashingService;
    }

    @Override
    public void update(CruiseConfig modifiedConfig) throws Exception {
        PipelineTemplateConfig existingTemplateConfig = findAddedTemplate(modifiedConfig);
        templateConfig.setAuthorization(existingTemplateConfig.getAuthorization());
        TemplatesConfig templatesConfig = modifiedConfig.getTemplates();
        templatesConfig.removeTemplateNamed(existingTemplateConfig.name());
        templatesConfig.add(templateConfig);
        modifiedConfig.setTemplates(templatesConfig);
    }

    @Override
    public boolean isValid(CruiseConfig preprocessedConfig) {
        return super.isValid(preprocessedConfig, false);
    }

    @Override
    public boolean canContinue(CruiseConfig cruiseConfig) {
        return isRequestFresh(cruiseConfig) && isUserAuthorized();
    }

    private boolean isUserAuthorized() {
        if (!securityService.isAuthorizedToEditTemplate(templateConfig.name(), currentUser)) {
            result.unauthorized(unauthorizedToEdit(), unauthorised());
            return false;
        }
        return true;
    }

    private boolean isRequestFresh(CruiseConfig cruiseConfig) {
        PipelineTemplateConfig pipelineTemplateConfig = findAddedTemplate(cruiseConfig);
        boolean freshRequest = entityHashingService.md5ForEntity(pipelineTemplateConfig).equals(md5);
        if (!freshRequest) {
            result.stale(staleResourceConfig("Template", templateConfig.name()));
        }
        return freshRequest;
    }
}

