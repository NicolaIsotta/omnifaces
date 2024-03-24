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
package org.omnifaces.event;

import jakarta.faces.FacesWrapper;
import jakarta.faces.component.UIComponent;
import jakarta.faces.event.FacesEvent;
import jakarta.faces.event.FacesListener;
import jakarta.faces.event.PhaseId;

/**
 * Provides a simple implementation of {@link FacesEvent} that can be sub-classed by developers wishing to provide
 * specialized behavior to an existing {@link FacesEvent} instance without the need to implement/override all the
 * methods which do not necessarily need to be implemented. The default implementation of all methods expect of
 * {@link FacesEvent#getSource()} and {@link FacesEvent#getComponent()} is to call through to the wrapped
 * {@link FacesEvent}.
 *
 * @author Bauke Scholtz
 * @since 1.1
 */
public abstract class FacesEventWrapper extends FacesEvent implements FacesWrapper<FacesEvent> {

    // Constants ------------------------------------------------------------------------------------------------------

    private static final long serialVersionUID = 1L;

    // Properties -----------------------------------------------------------------------------------------------------

    private FacesEvent wrapped;

    // Constructors ---------------------------------------------------------------------------------------------------

    /**
     * Construct a new faces event wrapper which wraps the given faces event for the given component.
     * @param wrapped The faces event to be wrapped.
     * @param component The component to broadcast this event for.
     */
    protected FacesEventWrapper(FacesEvent wrapped, UIComponent component) {
        super(component);
        this.wrapped = wrapped;
    }

    // Actions --------------------------------------------------------------------------------------------------------

    @Override
    public void queue() {
        wrapped.queue();
    }

    @Override
    public boolean isAppropriateListener(FacesListener listener) {
        return wrapped.isAppropriateListener(listener);
    }

    @Override
    public void processListener(FacesListener listener) {
        wrapped.processListener(listener);
    }

    // Getters/setters ------------------------------------------------------------------------------------------------

    @Override
    public PhaseId getPhaseId() {
        return wrapped.getPhaseId();
    }

    @Override
    public void setPhaseId(PhaseId phaseId) {
        wrapped.setPhaseId(phaseId);
    }

    @Override
    public FacesEvent getWrapped() {
        return wrapped;
    }

}