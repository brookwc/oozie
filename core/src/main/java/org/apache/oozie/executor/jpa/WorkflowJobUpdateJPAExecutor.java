/**
* Copyright (c) 2010 Yahoo! Inc. All rights reserved.
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License. See accompanying LICENSE file.
*/
package org.apache.oozie.executor.jpa;

import java.util.Date;

import javax.persistence.EntityManager;

import org.apache.oozie.WorkflowJobBean;
import org.apache.oozie.util.ParamChecker;
import org.apache.oozie.ErrorCode;

/**
* Persist the WorkflowJob bean.
*/
public class WorkflowJobUpdateJPAExecutor implements JPAExecutor<String> {

    private WorkflowJobBean wfJob = null;

    public WorkflowJobUpdateJPAExecutor(WorkflowJobBean wfJob) {
        ParamChecker.notNull(wfJob, "wfJob");
        this.wfJob = wfJob;
    }

    /* (non-Javadoc)
     * @see org.apache.oozie.executor.jpa.JPAExecutor#getName()
     */
    @Override
    public String getName() {
        return "WorkflowJobUpdateJPAExecutor";
    }

    /* (non-Javadoc)
     * @see org.apache.oozie.executor.jpa.JPAExecutor#execute(javax.persistence.EntityManager)
     */
    @Override
    public String execute(EntityManager em) throws JPAExecutorException {
        try {
            wfJob.setLastModifiedTime(new Date());
            em.merge(wfJob);
            return null;
        }
        catch (Exception e) {
            throw new JPAExecutorException(ErrorCode.E0603, e);
        }
    }

}