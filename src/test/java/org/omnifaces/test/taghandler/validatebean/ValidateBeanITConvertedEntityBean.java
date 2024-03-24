/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.test.taghandler.validatebean;

import static org.omnifaces.util.Faces.isValidationFailed;
import static org.omnifaces.util.Messages.addGlobalInfo;
import static org.omnifaces.util.Messages.addGlobalWarn;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.validation.Valid;

@Named
@RequestScoped
public class ValidateBeanITConvertedEntityBean {

    @Valid
    private ValidateBeanITConvertedEntity entity;

    public void action() {
        if (isValidationFailed()) {
            addGlobalWarn(" actionValidationFailed");
        }
        else {
            addGlobalInfo("actionSuccess");
        }
    }

    public ValidateBeanITConvertedEntity getEntity() {
        return entity;
    }

    public void setEntity(ValidateBeanITConvertedEntity entity) {
        this.entity = entity;
    }

}