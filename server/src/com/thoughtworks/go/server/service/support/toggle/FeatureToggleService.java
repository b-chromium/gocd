/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.service.support.toggle;

import com.thoughtworks.go.server.domain.support.toggle.FeatureToggle;
import com.thoughtworks.go.server.domain.support.toggle.FeatureToggles;

import java.text.MessageFormat;

public class FeatureToggleService {
    private FeatureToggleRepository repository;

    public FeatureToggleService(FeatureToggleRepository repository) {
        this.repository = repository;
    }

    public FeatureToggles allToggles() {
        FeatureToggles availableToggles = repository.availableToggles();
        FeatureToggles userToggles = repository.userToggles();

        return availableToggles.overrideWithTogglesIn(userToggles);
    }

    public boolean isToggleOn(String key) {
        FeatureToggle toggle = allToggles().find(key);
        return toggle != null && toggle.isOn();
    }

    public void changeValueOfToggle(String key, boolean newValue) {
        if (allToggles().find(key) == null) {
            throw new RuntimeException(MessageFormat.format("Feature toggle: ''{0}'' is not valid.", key));
        }
        repository.changeValueOfToggle(key, newValue);
    }
}
