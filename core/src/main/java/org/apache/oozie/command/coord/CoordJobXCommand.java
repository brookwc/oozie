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

import java.util.List;

import org.apache.oozie.CoordinatorActionBean;
import org.apache.oozie.CoordinatorJobBean;
import org.apache.oozie.ErrorCode;
import org.apache.oozie.XException;
import org.apache.oozie.command.CommandException;
import org.apache.oozie.command.PreconditionException;
import org.apache.oozie.command.jpa.CoordActionsSubsetGetForJobCommand;
import org.apache.oozie.command.jpa.CoordJobGetCommand;
import org.apache.oozie.service.JPAService;
import org.apache.oozie.service.Services;
import org.apache.oozie.util.ParamChecker;
import org.apache.oozie.util.XLog;

/**
 * Command for loading a coordinator job information
 */
public class CoordJobXCommand extends CoordinatorXCommand<CoordinatorJobBean> {
    private String id;
    private boolean getActionInfo;
    private int start = 1;
    private int len = Integer.MAX_VALUE;
    private final XLog LOG = XLog.getLog(CoordJobXCommand.class);

    /**
     * @param id coord jobId
     */
    public CoordJobXCommand(String id) {
        this(id, 1, Integer.MAX_VALUE);
    }

    /**
     * @param id coord jobId
     * @param start starting index in the list of actions belonging to the job
     * @param length number of actions to be returned
     */
    public CoordJobXCommand(String id, int start, int length) {
        super("job.info", "job.info", 1);
        this.id = ParamChecker.notEmpty(id, "id");
        this.getActionInfo = true;
        this.start = start;
        this.len = length;
    }

    /**
     * @param id coord jobId
     * @param getActionInfo false to ignore loading actions for the job
     */
    public CoordJobXCommand(String id, boolean getActionInfo) {
        super("job.info", "job.info", 1);
        this.id = ParamChecker.notEmpty(id, "id");
        this.getActionInfo = getActionInfo;
    }

    @Override
    protected boolean isLockRequired() {
        return false;
    }

    @Override
    protected String getEntityKey() {
        return this.id;
    }

    @Override
    protected void loadState() throws CommandException {
    }

    @Override
    protected void verifyPrecondition() throws CommandException, PreconditionException {
    }

    @Override
    protected CoordinatorJobBean execute() throws CommandException {
        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            CoordinatorJobBean coordJob = null;
            if (jpaService != null) {
                coordJob = jpaService.execute(new CoordJobGetCommand(id));
                if (getActionInfo) {
                    List<CoordinatorActionBean> coordActions = jpaService.execute(new CoordActionsSubsetGetForJobCommand(id, start, len));
                    coordJob.setActions(coordActions);
                }
            }
            else {
                LOG.error(ErrorCode.E0610);
            }
            return coordJob;
        }
        catch (XException ex) {
            throw new CommandException(ex);
        }
    }

}
