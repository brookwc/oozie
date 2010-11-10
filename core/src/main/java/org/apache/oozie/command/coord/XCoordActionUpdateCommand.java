/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. See accompanying LICENSE file.
 */
package org.apache.oozie.command.coord;

import java.util.Date;

import org.apache.oozie.CoordinatorActionBean;
import org.apache.oozie.ErrorCode;
import org.apache.oozie.WorkflowJobBean;
import org.apache.oozie.XException;
import org.apache.oozie.service.JPAService;
import org.apache.oozie.service.Services;
import org.apache.oozie.util.XLog;
import org.apache.oozie.util.db.SLADbOperations;
import org.apache.oozie.client.CoordinatorAction;
import org.apache.oozie.client.WorkflowJob;
import org.apache.oozie.client.SLAEvent.SlaAppType;
import org.apache.oozie.client.SLAEvent.Status;
import org.apache.oozie.command.CommandException;
import org.apache.oozie.command.jpa.CoordActionGetForExternalIdCommand;

public class XCoordActionUpdateCommand extends XCoordinatorCommand<Void> {
    private final XLog log = XLog.getLog(getClass());
    private WorkflowJobBean workflow;
    private CoordinatorActionBean caction = null;

    public XCoordActionUpdateCommand(WorkflowJobBean workflow) {
        //super("coord-action-update", "coord-action-update", 1, XLog.OPS);
        super("coord-action-update", "coord-action-update", 1);
        this.workflow = workflow;
    }

    @Override
    protected Void execute() throws CommandException {
        try {
            log.info("STARTED CoordActionUpdateCommand for wfId=" + workflow.getId());
            
            JPAService jpaService = Services.get().get(JPAService.class);
            if (jpaService == null) {
                log.error(ErrorCode.E0610);
            }
            
            //caction = store.getCoordinatorActionForExternalId(workflow.getId());
            caction = jpaService.execute(new CoordActionGetForExternalIdCommand(workflow.getId()));
            if (caction == null) {
                log.info("ENDED CoordActionUpdateCommand for wfId=" + workflow.getId() + ", coord action is null");
                return null;
            }

            if (workflow.getStatus() == WorkflowJob.Status.RUNNING
                    || workflow.getStatus() == WorkflowJob.Status.SUSPENDED) {
                //update lastModifiedTime
                //cstore.updateCoordinatorAction(caction);
                caction.setLastModifiedTime(new Date());
                jpaService.execute(new org.apache.oozie.command.jpa.CoordActionUpdateCommand(caction));
                    
                return null;
            }
            
            Status slaStatus = null;
            if (caction != null) {
                if (workflow.getStatus() == WorkflowJob.Status.SUCCEEDED) {
                    caction.setStatus(CoordinatorAction.Status.SUCCEEDED);
                    slaStatus = Status.SUCCEEDED;
                }
                else {
                    if (workflow.getStatus() == WorkflowJob.Status.FAILED) {
                        caction.setStatus(CoordinatorAction.Status.FAILED);
                        slaStatus = Status.FAILED;
                    }
                    else {
                        if (workflow.getStatus() == WorkflowJob.Status.KILLED) {
                            caction.setStatus(CoordinatorAction.Status.KILLED);
                            slaStatus = Status.KILLED;
                        }
                        else {
                            log.warn(
                                    "Unexpected workflow " + workflow.getId() + " STATUS " + workflow.getStatus());
                            //update lastModifiedTime
                            //cstore.updateCoordinatorAction(caction);
                            caction.setLastModifiedTime(new Date());
                            jpaService.execute(new org.apache.oozie.command.jpa.CoordActionUpdateCommand(caction));
                            
                            return null;
                        }
                    }
                }

                log.info("Updating Coordintaor id :" + caction.getId() + "status to =" + caction.getStatus());
                //cstore.updateCoordinatorAction(caction);
                caction.setLastModifiedTime(new Date());
                jpaService.execute(new org.apache.oozie.command.jpa.CoordActionUpdateCommand(caction));
                if (slaStatus != null) {
                    SLADbOperations.writeStausEvent(caction.getSlaXml(), caction.getId(), slaStatus,
                                                    SlaAppType.COORDINATOR_ACTION, log);
                }
                queue(new XCoordActionReadyCommand(caction.getJobId()));
            }
        }
        catch (XException ex) {
            log.warn("CoordActionUpdate Failed ", ex.getMessage());
            throw new CommandException(ex);
        }
        return null;
    }

    @Override
    protected String getEntityKey() {
        return null;
    }

    @Override
    protected boolean isLockRequired() {
        return true;
    }

    @Override
    protected void loadState() {

    }

    @Override
    protected void verifyPrecondition() throws CommandException {

    }
}
